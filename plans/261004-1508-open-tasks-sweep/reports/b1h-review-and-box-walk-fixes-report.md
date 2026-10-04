# B1h — review and box-walk fixes (Android editorial parity)

Branch `worktree-agent-ac7d5ea005ed9c6b3`, from `main` 12216185 (0.108.0). No version bump
(lead bumps). Not pushed.

## What changed, item by item

| # | Finding | Fix | Test |
|---|---------|-----|------|
| 1 | Phone Collections built ~109 cards in one lazy item | `tileLines` (ArtTile.kt): one lazy item per line, tiles `key(id)`; columns from `tileColumnsOf` (`BoxWithConstraints` around the page's `LazyColumn`). `TileFlow` kept for Search's handful, now the same weighted `TileLine` | `CollectionsScreenTest.onlyTheLinesInViewAreBuilt` (109 franchises: "SAGA 109" not composed until scrolled to) |
| 2 | TV season picker hid the shown season | `TvSeasonPicker`: `onEnter` (Up/Down only) to the shown pill, as `TvSectionTabs`; scrolls the shown pill into view whenever the shown season changes (pill spans from `onPlaced`, plain `ScrollState.scrollTo` so the page itself never moves) | `TvCollectionStateTest.theShownSeasonsPillIsInSightAndTakesTheRemoteFromTheTab` (6 seasons, resume in 5, native text so the row overflows) |
| 3 | TV pills outside the 544dp copy column | `actions()` moved into the capped column (one `Column`, `widthIn(max=544)`) | `TvTitlePageStateTest.aFullRowOfPillsStaysClearOfTheQuote` |
| 4 | Back from a franchise landed on Play; bare id collided with person ids | Title frame saves `franchiseRestoreKey(id)` = `"franchise:$id"`; "Part of" link takes a requester + `LaunchedEffect` like genre links; `TvFactSheet`'s `genreFocus` → `restoreKey`; Play's arrival skips when coming back to a link | `TvTitlePageStateTest.comingBackFromTheFranchiseLandsOnItsPartOfLink` (cast member with the same id) |
| 5 | Phone Movies genre tiles 192×96 cut the count | Row tiles 240dp (TV's width); `ArtTile` aspect is now a floor (`aspectRatioAtLeast`), so a large font grows the tile | `MoviesDepartmentScreenTest.aGenreTileGrowsToHoldItsNameAndCountAtTwiceTheFontSize` |
| 6 | Stale `listLabel` comment | Boolean overload folded in; comment says players only, title pages say "✓ My List" | existing `PlayerListLabelTest` |
| 7 | Dead SEASON frame | Removed `FrameKind.SEASON`, `openSeason`, `season`, `ResolvedPositions.season`, `Destination.Season`, phone `SeasonScreen` + branch, `CollectionScreen.onOpenSeason`, `TvSeason`, `TvSeasonFrame` + branch, false comments. Old saved stack: `decode` already drops unknown kinds, so `COLLECTION·SEASON·TITLE` restores as `COLLECTION·TITLE`. Course pages no longer fetch info/credits/similar (null provider key, empty similar) on both surfaces | `LibraryPositionsEncodingTest.aSavedSeasonFrameRestoresToItsShowsPage` |
| 8 | Duplicated phone/TV logic | `feature/catalog`: `ResumeVerb.word` + `resumeWordsOf`, `seasonOptionOf`, `franchiseLineOf`; used by phone and TV. `TvSearch` uses `SearchDestination.franchiseId` | `SeriesResumeTest.theResumeWords`, `…aSeasonOption…`, `FranchisesTest.aFranchisesLine…` |
| 9 | Franchise page scrolled the name off | Hero wears `revealsFromTop()`: the introduction taking the remote asks for the hero from its top | `TvFranchisePageStateTest.theIntroductionHoldingTheRemoteShowsTheHeroFromItsTop` (leanback pivot spec provided explicitly, native text) |
| 10 | Bar: Left from Search skipped scrolled-out pills | Bar holds each pill's requester; Search's `focusProperties { left = last pill }`; focus brings the row to it | `TvLibraryChromeUpTest.leftFromSearchReachesTheLastPillScrolledOutOfView` (real department names, native text) |
| 11 | Course play line "▶ Play S1 E1" | `resumeWordsOf`: lesson → "lesson N" ("▶ Continue lesson 3"), unnumbered → title, episode unchanged | `TvCoursePageStateTest.theResumeLineNamesALessonByItsNumber` |
| 12 | TV Preload pill had no outline | `TvPreloadPlate`/Remove are `TvSpreadPill`s; new `accent` = imprint border+text for the phone's Line states, rule border for Quiet; Done faint via constant-shape alpha/semantics | `TvTitlePreloadStateTest.thePlateIsAPillAsTallAsPlayAndDoneReadsDisabled` |
| 13 | Back from Editor's choice card → Home pill | **Not changed.** Design intends return-to-card (`homeTargetOf` covers FEATURES; architecture doc: "Back returns to whichever pill, rail row or plate opened the frame"). Could not reproduce: both a semantic walk and a non-touch-mode D-pad/Back-key walk return to the card on current code | `TvHomeFeatureReturnTest` (2 walks, both pass) |

Verified-before-fix: with the fixes for 2, 3, 9 and 10 reverted, their new tests fail
(Season 5 pill at x=964dp; "⋯" under the quote; hero top at −146dp after Up; Collections
not focused), and pass with them.

Docs: DESIGN.md (genre row tile 240dp + floor; TV Preload as outlined pill),
docs/system-architecture.md (course resume wording; season picker keeps shown pill in sight).

## Tests

`./gradlew -q --continue :ui-tv:testDebugUnitTest :ui-mobile:testDebugUnitTest
:ui-common:testDebugUnitTest :feature:catalog:testDebugUnitTest :feature:player:testDebugUnitTest
:ui-tv:compileDebugAndroidTestKotlin lint` — exit 0. ui-tv 490, ui-mobile 286, ui-common 74,
feature:catalog 343, feature:player 252; 0 failures, 0 skipped. Lint: no new findings on
changed files (ArtTile's `screenWidthDp` warning predates this).

## Concerns / box-only

- **Item 13 unresolved.** Most likely the cold-start first title-page return that commit
  aaff8785 recorded as still losing focus (root re-entry → bar's first pill = Home): a
  fresh self-update relaunch then opening Editor's choice first matches it. Box check: open
  any other Home card first, Back, then Editor's choice, Back. If only the first return after
  launch fails, it is that mechanism, not feature cards.
- Item 9 fix reasoned from the pivot + safe-band rules; the arrival-only scroll on the box
  was not reproduced in Robolectric (only the Up-from-film variant was). Confirm on the box
  with a long introduction (James Bond).
- Item 2/10 scrolling reads real widths; confirm on the box that the shown season and
  Collections pill scroll fully into view (focus scale 1.08 may clip a few px at the edge).
- Seen, not fixed (out of scope): `TvHome`'s `included` index omits the Continue item when
  it holds only the quote (and Recent with only "This month"), so arrival scrolls one item
  short for bands below; the request still lands because the next item is composed.
- Native lib on main predates the 0.107.0 core change; unit tests use the fake core.
