# Phase 2: TV foundation — artifacts, 10-foot theme, shell

**Context:** [plan.md](plan.md) · [phase 1](phase-01-move-shared-rules-out-of-ui-mobile.md) · `DESIGN.md` ~262–275 · `android/core/designsystem/src/main/kotlin/{Palette,Theme,Type,Spacing}.kt`

## Overview

- **Priority:** High
- **Status:** pending
- **Deliverable:** the placeholder is gone. On TV the app draws in the catalogue's dark palette at 10-foot scale inside a safe area, through a `TvApp` that already routes setup-vs-library (screens stubbed until phases 3–4).

## Key insights

- `androidx.tv:tv-material` **1.1.0** is current stable (Google Maven, 2026-09-24). It brings its own `androidx.tv.material3.MaterialTheme`; `MediagramTheme` (M3) does not reach it. The palette must be mapped into a tv `darkColorScheme` from the same `Palette.kt` values — one palette, two theme adapters.
- `Palette.kt` is `internal`. Expose the palette values (or a `CatalogueColors` data object) publicly from `core:designsystem`; do not duplicate hex values in `ui-tv`.
- Type: same Fraunces/Newsreader families, scaled for ~3 m viewing. Body text ≥ 18sp equivalent on a 960×540dp TV; titles ~34sp. Put the scale in `core:designsystem` as `TvTypeScale` values so the family choice stays in one file.
- Overscan: 48dp × 27dp (5 % of 960×540dp). Tokens in `Spacing.kt` as `Overscan`.
- Focus must be legible across a room: scale ≈1.08 + imprint-red (`#D26A55`) border/glow. That red already means "active" on the phone; TV reuses it for "focused", consistent with the one-accent rule.
- `MainActivity` calls `enableEdgeToEdge` on TV too; harmless, but TV screens pad with `Overscan`, not window insets.

## Requirements

- Functional: TV launches into `TvApp`; setup state decides the screen; Back at the root leaves the app.
- Non-functional: no M3 component in `ui-tv`; theme adapter unit-tested against `Palette`.

## Architecture

```
MainActivity (TV branch) ─► TvApp()
                             ├─ TvTheme { … }            (tv MaterialTheme from Palette + TvTypeScale)
                             ├─ SetupViewModel → Ready?  ─► TvLibrary()   (phase 4)
                             └─ else                     ─► TvSetupStep() (phase 3)
```

## Related code files

- Modify: `android/gradle/libs.versions.toml` (`tvMaterial = "1.1.0"`, `androidx-tv-material`), `android/ui-tv/build.gradle.kts`, `android/core/designsystem/src/main/kotlin/{Palette,Spacing,Type}.kt`, `android/app/src/main/kotlin/com/mediagram/android/MainActivity.kt`
- Create: `android/ui-tv/src/main/kotlin/ui/tv/{TvApp.kt,TvTheme.kt,TvFocus.kt}`, `android/ui-tv/src/test/kotlin/…`
- Consider: a `app.android.tv.screen` convention plugin mirroring `AndroidMobileScreenConventionPlugin` (coil, lifecycle-compose, hilt-compose). Only if it removes real duplication; otherwise plain deps.

## Implementation steps

### Task 1: Artifacts and module deps
- [ ] **1.1** Catalog: `tvMaterial = "1.1.0"`, library `androidx-tv-material`. Do not add `tv-foundation`: its `TvLazyRow`/`TvLazyColumn` are deprecated in favour of compose foundation's lazy lists, which handle D-pad focus scrolling themselves.
- [ ] **1.2** `ui-tv` deps: `:ui-common`, all four `feature:*`, `core:designsystem`, `core:model`, `core:playback`, tv-material, coil-compose, lifecycle-runtime-compose, hilt-lifecycle-viewmodel-compose, activity-compose; test: robolectric, mockk, ui-test-junit4, `isIncludeAndroidResources = true`.
- [ ] **1.2b** `AndroidLibraryComposeConventionPlugin` adds the whole `compose` bundle — including `androidx.compose.material3` — to every compose library, so M3 already reaches `:ui-tv` and `:ui-common` (which excludes it by hand in its own build file). Move material3 out of the base bundle into the mobile-screen convention (only `:ui-mobile` draws with M3), delete `:ui-common`'s `configurations.all { exclude(...) }`, and check `./gradlew :ui-tv:dependencies --configuration releaseRuntimeClasspath | grep androidx.compose.material3` prints nothing.
- [ ] **1.3** `./gradlew :ui-tv:assembleDebug`. Commit — `build(android): add tv-material 1.1.0 to the television surface`.

### Task 2: Palette, type scale, overscan in the design system
- [ ] **2.1** Test first (`core/designsystem/src/test/kotlin/`): `Overscan.horizontal == 48.dp`, `vertical == 27.dp`; TV body size ≥ 18sp; palette object exposes the same values `CatalogueColors` uses (extend `CatalogueColorsTest`).
- [ ] **2.2** Implement. Phone theme reads the same palette object — no visual change on phone.
- [ ] **2.3** Commit — `feat(android): overscan and ten-foot type in the design system`.

### Task 3: `TvTheme` and focus treatment
- [ ] **3.1** `TvTheme`: tv `MaterialTheme(colorScheme = darkColorScheme(…from palette…), typography = …TvTypeScale…)`. Always dark, like the phone.
- [ ] **3.2** `TvFocus.kt`: one shared `CardDefaults`/`ClickableSurfaceDefaults` set — scale 1.08, imprint border 3dp, no glow on text-only rows (underline + colour instead). Every later screen uses these; no per-screen focus styling.
- [ ] **3.3** Robolectric test: theme primary == palette imprint.

### Task 4: `TvApp` shell replaces the placeholder
- [ ] **4.1** `TvApp()` mirrors `MobileApp()`'s start rule via the same `SetupViewModel` (`recheck()` on resume, via `:ui-common`), rendering temporary `Text` stubs for setup and library branches inside `Overscan` padding.
- [ ] **4.2** `MainActivity`: TV branch → `TvApp()`; delete `TvPlaceholder`.
- [ ] **4.3** Emulator: `ANDROID_SERIAL=emulator-5554 ./gradlew :app:installDebug`, launch, `adb exec-out screencap -p` → dark ground, stub text inside safe area.
- [ ] **4.4** Commit — `feat(android): television shell in the catalogue theme`.

## Todo list
- [ ] tv-material pinned, ui-tv deps complete
- [ ] Overscan + TV type + public palette, tested
- [ ] `TvTheme` + shared focus treatment
- [ ] `TvApp` replaces placeholder, seen on emulator

## Success criteria
Emulator shows the dark catalogue ground with the right setup-state stub; phone unchanged; `:ui-mobile` dependency tree still has no `androidx.tv`.

## Risk assessment
| Risk | Mitigation |
|---|---|
| tv-material 1.1.0 needs a newer compose than BOM 2026.06.01 resolves | Check `./gradlew :ui-tv:dependencies` for forced upgrades; if it pulls compose forward, pin via BOM and note it |
| Two themes drift | Both adapters read one public palette object; test compares them |
| Font variation axes behave differently at large sizes | Look at the emulator screenshot, not just the test |

## Security considerations
None.

## Next steps
Phase 3.
