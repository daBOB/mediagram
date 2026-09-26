# Merge main into feat/android-tv-ui — 2026-09-26

## 1. Merge

- `git merge main` at merge-base `0dca854d0` (main 69 commits ahead, branch 8 ahead:
  `f3f32a1b..a5ae7291`), matching the task's stated facts exactly.
- Merge applied cleanly with **no conflicts** — `git merge-tree` had already confirmed this;
  `git merge` produced the same tree with no manual resolution needed.
- Commit: `3e275291` — "chore: merge main into the television branch".

## 2. Native rebuild

- `CARGO_INCREMENTAL=0 ANDROID_NDK_HOME=/home/andre/android-sdk/ndk/28.2.13676358
  scripts/build-android-core.sh` — built `libmediagram_core.so` for all four ABIs
  (arm64-v8a, armeabi-v7a, x86_64, x86) cleanly.
- `scripts/build-android-core.sh` only cross-compiles the `.so`; bindings are generated
  and committed separately by `scripts/generate-android-bindings.sh`, per its header
  comment ("bindings are committed; the .so files are not").
- Checked bindings freshness: ran `uniffi-bindgen generate` against the freshly built
  arm64-v8a `.so` into a scratch dir, trimmed trailing whitespace the same way the
  script does, and diffed against the committed
  `android/core/rust/src/main/kotlin/uniffi/mediagram_core/mediagram_core.kt`.
  **Byte-identical — bindings are up to date, no regeneration needed, nothing hand-edited.**
- FFmpeg `.so` files were already present under
  `android/core/ffmpeg/src/main/jniLibs/{arm64-v8a,armeabi-v7a,x86_64,x86}/libffmpegJNI.so`,
  so `scripts/build-android-ffmpeg.sh` was not run.

## 3. Verification

Two merge-caused compile breaks, both from main's cache-volume/cap work adding
`volumeLabel`, `fellBack` (to `CacheOccupancy` and `SystemUiState`) and a `cap: Long`
parameter to `cacheBudgetChoices`. The TV surface's own code (written before main added
these) still called the old shapes:

1. `ui-tv/.../TvCacheBudgetBlock.kt:49` — called `cacheBudgetChoices()` with no argument;
   main's `cacheBudgetChoices(cap: Long)` requires one. Fixed to
   `cacheBudgetChoices(current.capBytes)`, matching `ui-mobile/.../CacheBudgetBlock.kt`.
2. `feature/system/src/test/kotlin/SystemBlocksTest.kt:9` and
   `ui-tv/src/test/kotlin/ui/tv/TvAppFixture.kt:159-160` — TV-owned test fixtures built
   `SystemUiState`/`CacheOccupancy` without the two new fields. Added
   `volumeLabel = "Internal storage"`, `fellBack = false` (and `capBytes` for
   `CacheOccupancy`), the same defaults `ui-mobile`'s own test fixtures use.

Both fixes committed separately: `7c0eea15` — "fix(android): follow the cache and system
model through to the television surface". Main's shape wins throughout; only the TV's
call sites/fixtures were touched.

Re-run after the fix:

- `./gradlew testDebugUnitTest :core:model:test lint :ui-tv:compileDebugAndroidTestKotlin
  :app:assembleDebug` → **BUILD SUCCESSFUL** (667 tasks, no failures, no lint errors —
  only pre-existing deprecation/opt-in warnings unrelated to the merge).
- `cargo test -p mediagram-core` → **all suites pass**, 0 failures across every test
  binary (unit tests, `shared_*_fixtures`, `shows_query`, `shows_upsert_covers_schema`,
  `state_retirement`, `stream_cursor`, doc-tests).

## 4. Real TV box

- Devices: `192.168.0.35:5555` (UHD_Google_TV_STB, real box) confirmed connected
  alongside an unrelated emulator.
- `ANDROID_SERIAL=192.168.0.35:5555 ./gradlew :app:installDebug` → installed successfully.
- `am start -W -n com.mediagram.android/.MainActivity` → `Status: ok`, `LaunchState: COLD`,
  `TotalTime: 3448`.
- Screenshot after a 20s wait (`merge2-box.png`) shows Home with "Continue · 2" (Justice
  League, 4%, 4:13) and "Next up · 2" rows rendering correctly.
- `adb logcat -d -b crash` → **empty, no FATAL entries.**
- Browsed only (Home shown); nothing played. Phone `caad49da` was never touched.

## 5. Phone/web parity gaps — TV surface does not have

Comparing what main added since `2d7dfd8` against the merged TV surface
(`android/ui-tv/src/main/kotlin/ui/tv/`):

- **Magazine home.** `ui-tv/.../catalog/TvHome.kt` still builds the old row-based
  `HomeRow`/`homeRowsOf` shelf list (Continue/Next up, as shown on the box screenshot).
  The phone's magazine home lives in `ui-mobile/.../home/` (`MagazineHomeScreen` and
  friends, built from `web/public/lib/catalog/home-*.js`'s Kotlin port,
  `crates/mediagram-core/src/dto/summary.rs` etc.) — not present on TV.
- **Backdrops / editor's choice on the title page.** `ui-tv/.../catalog/TvTitlePage.kt`
  and `TvTitleHeader.kt` have no backdrop or editor's-choice code (`grep` for
  `backdrop|Backdrop|editorsChoice|editorial` under `ui-tv/` returns nothing). The phone
  wires these through `ui-mobile/.../catalog` off `crates/mediagram-core/src/state/editors_choice.rs`
  and `crates/mediagram-core/src/api/store/editorial.rs` (core work, `8f6ef31c`).
- **LAN cache server settings / System row.** The System screen's read-only "Source"
  row (`system.cacheRows` → `sourceLine`) *is* shared code and already shows on TV. What's
  missing is the interactive Settings UI: `ui-mobile/.../settings/LanCacheBlock.kt`
  (pairing token, enable/disable serving) and `ui-mobile/.../settings/CacheVolumeBlock.kt`
  (choose which storage volume the cache opens on). `ui-tv/.../system/` only has
  `TvCacheBudgetBlock.kt` (the size ladder); no TV equivalent of either block exists.
- **Featured reel and paged Movies shelf.** `ui-tv/.../catalog/TvCatalogScreen.kt` still
  renders each genre/shelf as a single `TvShelfWall` with no Featured reel and no paging
  (`grep` for `paging|Paging|LazyPagingItems` under `ui-tv/` returns nothing); the phone's
  equivalent (`android-featured-and-paging` branch, merged `371eb7b0`) added both to
  `ui-mobile`.
- No other main-side phone/web features surfaced in this pass beyond the four named
  above; the core Rust changes underneath them (backdrops-at-width, editor's choice sync,
  index-merge, cache server) are otherwise already exercised through the shared crates
  TV also links against, so nothing else appears core-only-missing.

This is a parity list, not implementation — no TV-side feature work was attempted.
