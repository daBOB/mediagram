# Phase 01 — Library chrome: rail + departments bar

## Context links

- Web reference: `web/public/index.html:46-90`, `web/public/styles/shell.css:18-257`,
  `web/public/app.js:116-123, 416-444, 500-508`, `web/public/lib/format.js:158-175`.
- Android today: `android/ui-mobile/src/main/kotlin/ui/AppChrome.kt:65-137`
  (`LibraryScaffold`, `TopAppBar`, `ProfileButton`), `ui/OverflowMenu.kt:28-124`,
  `ui/LibraryFlowBranches.kt:62-283`, `ui/catalog/CatalogScreen.kt:45-211`,
  `ui/catalog/ShelfTabs.kt:37-79`, `feature/catalog/CatalogTabs.kt:14-63`,
  `feature/catalog/KeptShelves.kt:36-55`, `feature/catalog/LibraryTally.kt:34-42`,
  `ui/settings/SettingsIndex.kt:69-184` (sidebar/wordmark/tally precedent).
- Reference shots: `reports/reference/home-web-{1,2,3}.png` (1164×777),
  `home-web-phone-{1..4}.png` (412 wide), `home-android-*.png` (today).

## Overview

Priority P2 · status pending. Replace the root library's Material top app bar and serif
tab row with the web player's chrome: a left rail on EXPANDED and a Geist departments
pill bar laid over the content; on compact/medium the web's ≤900px header.

## Web spec (the numbers to match; px → dp, rem = 16)

**Rail** (EXPANDED only)
- Width 184dp when the window is ≤1180dp wide, 224dp above (web `--rail-width`
  14rem / 11.5rem). Padding 26 top / 18 sides / 28 bottom. Background
  `tones.sidebar`; 1dp right border `tones.ruleSoft`. Full height, beside everything.
- Wordmark "mediagram": Fraunces 600, 27sp, line 1.1, tracking −0.02em, ink, 36dp
  below; tapping it goes Home (`toCatalog` + tab 0). Same wordmark as
  `SettingsIndex.kt:90-98` — extract one `Wordmark` composable, use it in both.
- Items, in order: My List (count), Continue watching (count), Latest, Genres,
  Settings, System (12dp extra top gap; shown only when the System screen is
  available — mirror whatever gates the overflow's System entry today).
  Row: min 44dp, padding 10×12, 12dp gap, 6dp radius; Geist 500 14sp in
  `onSurfaceVariant` (web ink-2); pressed/hover `ruleSoft` fill; active = ink on
  ink@10%. Icon 21dp line icon (convert the web's inline SVGs in `index.html`
  46-66 to vector drawables, stroke 1.4 — same icons on both surfaces).
  Count: right-aligned 12sp `tones.quiet`, tabular figures.
  Targets: My List / Continue → today's kept tab indices (`LibraryFlowBranches.kt:
  69-76`); Latest / Genres → their frames; Settings / System → today's routes.
  Active item: highlight when that destination is the one showing.
- Counts: My List = `watchlistWall(shelves, watch).size`; Continue =
  `continueWall(shelves, watch).size` (`KeptShelves.kt:36-55`).
- Tally under a `ruleSoft` line (28dp margin above, 24dp padding): one line per
  `libraryTallyLines(shelves)` entry, Geist 500 10sp, line height 2, tracking 0.34em,
  uppercase, quiet. (Documentaries stay out — the web's tally omits them too.)

**Departments bar**
- Items: Home (no count), Movies, Series, Documentaries (phase 03 adds the shelf;
  build the bar from the tab list so it appears when the shelf does), Tutorials,
  Collections. Counts: shelf `entries.size` per department; Collections =
  `watch.collections.size` (web counts the viewer's own lists — `app.js:420`).
- Bar height 76dp, starts after the rail, horizontal padding = gutter
  (`clamp(16dp, 3.2% of window width, 56dp)`), 24dp gap. Pills: fully round
  (`CircleShape`/50%), min height 44dp, padding 10×16 (12 sides ≤1180), Geist 500
  14sp, count 11sp 6dp after the name at 65% of the bar's ink. Active pill: ink@12%
  fill. Horizontally scrollable if they do not fit.
- Right side: search = 44dp round icon button (21dp magnifier, stroke 1.6) opening
  today's Search frame; avatar = 44dp circle, ink fill, profile initial in `paper`
  17sp Geist 600 ("?" without a profile) → `ProfileBarState.onChoose`
  (contentDescription keeps "Who's watching: $name"); then a ⋮ `OverflowMenu`
  holding only the Android-only actions: Update library (with its note), TMDB key…,
  Start over. My List/Continue/Latest/Genres/Settings/System leave the menu on
  EXPANDED (the rail has them) and on compact (the icon row has them).
- Over the cover (Home tab only): transparent-ish `Color(0x590A0A0B)` (web .35) with
  `on-image` text; as the home scrolls from 40% to 75% of the viewport height it
  blends to `paper` @ 94% with ink text (no blur on Android — see plan's deliberate
  differences). Other tabs: bar is `paper` @ 94% from the start and content starts
  below it (web: masthead sticky, hero below it).
- Status bar: the bar sits below the status-bar inset; on Home the cover (phase 02)
  draws under both.

**Compact / medium** (web ≤900px)
- Row 1: wordmark (Fraunces 600, ~23sp; 21sp under 480dp) left; icon-only rail items
  right (scroll sideways if needed), contentDescription = the label; no counts.
- Row 2: department pills, full width, scroll sideways (pill padding 8×12).
- Row 3: a full-width search field look-alike (tapping opens the Search frame) +
  avatar + ⋮.
- Header hides on scroll down, returns on scroll up (`enterAlways`-style); not over
  the cover — phone content starts below it.

## Architecture

- New `ui/chrome/` package in ui-mobile: `LibraryRail.kt`, `DepartmentsBar.kt`,
  `CompactLibraryHeader.kt`, `Wordmark.kt` (or in designsystem if TV will want it —
  keep it ui-mobile unless trivially shareable), `ChromeCounts.kt` (pure: counts from
  `shelves` + `watch`, unit-tested).
- `LibraryScaffold` keeps serving pushed frames (back bar), but on EXPANDED every
  library frame (not PLAYER, not Settings/System which have their own index pane)
  renders as `Row(LibraryRail, content)`.
- The root catalog renders through a new `LibraryHome`-level layout: bar overlaid on
  the content (`Box`), and the bar's height (`76dp` + status inset) handed down so the
  Home tab can draw under it and the other tabs can pad below it. This is the seam
  phase 02 builds on — expose it as one value (e.g. a `CompositionLocal` or a
  parameter `topChrome: Dp`), not ad-hoc paddings.
- Home-tab scroll position must reach the bar for the over-cover → solid blend:
  hoist the home's `LazyGridState`/`LazyListState` (or a derived fraction) to the
  layout that draws the bar.
- `ShelfTabs.kt` (serif Material tab row) is replaced by the pill bar; delete it if
  nothing else uses it. `visibleTabIndices` stays the source of which tabs show.

## Related code files

- Modify: `ui/AppChrome.kt`, `ui/OverflowMenu.kt`, `ui/LibraryFlowBranches.kt`
  (split if it grows — it is already 328 lines), `ui/catalog/CatalogScreen.kt`
  (233 → split out the tab routing if touched), `ui/settings/SettingsIndex.kt`
  (use shared `Wordmark`).
- Create: `ui/chrome/{LibraryRail,DepartmentsBar,CompactLibraryHeader,Wordmark,
  ChromeCounts}.kt`, rail icon vector drawables (core/designsystem res, alongside the
  `core_designsystem_ic_settings_*` ones).
- Delete: `ui/catalog/ShelfTabs.kt` if unused afterwards.
- Tests to update: `ui/MobileAppTest.kt` and `ui/LibraryFlowTest.kt` (they find the
  "Mediagram" bar title and a "Menu" button), `catalog/browse/OverflowUtilitiesTest.kt`
  (menu no longer holds My List/Continue/Latest/Genres), `VisibleTabIndicesTest.kt`.

## Implementation steps

1. Copy the six reference shots from the scratchpad
   (`/tmp/claude-1000/-home-andre-Workspace-mediagram/f9f35227-d855-45ef-bd42-4834fc9a8693/scratchpad/home-{web,android}-*.png`,
   `home-web-phone-{1..4}.png`) to `plans/260927-2050-android-home-web-parity/reports/reference/`.
2. `ChromeCounts` (pure) + unit test.
3. `Wordmark`, rail icons, `LibraryRail` (+ Robolectric test at `w1164dp-h777dp`:
   items, counts, tally, tapping each lands where the menu entry used to).
4. `DepartmentsBar` + `CompactLibraryHeader`; wire into the root catalog; trim the
   overflow menu to the Android-only actions.
5. EXPANDED: rail beside every library frame.
6. Update the tests listed above; `./gradlew :ui-mobile:testDebugUnitTest
   :feature:catalog:testDebugUnitTest :ui-tv:testDebugUnitTest` green (TV must not move).
7. Install on the tablet (`ANDROID_SERIAL=caad49da`, test profile), screenshot home
   at the same three scroll depths as the web shots; also portrait (medium).
8. Bump to 0.70.0 by regex in all three manifests; changelog entry; commit.

## Todo

- [x] reference shots copied
- [x] ChromeCounts + test
- [x] Wordmark shared with SettingsIndex
- [x] rail icons (from web SVGs)
- [x] LibraryRail + test at tablet width
- [x] DepartmentsBar (over-cover blend) + CompactLibraryHeader
- [x] overflow trimmed to Android-only actions
- [x] rail on every library frame (EXPANDED)
- [x] tests updated, all module tests green
- [x] tablet screenshots landscape + portrait
- [x] 0.70.0, changelog, commit (version + changelog done; commit left for review per instructions)

## Success criteria

- Tablet landscape home matches `home-web-1.png` chrome: rail left with counts and
  tally, pill bar with counts over the cover, search + avatar right.
- Every destination the old overflow reached is still reachable in one tap.
- No Material `TopAppBar` on the root library; pushed frames keep a back affordance.
- All ui-mobile, feature/catalog and ui-tv unit tests pass.

## Risks

- `LibraryFlowBranches.kt`/`CatalogScreen.kt` are already over 200 lines: split by
  concern while touching them, not after.
- Hidden kept-tab index math (`shelvesCount+1/+2`) shifts when phase 03 adds a shelf —
  derive from `firstKept`, never hard-code.
- Edge-to-edge insets: the bar over the cover must not sit under the status bar.

## Security

None — presentation only; the profile chooser and menu actions keep their handlers.

## Next

Phase 02 draws the home under the bar using the seam from step 4.
