# TV title/series pages, cast, library routing, Latest, person/franchise/collections — fixes

Work context: `/home/andre/Workspace/mediagram-tv-review` (worktree, branch `review/tv-editorial-parity`).
Fixed the review findings in the file-ownership scope given (`ui-tv/{TvLibrary.kt, TvLibraryFrames.kt,
TvLibraryBranches.kt}` + catalog title/series/collections/cast/similar/tabs/latest/person/franchise/genres
files, `ui-common/RememberLookups.kt`, `core/data/PortraitRequestLog.kt`), plus one cross-cutting item the
lead asked for mid-task (Watch now → player, `TvLibraryBranches.kt`).

## What changed, by finding

**M4 (tabs reset after Back from Cast/Similar; plates lose focus).** Chose the "derive the tab from
restoreKey" alternative the phase offered, not the `SaveableStateProvider`-per-frame one: `TvTitlePage`/
`TvCollection` are torn down and rebuilt (not kept alive underneath) when a person's or a similar
title's/show's page is pushed over them, so a plain `rememberSaveable(set.setId)` restart never had
anything to restore from — this is the actual root cause `rememberSaveable` on its own can't fix. Both
pages now compute `initialTab` from `restoreKey` (matching a cast member's personId or a similar
item's key) before creating their `rememberSaveable` state, so a fresh rebuild lands on the right tab
without depending on Compose's saved-state machinery at all. `TvCastRow`/`TvSimilarFilms`/
`TvSimilarShows` all gained a `restoreKey` param and a `FocusRequester` on the matching plate (default:
first plate, so the row is never left with nothing focused). Tests: `TvTitlePageTabsStateTest`/
`TvCollectionTabsStateTest` (`comingBackFrom...LandsOn...TabWithThat...Focused`).

**M5 (Cast row squeezed past 6, all portraits fetched at once).** `TvCastRow` is now a `LazyRow`
(was a plain `Row`): all ≤12 cast reachable by scrolling, and a portrait only fetches once its own
plate actually composes. Added the crew line ("Directed by"/"Created by", matching `cast.js`) above it.

**M2 (Latest part)/N10.** `TvLatestPage` no longer goes through `TvHomeRow`/`PlateRow` (hard-capped at
`HOME_ROW_LIMIT`=6 regardless of the 48-limit `homeRowsOf` was already asked for, and owned by the
parallel agent — I can't edit it) — it renders one `TvWall` over all three kinds concatenated, with a
`headings` map marking where each kind's plates start, matching the web's `renderLatest` (one page
heading, a sub-heading per kind, GRID walls, no "See all" — a reference page, not a rail). Sub-heading
label is `HomeRow.seeAll` (the shelf name, "Movies"/"Series"/"Tutorials") rather than the row-title
string previously matched against, and rows are filtered by `content is RowContent.Entries` (a
structural check) rather than the three literal `"Latest …"` title strings. `heldIds` reaches every
plate now (`TvWall`'s `plate` callback, not `TvHomeRow`'s hardcoded one).

**N2 (Similar didn't demote watched titles).** `TvLibraryCatalogFrames.kt`'s `TvTitleFrame`/
`TvCollectionFrame` now pass a real `watchedIds` set (from `watch.watched`) into `similarTo`/
`similarShows` instead of `{ false }`, matching phone's `TitleDetailScreen.kt`/`CollectionScreen.kt`.

**N3 ("Part of <franchise>" / crew line).** Title page's Overview now shows "Part of <franchise>"
(via `catalog.franchisesIn(allFilms)` + `set.collectionId`, opening `FrameKind.FRANCHISE`) beside the
genres, matching `film-page.js`/phone `FactSheet`. Crew line: see M5.

**N4 (Collections franchise focus/restore; squeeze).** `TvCollectionsPage` now gives franchise tiles
arrival/restore focus (matching a franchise id in `restoreKey`) and suppresses `TvLists`' own arrival
focus while a franchise is the target (`LocalTakesArrivalFocus` provided `false` locally — `TvLists`
itself untouched, not mine to edit). Fixed a real double-inset bug causing the squeeze: the page's own
outer `Column` padded `vertical = Overscan.vertical` on top of `TvLists`' own internal
`contentPadding` doing the same at the bottom — now only the top is padded at the page level.

**N5 (Person page flash; no marks).** Added `ui.catalog.rememberPersonLookup`/`PersonLookup` (new,
ui-common) distinguishing "still asking" from "resolved to nobody" — `TvPersonPage` now takes a
`loading: Boolean` and shows `TvLoadingIndicator()` instead of the "Nobody…" sentence while it's true.
`loading` is `personLookup.loading || catalogState !is Ready` (a cold restore's own shelves aren't
ready either). Wall plates now carry real `watch`/`heldIds` marks instead of `emptyMap()`/`emptySet()`.

**N6 (Franchise page leaves while catalogue still Loading).** `TvFranchiseFrame` now resolves its page
through the existing `TvResolvedBranch` (the same Loading/Stale/Resolved dance `TITLE`/`SEASON`/
`COLLECTION` already use) instead of an ad-hoc `if (page == null) leave()` that couldn't tell "not
ready yet" from "no such franchise".

**N8 (portrait marked requested before the fetch).** Root-caused entirely inside
`ui.catalog.rememberPortrait` (ui-common) — see "N8 in detail" below for why the fix could *not* touch
`PortraitRequestLog`'s external contract, and turned out not to need to.

**Item 10 (plan/phase references in comments).** Reworded at all six flagged lines
(`TvTitlePage.kt:192` — moved with `TvTitleDetails`; `TvCollectionsPage.kt:28`; `TvCollection.kt:105`;
`RememberLookups.kt:142`; `PortraitRequestLog.kt:9`) plus one more I found while rewriting
(`TvLatestPage.kt:25`, now a full rewrite).

**Item 11 (200-line rule).** `TvLibrary.kt` 425→176 (split into `TvLibraryCatalogFrames.kt` — Title/
Season/Collection frames — and `TvLibraryExtraFrames.kt` — Person/Franchise/Genres/Latest/
MoviesPage frames — plus `TvLibraryHomeFrame` moved into `TvLibraryBranches.kt`, 92→176).
`TvCollection.kt` 278→174 (`CollectionHeader`/`SeriesResumeRow`/`TvSeasonPlate` split into new
`TvCollectionHeader.kt`). `TvTitlePage.kt` 216→193 (`TvTitleDetails` split out; shared `TvTabBody`
scroll wrapper added to `TvSectionTabs.kt`, replacing near-duplicate private wrappers in both files).
Every `ui-tv` file I touched or created is under 200 lines. `ui-common/RememberLookups.kt` is 223 (not
a `ui-tv` file, so outside the rule's literal scope) — flagged rather than force-split; splitting ten
~15-line lookup functions that already share one shape would fragment cohesion for no real gain.

**Watch now → player (mid-task addition).** The browse agent's finished `TvCatalogScreen`/
`TvCatalogBody`/`TvHome` chain already threads `onPlay: (setId) -> Unit = onOpenTitle` down to the
cover story's "Watch now" — nothing wired it to the player. Added `onPlay` to `TvCatalogRoot`
(same default) and wired `TvLibraryHomeFrame`'s own call to `at.openPlayer(setId)` (marking
`restore.opened(here, setId)` first, the same as every other open-from-Home path). Did not touch
`TvCatalogScreen.kt`/`TvCatalogBody.kt`/`TvHome.kt`/`TvCoverStory.kt` (finished, not mine). New test:
`TvCatalogRootPlayStateTest.watchNowPlaysStraightAwayRatherThanOpeningTheTitlePage`.

## N8 in detail — why the fix lives entirely in `RememberLookups.kt`

The finding: `PortraitRequestLog.shouldRequest` reserves a person (`asked.add(id)`, true only once)
*before* the fetch runs; a fetch cut short (screen left, `LazyRow` item disposed mid-request) leaves
that person "already asked" forever, with no portrait to show for it.

A real fix needs a second signal — "this attempt actually finished" — alongside the existing reserve.
I could not add that signal through `PortraitRequestLog`/`BrowseViewModel`: `BrowseViewModel`
(`feature/catalog`, off-limits) is the *only* path from `ui-tv` to `PortraitRequestLog`, and it exposes
`shouldRequestPortrait`/`fetchPortrait` as a fixed pair with nothing to confirm completion through. The
same `BrowseViewModel` is shared with `ui-mobile` (also off-limits), so changing `shouldRequest`'s
own contract (e.g. making it a pure query instead of a reservation) would mean every caller that
doesn't also get a new "confirm" call — `ui-mobile`'s `CastPanel`/`CollectionScreen`/
`SearchGroupsView`, and `TvSearchResults.kt`/`TvSearch.kt` (the parallel agent's, also off-limits) —
would refetch on every remount instead of once per session, trading a narrow bug for a much bigger one
in files I'm not allowed to touch.

The fix instead lives entirely inside `ui.catalog.rememberPortrait` (`ui-common`, mine, the *sole*
caller of `shouldRequest` across the whole app): two new file-private `ConcurrentHashMap` sets,
`attemptedPortraitFetches`/`donePortraitFetches`, track "started" separately from "finished". A retry
is allowed when a person was attempted but never finished, bypassing `shouldRequest` entirely for that
case (so a stale "no" from the shared log doesn't block it); a normal first-ever ask still goes through
`shouldRequest` exactly as before. This fixes the bug for *every* caller in the app — including the two
I can't edit — without changing `rememberPortrait`'s signature or `PortraitRequestLog`'s external
contract at all. `PortraitRequestLog.kt` itself only gets its comment reworded (item 10); no functional
change was needed there. New tests: `RememberLookupTest.aPortraitFetchCutShortIsRetriedLaterInTheSession`,
`aFinishedPortraitFetchIsNotRetried` (both use a fake `shouldRequest` shaped like the real reserve-once
log, not a permissive stand-in, so the retry is proven not to depend on the shared log ever saying yes
twice).

## Files

**Main, modified:** `PortraitRequestLog.kt`, `RememberLookups.kt`, `TvLibrary.kt`, `TvLibraryBranches.kt`,
`TvCastRow.kt`, `TvCollection.kt`, `TvCollectionsPage.kt`, `TvLatestPage.kt` (full rewrite), `TvPersonPage.kt`,
`TvSectionTabs.kt`, `TvSimilarRow.kt`, `TvTitlePage.kt`.
**Main, created:** `TvLibraryCatalogFrames.kt`, `TvLibraryExtraFrames.kt`, `TvCollectionHeader.kt`,
`TvTitleDetails.kt`.
**Tests, modified:** `TvCollectionTabsStateTest.kt` (+2), `TvPersonPageStateTest.kt` (rewritten: +2 new
required params on every call, +2 new tests), `TvTitlePageTabsStateTest.kt` (+2), `RememberLookupTest.kt` (+2).
**Tests, created:** `TvCatalogRootPlayStateTest.kt`.
**Not touched (read only):** `TvWall.kt`, `TvPage.kt`, `TvPlate.kt`, `TvHeadings.kt`, `TvEntryPlate.kt`,
`TvWatchMarks.kt`, `TvRestoreKeys.kt`, `TvTitleHeader.kt`, `TvLists.kt`, `TvArrivalFocus.kt`,
`TvCatalogScreen.kt`/`TvCatalogBody.kt`/`TvHome.kt`/`TvCoverStory.kt` (parallel agent's), `LibraryResolve.kt`,
`Similar.kt`/`SeriesPageState.kt`/`HomeShelves.kt`/`Franchises.kt`/`BrowseViewModel.kt` (feature/catalog).

## Not fixed — outside reach, flagging rather than guessing

- **N9** (Movies front-page composes ~60 non-lazy plates): lives in `TvDepartmentPages.kt`/
  `TvMoviesDepartmentPage`/`TvMoviesPage` — parallel agent's files. Not touched.
- **Latest's `heldIds` on Home's own rows, and the >6 cut on department rows** (the other half of M2):
  needs a `heldIds`/uncapped param on `TvHomeRow`/`PlateRow` — parallel agent's file (`TvHomeRow.kt`,
  explicitly excluded). Latest itself no longer goes through `TvHomeRow` at all (see M2 above), so
  Latest's own `heldIds` gap is closed; Home's rows and department rows (still `TvHomeRow`-based) are
  not — flagging for whoever owns that file next.

## Verify

`./gradlew :ui-tv:testDebugUnitTest :ui-common:testDebugUnitTest :core:data:testDebugUnitTest
:ui-tv:compileDebugAndroidTestKotlin :ui-tv:lint` — **BUILD SUCCESSFUL**, clean, after the parallel
agent's own build finished (the shared build directory caused a run of transient `EOFException`/
`NoSuchFileException` failures from two concurrent `testDebugUnitTest` invocations against the same
worktree while both agents were active; unrelated to source, gone once serialized). Ran the full
target list to a clean pass twice in a row.

One transient failure during iteration, not mine: `TvSearchResultsStateTest` failed once while
`TvSearchResults.kt`/`TvSearchResultsStateTest.kt` were mid-edit by the parallel agent (git status
showed both modified/untracked at the time); not touched, not reported further since a later full run
was clean.

**Status:** DONE
**Summary:** Fixed M4, M5, M2(Latest)/N10, N2, N3, N4, N5, N6, N8, item 10 (comments), item 11
(200-line splits) within the given file ownership, plus the mid-task Watch-now→player wiring in
`TvLibraryBranches.kt`. Added/updated tests for every behavioral fix, including two that demonstrate
the N8 fix without relying on real coroutine-cancellation timing. All within `ui-tv/**` (owned paths),
`ui-common/RememberLookups.kt`, and `core/data/PortraitRequestLog.kt` — no edits to `feature/*`,
`ui-mobile`, or the parallel agent's files (`TvSearch*.kt`, `TvHome.kt`, `TvCoverStory.kt`,
`TvFeatureStrip.kt`, `TvDepartmentPages.kt`, `TvHomeRow.kt`, `TvCatalogScreen.kt`, `TvCatalogBody.kt`).
**Concerns/Blockers:** N9 and the Home/department half of M2 need `TvHomeRow.kt` changes outside my
ownership — flagged above, not attempted. `RememberLookups.kt` sits at 223 lines (not a `ui-tv` file,
so outside the literal 200-line rule in scope) — flagged rather than force-split.

**Unresolved questions:**
- Should `TvHomeRow`/`PlateRow` gain `heldIds` and drop its `HOME_ROW_LIMIT` cap for department rows
  too (the non-Latest half of M2/N9's neighbourhood), or is Home's own 6-wide rail a deliberate,
  different rule from a department's front page? Whoever owns `TvHomeRow.kt` next should decide.
