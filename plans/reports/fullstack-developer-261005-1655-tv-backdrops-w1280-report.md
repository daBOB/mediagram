# TV backdrops w1280 + re-fetch narrower: report

## Changed
- `crates/mediagram-tmdb/src/poster_files.rs`: private `is_held` (used by `download_each` and `already_held`); `<key>.width` record written after a successful width-carrying download (asked width, via `write_private`); failed re-fetch with old file present keeps it, counts key written, leaves record. `download_each` delegates to private `fetch_each(url_of)` so tests can use a local server (CDN URL is hardcoded otherwise).
- Tests: `crates/mediagram-tmdb/src/poster_files_tests.rs` (local TCP stand-in CDN; 780->1280 refetch, 1280/780 held, no record fetched once, failed refetch keeps old file and retries, failed first fetch still failure, posters unchanged). `crates/mediagram-core/tests/artwork_fetch.rs` + `fetch_stub/mod.rs` (`write_width_record`; held test now writes a record; new narrower-record test). Dev-deps on mediagram-tmdb: tempfile, tokio net/io-util, rustls ring (reqwest panics without a provider).
- Kotlin: `isTelevision` moved from `:app` to `android/core/data/src/main/kotlin/Television.kt` (package `data`); core:model is plain JVM so core:data is the lowest shared module with no new edge. `:app` callers repointed (MainActivity, UpdateModule). `SurfaceSelectionTest` became `core/data/.../TelevisionTest.kt`. `DeviceBackdropWidth`: TV -> 1280, else sw600 rule. New `BackdropWidthTest` (TV/phone/tablet). mockk test dep added to core:data.

## Results
- `cargo clippy --all-targets --all-features -- -D warnings`: clean. `cargo test --all`: pass.
- Gradle as check.sh runs it (`testDebugUnitTest :core:model:test lint :ui-tv:compileDebugAndroidTestKotlin :core:ffmpeg:compileDebugAndroidTestKotlin`): BUILD SUCCESSFUL. No detekt/ktlint in check.sh.

## Coil finding
TV passes `java.io.File` to `AsyncImage`. Coil 3.5.0 maps File to a file Uri; `FileUriKeyer` (javap on coil-core-android 3.5.0) appends `lastModifiedAtMillis` when `addLastModifiedToFileCacheKey` is true (default; nothing in the project overrides it). Memory and disk keys come from that keyer, so a replaced file at the same path is not stale. No code change.

## Notes
- Credits portraits (`PORTRAIT_WIDTH` 185, also `backdrop_width: Some`) get `.width` records too; harmless.
- Desktop `mediagram posters` re-fetches its backdrops once (no records yet), as accepted.
- `backdrops_fetched` counts a kept-old-file (failed re-fetch) as fetched since the key counts as written; only visible when the CDN is down.
- Cargo.lock changed (new dev-deps). Versions not bumped.
