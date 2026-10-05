# TV 4K review fixes

All 9 applied in the main checkout, uncommitted, no version bump, no device touched. `bash scripts/check.sh` passed (clippy -D warnings, cargo test --all, web, gradle tests + lint). `./gradlew :baselineprofile:assemble :app:assembleRelease :app:assembleBenchmark` passed.

1. Never uninstall: `android/gradle.properties` gets `android.injected.androidTest.leaveApksInstalledAfterRun=true` with a why-comment. The generator returns early, logging under tag BaselineProfile, when `FEATURE_LEANBACK` is absent. KDoc carries `ANDROID_SERIAL=192.168.0.35:5555 ./gradlew :app:generateBaselineProfile`. Test: compile only, since this needs a device.
2. Custom backdrops: `FetchPlan.custom_keys` is read from `artwork::keys` in `plan_fetch`. `fetch_into` drops posters and backdrops whose key the table holds before counting or downloading. Test `a_backdrop_the_library_holds_is_not_downloaded` (artwork_fetch.rs) seeds the table row with no file, so without the fix `failed` would be 2. Passes.
3. Double switch: in `TvDisplayModeMatch.match`, `start` is captured first, then `if (tracks.isEmpty()) return`. A real video track with unknown rate still restores `before`. No unit test: the logic sits in the composable, not the pure decision. Compiled only.
4. Keep a fitting mode: `pickDisplayMode` returns null when `current` already fits, via a shared `fit()` helper. Otherwise smallest multiple, then smallest error. New tests: 29.97 on 59.94 keeps, 24 on 120 keeps, 23.976 on 59.94 still picks 23.976, and `displayModeToApply` returns `before`. Passes.
5. Journey: after MOVIES_PAGE it presses DOWN (max 40) until the `All \d+ films` pill is focused, presses Enter, waits for MOVIES_PAGE to be gone, holds DOWN through the wall, presses Enter on a plate, waits for TITLE_PAGE, and presses Back once. Each failed step logs and returns. Still navigate-only. The wall has no tag, so "wall opened" is detected as the Movies page tag disappearing. Compiled only.
6. Hero reload: `LibraryUpdateCoordinator` now also checks `report.backdropsFetched > 0u`. Tests `aBackdropUpgradeAloneRereadsArtwork` and `aRunThatFetchedNoArtworkDoesNotRereadIt`; the fixture takes a `report` parameter. Passes.
7. Atomic writes: `write_private` in poster_files.rs writes `.<name>.<pid>.tmp` (0600 at creation) in the same dir, then renames. The temp file is removed on error. The explicit chmod is dropped, since the temp file is always new. Covered by the existing poster_files tests.
8. Truthful counts: `fetch_into` computes fetched as `already_held` after the download minus before, per half. The CLI `posters` line does the same. `a_backdrop_held_narrower_than_wanted_is_not_counted_held` now asserts `backdrops_fetched == 0`. Passes.
9. `UpdateModule.kt`: `data.isTelevision` moved into sorted position.

Unresolved: none.
