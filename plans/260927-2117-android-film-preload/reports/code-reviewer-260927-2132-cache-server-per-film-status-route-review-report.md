# Code review: cache server per-film status route

Worktree `/home/andre/Workspace/mediagram-preload`, branch `feat/android-film-preload`, base `225b3bd6`, uncommitted diff (18 files, +342/-11).

## Verdict

The server side is correct and safe. It uses a single mutex with no nesting, validates the id before any filesystem call, never touches `order` or chunk mtimes, and does not shadow any existing route. All tests pass. There are two Medium issues on the Android side, both cheap to fix before phase 02 builds on this:

- `setStatus` cannot be reached through DI.
- Its KDoc describes the 404 contract wrongly and contains a plan reference.

## Checks run

| Check | Result |
|---|---|
| `cargo test -p mediagram-cache` | 67 lib + 7 `api` + 11 `api_rejections`, 0 failed |
| `cargo clippy -p mediagram-cache --all-targets` | clean |
| `cargo fmt -p mediagram-cache -- --check` | clean |
| `./gradlew :core:playback:testDebugUnitTest --rerun` | 220 tests, 0 failures (`LanChunkClientTest` 17) |
| Mutation: `set_status` refreshes the set's chunks (scratch copy) | caught by `set_status_does_not_refresh_lru_order` |
| Plan references in added code lines | 1 (finding 2) |

## Findings, ranked

### 1. Medium: `setStatus` cannot be reached by any injected consumer
- **Where:** `android/core/playback/src/main/kotlin/LanChunkClient.kt:194`. The only instance is bound as the interface in `android/feature/player/src/main/kotlin/di/LanCacheModule.kt:41` (`fun provideLanChunkProtocol(): LanChunkProtocol = LanChunkClient()`), and `LanCacheRuntime.client` is typed `LanChunkProtocol` (`LanCacheRuntime.kt:33`).
- **Scenario:** the phase-03 film-page ViewModel injects `LanCacheRuntime` or `LanChunkProtocol`, and `client.setStatus(...)` does not compile. That leaves three options:
  - Downcast with `(client as? LanChunkClient)?.setStatus(...)`. This returns `null` under every test fake, so the "Home server" line would never be tested.
  - Add a second `@Provides LanChunkClient`.
  - Promote the method to the interface after all, which touches 5 fakes: `LanFirstChunkSourceTest`, `LanWriteQueueTest`, `LanCacheViewModelTest.FakeClient`, `CacheSectionTest.NoopLanChunkProtocol`, plus the class itself.

  The implementer's report says it is "callable identically (`client.setStatus(...)`)". That holds only for code that constructs `LanChunkClient` itself, and nothing does.
- **Fix:** put it on the interface with a default body. No fake needs to change, and the default of `null` has the same meaning as "older server":
  ```kotlin
  // LanChunkProtocol
  suspend fun setStatus(baseUrl: String, setId: String): LanSetStatus? = null
  // LanChunkClient
  override suspend fun setStatus(...)
  ```
- **On the implementer's call (concrete class, not the interface):** I disagree, for the reason above. The default-method form costs nothing now and removes the decision from phase 02.

### 2. Medium: the KDoc gets the 404 contract wrong and contains a plan reference
- **Where:** `LanChunkClient.kt:187-193`: "`null` for a 404 — an id this server was never asked to hold, or (before phase 01 ships to it) a server too old…". The same wrong claim appears in:
  - `LanChunkClientTest.kt:183`: "An id this server has never heard of does not exist as a route…"
  - The `docs/project-changelog.md` 0.70.0 entry: "(an id this server was never asked to hold, …)"
- **What the server actually does:** a well-formed unknown id gets `200 {"total":null,"chunks_held":0,"bytes_held":0}` (`http.rs:165-168`; asserted by `tests/api.rs` `set_status_of_an_unknown_id_is_200_with_zeros_and_a_null_total`). A 404 means either a malformed id or a server without the route.
- **Scenario:** following the KDoc, a phase-03 author hides the line on `null` and shows it otherwise. A film the server holds nothing of then renders "Home server: 0 B of …". Decision 2 in `plan.md` says the line should appear only "when the server holds any of it".
- **Fix:** reword all three places, for example: "`null` for a 404 (a server too old to know this route), a network error or a malformed body. An id this server holds nothing of is not a 404: it is a 200 with zero chunks." Drop "(before phase 01 ships to it)"; user rule 5 forbids plan references in code.

### 3. Low: the module doc in `http.rs` now states something false
- **Where:** `crates/mediagram-cache/src/http.rs:3-8`: "the 404 a bad shape gets is indistinguishable from one a well-formed but absent id gets … nothing here confirms whether a set exists to a caller that never proved it holds the pairing token."
- **Why it is now false:** the fifth route distinguishes a bad shape (404) from an absent id (200 with zeros). It also confirms, without authentication, that a set exists and how large it is.
- **Why the code stays:** the spec's Security section accepts this exposure (HEAD on a chunk already reveals it), so the doc is what needs to change.
- **Scenario:** a later change "restores" the documented invariant by returning 404 for unknown ids. That breaks finding 2's contract and turns Android's `null` into "older server or empty", which the client cannot tell apart.
- **Fix:** reword it to say that a bad shape is a 404 before any filesystem call, and that reads (the per-set status included) are open to the LAN.

### 4. Low: `total == null` does not mean "nothing held" (a contract to carry into phases 02/03)
- **Where:** `store.rs:187-188` reads `total` outside the lock, then snapshots the index.
- **Cases:**
  - A first PUT that lands between those two steps gives `total: null, chunks_held: 1`.
  - An eviction that lands between them gives `total: Some, chunks_held: 0`.
  - The first case can also persist: `scan.rs:50-53` trusts chunks in a set with no readable `total` (a hand-deleted file, or an empty one left by a legacy build).
- **Scenario:** a UI that treats `total == null` as "server has none", or that uses `total` as the "y GB", flickers or mislabels for one poll, or permanently for a set whose `total` file is missing.
- **Fix:** no code change. Reading `total` under the lock would put a disk read (possibly from a spun-down USB disk) behind the global index mutex, which is worse. Document it in the `LanSetStatus` KDoc, and have phase 03 decide whether to show the line from `bytesHeld > 0`, with "y" taken from the film's own known size.
- **Related doc fix:** the `SetStatus.total` doc (`store.rs:45-47`) says "no chunk of it has ever been PUT here". Full eviction also clears it, as `store_lru_tests.rs:130-132` asserts. Change the wording to "none of it held since …, or no readable total".

### 5. Low: the `read_valid` doc is backwards (pre-existing, but this diff edits it)
- **Where:** `store/total.rs:60-63`: "only a missing file and a genuinely unreadable one are `Err`/`Ok(None)` respectively". The code does the reverse: a missing file gives `Ok(None)` and an unreadable one gives `Err`.
- **Why it matters now:** the function has a second caller, which will read this doc.
- **Fix:** swap the two.

### 6. Nit: file sizes (the implementer's other call)
- `http.rs` went from 192 to 219 lines, `store.rs` from 199 to 222, and `LanChunkClient.kt` from 195 to 230.
- The repo has about 20 non-test files over 200 lines, so the guideline is soft here. Leaving the Rust files as they are is acceptable.
- On the Kotlin side, taking finding 1 gives a natural seam: move `LanChunkProtocol` and its value types (`LanServerStatus`, `LanSetStatus`, `LanPutResult`, lines 11-63) into `LanChunkProtocol.kt`. That brings `LanChunkClient.kt` to about 180 lines at no cost.
- Optional: `setStatus` repeats `status()` almost line for line (`LanChunkClient.kt:170-185` and `194-210`). A private helper taking the path and a parser would remove about 12 lines.

## Verified OK (no action)

- **Locking:**
  - `set_totals(&self)` runs on a temporary guard that is dropped at the end of the `let`.
  - There is one mutex, no nested acquisition, and no filesystem I/O under the lock in `set_status`.
  - `put`/`evict` hold the same single lock, so there is no lock-order risk.
  - `set_totals` cannot panic, so it cannot poison the mutex.
- **Cost:** the default budget is 64 GiB (`config.rs:12`), so the filter over `meta` covers at most about 65k entries. That is sub-millisecond under the mutex at the poll rate.
- **Path traversal:** `validate_id` runs before `blocking`. axum percent-decodes the path, so `%2e%2e` arrives as `..` and is rejected by `valid_id`.
- **Routes:**
  - `/v1/sets/{id}` does not shadow `/v1/sets/{id}/chunks/{n}`.
  - HEAD is served by `get`.
  - A PUT to the new path now gets 405 instead of 404, which is harmless.
  - An older server returns 404, which becomes `null`.
- **LRU and mtime:** `set_status` never opens a chunk file and never touches `order`. A mutation that refreshes the set's chunks inside `set_status` is caught by the new test.
- **Contracts:**
  - The `/v1/status` JSON, `LanServerStatus` and `LanChunkProtocol` are unchanged, so no fake in any module is affected.
  - `LanSetStatus` does not collide with any existing name.
  - JSON key order (serde sorts keys into a BTreeMap) does not matter to the regex parser.
- **Spec tests:** empty, partial, complete, after eviction, invalid id → 404, and the LRU case are all present. mtime is not asserted directly, which is acceptable because the code never opens chunk files.
- **Versioning:** 0.70.0 in `Cargo.toml`, `Cargo.lock` (only the 5 member entries), `web/package.json` and `versionName`; `versionCode` is untouched. Whichever branch merges second renumbers, per `plan.md`.
- **Docs:** the `system-architecture.md` row is correct. `running-the-player.md` covers only signing and needs no change.

## Unresolved questions

- Should the "y" in phase 03's "Home server: x of y GB" come from the film's own `totalSize` or from the server's `total`? Finding 4 argues for the film's own size.
