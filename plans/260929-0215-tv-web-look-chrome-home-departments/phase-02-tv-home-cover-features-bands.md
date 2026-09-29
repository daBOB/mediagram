---
phase: 2
title: "TV Home body: cover, features, bands, shelves"
status: completed
priority: P2
effort: 10h
dependencies: [1]
---

# Phase 02 — TV Home body: cover, features, bands, shelves

## Context links

- Tablet port: `android/ui-mobile/src/main/kotlin/ui/catalog/HomeScreen.kt:53-139` (section order,
  `LazyColumn`, hoisted list state), `ui/catalog/home/{HomeCover,CoverSlide,CoverControls,HomeFeatures,ContinueBand,ResumeCard,RecentBand,HomeShelfRow,CourseList,HomeType}.kt`;
  numbers in `plans/260927-2050-android-home-web-parity/phase-02-home-cover-features-bands-shelves.md`
  ("Web spec"); review lessons `.../reports/code-reviewer-260928-0005-home-body-review-report.md`
  (H1 index-keyed crossfade crash, M1 hold rotation on focus, M2 scrim layer order).
- Web: `web/public/lib/catalog/home-view.js:64-114`, `home-cover.js`, `styles/home.css:6-360`.
- Shared data: `feature/catalog/src/main/kotlin/MagazineHome.kt:18-83`, `EditorialPicks.kt`,
  `HomeShelves.kt` (`HOME_POSTER_ROW_LIMIT = 8`); tablet call `ui-mobile/.../catalog/CatalogScreen.kt:189-197`.
- TV today: `ui-tv/.../catalog/TvHome.kt` (Column + `verticalScroll` composing every row,
  `:108-119`; arrival `:102-106`; its recorded difference "Continue and Next up stay in rows"
  `:55-60`), `TvCoverStory.kt:73-126` (21:9 pager inside overscan), `TvFeatureStrip.kt`,
  `TvHomeRow.kt` ("plates do not scroll sideways" `:36-41`), `TvCatalogBody.kt:52-78`.

## Overview

Rebuild TV Home to the tablet's magazine layout: cover under the bar, features, Continue band
with the quote, Recently added with This month, Latest series, Latest courses. Data selection is
already shared (`magazineHomeOf`); this phase is layout, type, focus and two new actions on TV
(+ My List, the quote's link). Removes the two TV Home differences recorded at `TvHome.kt:55-60`
and `TvHomeRow.kt:36-41` — the web itself now draws strips (`home-view.js:94,106`).

## Key decisions

**Sizes = the web at W = 960 (a TV is one width), text raised to the ten-foot floors** (primary
18sp, secondary 16sp; display type untouched). One exception, written down: **cover height =
window height − 72dp** (468dp) so its buttons sit on the first screen with the features peeking;
the web's `clamp(620px, 63.5vh, 705px)` is taller than a 540dp screen.

**Cover with the remote**
- Rotation every 9s (`HOLD_MS`) only while no focus is inside the cover (web: `focusin` holds;
  tablet `HomeCover.kt:137`) and while Home is on screen.
- Stops, left to right: Watch now (plays) · + My List / ✓ My List (toggles) · Details (title
  page) · one dot per film (bottom-right). **A focused dot shows its film** (focus-selects, the
  Settings-index rule); OK on a dot does nothing more. Left from Watch now → rail; Up → pills;
  Down → features.
- No pause button: focus on the cover already holds it, so the toggle could only ever be pressed
  while held. No 14s drift zoom: a continuous redraw on a weak box for a flourish. (Both written
  down as TV differences.)
- Slides (art + words) crossfade 1.4s **keyed by setId**; the action row and dots live outside
  the crossfade, bound to the film shown — focus never sits in a slide being faded out, and a
  shrinking lineup cannot index past the list (tablet H1).
- Artwork mode: exactly the tablet's `CoverSlide` rule (cover keeps its art under Solid —
  `DepartmentHero.kt:68-72` notes the split); scrim = the tablet's, shared (below).

**Focus through the page**: `LazyColumn` of sections (only visible ones composed — today's
`Column` composes all of them); the page root `focusRestorer(fallback = cover Watch now, else
first stop)`. Strips scroll sideways with the remote; the row's last stop hands Right to its
"See all →" (the `SeeAllLink` pattern from `TvHomeRow.kt`); from Recently added's See all, Right
reaches This month. Restore on return: pure `homeTargetOf(sections, restoreKey)` → section +
stop, scroll-then-focus (`TvDepartmentRows.kt:58` precedent).

**Bar over the cover**: translucent (`OverCoverBg`) over the cover, opaque once scrolled past,
from the Home `LazyListState` through the shared `coverBlend` — the chrome's `blend` param from 01.

## Requirements

Functional
- Order and content = `HomeScreen.kt:84-137`: cover (≤5, `editorial.cover`), features (≤3),
  Continue band (`magazine.resumeCards` + `editorial.quote`), Recently added (≤8, count
  `recentlyAddedRow.total`) + This month (≤5), Latest series (≤8 posters, Fraunces caption,
  "21 episodes · three seasons"), Latest courses (list rows, "eight lessons · one chapter").
  Inputs: `magazineHomeOf(…, recentLimit = HOME_POSTER_ROW_LIMIT)` and `homeRowsOf(…, posterLimit
  = HOME_POSTER_ROW_LIMIT)` filtered as `CatalogScreen.kt:194-197`. A section with nothing is not drawn.
- Actions: Watch now and resume cards **play**; + My List → `catalogViewModel.setWatchlisted`
  (new TV wiring); Details, features, posters, This month lines, quote attribution → title page;
  series posters and course rows → collection; See all → Continue wall / Movies / Series / Tutorials.
- Cover eyebrow "FEATURED TODAY · {first genre}", title uppercase Fraunces 600 (`CoverTitle`),
  61sp (42sp when > 18 chars), ≤ 2 lines; deck ≤ 2 lines; meta `coverFactsLine`.
- Feature cards: first full width (~300dp), two beside each other (~220dp); 14dp corners;
  labels from one shared mapping; second card not uppercased (web rule).

Non-functional
- No section composes off screen beyond the lazy cache; no text < 16sp; every stop shows a ring
  (pill shape on cover buttons/dots, card shape on features, plate on posters).
- R1 (Home → poster → Back) on the benchmark build stays at 0 Davey / 0 skipped.

## Architecture

```
TvCatalogBody(selected == 0) ─ TvHome(magazine, rows, watch, listState*, actions)
  LazyColumn(state = listState*)                      *hoisted to TvCatalogScreen → chrome blend
    "cover"    TvHomeCover { Crossfade(key=setId){TvCoverSlide}; TvCoverActions; TvCoverDots }
    "features" TvHomeFeatures
    "continue" TvContinueBand { TvResumeRow | TvQuote }
    "recent"   TvRecentBand   { poster strip | This month }
    "series"   TvBandHeading + TvPosterStrip(captions)
    "courses"  TvBandHeading + TvCourseList
Shared: ui-common CoverScrim/DeptArtScrim(paper), CoverBlend; feature/catalog spelledCountOf,
        FeatureKind.label
```

Data in: `CatalogUiState.Ready` → `MagazineHome` + filtered `HomeRow`s. Out: `onPlay`,
`onOpenTitle`, `onOpenCollection`, `onToggleWatchlist(setId, listed)`, `onSeeAll(tabTitle)`,
each recording its restore key (as `TvLibraryBranches.kt:128-170`).

## Related code files

Move / lift (ui-mobile behaviour identical; its tests stay green)
- `ui-mobile/src/main/kotlin/ui/chrome/CoverBlend.kt` (+ `ui-mobile/src/test/kotlin/ui/chrome/CoverBlendTest.kt`) → `android/ui-common/src/main/kotlin/ui/chrome/CoverBlend.kt` (+ test).
- Scrim brushes from `ui-mobile/.../catalog/home/CoverControls.kt:140-186` and `DepartmentHeroLayouts.kt:61-76` → `android/ui-common/src/main/kotlin/ui/catalog/HeroScrims.kt` taking `paper: Color` (layer order as fixed by review M2); ui-mobile calls them.
- `feature/catalog/src/main/kotlin/LibraryTally.kt:24-31` `spelledCountOf` → public; `ui-mobile/.../catalog/home/HomeType.kt:49-58` `countOf` becomes a one-line delegate (duplicate removed).
- Feature labels (`HomeFeatures.kt:46-52`, `TvFeatureStrip.kt:38-44`) → `FeatureKind.label` in `feature/catalog/src/main/kotlin/EditorialPicks.kt:30`.

Create (`android/ui-tv/src/main/kotlin/ui/tv/catalog/home/`)
- `TvHomeCover.kt`, `TvCoverSlide.kt`, `TvCoverActions.kt` (buttons + dots), `TvHomeFeatures.kt`,
  `TvContinueBand.kt` (+ quote), `TvResumeCard.kt`, `TvRecentBand.kt` (+ This month),
  `TvPosterStrip.kt`, `TvCourseList.kt`, `TvBandHeading.kt` (heading, count, See all, `SeeAllLink`),
  `TvHomeTargets.kt` (pure `homeTargetOf`).

Modify
- `ui-tv/.../catalog/TvHome.kt` (rewrite: LazyColumn of sections), `catalog/TvCatalogBody.kt:52-78`
  (inputs as the tablet's), `catalog/TvCatalogScreen.kt` (hoist home list state, bleed + blend to
  the chrome), `chrome/TvLibraryChrome.kt` (bleed: page draws under the bar, `LocalTvPagePadding`
  top 0 on Home when a cover exists), `TvLibraryBranches.kt` (`onToggleWatchlist`), `TvFocus.kt`
  (card shape, 14dp).
- ui-mobile: `ui/chrome/{LibraryHome,DepartmentsBar}.kt`, `ui/LibraryBranchSupport.kt` (imports),
  `catalog/home/{CoverControls,HomeFeatures,HomeType}.kt`, `catalog/DepartmentHeroLayouts.kt`.
- Tests: `ui-tv/src/test/kotlin/ui/tv/catalog/{TvHomeStateTest,TvCatalogScreenStateTest}.kt`,
  `TvCatalogRootPlayStateTest.kt`, androidTest `catalog/TvCatalogScreenTest.kt`.

Delete
- `ui-tv/.../catalog/TvFeatureStrip.kt`, `ui-tv/.../catalog/TvHomeRow.kt` (course/plate rows
  replaced; `keysOf` superseded by `homeTargetOf`). `TvCoverStory.kt` stays until 03.

## Implementation steps

1. Lifts: `CoverBlend` → ui-common, scrims → `HeroScrims.kt`, `spelledCountOf` public +
   `countOf` delegate, `FeatureKind.label`. Run `:feature:catalog :ui-common :ui-mobile` unit tests.
2. `homeTargetOf` + unit test (cover default, restore into each section, missing key → default).
3. Cover: slide (art, scrim, words), actions, dots; rotation held by `hasFocus`, keyed by setId.
4. Features, Continue band (resume cards 16:8.4, ~240dp wide, progress bar; quote with focusable
   attribution), Recent band + This month, poster strip, course list, band heading with See all.
5. `TvHome` as `LazyColumn`; list state hoisted; chrome bleed + blend; bottom padding 27+48dp.
6. Wire `onToggleWatchlist`; delete `TvFeatureStrip.kt`, `TvHomeRow.kt`.
7. Tests; `scripts/check.sh`.
8. Box (benchmark, compile speed, "TV test" profile for any play; D-pad only): screencap Home at
   top (cover, focus on Watch now), a focused dot, features, Continue band, Recent band, Latest
   series/courses; side by side with `plans/260927-2050-android-home-web-parity/reports/tablet-landscape-home-{top,mid,lower}.png`
   and the web stub at 960×540 → `reports/tv-home-*.png`. Re-run R1–R2 on benchmark.
9. Bump next minor; changelog; commit.

## Todo

- [x] shared lifts (blend, scrims, spelled counts, feature labels); ui-mobile tests green
- [x] homeTargetOf + test
- [x] cover: slide, actions, focus-selecting dots, focus-held rotation, setId crossfade
- [x] features, Continue band + quote, Recent band + This month, series strip, course list
- [x] LazyColumn Home, hoisted state, bar blend over the cover
- [x] + My List wiring; old files deleted
- [x] tests, check.sh green
- [ ] box shots vs tablet/web; R1–R2 benchmark clean — left for the lead, see report
- [x] version, changelog, commit

## Success criteria

- Robolectric: sections in the tablet's order; eyebrow "FEATURED TODAY · …"; title uppercase;
  Watch now plays; + My List toggles and reads "✓ My List"; Details opens the title; resume card
  OK plays; See all lands on Continue/Movies/Series/Tutorials; quote attribution opens its film;
  arrival lands on Watch now; `mainClock` +20s with focus on a cover button → same film; focusing
  the third dot shows the third film; a lineup shrinking 3 → 2 while on film 3 does not crash;
  Back from a title opened from Recently added lands on that poster.
- Box: sheets beside the tablet show the same sections, order and type; differences listed in the
  phase report with reasons; R1/R2 benchmark 0 Davey / 0 skipped.

## Risk assessment

| Risk | L×I | Mitigation |
|------|-----|------------|
| LazyColumn + D-pad: Down to a section not yet composed | M×H | lazy beyond-bounds focus search; cache window as `TvWall.kt:41-42`; test Down through every section |
| Restore target inside an uncomposed section | M×M | `homeTargetOf` → `scrollToItem(section)` then focus (DeptRow precedent) |
| Crossfade + two full-bleed bitmaps on the weak box | M×M | 1.4s fade only, no zoom; measure R1/R2; Coil size = view size |
| Fraunces 600/opsz cut renders as the 900 default | L×M | use `CoverTitle` (static cut) as the tablet does; check on the box |
| Lifting scrims shifts tablet visuals | L×M | identical stops/order; ui-mobile Robolectric + one tablet shot compared |
| Focus ring over bright art unreadable | M×L | ring + scale; verify on the box shots |

## Security

None: watchlist toggle goes through the existing `CatalogViewModel.setWatchlisted`.

## Next

Phase 03 reuses `TvBandHeading`, `TvResumeCard`, `TvPosterStrip` and the shared scrims for the
department heroes and rows.
