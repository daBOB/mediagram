# Phase Implementation Report

### Executed Phase
- Phase: phase-01-web-tokens-geist-radius-design-doc
- Plan: /home/andre/Workspace/mediagram-channel-index/plans/260927-1731-android-settings-system-redesign
- Worktree: /home/andre/Workspace/mediagram-channel-index (branch feat/android-settings-redesign, from main 0a9bd249 / 0.68.14)
- Status: completed, not committed (lead commits)

### Files Modified
- `android/core/designsystem/src/main/kotlin/Palette.kt` (+56/-16): dark values → web's dark theme verbatim (Ground `#0D0D0E`, Page `#151517`, Sunk `#1B1B1D`, Text `#F3EFE7`, Figures `#CBC5BA`, Rule 18%, RuleStrong `#9C968B`, Ochre `#E6C47F`, Sage `#A3D3A4`); added `Sidebar #09090A`/`LightSidebar #EBE5D9`, `RuleSoft` 8%/`LightRuleSoft` 9%; object KDoc rewritten (no more "inverted stock" reasoning).
- `android/app/src/main/res/values/colors.xml`: `ground` → `#FF0D0D0E`.
- `android/core/designsystem/src/main/kotlin/CatalogueTones.kt` (new, 26 lines): `CatalogueTones(sidebar, ruleSoft, quiet)`, `DarkTones`/`LightTones`, `LocalCatalogueTones` (defaults dark).
- `android/core/designsystem/src/main/kotlin/Theme.kt`: `CatalogueShapes` (6dp extraSmall..large), `MediagramTheme` now provides `LocalCatalogueTones` and passes `shapes` into `MaterialTheme`.
- `android/core/designsystem/src/main/kotlin/Spacing.kt`: added `Radius.control = 6.dp`.
- `android/core/designsystem/src/main/kotlin/Type.kt` (+144/-31): Geist `Interface` family (400/500/600, weight-only variation); `bodyMedium`/`bodySmall`/`labelLarge`/`labelMedium`/`labelSmall` → Geist (tnum kept where it was); `bodyLarge` untouched (Newsreader 17sp); new `PageTitle`/`PageTitleCompact` (Fraunces 500, opsz 112/72, −0.03em, 0.86em line height, `LineHeightStyle(Center, Trim.None)`), `Eyebrow` (Geist 500 11sp, 0.32em, 1.4em), `SectionHead` (Geist 600 16sp), `LedgerLabel`/`LedgerValue` (Geist 14/15sp, value tabular); `TvTypeScale.body` → Geist 18sp, added `pageTitleMax = 72.sp`, `eyebrow = 16.sp`.
- `android/core/designsystem/src/main/kotlin/PageHead.kt` (new, 55 lines): `PageHead(title, eyebrow, titleColor, eyebrowColor, maxTitleSize, modifier)`, `BasicText` + `TextAutoSize.StepBased`, merged `semantics { heading() }`. Foundation-only (no material3), so `:ui-tv` can call it in phase 03/04.
- `android/core/designsystem/licenses/README.md`: documents the third face (Geist) and its regenerate command.
- `android/core/designsystem/licenses/OFL-geist.txt` (new): copied verbatim from `web/public/font/`.
- `android/core/designsystem/src/main/res/font/geist.ttf` (new, 65,800 bytes): `woff2_decompress web/public/font/geist-latin.woff2`.
- `android/core/designsystem/src/test/kotlin/ContrastMeasurement.kt` (new, 20 lines): `luminance`/`contrast`/`MINIMUM_CONTRAST`, extracted out of `AccentContrastTest` per the phase's own "move it to a shared test file if needed" — both contrast tests now share one WCAG implementation.
- `android/core/designsystem/src/test/kotlin/AccentContrastTest.kt`: drops its private duplicate helpers, doc comment no longer claims Android's ground is "its own warmer near-black" than the web's.
- `android/core/designsystem/src/test/kotlin/PaletteContrastTest.kt` (new, 51 lines): Text/Figures/RuleStrong/Ochre/Sage vs Ground/Page/Sunk/Sidebar, both themes — the Measured Colour Rule, 2 test methods.
- `DESIGN.md` (repo root, Android): full rewrite — colors section with an Android↔web role-mapping table, three-face typography (Fraunces/Newsreader/Geist) with Page Title/Eyebrow/Section Head/Ledger hierarchy entries, a two-shape Shapes section (6dp control vs 0dp plate, with the 12dp swatch-card exception), Components section extended with the token contracts for settings-index-row/page-head/ledger/pill-line/pill-quiet/chip/toggle/swatch-card (explicitly flagged as tokens carried from the approved mockups, not yet composed as Kotlin), One Accent Rule widened to match the web's own accent use, "What this system does not yet cover" updated honestly.
- `docs/project-changelog.md`: new `## 0.69.0` top entry.
- `Cargo.toml` (workspace version), `Cargo.lock` (refreshed via `cargo check -q -p mediagram`), `web/package.json`, `android/app/build.gradle.kts` (`versionName` only, `versionCode` untouched at 18): `0.68.14` → `0.69.0`.
- `plans/260927-1731-android-settings-system-redesign/plan.md` and its own phase file: phase 01 row/status/todo ticked.

No screen files (`ui-mobile`, `ui-tv`, `feature/*`) were touched — matches the phase's "no screen code changes" scope.

### Tasks Completed
All ten implementation steps and all ten todo items in the phase file, checked.

### Tests Status
- Type check / compile: pass (`:core:designsystem:compileDebugKotlin` clean, no new warnings beyond two pre-existing ones — missing `compose_compiler_config.conf` and an unresolved adaptive opt-in marker, both present before this phase).
- Full verify command (`testDebugUnitTest :core:model:test lint :ui-tv:compileDebugAndroidTestKotlin :core:ffmpeg:compileDebugAndroidTestKotlin :core:rust:compileDebugAndroidTestKotlin`): **BUILD SUCCESSFUL** on both trees.
  - Before (clean tree, stashed): BUILD SUCCESSFUL, 1488 unit tests total across all modules.
  - After (this phase's changes): BUILD SUCCESSFUL, 1490 unit tests total (+2, both in the new `PaletteContrastTest`). No lint failures, no test failures, no compile errors on either run.
  - `designsystem`'s own suite: 11 → 13 tests (`AccentContrastTest` 1, `CatalogueColorsTest` 4, `OverscanTest` 1, `PaletteContrastTest` 2 new, `SharedPreferencesAppearanceSettingsTest` 3, `TvTypeScaleTest` 2), all green.
- Measured Colour Rule sanity-checked by hand before writing the test (WCAG luminance formula, Python): every dark text colour clears 4.5:1 on all four grounds, tightest is `RuleStrong` on `Sunk` at 5.85:1; every light text colour clears it too, tightest is `LightRuleStrong` on `LightSunk` at 4.62:1.
- `git grep -n 'Color(0x' android` outside `core/designsystem` (build dirs excluded): 0 hits — success criterion holds.
- APK size (`:app:assembleDebug`, debug build): before 89,998,222 bytes, after 89,345,020 bytes (raw delta −653,202 bytes). This raw stat delta is noisy — a debug APK isn't byte-reproducible between two separate incremental Gradle invocations (dex/zip packing order varies) — so I checked the actual added asset directly: `res/font/geist.ttf` is a genuinely new zip entry, 65,800 bytes uncompressed / 35,288 bytes compressed in the APK, well under the phase's ≤~80KB budget. No evidence of a real regression; the raw negative delta is build-to-build packing noise, not a shrink this phase caused.

### Issues Encountered
None — no file-ownership conflicts, no blockers. One judgment call: added `ContrastMeasurement.kt` (not itself listed under "Create" in the phase file) to de-duplicate the luminance/contrast math between `AccentContrastTest` and the new `PaletteContrastTest`; the phase file's own step 8 explicitly invites this ("reuse AccentContrastTest's luminance helper — move it to a shared test file if needed").

### Follow-up list for phase 05 (screens/components that will look off under the new palette/type)

- **TV has no reading face left at all.** `TvTypeScale.body` is TV's *only* body style and is now Geist end to end — including whole-sentence prose: `TvTitleHeader.kt:109` (overview), `TvCollection.kt:149-151`/`TvTitleDetails.kt:31-34` (network/status lines), `TvNotesPanel.kt:178` (lesson notes), `TvPlayerFailure.kt:54`, `TvSetupStep.kt:170`, `TvTextQuestion.kt:117-131`, `TvSystemScreen.kt:80-90`. Mobile kept `bodyLarge` in Newsreader for exactly this; TV has no equivalent second style to fall back to, so this is the single largest asymmetry between the two surfaces this phase leaves behind. Phase 05 likely wants a `TvTypeScale.read` (Newsreader) alongside `body` (Geist), the same split mobile already has.
- **Mobile's own whole-sentence text moved to Geist too**, breaking DESIGN.md's Whole-Sentence Rule at several sites still on `bodyMedium`: `TitleDetailScreen.kt:224` (overview), `TitleSpread.kt:99,107` (overview), `CoverStory.kt:177` (deck), `PersonScreen.kt:74`, `FranchiseScreen.kt:60`, `GenreScreen.kt:63`, `GenresIndexScreen.kt:45`, `LatestScreen.kt:60`, `NotesPanel.kt:97` (lesson notes). These read as sentences, not labels, and are candidates to move to `bodyLarge` rather than stay on `bodyMedium`.
- **`AppChrome.kt:117`**: `Scaffold`'s `containerColor` is `colorScheme.surface` (→ Page, `#151517`), i.e. every mobile screen's own background is one step lighter than the web's actual page background (`--paper`/Ground, `#0D0D0E`) — a pre-existing mismatch this phase's palette swap makes literal rather than approximate (flagged in the phase's own risk table, confirmed still present).
- Nothing else showed up in `Color(0x…)` (0 hits outside designsystem) — every other screen reads roles off `MaterialTheme.colorScheme`/`TvTypeScale`, so it takes the new tokens automatically; the two items above are the ones worth a screenshot pass in phase 05.

### Next Steps
Phase 02 (Artwork setting) builds on `Appearance`, unaffected by this phase. Phase 03/04 consume `PageHead`, `Radius.control`, `LocalCatalogueTones`, and the new `PageTitle`/`Eyebrow`/`SectionHead`/`LedgerLabel`/`LedgerValue` styles directly — all now public in `designsystem`. Phase 05 owns the follow-up screenshot sweep and the two items above.

**Status:** DONE
