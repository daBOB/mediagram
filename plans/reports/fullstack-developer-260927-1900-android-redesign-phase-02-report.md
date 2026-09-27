# Phase Implementation Report

### Executed Phase
- Phase: phase-02-artwork-setting-model-and-rendering
- Plan: /home/andre/Workspace/mediagram-channel-index/plans/260927-1731-android-settings-system-redesign
- Worktree: /home/andre/Workspace/mediagram-channel-index (branch feat/android-settings-redesign, from phase 01's 5ae998e3 / 0.69.0)
- Status: completed, not committed (lead commits)

### Files Modified
Core model/theme (designsystem):
- `android/core/designsystem/src/main/kotlin/Backdrop.kt` (new, 29 lines): `Backdrop` enum (DEFAULT/BLURRED/ARTWORK/SOLID, `storageKey`/`label`/`note`, `fromStorageKey`), `LocalBackdrop` (`staticCompositionLocalOf`).
- `ThemeChoice.kt`, `Accent.kt`: added `label`/`note` fields to the enums (web's own copy), unused by any consumer yet (phase 03/04's job) — no existing call site touched beyond the one sentence below.
- `Appearance.kt`: `backdrop` field, `AppearanceSettings.chooseBackdrop`, both impls (`InMemoryAppearanceSettings`, `SharedPreferencesAppearanceSettings` — new key `"backdrop"`).
- `Theme.kt`: `MediagramTheme` now provides `LocalBackdrop` alongside `LocalCatalogueTones`.
- `feature/setup/.../AppearanceViewModel.kt`: `chooseBackdrop`.
- `ui-tv/.../TvTheme.kt`: `backdrop` param (default `Backdrop.Default`), provides `LocalBackdrop`; `TvApp.kt` passes `appearance.backdrop`.

Rendering (ui-common, ui-mobile, ui-tv):
- `ui-common/build.gradle.kts`: + `core:designsystem`, + `coil.compose`.
- `ui-common/src/main/kotlin/ui/catalog/HeroArtwork.kt` (new, 103 lines): `HeroArtwork(path, modifier, contentScale)` — reads `LocalBackdrop`; BLURRED → `Modifier.blur(28dp, Rectangle)` (API 31+) or a tiny (48px) Coil decode below it, both scaled 1.12x + `saturate(1.25)`, clipped to bounds; every other mode draws plainly. Exposes `HERO_ARTWORK_TEST_TAG` for tests.
- `ui-mobile/.../TitleSpread.kt`: SOLID skips the whole hero block (art, gradient, tagline quote); else `HeroArtwork`.
- `ui-mobile/.../DepartmentHero.kt`: SOLID reuses the existing no-art (`art == null`) branch via `lead?.backdropPath?.takeIf { backdrop != SOLID }`.
- `ui-mobile/.../CoverStory.kt`: `HeroArtwork` (BLURRED-only effect; Artwork/Solid keep the picture, matching the web's `.cover-stage`).
- `ui-mobile/.../MoviesDepartmentScreen.kt`, `ShowsDepartmentScreen.kt`: their own `PullQuote` guarded on `backdrop != SOLID`.
- `ui-tv/.../TvCoverStory.kt`: new `departmentHero: Boolean = false` param; SOLID drops the art only when `departmentHero` (Home's own cover never does); `HeroArtwork` everywhere else.
- `ui-tv/.../TvMoviesDepartmentPage.kt`, `TvShowsDepartmentPage.kt`: pass `departmentHero = true` to their `TvCoverStory` call.
- `ui-mobile/.../settings/AppearanceSection.kt`: removed the "artwork mode is not ported" sentence (phase's own step 7; rest of the file untouched, rebuilt in phase 03).

Tests:
- `SharedPreferencesAppearanceSettingsTest.kt`: +1 test (backdrop round trip), unknown-value test extended to cover `"backdrop"` too.
- `AppearanceViewModelTest.kt`: +1 test (`chooseBackdrop` reaches settings).
- `ui-mobile/src/test/kotlin/ui/catalog/HeroBackdropModesTest.kt` (new, 4 tests): SOLID/DEFAULT × TitleSpread/MoviesDepartmentScreen, asserting the hero art tag and the tagline-quote text.

Release bookkeeping:
- `Cargo.toml`, `Cargo.lock` (via `cargo check -q -p mediagram`), `web/package.json`, `android/app/build.gradle.kts` (`versionName` only): `0.69.0` → `0.69.1`.
- `docs/project-changelog.md`: new `## 0.69.1` top entry (Added: what each mode does, Artwork = Default called out as deliberate web parity, Settings picker named as separate later work).
- `plans/260927-1731-android-settings-system-redesign/plan.md` and the phase file: phase 02 row/todo ticked.

### Tasks Completed
All 8 implementation steps and all 7 todo items in the phase file.

### Tests Status
- Type check / compile: pass, no new warnings.
- Full verify (`testDebugUnitTest :core:model:test lint :ui-tv:compileDebugAndroidTestKotlin :core:ffmpeg:compileDebugAndroidTestKotlin :core:rust:compileDebugAndroidTestKotlin`): **BUILD SUCCESSFUL**.
  - Before (phase 01's own HEAD, per its report): 1490 unit tests.
  - After (this phase): 1496 unit tests (summed `tests=` across every module's `TEST-*.xml`) — +6, matching exactly: +1 `SharedPreferencesAppearanceSettingsTest`, +1 `AppearanceViewModelTest`, +4 `HeroBackdropModesTest`.
  - No lint failures, no compile errors, `:ui-tv`/`:core:ffmpeg`/`:core:rust` androidTest Kotlin all compile.

### Issues Encountered
- One real bug caught by `HeroBackdropModesTest`, not a false start: `DepartmentHero`'s clickable `Box` merges its descendants' semantics (`mergeDescendants = true`), so `HeroArtwork`'s own `testTag` is unreachable to `assertIsDisplayed()` under the default merged tree — fixed by querying with `useUnmergedTree = true`, the same pattern `TvPlateStateTest` already uses for a plate's own tags. Confirmed via a throwaway `printToString(useUnmergedTree = true)` dump before fixing; no such dump left in the file.
- `HeroBackdropModesTest`'s department-screen cases needed the same tall-window `@Config(qualifiers = "w400dp-h2400dp")` `ui.LibraryFlowTest` already documents for a `LazyColumn`-based screen under Robolectric's short default window.
- No file-ownership conflicts. `AppearanceSection.kt` is not in the phase's own "Modify" list but its step 7 explicitly asks for the one-sentence removal there; done narrowly, nothing else in that file touched (03 rebuilds it).

### Next Steps
Phase 03 (Settings + System, phone/tablet) draws the four Backdrop swatch cards using `Backdrop.label`/`.note` and `AppearanceViewModel.chooseBackdrop`; phase 04 does the same for TV's Appearance section, on top of `TvTheme`'s new `backdrop` param.

**Status:** DONE
