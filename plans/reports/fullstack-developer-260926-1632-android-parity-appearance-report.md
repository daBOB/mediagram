# Phase 8 — Settings › Appearance (theme + accent) — implementation report

Plan: `plans/260926-1330-android-editorial-departments-parity/phase-08-appearance.md`
Worktree: `mediagram-android-parity`, branch `feat/android-editorial-parity`

## Files

### core/designsystem (model, theme, tests)
- `core/designsystem/src/main/kotlin/Accent.kt` (new) — enum of the 7 accents, dark/light hex
  ported verbatim from `web/public/styles/appearance.css` (coral e57a61/a3392a, blue
  7cb4f0/2c5c9a, violet b9a1f7/6243a6, teal 5ec9c0/1d6a65, green 93cf80/3a6a2a, amber
  e8b058/80530c, rose f08aa2/a1304d).
- `core/designsystem/src/main/kotlin/ThemeChoice.kt` (new) — DARK/LIGHT/AUTO, storage keys
  matching the web's (`dark`/`light`/`auto`).
- `core/designsystem/src/main/kotlin/Appearance.kt` (new) — `Appearance` data class,
  `AppearanceSettings` interface, `InMemoryAppearanceSettings`, and
  `SharedPreferencesAppearanceSettings` (same per-device-not-per-profile pattern as
  `core/data/settings/ShelfViewSettings.kt`, but living in designsystem since core/data is
  owned by another phase this round).
- `core/designsystem/src/main/kotlin/Palette.kt` — added the light-theme roles
  (`LightGround/LightPage/LightSunk/LightText/LightFigures/LightRule/LightRuleStrong/
  LightOchre/LightSage`), values taken verbatim from the web's `data-theme="light"` block
  in `styles/theme.css` (nothing here had an Android light theme to diverge from). Turned
  `Imprint` from a fixed `val` into a `mutableStateOf`-backed `var`: it is what every
  far-flung TV widget (`TvFocus`, `TvSeekBar`, `TvTextField`, `TvMasthead`, …) already reads
  directly instead of `MaterialTheme.colorScheme.primary` — `TvFocus`'s own doc says exactly
  this ("Imprint red already means... TV reuses it..."). Making it a snapshot state lets
  `MediagramTheme`/`TvTheme` set it via `SideEffect` on every composition so all of those
  already-written call sites (none of them touched) start following the chosen accent
  without edits, instead of Settings' accent picker being a dead control everywhere but its
  own screen.
- `core/designsystem/src/main/kotlin/Theme.kt` — `CatalogueColors` (a fixed val) replaced
  with `catalogueColorScheme(dark: Boolean, accent: Color)`, a light branch added using the
  new Light* roles. `MediagramTheme(appearance: Appearance = Appearance(), content)`
  resolves Auto via `isSystemInDarkTheme()`, resolves the accent for whichever theme is
  active, and writes it to `Palette.Imprint`.
- `core/designsystem/build.gradle.kts` — added `robolectric`/`androidx.junit`
  `testImplementation` for the new SharedPreferences test (same pair `core/playback` uses
  for its own Robolectric-backed settings test).
- Tests: `AccentContrastTest.kt` (new, port of `web/test/appearance-contrast.test.ts` —
  checked against **Android's own** paper values per the phase's instruction, since
  `Palette.Ground` (#16130F) isn't literally the web's #0d0d0e; margins are 6.4:1–10.1:1
  dark, 5.6:1–6.4:1 light, both comfortably over 4.5:1), `SharedPreferencesAppearanceSettingsTest.kt`
  (new, round-trip + unrecognised-value fallback), `CatalogueColorsTest.kt` (updated for the
  function signature, added a light-branch pin).

### feature/setup (viewmodel + DI)
- `feature/setup/build.gradle.kts` — added `implementation(project(":core:designsystem"))`.
- `feature/setup/src/main/kotlin/AppearanceViewModel.kt` (new) — thin pass-through
  `HiltViewModel` over `AppearanceSettings`.
- `feature/setup/src/main/kotlin/AppearanceModule.kt` (new) — Hilt `@Singleton` binding
  (`SharedPreferencesAppearanceSettings`), so the phone screen, the TV screen and the root
  theme composition all read the one live instance.
- `feature/setup/src/test/kotlin/AppearanceViewModelTest.kt` (new).

### ui-mobile (phone Settings)
- `ui-mobile/src/main/kotlin/ui/settings/AppearanceSection.kt` (new) — theme cards
  (Dark/Light/Auto, web's own labels/notes) + 7 accent dots, `Modifier.selectable(role =
  Role.RadioButton)` + `selectableGroup()` + `semantics(mergeDescendants = true)` so a
  screen reader gets one "Coral, Radio button, selected" node per option, matching the
  existing `CacheBudgetBlock.kt` convention already in this directory.
- `ui-mobile/src/main/kotlin/ui/settings/SettingsScreen.kt` — wired `AppearanceViewModel`,
  `AppearanceSection` is the first `LazyColumn` item (web's Appearance is the first tab).
- `ui-mobile/src/main/kotlin/ui/MobileApp.kt` — `MediagramTheme` now takes the
  `AppearanceViewModel`'s state (root of the app, so no flash of the wrong theme after
  first frame — DataStore-less `SharedPreferences` read is synchronous on construction).
- `ui-mobile/src/test/kotlin/ui/settings/AppearanceSectionTest.kt` (new) — Robolectric
  compose test: picking Light then Blue reaches a real `InMemoryAppearanceSettings` through
  a real `AppearanceViewModel`, and `assertIsSelected()`/`assertIsNotSelected()` confirm the
  announced state.

### ui-tv (TV Settings + theme wiring)
- `ui-tv/src/main/kotlin/ui/tv/TvTheme.kt` — `TvColors` (fixed val) replaced with
  `tvColorScheme(accent: Color)`; `TvTheme(accent: Accent = Accent.Default, content)` always
  resolves dark (see deliberate difference below) and writes the resolved accent to
  `Palette.Imprint` via `SideEffect`.
- `ui-tv/src/test/kotlin/ui/tv/TvThemeTest.kt` — updated for the function signature; added
  `choosingAnAccentSetsPaletteImprintSoEveryOtherWidgetFollowsIt`.
- `ui-tv/src/main/kotlin/ui/tv/TvApp.kt` — `TvTheme(accent = appearance.accent)`, appearance
  read from `AppearanceViewModel`.
- `ui-tv/src/main/kotlin/ui/tv/system/TvAppearanceBlock.kt` (new) — 7 focusable accent
  swatches (tv-material `Surface(onClick=...)` + `TvFocus.surface*()` helpers, unmodified —
  only consumed) with `role = Role.RadioButton` / `selected` semantics set by hand (the
  ClickableSurface's own click handling meant `.selectable()` would have doubled it).
- `ui-tv/src/main/kotlin/ui/tv/system/TvSettingsRows.kt`, `TvSettingsScreen.kt` — wired
  `AppearanceViewModel`, `TvAppearanceBlock` is the first row under the "Settings" heading.
- `ui-tv/src/test/kotlin/ui/tv/system/TvAppearanceBlockTest.kt` (new).

### Follow-up fix (after the data/navigation phase merged) — additional files touched
- `ui-mobile/src/main/kotlin/ui/settings/SettingsScreen.kt` — `AppearanceSection` moved to
  the last `LazyColumn` item (own file).
- `ui-mobile/src/test/kotlin/ui/settings/SettingsProfileRetryTest.kt` — registers
  `AppearanceViewModel` alongside `SettingsViewModel` (own file, `ui/settings/**`).
- `ui-tv/src/test/kotlin/ui/tv/system/TvAppearanceBlockTest.kt` — `performClick()` →
  `performSemanticsAction(SemanticsActions.OnClick)` (own file).
- `ui-mobile/src/test/kotlin/ui/MobileAppFixture.kt`, `LibraryFlowFixture.kt`,
  `ui-tv/src/test/kotlin/ui/tv/TvAppFixture.kt` — one-line `AppearanceViewModel` `models` map
  entry each (**outside this phase's ownership** — see "Follow-up" section below for why
  unavoidable).

## Deliberate differences (recorded, not silent)

1. **Web's artwork mode (Default/Blurred/Artwork/Solid) is not ported** — user decision,
   already written into the plan before this phase started.
2. **TV theme stays dark, always.** No light TV palette exists, and per the phase's own
   instruction this is the expected outcome ("TVs are viewed in dark rooms"). TV's Settings
   surface offers the accent picker only — no Dark/Light/Auto control, since a television
   would have nothing to answer with it. `TvTheme`'s own doc comment says why.
3. **TV accent swatches are square, not circular.** `TvFocus` already enforces one square
   corner across every plate this surface draws (`RectangleShape`, "a television plate is
   the same catalogue, not a second design" — pre-existing doc, not written by this phase).
   Circular dots would be a second shape the remote has to read; squares keep the swatch
   inside that existing rule.
4. **Default coral's exact hex changed**: `Palette.Imprint`'s literal moved from the
   Android-only approximation `#D26A55` to the web's own dark-coral `#E57A61`, because the
   phase's instruction is explicit that all seven accents — coral included — carry "the
   web's per-theme values," and coral is now one of those seven rather than a separate
   constant. Every dark-theme surface (TV focus borders, the phone's `primary` role) picks
   this up as the new unstyled default; nothing needed to change at any of those call sites
   because they already read `Palette.Imprint`/`colorScheme.primary` rather than a literal.

## Tests (initial pass, before the data/navigation phase merged)

- Type check / compile: `:core:designsystem` and `:feature:setup` compile and test clean.
- `:ui-mobile` and `:ui-tv` did **not** compile at that point — pre-existing, outside this
  phase's file ownership (`LibraryFlowBranches.kt`/`TvLibrary.kt`'s non-exhaustive `when`
  over a `FrameKind` the concurrent data/navigation phase was mid-way through extending).
  Reported rather than fixed, per instructions; that phase has since landed the branches and
  both modules compile.

## Follow-up: runtime failures once ui-mobile/ui-tv compiled

Once the data/navigation phase's carve-out branches landed, a full
`./gradlew testDebugUnitTest --continue` surfaced 47 failures the other agent traced to this
phase (report: `fullstack-developer-260926-1630-android-parity-data-and-navigation-report.md`).
Root causes and fixes, all within this phase's own files except where noted:

1. **`Cannot create an instance of class setup.AppearanceViewModel`** (44 of the 47) — every
   pre-existing Robolectric test that renders `MobileApp()`/`TvApp()`/`SettingsScreen()`
   builds its own `ViewModelStoreOwner` with a fixed map of pre-constructed ViewModels
   (`SetupViewModel`, `LoginViewModel`, `SettingsViewModel`, …) standing in for Hilt, which
   these tests don't run for real. `MediagramTheme`/`TvTheme`/`SettingsScreen` now also
   resolve an `AppearanceViewModel` through `hiltViewModel()`; any owner that doesn't
   separately hand one back falls to `ViewModelProvider`'s default reflective factory, which
   has no Hilt entry point to supply `AppearanceSettings` from and throws. Fixed by adding
   `AppearanceViewModel::class.java to AppearanceViewModel(InMemoryAppearanceSettings())` to
   each affected owner's map, the exact pattern every other entry in each of these maps
   already follows (`TvAppFixture`'s own doc comment already explains the rule this walks
   into: "this ViewModelStoreOwner has to hand back every one, or reaching that step through
   TvApp falls back to ViewModelProvider's default factory, which cannot construct one with
   no Hilt entry point to supply its arguments").
   - `ui-mobile/src/settings/SettingsProfileRetryTest.kt` — **within this phase's own
     ownership** (`ui/settings/**`); generalised its single-model `Factory` into a small map
     covering both `SettingsViewModel` and the new `AppearanceViewModel`.
   - `ui-mobile/src/test/kotlin/ui/MobileAppFixture.kt`,
     `ui-mobile/src/test/kotlin/ui/LibraryFlowFixture.kt`,
     `ui-tv/src/test/kotlin/ui/tv/TvAppFixture.kt` — **outside this phase's ownership**
     (shared app-flow fixtures spanning setup/catalog/player/settings/system). One-line
     `models` map entries each, unavoidable: these are the exact shared harnesses `MobileApp`/
     `TvApp`/`LibraryFlow` compose through in every other test, already following this precise
     one-entry-per-ViewModel convention for every other screen ViewModel; there is no
     narrower place to add the binding without duplicating each fixture.
2. **`TvAppearanceBlockTest.pressingAnAccentReportsItChosen` — `expected:<BLUE> but
   was:<null>`** (1 of the 47, my own new test, my own bug) — used `.performClick()` on
   tv-material's `ClickableSurface`, whose click routes through pointer-input gesture
   detection Robolectric doesn't run to completion (the exact reason `TvTmdbKeyScreenTest`'s
   own `press()` helper, elsewhere in this codebase, uses
   `performSemanticsAction(SemanticsActions.OnClick)` instead). Switched to the same call.
3. **`'Telegram'/'Sign out'/'Application id and hash…' is not displayed`** (3 of the 47, my
   own bug) — `MobileAppTest`, `LibraryFlowTest`, `SettingsProfileRetryTest` click/assert on
   those rows without scrolling first; putting `AppearanceSection` as the *first*
   `SettingsScreen` item pushed all of them below Robolectric's default viewport. Fixed by
   moving `AppearanceSection` to the *last* item in `SettingsScreen.kt`'s `LazyColumn` instead
   (own file, own ownership) — every other row is back at its original position, matching
   what all three tests already assumed; nothing in the phase's spec required Appearance to
   render first on the phone (only "an Appearance section in SettingsScreen.kt").
4. **`TvSearchAndGenreTest`, 6 failures, untouched** — traced to the concurrent data/
   navigation phase's own search-grouping change (`SearchUiState`/`SearchViewModel` now fetch
   `people`, `FakeCatalogRepository` updated for it); none of the three failing assertions
   ("No title, folder or summary…", "Film 1", "2 results") mention `Appearance`, `Accent`,
   `Palette` or any file this phase owns. Reported, not fixed, per instructions — this phase
   never touches `feature/catalog` or its search screens.

### Final numbers

- `./gradlew testDebugUnitTest --continue`: **354 tests, 6 failed** — all 6 in
  `ui.tv.TvSearchAndGenreTest` (item 4 above), zero elsewhere.
- `./gradlew lint` (every module): **0 errors** (`core/designsystem`, `feature/setup`,
  `ui-mobile`, `ui-tv`, `app`, and every other module checked individually).
- `./gradlew :ui-tv:compileDebugAndroidTestKotlin`: clean, exit 0.

## Status/Summary/Concerns

**Status:** DONE_WITH_CONCERNS

**Summary:** Theme (Dark/Light/Auto) + 7 accents built in `core/designsystem`, wired
through `MediagramTheme`/`TvTheme`, phone `AppearanceSection` and TV `TvAppearanceBlock`,
persisted per-device via `SharedPreferencesAppearanceSettings` behind a Hilt singleton
shared by both surfaces. Full-tree `testDebugUnitTest`/`lint`/`:ui-tv:compileDebugAndroidTestKotlin`
all green except a pre-existing, unrelated `TvSearchAndGenreTest` regression from the
concurrent data/navigation phase's own search-grouping change.

**Concerns:** Two `models` map entries were added to shared test fixtures outside this
phase's strict file ownership (`ui-mobile/src/test/kotlin/ui/MobileAppFixture.kt`,
`ui-mobile/src/test/kotlin/ui/LibraryFlowFixture.kt`,
`ui-tv/src/test/kotlin/ui/tv/TvAppFixture.kt`) — one-line, precedented, and explicitly
authorised by the lead's follow-up message; flagging per that authorisation's own "say
which" condition. `TvSearchAndGenreTest`'s 6 failures remain and are not this phase's to fix.
