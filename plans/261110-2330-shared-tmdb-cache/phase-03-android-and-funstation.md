# Phase 03: the Android app, and funstation end to end

Spec: `docs/superpowers/specs/2026-11-10-shared-tmdb-cache-design.md`. Global Constraints and Review Focus: `plan.md`.

### Task 6: Android core and app: `set_home_cache`, keyless enrichment

**Files:**
- Modify (Rust): `crates/mediagram-core/src/api/mod.rs` (new exported `set_home_cache`), core state holding the server (follow how other runtime settings are held on `Core`), `crates/mediagram-core/src/api/enrich/artwork.rs` (`fetch_missing`, `verify_then_fetch`), `crates/mediagram-core/src/api/enrich/fetch.rs`, `crates/mediagram-core/src/api/credits.rs`, `crates/mediagram-core/src/game_posters.rs` (pass the home cache to downloads)
- Modify (Kotlin): `android/core/data/src/main/kotlin/CatalogEnrichmentFetcher.kt`, the owner of the resolved LAN server and token (`android/core/playback/src/main/kotlin/LanServerPicker.kt`, `android/core/data/src/main/kotlin/settings/LanCacheTokenSettings.kt`; wire where both are already injected), `android/core/testing/src/main/kotlin/testing/FakeCore.kt`, the enrichment card's text (find with `grep -rn "No TMDB key is stored" android`)
- Regenerate: `scripts/generate-android-bindings.sh`; rebuild `scripts/build-android-core.sh` (a stale `.so` crashes the app at launch)
- Test: `crates/mediagram-core/src/api/enrich/` tests (follow existing `*_tests.rs`), `android/core/data/src/test/kotlin/CatalogEnrichmentFetcherTest.kt` (create if absent)

**Interfaces:**
- Consumes: `HomeCache`, `NoKey`, `DiskCachedApi::with_home_cache` (Task 3); `download_into/download_each(.., home, ..)` (Task 4).
- Produces: `Core::set_home_cache(&self, url: Option<String>, token: Option<String>)` (UniFFI; Kotlin `setHomeCache(url: String?, token: String?)`); `None` url clears it. `fetch_missing` keeps its signature: an empty `tmdb_key` with a home cache set runs keyless (no `verify_key`, inner `NoKey`); an empty key without one returns the same as today.
- Kotlin: whenever the resolved server address or the pairing token changes (including to none), call `core.setHomeCache(address, token)`. `CatalogEnrichmentFetcher`: key null and a server connected → `core.fetchMissing("", fallbackLanguage, width)`, state `hasKey = false, viaHomeCache = true`; key null and no server → unchanged ("No TMDB key is stored."). Card text when `viaHomeCache`: "Artwork comes from the home cache server."

- [ ] **Step 1: Write failing tests:**
  - Rust: `keyless_fetch_reads_artwork_from_the_home_cache` (fake server holds the details JSON and the poster; report counts the title fetched; no request to the TMDB API host), `keyless_without_a_home_cache_fetches_nothing` (report equals today's no-key report), `a_keyed_fetch_uploads_what_tmdb_answered`.
  - Kotlin: `noKeyWithAServerRunsKeyless` (FakeCore records `fetchMissing("", ...)`), `noKeyWithoutAServerSaysSoAsToday` (no call; error text unchanged), `aServerChangeReachesTheCore` (FakeCore records `setHomeCache(url, token)` and `setHomeCache(null, null)`).
- [ ] **Step 2:** `cargo test -p mediagram-core enrich` and `cd android && ./gradlew :core:data:testDebugUnitTest` → FAIL.
- [ ] **Step 3:** Implement; regenerate bindings; rebuild the core for all ABIs.
- [ ] **Step 4:** `bash scripts/check.sh` → "all checks passed".
- [ ] **Step 5:** Commit `feat(android): a device without a TMDB key gets its artwork from the home cache server`.

### Task 7: Funstation script, docs, end-to-end check on the box

**Files:**
- Modify: `scripts/setup-funstation.sh` (the posters config gains `cache_server = "http://127.0.0.1:7788"` and `cache_server_token = "<server token>"`, added on every run when missing), `docs/running-the-player.md` ("Home cache server": the two routes, `MEDIAGRAM_CACHE_BLOB_BUDGET`, the blob test vector from Task 1), `docs/project-changelog.md` (the Unreleased funstation entry)

- [ ] **Step 1:** Re-run the script on funstation (`scp scripts/setup-funstation.sh mediagram@192.168.0.43: && ssh mediagram@192.168.0.43 'bash ~/setup-funstation.sh </dev/null'`) → exit 0; `curl -s http://192.168.0.43:7788/v1/status` shows `blob_budget_bytes: 8589934592`.
- [ ] **Step 2:** On funstation, run `mediagram posters --index ~/.cache/mediagram-channel-index/current/library.db`; then `/v1/status` shows `blob_bytes > 0` (the already-full disk cache uploaded).
- [ ] **Step 3:** Install the new benchmark APK on funstation; in the TV app, remove the TMDB key (Settings) and clear the app's artwork; trigger enrichment → posters return with "Artwork comes from the home cache server."; `/v1/status` blob bytes do not drop. Restore the key afterwards.
- [ ] **Step 4:** Commit `docs: the home cache server's TMDB routes, and funstation fills them`; push `feat/funstation`.
