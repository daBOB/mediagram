# Phase 01 — TV chrome: rail, departments bar, focus model

Worktree: `/home/andre/Workspace/mediagram/.claude/worktrees/agent-ad2335b005b156856`
Branch: `feat/tv-web-look-chrome`, based on `main` `7d91c866` (0.81.1)
Version: `0.82.0` (Cargo.toml, Cargo.lock's 5 workspace crates, web/package.json, android/app versionName; versionCode untouched at 18)

## Status

`in-review` — implementation, tests and docs done; on-box verification left for the lead (see below). Not marked `completed`.

## Files

### Created
- `android/ui-common/src/main/kotlin/ui/RailItem.kt` — `RailItem` enum, moved from `ui-mobile`.
- `android/feature/catalog/src/main/kotlin/ChromeCounts.kt` — `ChromeCounts`/`chromeCountsOf`/`profileInitial`, moved from `ui-mobile`.
- `android/feature/catalog/src/test/kotlin/ChromeCountsTest.kt` — moved test, package `catalog`.
- `android/ui-tv/src/main/kotlin/ui/tv/TvIndexRow.kt` — shared row visual (rail row / Settings index row).
- `android/ui-tv/src/test/kotlin/ui/tv/TvFocusShapesTest.kt` — shape-invariant unit test.
- `android/ui-tv/src/main/kotlin/ui/tv/chrome/TvLibraryChrome.kt` — regions, `LocalTvPagePadding`, Back chain, `TvChromeFocus`.
- `android/ui-tv/src/main/kotlin/ui/tv/chrome/TvLibraryRail.kt` — collapsed/open rail.
- `android/ui-tv/src/main/kotlin/ui/tv/chrome/TvDepartmentsBar.kt` — pills + search/avatar/⋮.
- `android/ui-tv/src/main/kotlin/ui/tv/chrome/TvChromeControls.kt` — `TvPill`, `TvRoundIconButton`, `TvAvatar`.
- `android/core/designsystem/src/main/res/drawable/core_designsystem_ic_menu_more.xml` — ⋮ icon (no material-icons-core on ui-tv's classpath).

### Modified
- `android/ui-tv/src/main/kotlin/ui/tv/TvFocus.kt` — `PillShape`/`ControlShape`, `shape` param threaded through `cardShape`/`cardBorder`/`surfaceShape`/`surfaceBorder`/`fieldBorder` (default preserves the old square plate).
- `android/ui-tv/src/main/kotlin/ui/tv/catalog/TvCatalogScreen.kt` — chrome wiring, pill/rail dispatch, new `menu`/`onOpenLatest`/`onOpenGenresIndex` params.
- `android/ui-tv/src/main/kotlin/ui/tv/catalog/TvCatalogNav.kt` — `TvChromeFocus`-based restore/focus logic, new sentinels, pill-press arrival suppression.
- `android/ui-tv/src/main/kotlin/ui/tv/catalog/TvCatalogBody.kt` — kept-wall `tabFocus` now the rail's own row.
- `android/ui-tv/src/main/kotlin/ui/tv/TvLibraryBranches.kt` — `TvCatalogRoot` back-chain removed (now chrome's own), new params.
- `android/ui-tv/src/main/kotlin/ui/tv/TvLibrary.kt` — `menu` threaded to the home frame, trimmed `TvMenuPage` call, two sentinels removed.
- `android/ui-tv/src/main/kotlin/ui/tv/system/TvMenuPage.kt` — trimmed to Update library / TMDB key… / Start over / Preloads.
- `android/ui-tv/src/main/kotlin/ui/tv/system/TvSettingsIndex.kt` — `TvSettingsIndexRow` delegates to `TvIndexRow`.
- `android/ui-tv/src/main/kotlin/ui/tv/catalog/{TvWall,TvHome,TvMoviesDepartmentPage,TvCollectionsPage,TvLists,TvKeptWall}.kt` — read `LocalTvPagePadding` instead of plain `Overscan`.
- `android/ui-mobile/src/main/kotlin/ui/chrome/{LibraryRail,ChromeControls,CompactLibraryHeader,LibraryHome}.kt`, `ui/{AppChrome,LibraryFlowBranches}.kt` — import-only fixes for the two moves; behaviour unchanged.
- `docs/system-architecture.md` — § 8 TV chrome paragraph rewritten; § Television differs: rail collapse, six rail rows vs. departments-only bar, pill-press-keeps-focus, pushed-frames-full-screen (deliberate tablet difference).
- `docs/project-changelog.md` — `0.82.0` entry at the top.
- `Cargo.toml`, `Cargo.lock` (5 workspace crates), `web/package.json`, `android/app/build.gradle.kts` — version bump.

### Deleted
- `android/ui-tv/src/main/kotlin/ui/tv/catalog/TvMasthead.kt` — `TvSearchEntryKey`/`TvMenuEntryKey` string values moved to `TvCatalogNav.kt`.
- `android/ui-mobile/src/main/kotlin/ui/chrome/ChromeCounts.kt`, `android/ui-mobile/src/test/kotlin/ui/chrome/ChromeCountsTest.kt` — moved, not just deleted.

### Tests updated
`ui-tv/src/test/kotlin/ui/tv/{TvMenuTest,TvLibraryTest,TvHousekeepingTest,TvSearchAndGenreTest,TvAppTest}.kt`, `catalog/{TvCatalogScreenStateTest,TvKeptWallStateTest}.kt` — masthead-era selectors and Back-chain assertions rewritten for the rail/bar model; new `TvFocusShapesTest.kt`.

## What changed, structurally

- Rail (`TvLibraryRail`) collapses to icons (96dp, no counts, no tally) and opens to 288dp — same web-parity content as the tablet's rail — the moment anything inside it has focus (`Modifier.onFocusChanged` on its own `Box`, no separate open/closed flag). Each row carries a `contentDescription` fallback for when it's collapsed (no visible label to read).
- `TvDepartmentsBar` mirrors `mastheadSplitOf(shelves).departments` only (Home, shelves, Collections) plus search/avatar/⋮; Continue watching, My List, Latest, Genres, Settings, System all moved to the rail, matching the tablet's own split.
- `TvLibraryChrome` draws the bar and content as **Z-order overlay siblings** in one `Box` (not a `Column`) — content's own top inset (`LocalTvPagePadding.top` = bar height) is what visually clears the bar; a later phase's cover bleed only has to change the bar's own `blend`, not this structure. Rail is a further sibling overlay, aligned start.
- Pill press keeps the remote on the pill (`pillPressed` flag in `TvCatalogNav`, suppresses `LocalTakesArrivalFocus` until either something is opened-and-left, i.e. a wall-level restore key, or Down explicitly enters via `focusProperties { down = contentFocus }` set on every bar control). Rail row selection (My List/Continue/Latest/Genres/Settings/System) does **not** set this flag — arrival happens immediately, per spec.
- Back chain: three `BackHandler`s in `TvLibraryChrome`, gated on `contentHasFocus`/`barHasFocus` (rail has none — an unhandled Back there falls through to the activity). Both handlers target the rail's active-kept-row-or-My-List via one `railArrivalTarget()` helper.
- New restore-key sentinels in `TvCatalogNav.kt`: `TvLatestRailKey` ("rail:latest"), `TvGenresRailKey` ("rail:genres"); `menuRestoreKey(MenuScreen.System/Settings)` ("menu:System"/"menu:Settings") now also resolved **at the catalog level** (previously only meaningful inside the now-removed masthead-adjacent menu page) to redirect to the matching rail row. `TvSearchEntryKey`/`TvMenuEntryKey` keep their exact string values, moved from the deleted `TvMasthead.kt`.
- **Root-cause fix during the work**: `takesArrivalFocus` initially only accounted for the pill-press flag, not the sentinel-redirect cases — content's own arrival effect was winning the race against `chromeFocus.searchFocus.requestFocus()`/`menuButtonFocus.requestFocus()` etc. Fixed once, in the one formula (`wallKey != null || (!pillPressed && !redirectsFocus)`), not per sentinel.

## Deviations from the phase file

- **TvFocus shape invariant test** (step 2: "unit test that every shape's border uses the same shape"): `CardShape`/`CardBorder`/`ClickableSurfaceShape`/`ClickableSurfaceBorder`'s fields are `internal` to `tv-material`'s own Gradle module (confirmed via `javap` against the AAR — Kotlin blocks the read at compile time even though the bytecode exposes a `$tv_material`-suffixed accessor). `TvFocusShapesTest.kt` instead asserts the invariant on `TvFocus.fieldBorder`, the one function here whose return type (`Border`) exposes `shape` publicly; `cardShape`/`cardBorder`/`surfaceShape`/`surfaceBorder`'s own invariant is enforced by inspection of their four-line bodies (each threads one `shape` parameter to both container and border) rather than a runtime assertion tv-material's own encapsulation makes impossible from outside its module.
- **Avatar shows an initial, not the full name**: per the phase text ("avatar (initial via `profileInitial`...)"), several pre-existing tests asserted `onNodeWithText("Ada")`/`"andre"` against the old masthead's full-name row. Rewrote the JVM ones to `onNodeWithContentDescription("Who's watching: $name")`; the androidTest ones (`TvCatalogScreenTest.kt`) still assert the old full-name text and are left for the lead (see below) since they only need to compile for `check.sh`.
- **Wordmark size**: no spec number given for the rail's own "mediagram" at 288dp; reused the tablet's own narrow-rail lesson (23sp, rounded to 24sp) rather than `TvTypeScale.title` (34sp), which the tablet found wraps in a similarly narrow rail. Flag for the lead's on-box screenshot review.
- **Bar's own left inset**: phase names 32dp (content gutter) and 48dp (right) but not the bar's own left padding beyond the outer 96dp already reserved for the collapsed rail. Added `Spacing.medium` (16dp) for visual breathing room — an aesthetic choice, not a safety-margin one (96dp much greater than the 48dp overscan floor either way).
- **`TvCatalogRoot`/`TvCatalogScreen`'s new `menu`/`onOpenLatest`/`onOpenGenresIndex` params default rather than being required**, so `TvCatalogScreenTest.kt`/`TvLibraryRemoteTest.kt`/`TvCatalogRootPlayStateTest.kt` (androidTest + one JVM test) compile unchanged — deliberate, keeps the diff smaller without weakening anything the phase asked for.

## Tests

- Type check / compile: `:ui-tv:compileDebugKotlin`, `:ui-tv:compileDebugAndroidTestKotlin` — pass.
- Unit tests: `:ui-tv:testDebugUnitTest :ui-mobile:testDebugUnitTest :feature:catalog:testDebugUnitTest` — pass (ui-tv 359 tests, ui-mobile and feature:catalog unchanged and green, confirming the two shared-module moves didn't touch phone/tablet behaviour).
- `scripts/check.sh` (clippy, `cargo test --all`, `bun run lint` + `bun test`, `gradle testDebugUnitTest :core:model:test lint :ui-tv:compileDebugAndroidTestKotlin :core:ffmpeg:compileDebugAndroidTestKotlin`) — **all checks passed**. Android lint: 14 warnings, 1 pre-existing baselined error (`lint-baseline.xml`, unrelated to this phase's files) — no new lint failures.

## Left for the lead (on-box, explicitly out of scope here)

1. Step 0 baseline (benchmark + debug Davey/skipped-frame counts) — not measured, per instructions.
2. Box screenshots `tv-chrome-{home,rail-open,pill-focus,mylist,menu}.png` beside the tablet's/web's; overscan-margin check for text/focus stops.
3. `TvCatalogScreenTest.kt`/`TvLibraryRemoteTest.kt` (androidTest) compile against the new API but their own assertions still describe the old masthead (tab count via "nine rights", full-name text, no rail/pill Back chain) — they need rewriting to the new focus graph before they'd mean anything on a real device; they are not run by `check.sh` (compile-only) so this did not block the gate.
4. R1 repro (Home to OK on a poster to Back) Davey/skip count on the benchmark build.
5. Wordmark size and bar's own left gutter (see Deviations) — no explicit spec number, my choices are reasonable but unverified against the box.

## Unresolved questions

None blocking — the two deviations above (avatar initial vs. full name; TvFocus shape test scope) are both direct, verified consequences of the phase text and a real Kotlin `internal`-visibility wall respectively, not open judgment calls.
