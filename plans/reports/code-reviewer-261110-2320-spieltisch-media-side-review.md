# Code review: Spieltisch, media-library side (uncommitted, feat/spieltisch-media-side vs main 2f3e7d81)

## Scope
- 20 modified + 4 new files: release tag per app (mlib-spec, publish, core lookup, AppUpdater), `game_night_titles` / `textless_poster` export, process-wide poster locks, fixture shared with `mediagram-game`, regenerated uniffi Kotlin.
- Verification run:
  - `cargo test -p mlib-spec -p mediagram -p mediagram-core -p mediagram-game`: all green.
  - Kotlin bindings regenerated from a host build of `mediagram-core` and diffed against the committed `mediagram_core.kt`: identical.
  - Gradle was not rerun.

## Verdict
The release-tag split is correct and safe for old clients. The JSON contract matches `GameTitle`. Two real problems sit in the new process-wide poster lock, one test gap leaves the per-app "newer than newest" rule unguarded, and one kids-safety hardening point should land before the provider does.

---

## Real bugs

### 1. MEDIUM-HIGH: a process-wide lock is held across a TMDB request with no time limit
- **Where:** `crates/mediagram-core/src/game_posters.rs:48-58`. The lock is held across `inner(key).await`.
- **Why it can hang:**
  - `textless_poster` calls `TmdbClient::get_json` at `game_posters.rs:79-83`.
  - `get_json` sends with no timeout: `crates/mediagram-tmdb/src/tmdb_client.rs:133-137`.
  - `crate::http::client()` sets no timeout either: `crates/mediagram-core/src/http.rs:41-43`.
  - On a 429, it sleeps for whatever `Retry-After` the server sends, up to 3 times: `tmdb_client.rs:139-147`.
  - Only the image download is bounded (`POSTER_TIMEOUT`).
- **Scenario:** The TV box changes Wi-Fi while the `/images` request for `m1` is waiting, so that request hangs. On main, the locks belonged to one lookup, so only that night's callers for `m1` waited, and the next night got fresh locks. Now the lock is in a `static`, so every later caller for `m1` waits until the media app's process dies. That includes in-app nights and every `textless_poster("m1")` call from the coming provider. Once the provider exists, each Spieltisch `openFile(/textless/m1)` holds a binder thread. Repeated asks for that key can use up the media app's binder pool.
- **Fix (one line in `one_at_a_time`):**
  ```rust
  let _held = lock.lock().await;
  tokio::time::timeout(LOOKUP_TIMEOUT, inner(key)).await.ok().flatten()
  ```
  For example, set `LOOKUP_TIMEOUT = 30 s`. tokio's `time` feature is already enabled. A timeout leaves no `.none` marker, so the next ask retries.

### 2. MEDIUM: the lock map is keyed by the untrusted `title_key`, before the key is checked, and is never pruned
- **Where:** `game_posters.rs:53` adds an entry for any key. The check against the catalog (`keys.get(&title_key)?`, `game_posters.rs:35`) only runs inside `inner`, after the lock is taken.
- **Scenario:** `title_key` will come from another app. Every distinct string, of any length, adds a `String` plus `Arc<Mutex>` to a `static` for the life of the process. The test at `game_night_export_tests.rs:117` (`"unknown"`) already adds one. A buggy or looping caller grows memory without bound. The caller is guarded by a signature permission, so this is low-probability, but it is unbounded.
- **Same root cause, already on main:** the lock guards a title key, but the shared resource is the cache file named by the poster key. Two sets of one film (for example a 1080p and a 4K Dune: `m1` and `m2`, both `tmdb-movie-438631`) are not serialised. Both write `.tmdb-movie-438631.jpg.<pid>.tmp`, and the comment at `:41-47` exists to prevent exactly that.
- **Fix:** in `poster_lookup`, resolve `poster_key` from `keys` first, and lock on `poster_key`. The map is then bounded by the catalog's poster keys, and only checked keys ever enter it.
  ```rust
  let poster_key = keys.get(&title_key)?.clone();        // before any lock
  let lock = LOCKS.lock()...entry(poster_key.clone())...; // lock the file, not the title
  ```
  This means folding `one_at_a_time` into the closure, or passing it the poster key.

---

## Test gap (correct code, but the tests do not guard it)

### 3. MEDIUM: a broken per-app "newer than newest" check in `publish` would pass every test
- **Where:** `crates/mediagram/src/app_release/publish.rs:44`.
- **The mutation that survives:** I checked this by reasoning through the tests, not by running the mutant. Replace `newest(tag, &posted)` with `newest(MEDIA_APP_TAG, &posted)`. Every test still passes:
  - `each_app_replaces_only_its_own_release` only publishes Spieltisch codes that are above the media app's.
  - `a_version_code_not_above_the_newest_release_is_refused` uses only the media package.
- **What that mutant would do in production:** a Spieltisch APK with a versionCode at or below the pinned Spieltisch release gets posted and pinned. The unpin loop (filtered by `tag`) then unpins the higher release, so Spieltisch devices lose their newest update.
- **Fix:** add the refusal for the games package to `each_app_replaces_only_its_own_release` or to a new test:
  ```rust
  assert!(publish(&channel, b"g0", None, &games(175_001), 5).await.is_err(), "not above Spieltisch's own newest");
  ```
  Watch it fail against the mutant once before trusting it.
- **Mutations the existing tests already catch** (reasoned through): the unpin filter using the wrong tag, or no tag; `newest` across all tags; `newest` always using the Spieltisch tag. `ReleaseApp::tag()` is pinned to the literal strings (`app_release_tests.rs:37-42`), and `AppUpdaterTest` asserts `MEDIA_APP`.

---

## Design hardening (not a regression)

### 4. MEDIUM: the kids lock cannot be applied at the source
- **Where:** `crates/mediagram-core/src/dto/game_night.rs:18-24` and `api/game_night_export.rs:20-28`.
- **What happens:** `game_night_titles` exports every visible title, each with its `kids_ok` flag. The spieltisch-app plan (phase-02, Security) has the Kotlin provider parse and filter this JSON when the lock is on.
- **Compared with main:** nothing is wider today. Nothing calls the export from Kotlin yet. The in-app night is unchanged: the round trip keeps `kids_ok`, and `TitlePool::new(titles, kids_locked)` still drops unsafe titles first.
- **Risk:** when the provider lands, the kids rule's only enforcement before the boundary is JSON handling in Kotlin, which no Rust test covers.
- **Suggestion:** add `kids_locked: bool` to `NightTitlesConfig` and apply `titles.retain(|t| t.kids_ok)` in `game_night_titles`, with one test beside the existing ones. It costs about 3 lines, and doing it now avoids a second regeneration of the bindings later. This matches TitlePool's own rule: "nothing unsafe may ever be drawn". This is input for the user, because the plan chose the provider for this.

---

## Performance

### 5. LOW-MEDIUM: each `textless_poster` call reads the whole catalog and builds a TLS client
- **Where:** `game_night_export.rs:35-40`. Each call does `list_sets` + `poster_keys` + `poster_lookup`, which runs `crate::http::client()` (rustls config, webpki roots, a new connection pool). This happens even when the file is already cached.
- **Scenario:** Spieltisch prefetches textless posters for a 500-title night on the box. That is 500 full catalog reads and 500 client builds, and every cache miss also does a fresh TLS handshake.
- **Fix, if the box-run timing shows it:**
  - For a film, resolve the one key with `store::media_set(core, &title_key)`.
  - Or memoise `poster_keys` per catalog refresh.
  - Or reuse one lookup per TMDB key in `Core`.
- The credits are already N+1 (`night_titles`, one SQLite query per title), as on main. That is fine for SQLite in the same process.

---

## Process and nits

6. **LOW, version not bumped:** all three manifests are still at `0.174.0`. CLAUDE.md § Versioning requires a bump before commit. This is a new feature plus a breaking uniffi signature (`latest_app_release`) at 0.x, so the bump is minor: `0.175.0`. `docs/project-changelog.md` has no entry yet.
7. **LOW, untracked files:** `production/session-logs/` is untracked and not ignored. Keep it out of the commit.
8. **NIT, missing doc comments:** `TitleKind` in `game_titles.rs:30` has none. `NightTitle.key/kind/title/year/still/directors/kids_ok` have none either. The coding standards ask for doc comments on public APIs.
9. **NIT, fixture coverage:** the fixture has no `"year": null` case. Missing years are real for films. One more entry would cover the nullability of every `Option` field.
10. **NIT, duplicated input handling:** `start_game_night` and `game_night_titles` both repeat the conversion of the id lists into `HashSet`s and the blank-TMDB-key filter. This is small; leave it unless the in-app night stays after the plan's phase 4.
11. **NIT, two app tables:** `mlib-spec` takes the tag as a free `&str`. The package-to-tag table lives in `publish.rs` and the app-to-tag table in `ReleaseApp`, so there are two tables for one fact. It is acceptable at two apps; one enum in mlib-spec would be the single source if a third app ever comes.

---

## Verified OK (checks that turned up no problem)

- **Old-client parse, byte for byte:** `marker.trim_end().strip_prefix("#mlib-app") == Some(" v=1")` holds exactly when `marker.trim_end() == "#mlib-app v=1"`. That is the same rule as main's `!= MARKER`. All earlier caption tests pass, now with the tag argument.
- **No Spieltisch post can reach a `#mlib-app` reader:**
  - The only path to `MEDIA_APP_TAG` is a badging package of exactly `com.mediagram.android`.
  - Spieltisch's `applicationId` is `com.mediagram.spieltisch` (`mediagram-spieltisch-app/android/spieltisch/build.gradle.kts:23`).
  - Old cores' `parse` rejects `#spieltisch-app v=1`.
  - An old `publish-app` binary refuses the Spieltisch package and unpins only `#mlib-app`.
  - The index unpinning (`channel_index/unpin.rs`) works from its own id list and never touches release posts.
  - No web or iOS code reads `#mlib-app`.
- **JSON contract:**
  - Field names match `GameTitle` exactly.
  - `kind` is snake_case on both sides.
  - Every `Option` field is written as `null`, and `GameTitle` reads `null` as `None`.
  - `PathBuf` reads from a string.
  - `GameTitle` has no `deny_unknown_fields`, so new fields are forward-compatible.
  - Both sides are held to the fixture: the core checks exact `Value` equality, and the game crate parses the same file.
- **Round trip in `start_game_night`:** it keeps kind, year (`Option<u16>`), the order of directors and cast (`Vec`), `kids_ok`, and key and title. Nothing is lost.
- **Path handling for untrusted keys:**
  - `title_key` is only ever a HashMap key. It never reaches the filesystem, so `../`, `/` and odd show names are inert.
  - The path comes from the catalog's `poster_key`. `title_of` → `tmdb_title_of` → `poster_key_is_valid` checks it before `cache_dir.join`, and `download_into` checks it again.
- **Deadlock:** the std `MutexGuard` is a temporary dropped at the end of the `let` statement, so it is never held across `.await`; the future would not be `Send` otherwise. There are no nested locks, and a poisoned lock is handled.
- **Bindings:** regenerated from a host build, they are identical to the committed `mediagram_core.kt`. `AppUpdater` passes `ReleaseApp.MEDIA_APP`. FakeCore implements the new methods. Every `CoreInterface` test double delegates to FakeCore, and nothing else calls `latestAppRelease`.
- **Project rules:** every non-test source file is under 200 lines (`app_release.rs` is closest at 188). No code references plans, phases or finding codes.

## Recommended actions, in order
1. Bound the locked lookup with a timeout (#1).
2. Lock on the checked `poster_key`, resolved before locking (#2).
3. Add the Spieltisch refusal assertion, and watch it fail against the mutant (#3).
4. Decide whether `kids_locked` filtering moves into `game_night_titles` (#4).
5. Bump to 0.175.0, add a changelog entry, and keep `production/` out of the commit (#6, #7).

## Open questions
- Should the kids filter live in Rust (#4) or in the provider as the plan says? This is the user's call.
- Does Spieltisch prefetch every textless poster at night start, or ask per round? That decides whether #5 matters.

**Status:** DONE_WITH_CONCERNS
