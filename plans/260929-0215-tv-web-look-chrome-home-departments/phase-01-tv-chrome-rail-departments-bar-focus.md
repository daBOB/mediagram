---
phase: 1
title: "TV chrome: rail, departments bar, focus model"
status: completed
priority: P2
effort: 8h
dependencies: []
---

# Phase 01 — TV chrome: rail, departments bar, focus model

## Context links

- Tablet (the port to match): `android/ui-mobile/src/main/kotlin/ui/chrome/LibraryHome.kt:73-202`
  (rail beside, bar over, `LocalTopChrome` seam `:175`), `LibraryRail.kt:47-201`,
  `DepartmentsBar.kt:35-130`, `ChromeCounts.kt:22-58`, `ChromeControls.kt`; spec numbers in
  `plans/260927-2050-android-home-web-parity/phase-01-library-chrome-rail-departments-bar.md`
  ("Web spec"). Web: `web/public/index.html:46-90`, `web/public/styles/shell.css:18-257`.
- TV today: masthead `ui-tv/.../catalog/TvCatalogScreen.kt:134-165`, its focus/sentinel logic
  `catalog/TvCatalogNav.kt:48-103`, Back-to-masthead `TvLibraryBranches.kt:49-53`, menu page
  `TvLibrary.kt:144-169` + `system/TvMenuPage.kt:73-90`, focus treatment `TvFocus.kt:57-169`,
  index-row precedent `system/TvSettingsIndex.kt:113-169`, wall padding `catalog/TvWall.kt:129`.
- Reference shots: `plans/260927-2050-android-home-web-parity/reports/tablet-landscape-home-top.png`,
  `reports/reference/home-web-1.png`; plus a web stub shot at 960×540 (`cd web && bun run preview`).
- Measurement method: `plans/260928-0200-android-decoder-stall-recovery/reports/debugger-260928-0200-decoder-freeze-rootcause-and-closeout-report.md` §§ "Debug vs. benchmark", Part 3.

## Overview

Replace `TvMasthead` with the tablet's chrome in a remote-first form: a left rail and a
departments pill bar (search, avatar, ⋮). Today's Home and department bodies keep rendering
inside it unchanged (rebuilt in 02/03). Ships alone: new navigation, old page bodies.

## Key decisions (settled here)

**Rail: collapsed-until-focused.** Collapsed = the web's own ≤900px form of the same items (icons,
no counts: `shell.css:184-216`); focused = the tablet's full rail (wordmark, rows with counts,
tally) drawn over the content. Why: at the ten-foot floors (`TvTypeScale` body 18sp, secondary
16sp) the full rail needs ~288dp = 30% of 960dp (tablet 16%, web-at-960 19%) — always open it
would take a third of the width every cover, hero and wall has; Google TV (the box's own launcher)
uses exactly this collapse; and an overlay that never resizes the content costs no remeasure of
the Home tree (the performance finding). Counts and tally show wherever the tablet shows them the
moment the remote is in the rail. Alternative "always expanded" rejected for the width cost.

**Focus graph** (all regions are `focusGroup`s with `focusRestorer`):

```
            ┌──────────── bar: [Home][Movies n]…[Collections n]  (search)(avatar)(⋮) ┐
 rail  ◄─Left─┤  Up ▲ │ Down ▼ (→ page restorer: last stop, else page's first stop)   │
 (overlay) ─Right→ back to the region it was entered from                            │
            └──────────── content: current tab's page ────────────────────────────────┘
```

- Launch (fresh, no restore key): the page's own arrival stop (today: cover Watch now).
- Pill OK: selects the department, **the remote stays on the pill**, page shown from its top
  (a hero would otherwise scroll away the instant it appears). Down enters the page.
- Rail OK: My List / Continue → kept wall shown, remote to its first plate, rail collapses;
  Latest / Genres / Settings / System → pushed frame (full screen).
- Left from the left-most stop of the bar or page → rail, landing on the active row (My List /
  Continue when their wall shows) else My List. Right from any rail row → the region it came from.
  Up/Down stop at the rail's ends; nothing to the rail's left.
- **Back**: page → the selected pill (no pill selected, i.e. a kept wall → its rail row);
  bar (pill, search, avatar, ⋮) → rail; rail → not handled, the activity finishes (today's
  "Back at the top closes the app", `TvLibraryBranches.kt:21-28`).
- Empty/loading library: the remote rests on the avatar (the way out, as `TvCatalogNav.kt:71`).

**Overscan** (`Overscan` 48×27dp, `core/designsystem/.../Spacing.kt:34-37`): rail background
from x=0 full height, its icons/text start ≥48dp in and stay 27dp clear of top/bottom; bar row
padded 27dp from the top; content gutter 32dp from the collapsed rail's edge, 48dp at the right;
full-bleed art (02/03) may run into the unsafe margin, text and focus stops never do.

**Pushed frames sit over the chrome, full screen** (title, season, collection, person, genre,
search, Latest, Genres, Settings/System, preloads, player). Why: the TV has no back bar — Back
is the remote's key; those pages are laid out for the full 960dp and their focus edges are tested
as they are (a rail beside them adds a Left exit into it); Settings/System already draw their
own index rail (the tablet exempts them for that reason); the player is full screen anyway.
Back returns to the stop that opened the frame, rail rows included (restore keys below).
Deliberate difference from the tablet (rail stays beside its pushed pages) — written down in
`docs/system-architecture.md` § Television differs.

**Focus rings.** `TvFocus` gains shapes beside its square plate: pill (fully round) for pills and
cover buttons, control (6dp, `Radius.control`) for rail rows. Container and ring are always cut
from the same named shape (the invariant `TvFocus.kt:42-47` guards); accent ring 3dp + scale.

**⋮ menu**: `TvMenuPage` keeps only the Android-only rows, in the tablet's order (Preloads,
Update library, TMDB key…, Start over — `ui-mobile/.../OverflowMenu.kt:111-143`); My List,
Continue, Latest, Genres, Settings, System move to the rail (the tablet did the same).

## Requirements

Functional
- Rail rows in order My List (count), Continue watching (count), Latest, Genres, Settings,
  System (12dp apart); wordmark "mediagram" (not focusable — the Home pill is one press away);
  tally = `libraryTallyLines(shelves)` spaced caps. Counts = `chromeCountsOf(shelves, watch)`.
- Pills = `mastheadSplitOf(shelves).departments` with `ChromeCounts.departmentCount(title)`;
  selected = ink@12% fill; none selected on a kept wall (tablet review L3).
- Search (round icon) → Search frame; avatar (initial via `profileInitial`, contentDescription
  "Who's watching: $name") → `profile.onChoose`; ⋮ ("Menu") → trimmed menu page.
- Rail/pill/button semantics: `selectable(role = Tab)` for rows/pills; merged row semantics.
- Restore on return: `masthead:search` → search button; `masthead:menu` → ⋮;
  `menu:Settings` / `menu:System` (already recorded by `tvMenuActions`, `TvMenuBranches.kt:39-45`)
  and new `rail:latest` / `rail:genres` → that rail row, rail open; plate keys → page as today.
  Kept walls are chosen directly by the rail, so the `menu:mylist`/`menu:continue` sentinels
  (`TvLibrary.kt:187-191`, `TvCatalogNav.kt:64-65,90-101`) are deleted.

Non-functional
- Text ≥ 16sp everywhere in the chrome (labels 18sp `TvTypeScale.body`, counts/tally 16sp).
- Rail fits 540dp without scrolling (tally visible); expanding it never relayouts the content.
- Kotlin files ≤ ~200 lines; `TvCatalogScreen.kt` (200) and `TvLibraryBranches.kt` (179) shrink.

## Architecture

```
TvLibraryHomeFrame ─ TvCatalogRoot ─ TvCatalogScreen
  └ TvLibraryChrome(rail, bar, body)            ← new, owns regions, Back chain, padding seam
      Box {
        Column(padding start = 96dp) { body under/below TvDepartmentsBar }  // blend = 1 (opaque) in 01
        scrim (only while rail open)
        TvLibraryRail(open = railHasFocus)      // width 96 ↔ 288dp, overlay
      }
  CompositionLocal LocalTvPagePadding: chrome → (start 32, top = bar height, end 48, bottom 27)
                                        default (pushed frames) → Overscan on all sides
```

Data in: `CatalogUiState.Ready(shelves, watch)` → `chromeCountsOf`, `libraryTallyLines`,
`mastheadSplitOf`; `TvChosenProfile`; `MenuActions` (`tvMenuActions`, built at `TvLibrary.kt:107`,
now also passed to the home frame). Data out: `choose(tabIndex)`, `at.openLatest()`,
`at.openGenresIndex()`, `menu.onSettings/onSystem`, `at.openSearch()`, menu page open,
`profile.onChoose` — each recording its restore key at depth 0 first.

## Related code files

Move (git mv, package change only; ui-mobile imports updated, behaviour identical)
- `android/ui-mobile/src/main/kotlin/ui/chrome/ChromeCounts.kt` → `android/feature/catalog/src/main/kotlin/ChromeCounts.kt` (+ `profileInitial`); test `ui-mobile/src/test/kotlin/ui/chrome/ChromeCountsTest.kt` → `feature/catalog/src/test/kotlin/`.
- `RailItem` enum (`ui-mobile/.../ui/chrome/LibraryRail.kt:47-54`) → `android/ui-common/src/main/kotlin/ui/RailItem.kt` (labels, icon res, order shared).

Create
- `android/ui-tv/src/main/kotlin/ui/tv/chrome/TvLibraryChrome.kt` — regions, focus routing, Back chain, `LocalTvPagePadding`.
- `android/ui-tv/src/main/kotlin/ui/tv/chrome/TvLibraryRail.kt` — collapsed/open rail, wordmark, tally.
- `android/ui-tv/src/main/kotlin/ui/tv/chrome/TvDepartmentsBar.kt` — pills + search/avatar/⋮, `blend` param.
- `android/ui-tv/src/main/kotlin/ui/tv/chrome/TvChromeControls.kt` — `TvPill`, `TvRoundIconButton`, `TvAvatar`.
- `android/ui-tv/src/main/kotlin/ui/tv/TvIndexRow.kt` — row visuals shared by the rail and the Settings index.

Modify
- `ui-tv/.../catalog/TvCatalogScreen.kt` (masthead → chrome; pill-press arrival rule), `catalog/TvCatalogNav.kt` (pill/rail/sentinel targets), `catalog/TvCatalogBody.kt` (kept walls' `tabFocus`), `TvLibraryBranches.kt` (Back chain moves to the chrome; rail callbacks + restore keys), `TvLibrary.kt` (menu page wiring, `menu` to home frame, drop sentinels), `system/TvMenuPage.kt` (Android-only rows), `system/TvSettingsIndex.kt` (use `TvIndexRow`), `TvFocus.kt` (pill/control shapes), `catalog/TvWall.kt:129`, `catalog/TvHome.kt:118-119`, `catalog/TvMoviesDepartmentPage.kt:62-78`, `catalog/TvCollectionsPage.kt:83-84`, `catalog/TvLists.kt:78`, `catalog/TvKeptWall.kt:129` (padding from `LocalTvPagePadding`).
- ui-mobile import-only: `ui/chrome/{LibraryRail,LibraryHome,CompactLibraryHeader}.kt`, `ui/AppChrome.kt`, `ui/LibraryFlowBranches.kt`.
- Tests: `ui-tv/src/test/kotlin/ui/tv/{TvMenuTest,TvLibraryTest,TvHousekeepingTest,TvSearchAndGenreTest}.kt`, `catalog/{TvCatalogScreenStateTest,TvKeptWallStateTest}.kt`; androidTest `ui-tv/src/androidTest/kotlin/ui/tv/{TvLibraryRemoteTest,catalog/TvCatalogScreenTest}.kt`.
- Docs: `docs/system-architecture.md:654-656` ("Continue and My List live in the Menu" → rail; add pushed-frames and collapsed-rail differences), `docs/project-changelog.md`.

Delete
- `android/ui-tv/src/main/kotlin/ui/tv/catalog/TvMasthead.kt` (keep `TvSearchEntryKey`/`TvMenuEntryKey` string values, moved to `TvCatalogNav.kt`).

## Implementation steps

0. Baseline, before any change (box, benchmark build 0.81.1, "TV test" profile): repros R1 Home →
   OK on a Recently added poster → Back; R2 cover Watch now → Back → Back; R3 Movies pill → Home
   pill. 3 runs each; `adb -s 192.168.0.35:5555 logcat -c`, repro, `logcat -d | grep -c "Davey!"`
   and `grep -c "Choreographer.*Skipped"`, plus `dumpsys gfxinfo com.mediagram.android` janky %.
   Repeat on the debug build (`:app:installDebug` — same debug key, installs over the benchmark
   keeping the sign-in; if it ever asks to uninstall, STOP and measure benchmark only).
   Reinstall benchmark + `cmd package compile -m speed -f`. Save as `reports/baseline-home-return-measurements.md`.
1. Moves: `ChromeCounts` → feature/catalog, `RailItem` → ui-common; fix imports; run
   `:feature:catalog:testDebugUnitTest :ui-mobile:testDebugUnitTest` — unchanged results.
2. `TvFocus`: named shapes (plate/pill/control), each with its matching border; unit test that
   every shape's border uses the same shape.
3. `TvIndexRow` from `TvSettingsIndexRow`; Settings uses it (`TvMenuTest` Settings cases stay green).
4. `TvLibraryRail`, `TvDepartmentsBar`, `TvChromeControls` (Robolectric at w960dp-h540dp:
   labels, counts, tally last line displayed, rail open only while focused).
5. `TvLibraryChrome`: regions, restorers, Left/Right routing (`focusProperties` on the rail
   column, the `TvSettingsPanes.kt:118-128` onExit pattern for its ends), Back chain,
   `LocalTvPagePadding`; wire into `TvCatalogScreen`; apply the padding seam to the listed pages.
6. Pill-press rule: arrival focus runs only on launch or with a restore key naming a stop
   (`LocalTakesArrivalFocus` false after a pill press until something is opened).
7. Rail callbacks + restore keys in `TvLibraryBranches`/`TvLibrary`; trim `TvMenuPage`; delete
   the two sentinels and `TvMasthead.kt`.
8. Tests (see Success criteria); `scripts/check.sh`.
9. Box: install benchmark, compile speed, walk D-pad only (never OK on a Settings choice);
   screencap each state below; side-by-side with the tablet/web shots
   (`magick tv.png -resize x777 a.png; magick tablet.png a.png +append sheet.png`) into
   `reports/tv-chrome-*.png`. Re-run R1 on benchmark (must stay 0 Davey).
10. Docs; bump next minor (0.82.0 if nothing else released); changelog; commit.

## Todo

- [x] baseline measurements recorded (benchmark; debug numbers are in the decoder-freeze closeout)
- [x] ChromeCounts / RailItem moved, ui-mobile + feature tests green
- [x] TvFocus shapes; TvIndexRow shared with Settings
- [x] rail (collapsed/open, counts, tally), bar (pills, search, avatar, ⋮)
- [x] chrome regions, Left/Right/Up/Down routing, Back chain, padding seam
- [x] pill-press keeps focus; rail restore keys; menu page trimmed; masthead deleted
- [x] tests updated/added, check.sh green
- [x] box screenshots vs tablet; R1 still clean (0.82.1; see reports/lead-verification-phase-01-on-box.md)
- [x] docs, version, changelog, commit

## Success criteria

- Robolectric (w960dp-h540dp): bar shows Home, Movies n, Series n, Anime n, Documentaries n,
  Tutorials n, Collections n (no Continue/Watchlist pill); rail shows the six rows with My List
  and Continue counts and the tally; avatar opens the picker; ⋮ page lists exactly Preloads (when
  queued), Update library, TMDB key…, Start over.
- Focus: pill OK keeps focus on the pill and swaps the page; Down lands on the page's first stop;
  Up from a wall's top row → a pill; Left from the left-most plate opens the rail on My List (or
  the active row); Right returns to that plate; Back: plate → selected pill → rail → no handler
  enabled (activity finishes).
- Settings/System/Latest/Genres from the rail; Back lands on that rail row with the rail open.
- Every existing `TvMenuTest` Settings/System/storage/session case passes via the rail entry.
- Box: screenshots `tv-chrome-{home,rail-open,pill-focus,mylist,menu}.png` beside the tablet's; no
  text or focus ring inside the 48×27dp margin; R1 benchmark 0 Davey / 0 skipped.

## Risk assessment

| Risk | L×I | Mitigation |
|------|-----|------------|
| Focus lost/trapped between overlay rail and content | M×H | explicit Right routing + restorers; tests for each edge; emulator walk before box |
| Geometric Left lands on a rail row by y, not the active row | M×M | rail `focusRestorer(activeOrMyList)` on a `focusGroup` |
| Pill-press rule breaks restore after a pushed frame | M×H | restore key wins over the flag; `TvLibraryTest` walks cover it |
| Rail does not fit 540dp (tally clipped) | M×M | budget: wordmark 60 + 6×48 + 12 + tally ~116 ≤ 486dp; test the last tally line displayed |
| Pills overflow the bar (7 pills + 3 buttons > width) | H×L | pill row scrolls inside its slot (tablet `DepartmentsBar.kt:112-120`); focused pill brought into view |
| Moving shared code changes tablet behaviour | L×M | package move only; ui-mobile tests unchanged and green |

## Security

None new: profile chooser, Start over confirmation and TMDB key screen keep their handlers.

## Next

Phase 02 draws Home under the bar (blend) using `LocalTvPagePadding` and the bar's `blend` param.
