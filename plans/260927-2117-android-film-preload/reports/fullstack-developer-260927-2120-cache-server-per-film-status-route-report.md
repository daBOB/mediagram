## Phase Implementation Report

### Executed Phase
- Phase: phase-01-cache-server-per-film-status-route
- Plan: /home/andre/Workspace/mediagram-preload/plans/260927-2117-android-film-preload
- Status: completed

### Files Modified

Rust (`crates/mediagram-cache`):
- `src/http.rs` (+29) — `validate_id`, `set_status` handler, route registration, doc comment ("four rows" → "five rows")
- `src/store.rs` (+23) — `SetStatus` struct, `ChunkStore::set_status`
- `src/store/index.rs` (+16) — `Index::set_totals`
- `src/store/index_tests.rs` (+12) — unit test for `set_totals`
- `src/store/total.rs` (+7, net) — `read_valid` made `pub(super)`, doc updated
- `src/store_tests.rs` (+45) — empty/partial/complete
- `src/store_lru_tests.rs` (+45) — after-eviction, no-LRU-effect, plus an assertion added to the existing full-eviction test
- `tests/api.rs` (+35) — route-level: unknown id 200 zeros/null, partial-fill 200
- `tests/api_rejections.rs` (+14) — route-level: invalid id 404

Android (`android/core/playback`):
- `src/main/kotlin/LanChunkClient.kt` (+35) — `LanSetStatus` data class, `LanChunkClient.setStatus`, `setStatusPath`
- `src/main/kotlin/LanChunkSigning.kt` (+13) — `setStatusFromJson`
- `src/test/kotlin/LanChunkClientTest.kt` (+47) — 5 new tests against `MockWebServer`

Docs/versioning:
- `docs/system-architecture.md` (+1) — new route row beside `/v1/status`
- `docs/project-changelog.md` (+15) — `0.70.0` entry at the top
- `Cargo.toml`, `web/package.json`, `android/app/build.gradle.kts` — `0.69.2` → `0.70.0` (regex-matched on the version field, not the literal string); `versionCode` left at 18 per the project's own rule
- `Cargo.lock` — the five workspace members' `version` fields only (`cargo check -p mediagram-cache`, not `cargo generate-lockfile`; see Issues below)

Plan bookkeeping: `phase-01-*.md` Todo boxes ticked, status `pending` → `done`; `plan.md`'s phase table row → `done`.

### Tasks Completed
- [x] count per set (index or scan) — index, not a scan (see Design below)
- [x] `GET /v1/sets/{id}` + tests
- [x] clippy clean
- [x] `LanChunkClient.setStatus` + test (404 → null)
- [x] docs, version, changelog

### Design choice: index vs. scan

Went with the in-memory index for `chunks_held`/`bytes_held`, a filesystem
read for `total`:

- The index's `meta: HashMap<ChunkKey, Meta>` already carries every held
  chunk's size, keyed `(id, n)`. `Index::set_totals(id)` filters it and folds
  — O(entries currently held by the whole store), no syscalls, and it runs
  under the same `Mutex` `insert`/`refresh`/`forget`/`evict_over_budget`
  already take, so it is consistent with concurrent PUT/evict by construction
  rather than by a new lock. It never touches `order`, so a status poll
  cannot itself move what eviction picks next — the mtime/LRU requirement
  falls out of simply not calling into that path, not a special case.
- `total` isn't in the index at all (only per-chunk sizes are) — recorded
  only on disk at `root/<id>/total`. Reading it is one `read_to_string` +
  parse, already implemented as `total::read_valid` for the pairing race in
  `put`; made it `pub(super)` and reused it rather than re-deriving the same
  missing/unparseable leniency in `store.rs`. This is a single file read,
  not a directory scan, and is the one FS touch `set_status` makes.
- Rejected: a full `fs::read_dir` + per-entry `metadata()` of `root/<id>`
  (what `scan.rs`/`evict.rs`'s `remove_set_dir_if_empty` do). For a large
  film that's up to ~5,500 stat calls per poll; the index answer is a linear
  scan of an in-memory map bounded by the store's own `budget`, no syscalls
  at all — cheaper for the common case the phase's own risk note flagged.
- Rejected: a second per-id index (`HashMap<String, (u64,u64)>` kept in step
  with `insert`/`refresh`/`forget`). More bookkeeping to keep correct (every
  mutation site would need a second update) for a query load (a few polls a
  second from one film page) the plain filter already answers in
  microseconds at the store's realistic held-chunk counts. Flagged if a
  future budget/host makes `meta` large enough for this to matter in
  practice — untested at that scale.

### Tests Status
- Type check / build: pass (`cargo build -p mediagram-cache`)
- Unit tests: pass — `cargo test -p mediagram-cache`: 67 lib tests + 7
  `tests/api.rs` + 11 `tests/api_rejections.rs`, 0 failures
- Clippy: `cargo clippy -p mediagram-cache --all-targets` clean
- Format: `cargo fmt -p mediagram-cache -- --check` clean (ran `cargo fmt`
  once to fix 3 files' spacing after adding tests)
- Android: `./gradlew :core:playback:testDebugUnitTest` — BUILD SUCCESSFUL,
  `LanChunkClientTest` (5 new tests) included
- Manual: built `mediagram_cache`, ran it against a scratch temp dir on
  `127.0.0.1:18877` (`MEDIAGRAM_CACHE_MDNS=false`), confirmed with `curl`:
  unknown id → `200 {"bytes_held":0,"chunks_held":0,"total":null}`; invalid
  id (`bad_id`) → `404`; a real signed PUT then `GET /v1/sets/abc123` →
  `{"bytes_held":5,"chunks_held":1,"total":5}`; `/v1/status` unaffected.
  Process killed and scratch dirs removed afterward — nothing installed or
  left running.

### Issues Encountered

- The phase file's "beside the existing ones (`store_tests.rs`/http tests)"
  undersold what was actually there: this crate already has
  `tests/api.rs` + `tests/api_rejections.rs` + `tests/common/mod.rs` — full
  `tower::ServiceExt::oneshot` route-level tests, a pattern I didn't find on
  the first pass (a `zsh` glob issue on `find --include=*.rs` came back
  empty). Built a standalone `src/http_tests.rs` unit-test module first,
  then found the real integration-test home and moved the coverage there
  instead — `invalid id → 404` and the two 200-path shapes now live in
  `tests/api_rejections.rs`/`tests/api.rs` beside their siblings, matching
  the crate's actual convention rather than inventing a second one.
- `cargo metadata` alone did not update `Cargo.lock`'s version fields for
  the bumped workspace members. `cargo generate-lockfile` did, but is a full
  re-resolve — it started pulling in ~150 lines of unrelated transitive
  dependency changes (askama, icu_*, cargo-platform, etc., all from the
  `mediagram` binary's own dependency tree, not touched here). Reverted that
  and used `cargo check -p mediagram-cache` instead, which only refreshed
  the five workspace members' own `version` entries (10 lines) — the
  targeted update the versioning rule actually asks for.
- `http.rs` (223 lines) and `store.rs` (222 lines) are now past the 200-line
  guideline by a modest margin. Trimmed the new doc comments to keep the
  overshoot small; did not split either file — the new route/method is a
  few lines of genuinely cohesive router/store logic, and splitting either
  module for ~20 lines would trade a soft guideline for a real fragmentation
  cost (a new file, or an `open()` visibility change to support an extension
  function on the Kotlin side's would-be Rust analogue). Flagging rather
  than silently deciding it doesn't matter.
- `LanChunkClient.kt` is 230 lines for the same reason — `setStatus` added
  as a method on the concrete class, not on `LanChunkProtocol` (see below).

### Design note: `setStatus` is not on `LanChunkProtocol`

`LanChunkProtocol` is implemented by fakes in three other modules
(`feature/system`, `ui-mobile`, plus two more in `core/playback`'s own test
sources) not owned by this phase. The phase spec names
`LanChunkClient.setStatus(...)` specifically, not the interface, so it's a
plain method on the concrete class — callable identically
(`client.setStatus(...)`), zero ripple into fakes this phase doesn't own.
Phase 02 (the preload engine, which actually needs to fake this in tests)
is the natural place to decide whether it belongs on the interface.

### Next Steps
- Phase 02 can build the preload queue against `LanChunkClient.setStatus`
  directly, or promote it onto `LanChunkProtocol` first if its own tests
  need to fake it — that decision is phase 02's, not pre-made here.
- Deploy note from `plan.md` stands: build for the home box and copy the
  binary there with the user once this phase is reviewed; nothing was
  installed or run as a service on this machine.

### Unresolved Questions
- None blocking. Worth a second look in review: whether 223/222/230-line
  files crossing the 200-line guideline by ~10-15% is acceptable as-is, or
  should trigger a split (see Issues above for what a split would cost on
  each side).

**Status:** DONE
**Summary:** `GET /v1/sets/{id}` ships on the cache server (index-backed chunk/byte tally, one file read for `total`, no mtime/LRU touch) with route + store + index tests, and `LanChunkClient.setStatus` on Android parses it the same lenient way `status()` already does; version bumped to 0.70.0 across all three manifests, changelog and system-architecture updated, nothing committed.
**Concerns/Blockers:** None blocking — two minor judgment calls flagged above (file sizes past the 200-line guideline; `setStatus` kept off `LanChunkProtocol` pending phase 02's actual need) for the reviewer to confirm or override.

## Review fixes applied (260927-2132 code-reviewer report)

Both Medium findings and all three Low findings addressed, in the same
worktree, still uncommitted:

1. **`setStatus` unreachable through DI.** Moved onto `LanChunkProtocol`
   with a default body `= null` ("server too old" for every existing
   fake); `LanChunkClient.setStatus` now `override`s it. `LanChunkProtocol`
   and its value types (`LanServerStatus`, `LanSetStatus`, `LanPutResult`)
   moved into a new `LanChunkProtocol.kt` — the natural seam the reviewer
   pointed at. `status()`/`setStatus()` now share a private `getJson(url,
   parse)` helper in `LanChunkClient`, removing the near-duplicate body.
   `LanChunkClient.kt`: 230 → 167 lines; new `LanChunkProtocol.kt`: 72
   lines. No fake changed (`LanFirstChunkSourceTest`, `LanWriteQueueTest`,
   `LanCacheViewModelTest`, `CacheSectionTest` all still compile against
   the interface unmodified).
2. **404 contract wording wrong, plan reference present.** Reworded in
   three places — `LanChunkProtocol.setStatus`'s KDoc,
   `LanChunkClientTest.kt`'s `aFourOhFourOnSetStatusIsNullRatherThanThrowing`
   comment, and the `0.70.0` changelog entry: 404 means a malformed id or a
   server without the route; a well-formed but unknown id is `200` with
   zeros and `total: null`. Dropped "(before phase 01 ships to it)".
3. **`LanSetStatus.total` can be null while chunks are held.** KDoc (now in
   `LanChunkProtocol.kt`) rewritten: a PUT landing between the server's two
   separate reads (total file, then index snapshot), or a `total` file a
   restart's scan found missing/unreadable while still trusting the chunks
   around it, both leave `total` null without meaning "holds nothing" —
   decide from `bytesHeld > 0`, take the film's size from the catalogue.
4. **Rust doc fixes:**
   - `http.rs` module doc: no longer claims every id's presence is hidden;
     now states `set_status` deliberately tells a well-formed absent id
     apart from a held one (unauthenticated), why that is accepted (`HEAD`
     already proves as much), and why a later "fix" to a uniform 404 would
     break Android's "older server" signal.
   - `store.rs`'s `SetStatus.total` doc: added the full-eviction case
     (clears the recorded total along with the set's last chunk).
   - `store/total.rs`'s `read_valid` doc: the missing-file/unreadable-file
     outcomes were stated backwards; swapped (missing → `Ok(None)`,
     unreadable → `Err`).

### Re-verification
- `cargo test -p mediagram-cache`: 67 lib + 7 `api` + 11 `api_rejections`,
  0 failed
- `cargo clippy -p mediagram-cache --all-targets`: clean
- `cargo fmt -p mediagram-cache -- --check`: clean
- `./gradlew :core:playback:testDebugUnitTest`: BUILD SUCCESSFUL
- `./gradlew testDebugUnitTest` (every module, every `LanChunkProtocol`
  fake included — `feature/system`, `ui-mobile`, `feature/player`, `app`,
  `ui-common`, `ui-tv`): BUILD SUCCESSFUL, 361 tasks

### Files touched by this pass
`android/core/playback/src/main/kotlin/{LanChunkClient.kt,LanChunkProtocol.kt(new)}`,
`android/core/playback/src/test/kotlin/LanChunkClientTest.kt`,
`crates/mediagram-cache/src/{http.rs,store.rs,store/total.rs}`,
`docs/project-changelog.md`
