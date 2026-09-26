# Phase 4 — phone title pages (film spread + tabs, series page) report

Plan: `plans/260926-1330-android-editorial-departments-parity/plan.md`, `phase-04-phone-title-pages.md`
Worktree: `feat/android-editorial-parity`, work context `/home/andre/Workspace/mediagram-android-parity`
Read first: `fullstack-developer-260926-1630-android-parity-data-and-navigation-report.md` (VM/UiState/hook surface), `fullstack-developer-260926-1612-android-parity-pure-rules-report.md`.

This report covers two rounds: the screens themselves, then (once the browse agent's own
routing work landed) wiring Cast/My List/the franchise link live, fixing the 5 `LibraryFlowTest`
cases that asserted the old season wall, and making a chosen season survive leaving the page
and coming back. Moved here from an earlier, mistaken write to the main checkout.

## Concurrency incident (round 1 — resolved)

Mid-session, the phone browse agent ran `git stash push/pop` over the shared worktree to
isolate its own compile checks, reverting several of my files to older snapshots while I was
mid-edit. The lead confirmed the cause; the browse agent has stopped and will not run
stash/checkout/restore/reset again. Every file was re-verified against its intended content
before continuing. No repeat since.

## What phase 4 builds

**Film page** (`TitleDetailScreen.kt`) — `TitleSpread` (backdrop, title, facts line, overview
clamped to 4 lines, tagline as a pull-quote over the artwork) → `TitlePills` (Play/Resume,
My List, ⋯ with editor's choice) → `TitleTabs`: Overview (poster + fact sheet: Released,
Runtime, Rated, Score, Genres, "Part of" a franchise only when the library holds ≥2 of its
films) · Cast (only once `titleCredits` names a cast) · Similar (`similarTo`, poster row) ·
Details (quality/HDR, codecs, subtitle languages, container, size, bitrate, parts).

**Series page** (`CollectionScreen.kt`'s new `SeriesPage`, `SeasonWall.kt`'s new
`seriesEpisodes`) — same spread, a `SeriesResume`-labelled pill that plays the picked episode
directly, tabs Episodes (season picker + readable episode list, replacing the old
season-poster wall) · About (aired years, held counts, network/status, genres, picture,
subtitle languages — `SeriesSummary.kt`, new) · Cast · Similar. A course (`CollectionKind.COURSE`)
is untouched — same flat/nested list it has always been; only the `SHOW` branch is new.

**Tab persistence** — tabs are chosen by label, not position (`rememberChosenTab` in
`TitleTabs.kt`), so a page rebuilt by a watch-state update, or a Cast tab arriving after
credits load and shifting positions, never throws the viewer back to the first tab. Verified
by `TitleTabsTest` and `SeriesPageTest.tabSelectionSurvivesAWatchStateUpdate`.

**Deliberate difference**: the spread lays the words *below* the backdrop rather than beside
it. The web sets them in two columns because a magazine page has the width; a phone does not,
so the backdrop sits on top (fading into the page along its foot) and the title/facts/overview
sit below it. Documented in `TitleSpread.kt`'s own doc comment, not silent.

## Round 2 — live wiring, the 5 tests, and season persistence

### Cast, My List, the franchise link: now live

`LibraryFlowBranches.kt`'s `TITLE` and `COLLECTION` branches pass every new parameter through
to `TitleDetailScreen`/`CollectionScreen`, both now resolving a `BrowseViewModel` (the browse
agent's own — `hiltViewModel()`, already built for the person/franchise pages, reused rather
than duplicated) for `fetchPortrait`/`shouldRequestPortrait`, and `CatalogViewModel` for
`titleCredits`/`setWatchlisted` (two small additions, matching `titleInfo`/`posterPath`/
`setEditorsChoice`'s own one-line-delegate shape exactly):

```kotlin
fun setWatchlisted(setId: String, listed: Boolean) { viewModelScope.launch { watchState.setWatchlisted(setId, listed) } }
suspend fun titleCredits(key: String): TitleCredits = repository.titleCredits(key)
```

A show's "My List"/editor's-choice pills key off its first episode's id, the same identity
`CollectionScreen`'s own pills already used — `firstItemOf(collection.divisions)?.setId`, read
once in the branch rather than inside the screen, so a course (which has none of these
controls) never has to carry a null case through them.

Dropped from the original plan for `CatalogViewModel`: a `franchiseOverviews` delegate. Re-reading
`film-page.js#overview`, the franchise link only ever needs to know the franchise *exists*
(`franchisesIn`, pure, already wired); the TMDB overview text belongs to the franchise's own
page, not this one — nothing to fetch here.

### Season now lives in the navigation state, not component state

Per the plan's own words ("stays in the navigation state so back/rotation keep it"), the
chosen season moved out of `SeriesPage`'s local `rememberSaveable` and into
`ui.LibraryPositions` — the collection frame's own payload now carries it alongside the
collection's key, the same way a player frame's payload already carries its run:

```kotlin
// LibraryPositions.kt
val collectionSeason: String?   // null until chosen; the page picks its own first default then
fun setCollectionSeason(name: String)   // rewrites the top collection frame's payload in place
```

`CollectionScreen`/`SeriesPage` gained `season: String?`/`onSelectSeason: (String) -> Unit`
parameters (both default to inert values, so this is additive); `LibraryFlowBranches.kt` wires
them to `at.collectionSeason`/`at::setCollectionSeason`. A `null` season (nobody has chosen
one yet, or the whole stack was just cleared) still falls back to the page's own default — a
resume point's season, else the first — exactly as before; the difference is only that a
season chosen once now survives a title opened from Similar, Cast or an episode and left
again, and a killed-and-recreated process, the same way the collection's own key already did.
Covered by two new `LibraryPositionsTest` cases and `SeriesPageTest`'s own `renderCollection`
helper (rewired to pass season/onSelectSeason like a real caller, not keep its own state).

### The 5 `LibraryFlowTest` cases: fixed, asserting the new UI

`collection()`/`season()`/`title()` (the shared helpers all five call) now open the show,
assert its Episodes tab shows the first episode directly (no more clickable "Season 1" card),
pick Season 2 from the picker (`hasClickAction()` is what tells the picker's own button apart
from the episode list's plain "Season 1" heading, which names the same season but opens
nothing), and open an episode from there. Each test's own further assertions were adjusted for
one fewer stack level — there is no more separate `SEASON` frame in this flow, since choosing a
season is in-place now rather than a push — while keeping each test's original intent (a title
opened from a show and left again, a killed process restoring it, a menu overlay not
disturbing it, a stale season not leaking into a fresh visit after an update).

### Fixture gaps this surfaced, fixed in `LibraryFlowFixture.kt`

Fixing the season assertions let these tests reach code paths they never had before,
surfacing three fixture gaps — none touching catalog/title code, all one missing stub or one
missing registration in the same style already used throughout this file:

- `BrowseViewModel` was never registered in the fixture's `ViewModelProvider.Factory` map —
  needed now that `TITLE`/`COLLECTION` both resolve one. Added, backed by the same mocked
  `repository` plus a real `PortraitRequestLog`.
- `repository.titleCredits`/`repository.fetchPortrait` were unstubbed on the fixture's strict
  `mockk<CatalogRepository>()` — every title/series page now asks every time it renders,
  whether or not a test ever opens the Cast tab. Stubbed to the same "nothing recorded" answer
  this fixture's own sets already imply (`TitleCredits.Empty`/`null`).
- Reaching the Settings menu (already part of these tests' own original flow) uncovered two
  further pre-existing, unrelated gaps once nothing hid them any longer: `CacheBudgetViewModel.volumes`/
  `.chosenVolumeId` were unstubbed on a `relaxed` mock, and a relaxed mock's own generic answer
  for a `StateFlow<List<...>>` is not actually a `List` — a `ClassCastException` the instant
  `CacheVolumeBlock` collected it. And `LanCacheViewModel` was never registered at all, so
  `hiltViewModel()` fell through to the platform's own reflection-based factory and failed
  outright (that view model's constructor takes six real dependencies). Both fixed the same
  way as `BrowseViewModel`/`CacheBudgetViewModel` already were: a relaxed mock, `state`/
  `volumes`/`chosenVolumeId` stubbed to the "nothing yet" values those screens already render
  as nothing (`if (state == null) return`, `if (volumes.isEmpty()) return`) — neither screen's
  own code changed, and neither is catalog/title work; noted here rather than filed as a
  separate report since fixing them was a two-line, same-pattern addition to a file I was
  already in, not a design decision about a feature I don't own.

## Why a `LazyColumn` (real bug caught before shipping)

The series page was first written as a scrolling `Column`, matching the film page. Its
Episodes tab is a second, unboundedly-long list (a show's episode count), and nesting a
`LazyColumn` inside a `Modifier.verticalScroll` `Column` is the classic Compose crash
("measured with an infinity maximum height") or, at best, a badly clipped list. Fixed by
making the whole series page one `LazyColumn`, with the tab row as one `item` and the Episodes
tab's rows joining it directly (`seriesEpisodes`, a `LazyListScope` extension reusing
`CollectionRows.kt`'s own `items(...)`) instead of a nested scrollable. One visible cost: the
spread's own backdrop is inset by the page's own margin rather than filling the edge — noted
in-code, a minor, deliberate difference from the film page's edge-to-edge hero.

## Test fixture pitfall worth flagging (Robolectric's small default window)

Robolectric's default test window is small (320×470px in this project's config). A
`LazyColumn` that tall genuinely does not compose rows past its prefetch window — not a
display/scroll problem `performScrollTo()` can fix, since there is nothing yet to scroll to
(confirmed by dumping the semantics tree mid-failure: composition simply stopped at an empty,
barely-clipped node). `SeriesPageTest` and `LibraryFlowTest` both carry
`@Config(qualifiers = "w400dp-h2400dp")` for this reason. `FilmPageTest` needed no such
qualifier (a plain `Column.verticalScroll` composes everything regardless of scroll position)
but did need `.performScrollTo()` before assertions on content below the spread, and one
assertion (a cast member's name, nested in the Cast row's *horizontal* scroll inside the
page's *vertical* one) checks existence rather than on-screen display, since `performScrollTo()`
only ever drives the nearest scrollable.

## Deviations from the plan / web, with reasons

- **No "Audio languages" row** on Details/About. `MediaSet` carries `subtitleLanguages` but no
  audio-language field at all (confirmed: neither `CatalogRepository.toMediaSet` nor the v9
  `SetSummary` mapping carries one). A forced gap, not a choice — flagging for whoever owns
  the core schema next, not fixing it here.
- **No "8 of 16 episodes" / provider air-date range** on the series About tab. `TitleInfo`
  (the uniffi record) carries `overview/tagline/genres/rating/network/status` only — no
  `firstAir`/`lastAir`/`totalEpisodes`/`totalSeasons` reach Android yet, unlike the web's own
  `ShowMeta`. `SeriesSummary.kt`'s `scaleLine`/`yearLine` can only ever describe what this
  library holds, never a total the provider named. Documented in the file's own doc comment.
- **Franchise TMDB overview text is not fetched on the film page** — see "Round 2" above.

## Files

### Created
- `feature/catalog/src/main/kotlin/TitlePageCandidates.kt`, `SeriesSummary.kt` (+ tests).
- `ui-mobile/src/main/kotlin/ui/catalog/TitleSpread.kt`, `TitlePills.kt`, `FactSheet.kt`,
  `TitleTabs.kt`, `CastPanel.kt`, `PosterRow.kt`.
- `ui-mobile/src/test/kotlin/ui/catalog/title/TitleTabsTest.kt`, `FilmPageTest.kt`,
  `SeriesPageTest.kt`.

### Modified
- `ui-mobile/src/main/kotlin/ui/catalog/TitleDetailScreen.kt`, `CollectionScreen.kt`,
  `SeasonWall.kt` — the screens themselves (round 1) plus `season`/`onSelectSeason` becoming
  caller-supplied state (round 2).
- `ui-mobile/src/test/kotlin/ui/catalog/OptionalMetadataTest.kt` — the two `SeasonWall`
  poster-artwork tests replaced with the equivalent for the new `titleCredits` lookup.
- `feature/catalog/src/main/kotlin/CatalogViewModel.kt` — `titleCredits`, `setWatchlisted`.
- `ui-common/src/main/kotlin/ui/LibraryPositions.kt` (+ its two test files) — `collectionSeason`,
  `setCollectionSeason`, the `SEASON_SEP`-joined payload.
- `ui-mobile/src/main/kotlin/ui/LibraryFlowBranches.kt` — `TITLE`/`COLLECTION` branches wired
  live; `shelvesOrEmpty()` helper added alongside the existing `heldIdsOrEmpty()`.
- `ui-mobile/src/test/kotlin/ui/LibraryFlowFixture.kt`, `LibraryFlowTest.kt` — fixture gaps
  fixed, the 5 cases rewritten for the new UI (see "Round 2" above).

## Tests — final totals

`cd android && ./gradlew testDebugUnitTest lint :ui-tv:compileDebugAndroidTestKotlin` (whole
tree, forced with `--rerun-tasks` for the test task to confirm a real run, not a cached one):
**BUILD SUCCESSFUL**, every module's `testDebugUnitTest` executed and passed —
`core:designsystem`, `core:data`, `core:playback`, `core:model`, `feature:setup`,
`feature:system`, `feature:player`, `feature:catalog`, `ui-common`, `ui-mobile`, `ui-tv`,
`app` — zero failures anywhere in the tree. `lint` clean across every module (only pre-existing
deprecation warnings — `ScrollableTabRow`, `WindowWidthSizeClass` — no errors).
`ui-tv:compileDebugAndroidTestKotlin` — up to date, compiles clean (I did not touch `ui-tv`;
the TV agent's own concurrent work there was unaffected by anything here).

Per-suite counts I can confirm precisely (Gradle's console only prints per-test totals on a
run with failures, and the build/ reports themselves are outside my read access in this
session): `ui-mobile:testDebugUnitTest` and `ui-common:testDebugUnitTest` both ran clean on a
forced rerun; `LibraryFlowTest` (6 tests) and `LibraryPositionsTest` (now 12 tests, +2 for the
season round-trip) both individually confirmed 0 failures before the whole-tree run.

## Accessibility

Tab row: Material3 `Tab`/`ScrollableTabRow` (native tab semantics, `role=Tab`, arrow-key
traversal built in) with an explicit `contentDescription` per tab. Pills: `Modifier.heightIn(min = 48.dp)`
on every pill/menu trigger, plus a content description on the My List toggle and the ⋯ menu
(state-aware: "Add to My List"/"Remove from My List"). Dark and light both use
`MaterialTheme.colorScheme` throughout, no hardcoded colors.

**Status:** DONE
**Summary:** Film spread+tabs and series page (spread+tabs, season picker replacing the poster
wall) built; Cast/My List/franchise link now live end to end; the 5 `LibraryFlowTest` cases
rewritten for the new UI; a chosen season now survives leaving the page and coming back via
`LibraryPositions` rather than component state, matching the plan's own wording. Whole-tree
`testDebugUnitTest lint :ui-tv:compileDebugAndroidTestKotlin` is green.
**Concerns/Blockers:** None outstanding. If the browse agent's own routing later needs a
different line in `TITLE`/`COLLECTION` than what is wired now, it should come through the lead
as agreed.
