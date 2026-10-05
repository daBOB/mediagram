# Verification: review fixes, commit `92d00d3c` (TV 4K, 0.117.0)

**Verdict: PASS.** It is safe to generate the profile on the box and release. The review was read-only; `scripts/check.sh` had already passed on this tree.

## Fixes

| # | Verdict | Evidence |
|---|---|---|
| 0 | FIXED | See below. |
| 1 | FIXED | See below. |
| 2 | FIXED | See below. |
| 3 | FIXED | See below. |
| 4 | FIXED | `LibraryUpdateCoordinator.kt:52` re-reads artwork when only backdrops were fetched. Two tests cover it. |
| 6 | FIXED | See below. |
| 7 | FIXED | See below. |
| 9 | FIXED | `DisplayModeMatch.kt:35` keeps a start mode that already fits. Tests: 29.97 on 59.94 and 24 on 120. |
| 10 | FIXED | Import order corrected. `PlayerFactory` uses the shared `isTelevision`. |

### #0: the profile run no longer uninstalls the app

- `android.injected.androidTest.leaveApksInstalledAfterRun` is a stable AGP option (`BooleanOption.kt`).
- `DeviceProviderInstrumentTestTask` reads it for `com.android.test` modules, which is the task the baselineprofile plugin's connected run goes through.
- The leanback guard (`BaselineProfileGenerator.kt:45`) comes before `pressHome()`.

### #1: custom backdrops are no longer overwritten

- `artwork.rs:54` reads `custom_keys`, and `fetch.rs:49-51` drops them before counting or downloading.
- **Desktop CLI:** `mediagram posters` cannot overwrite custom art. It lives only in the table, and the web player serves the table first.
- **Portraits:** `credits.rs` checks `poster_path` first, so a custom portrait is never downloaded over.
- **Shipped devices:** the overwrite never shipped, because 0.116.0 predates the re-fetch commit.

### #2: an empty track list no longer causes a second switch

- `TvDisplayModeMatch.kt:42` records `start` first, and `:47` returns early on an empty track list.
- A real track with an unknown rate still restores `before`.
- A title change writes at most once.

### #3: the journey reaches the wall and a title page

- The `ALL_FILMS_PILL` regex matches `"All N films →"` and not the wall heading `"All films · N"`.
- Enter is pressed only when the pill is focused.
- Back is pressed only on the title page, and returns to the wall.
- OK is pressed only on the Movies pill, the All-films pill and a plate.

### #6: images are written to a temp file and renamed

- The temp file is in the same directory, so the rename is atomic, and it is created 0600.
- It is removed on error, leaving the old file intact.

### #7: a failed re-fetch is no longer counted as fetched

- `fetch.rs:72-75` counts what is held after the run minus what was held before, per half.
- A failed re-fetch that kept the old file is counted as neither fetched nor held.

## New defects

None of medium severity or above.

## Nits (not fixed; recorded)

1. **Temp file name.** `poster_files.rs:159` names it by process id only. `resolve.rs` uses process id plus `WRITE_SEQ`. It is unreachable today, because the Android fetch takes a mutex and the CLI runs once per process.
2. **CLI summary.** `posters.rs:93-101`: a failed re-fetch that kept the old file appears in none of the fetched, held or failed totals.
3. **Duplicate test.** `aCurrentModeThatDoesNotFitIsLeftForTheSmallestMultiple` is the same as `filmRateFindsItsOwnMode`, with no second multiple on offer.
4. **No test pins 0600 or temp-file cleanup.** This was already the case before the commit.
5. **Changelog.** The 0.117.0 entry claims the profile ships before it is generated. **Fixed by the lead:** the profile ships in 0.117.1.

## Informational

- **(a) Compilation reset.** `BaselineProfileRule` resets compilation before the first iteration. On a non-rooted API 33 device that is `pm uninstall` plus reinstall, which wipes data. On API 34+ it is `clear-app-profiles`. The box is API 34, so it is safe.
- **(b) The box can get stuck on the generation build.**
  - Generation installs `nonMinifiedRelease`. It is created with `initWith(release)`, so it has the release key, `self_update=true` and the tree's versionCode.
  - The self-updater installs only a strictly higher versionCode (`UpdateRules.kt:22`).
  - **The release must therefore be a higher version than the generation build.** Generate at 0.117.0, then commit and release the profile as 0.117.1.
  - The test APK `com.mediagram.android.baselineprofile` stays installed, which is harmless.
- **(c) Android and web disagree on one case.** A custom row added after TMDB's file is on disk stays hidden on Android (`resolve.rs:121`, where the on-disk file wins), while the web player prefers the table. This predates the work.
