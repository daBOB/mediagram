# Phase 2 (data/view-model half) + Phase 3 (navigation) report

Plan: `plans/260926-1330-android-editorial-departments-parity/plan.md`,
`phase-02-kotlin-data-and-rules.md`, `phase-03-navigation.md`
Worktree: `feat/android-editorial-parity`, work context
`/home/andre/Workspace/mediagram-android-parity`

Read first: `fullstack-developer-260926-1610-android-parity-core-read-api-report.md` (the
Rust/UniFFI surface this builds on) and `fullstack-developer-260926-1612-android-parity-pure-rules-report.md`
(the 5 pure rules + `MediaSet`'s 4 new fields, already landed in this worktree before I started).

A concurrent phase-8 session (Settings › Appearance) was writing to this same worktree while
I worked — `core/designsystem`, `feature/setup`, `ui-mobile/settings`, `ui-tv/system` are
theirs, not touched here. See "Concerns" for how that interacts with this phase's own
`ui-mobile`/`ui-tv` carve-out.

## What phase 2's data half builds (`CoreClient` → `DefaultCoreClient` → `CatalogRepository`)

`CoreClient` gained 5 methods, each with a default answering empty/`null` so every fake
implementing it (`core:data`'s own `FakeCore`, and any other) keeps compiling without
knowing they exist:

```kotlin
suspend fun titleCredits(key: String): TitleCreditsRecord = TitleCreditsRecord(cast = emptyList(), crew = emptyList())
suspend fun person(personId: Long): PersonRecord? = null
suspend fun franchises(): List<FranchiseRecord> = emptyList()
suspend fun searchPeople(query: String): List<PeopleHitRecord> = emptyList()
suspend fun fetchPortrait(personId: Long): String? = null
```

`DefaultCoreClient` delegates each straight to the generated `Core`, converting `Long`↔`ULong`
at this one boundary (the established pattern — see `totalSize`/`read`).

`CatalogRepository` gained the domain-mapped equivalents, also interface-defaulted (a second
fake, `feature:player`'s own `FakeCatalogRepository`, needed the same protection):

```kotlin
suspend fun titleCredits(key: String): TitleCredits = TitleCredits.Empty
suspend fun person(personId: Long): Person? = null
suspend fun franchises(): List<FranchiseInfo> = emptyList()
suspend fun searchPeople(query: String): List<PersonHit> = emptyList()
suspend fun fetchPortrait(personId: Long): String? = null
```

`DefaultCatalogRepository` resolves every `portraitKey` to a file path via `core.posterPath`
— the same rule `backdropKey`/`posterKey` already follow — except `fetchPortrait`, whose
own core call already returns a resolved path, not a key.

`toMediaSet` now maps the 4 v9 `SetSummary` fields the earlier session added to `MediaSet`
but this mapping function had not yet been updated for: `collectionId` (`ULong?→Long?`),
`collectionName`, `seriesType`, `showStatus`.

### Model types (`core/model/Credits.kt`)

```kotlin
data class Credit(val personId: Long, val name: String, val role: String?, val portraitPath: String?)
data class TitleCredits(val cast: List<Credit>, val crew: List<Credit>) { companion object { val Empty } }
data class Person(val personId: Long, val name: String, val portraitPath: String?, val titleKeys: List<String>)
data class FranchiseInfo(val id: Long, val name: String, val overview: String?)
data class PersonHit(val personId: Long, val name: String, val portraitPath: String?, val titleKeys: List<String>)
```

Every `portraitPath` is already resolved (a file path or `null`), never a raw key — screens
never call `posterPath` a second time on these.

**Deliberate rename:** `VisiblePeople.kt`'s existing `PersonCandidate`/`VisiblePerson` (landed
by the earlier pure-rules session as speculative "trivially mappable" fields) had a
`portraitKey: String?` field. Now that a real record exists and always carries a resolved
path, I renamed it to `portraitPath` in both types, in this same file I own — for the same
reason every other model here is named by what it holds, not by where it came from. No other
file referenced the old name.

## View-model surface (`feature/catalog`) — what phases 4–6 call

**Credits** — no ViewModel of its own; `ui.catalog.rememberTitleCredits(key, lookup)`
(ui-common, alongside `rememberTitleInfo`) fetches once per key and answers
`TitleCredits.Empty` meanwhile/on failure/for a v8 index. A screen gates its Cast tab on
`credits.cast.isNotEmpty()`.

**Person page** — `catalog.PersonPage(person: Person, films: List<MediaSet>, shows: List<Entry.Collection>)`
and `personPageOf(person: Person?, shelves: List<Shelf>): PersonPage?`. Pass the **already
kids-filtered** `shelves` from `CatalogUiState.Ready` (`CatalogViewModel.state`'s own
projection) — that is what makes this "resolved against the profile-visible library" without
a second filter. `null` both for nobody by that id and for somebody nobody can see; either
way the screen shows the fixed "Nobody by that number is credited on anything in your
library" with no name, per `cast.js#renderPerson`. Fetch the `Person` itself with
`ui.catalog.rememberPerson(personId, lookup)`.

**Franchises** — `catalog.Franchise` (already landed) plus new
`FranchisePage(franchise: Franchise, overview: String?)` and
`franchisePageOf(id: Long, movies: List<MediaSet>, overviews: List<FranchiseInfo>): FranchisePage?`
in `Franchises.kt`. `overviews` comes from `ui.catalog.rememberFranchiseOverviews(lookup)`
(fetched once, session-cached the way the web's own module-level `overviews` is — for as
long as the Composable stays live).

**Search** — `SearchUiState.Ready` gained `people: List<PersonHit> = emptyList()`,
fetched by `SearchViewModel` alongside `hits` in the same round (`repository.searchPeople`).
Grouping/joining is a new pure function, `SearchGroups.kt`, called by the screen the same way
`searchRowsOf` already is:

```kotlin
enum class SearchFilter { ALL, MOVIES, SERIES, TUTORIALS, PEOPLE, COLLECTIONS }
data class SearchDestination(val filter: SearchFilter, val name: String, val itemCount: Int, val art: String?, val href: String)
data class SearchGroups(
    val films: List<SearchRow>, val matchedShows: List<Entry.Collection>,
    val episodes: List<SearchRow>, val lessons: List<SearchRow>,
    val people: List<VisiblePerson>, val collections: List<SearchDestination>,
    val filters: List<Pair<SearchFilter, Int>>,
)
fun searchGroupsOf(query: String, catalogState: CatalogUiState, hits: List<SearchHit>,
    peopleHits: List<PersonHit>, franchises: List<Franchise>, lists: List<ListOfSets>): SearchGroups
```

Episodes roll up into `matchedShows` by `MediaSet.show` name (never listed twice, matching
web); `filters` names only a non-empty kind, `ALL` prepended only once ≥2 other kinds have
results (web's `kinds.length > 2` gate — nothing to filter below that). **Current selection**
(which chip is active) is left to the screen, a `remember { mutableStateOf(SearchFilter.ALL) }`
— that is UI state, not a rule.

**Departments** (`Departments.kt`) — pure, tested, no `UiState`/ViewModel wrapper needed
(nothing here is async):

```kotlin
data class MoviesDepartment(val filmCount: Int, val hours: Int, val lead: MediaSet?,
    val featured: List<MediaSet>, val genres: List<GenreIndexEntry>,
    val acclaimed: List<MediaSet>, val recentlyAdded: List<MediaSet>)
fun moviesDepartmentOf(films: List<MediaSet>, watched: (String) -> Boolean): MoviesDepartment?

data class ShowsDepartment(val showCount: Int, val itemCount: Int, val lead: Entry.Collection?,
    val underway: Underway, val popular: List<Entry.Collection>,
    val newEpisodes: List<Entry.Collection>, val all: List<Entry.Collection>)
fun showsDepartmentOf(kind: Kind, shows: List<Entry.Collection>, byId: Map<String, MediaSet>, watch: WatchSnapshot): ShowsDepartment?
```

`films`/`shows` are the caller's own Movies/Series/Tutorials shelf entries, unwrapped —
Documentaries has no Android department (an already-recorded, prior difference: no
`Kind.DOCUMENTARY`). `popular`/`newEpisodes` are empty below 12 shows, the web's own
"nothing to select from" gate; `newEpisodes` reuses `HomeShelves.kt`'s own `newestFirst`
(now `internal`, was `private` — the one visibility change to that file) rather than
recomputing arrival order a second way.

**Latest / Genres index** — deliberately **no new function**. `homeRowsOf(shelves, watch,
heldIds, limit = 48)` already builds exactly this (`HomeShelves.kt`, pre-existing); a Latest
screen calls it with a 48 cap and keeps only the three rows titled `"Latest films"`,
`"Latest series"`, `"Latest courses"` — reusing what the start page already computes rather
than a second implementation of "newest by kind". The Genres index is
`genreIndex(allTitles(shelves))`: `genreIndex` already existed, `allTitles(shelves): List<MediaSet>`
is the one new function (`GenreIndex.kt`) — films plus each show's first episode, mirroring
web's `titlesOf`, never tutorials (no provider genre).

**Series page state** (`SeriesPageState.kt`) —

```kotlin
fun seriesResumeFor(collection: Entry.Collection, watch: WatchSnapshot): SeriesResumePick?
fun similarShows(current: Entry.Collection, shows: List<Entry.Collection>, watched: (String) -> Boolean): List<Entry.Collection>
```

`seriesResumeFor` wires the already-ported `seriesResume` to real `Progress`/`Watched` rows
via `ResumePoint.resumeAt`, matching `series-page.js`'s own wiring exactly (`recent` is every
progress row by recency, unfiltered — the per-item `resumeOf` null-check does the filtering,
same as web's `inProgress()` + `seriesResume`'s own loop). `similarShows` stands each show in
for `similarTo`'s `MediaSet` shape (its first episode, `setId` swapped for the show's own
`Entry.Collection.key` so it dedupes against other shows rather than a coincidentally-shared
episode id), "seen" meaning every episode watched. **Film similar** needs no wrapper — a
screen calls `similarTo(film, otherFilms, watched)` directly.

**Lazy portraits** — `data.PortraitRequestLog` (new, `@Singleton @Inject constructor()`, no
`DataModule` entry needed — Hilt binds a bare injectable constructor on its own), one
`ConcurrentHashMap`-backed `shouldRequest(personId): Boolean`, true exactly once per id per
process. `ui.catalog.rememberPortrait(personId, known, shouldRequest, fetch)` is the
Composable a Cast row or person page calls: fetches only when `known == null` and
`shouldRequest` still says yes.

## Phase 3 — navigation

**`LibraryPositions` (`ui-common`)** — `FrameKind` gained `PERSON, FRANCHISE, GENRES, LATEST,
MOVIES_PAGE`, additive at the end of the enum. Encoding keys every frame by `FrameKind.name`
(never ordinal), so this changes nothing about how an already-saved stack decodes — verified
by a new test that decodes a hand-written pre-phase-3 string and asserts it restores exactly
as before. New accessors/pushes: `personId`, `franchiseId`, `openPerson(id)`,
`openFranchise(id)`, `openGenresIndex()` (the index — `openGenre(name)` already existed, for
one shelf), `openLatest()`, `openMoviesPage()`. `MOVIES_PAGE` carries no page number — Android
renders "All N films" as one lazy-scrolling wall, not web's numbered
`#/movies/page/N`, so one frame is the whole of it.

**`Destination` (`feature/catalog`)** — gained `Person(name)`, `Franchise(name)`,
`MoviesPage`, `Genres`, `Latest`; `barTitleFor`/`backLabelFor` cover all five (`"Movies"` for
`MoviesPage`, the person's/franchise's own name for those two, `"Genres"`/`"Latest"`
otherwise) — every branch is `"Back"` except the catalog root, as already true for every
other destination.

**`CatalogTabs.kt` — the masthead/utility split.** Read today's wiring first
(`ShelfTabs.kt`, `CatalogScreen.kt`, `TvCatalogScreen.kt`, `TvMasthead.kt`, `OverflowMenu.kt`):
both phone and TV read `catalogTabsOf(shelves)` into one flat tab row of 7 —
Home · Movies · Series · Tutorials · Continue · Watchlist · Collections — and separately
already carry a 5-item overflow menu (System, Settings, Update library, TMDB key, Start
over) opened from a fixed "⋯" icon. **Decision:** move Collections into the tab row (it is a
department in web 0.62.1's own `nav.departments`, not a rail utility) and the two currently-tabbed
utilities (Continue, Watchlist/My List) out to join Latest and Genres in the overflow, next to
the Settings entry already there — five utilities, each reachable exactly once, matching
web's `rail-nav` split from `nav.departments` as closely as Android's own chrome (a tab row
plus one overflow menu) allows.

I did **not** rewire `catalogTabsOf`/`CatalogScreen.kt`/`TvCatalogScreen.kt` themselves —
that would touch `ui-mobile`/`ui-tv` beyond the FrameKind carve-out and this phase ships no
screens. Instead, `catalogTabsOf`/`CatalogTabs`/`KeptKind` stay byte-for-byte as they are
(both surfaces keep compiling and behaving exactly as today), and I added, additively:

```kotlin
data class MastheadSplit(val departments: List<String>, val utilities: List<UtilityDestination>)
enum class UtilityDestination(val label: String) { MY_LIST, CONTINUE_WATCHING, LATEST, GENRES, SETTINGS }
fun mastheadSplitOf(shelves: List<Shelf>): MastheadSplit
```

`departments` = `Home, <shelf titles>, "Collections"`; `utilities` = all five, in web's own
rail order. This is what the phase-4/5/6 screen work rebuilds `ShelfTabs`/`CatalogScreen`
against — the design decision is made and tested here; the tab-row/overflow-menu Composables
themselves are screens, out of this phase's scope.

**`when (at.top)` in `ui-mobile`'s `LibraryFlowBranches.kt` and `ui-tv`'s `TvLibrary.kt`** —
both were exhaustive over the old 8-value `FrameKind` with no `else`; adding 5 values broke
that compile (confirmed: this is exactly what the concurrent phase-8 report flagged as
blocking it). Per this phase's own carve-out, I added one branch each:

```kotlin
FrameKind.PERSON, FrameKind.FRANCHISE, FrameKind.GENRES, FrameKind.LATEST, FrameKind.MOVIES_PAGE -> at.pop()   // ui-mobile
FrameKind.PERSON, FrameKind.FRANCHISE, FrameKind.GENRES, FrameKind.LATEST, FrameKind.MOVIES_PAGE -> leave()    // ui-tv
```

Pops/leaves rather than rendering anything — a screen agent replaces this branch with the
real ones. Nothing else in either file changed.

## Files

### Modified
- `android/core/data/src/main/kotlin/CoreClient.kt`, `DefaultCoreClient.kt`,
  `CatalogRepository.kt` — the 5-method surface + `toMediaSet`'s 4 new fields.
- `android/core/data/src/test/kotlin/FakeCore.kt`, `CatalogRepositoryTest.kt` — fake support
  + tests for the new mapping and repository methods.
- `android/feature/catalog/src/main/kotlin/CatalogTabs.kt` (additive `MastheadSplit`),
  `Destination.kt` (5 new cases), `HomeShelves.kt` (`newestFirst` → `internal`),
  `SearchUiState.kt` (`Ready.people`), `SearchViewModel.kt` (fetches people too),
  `Franchises.kt` (`franchisePageOf`), `GenreIndex.kt` (`allTitles`), `VisiblePeople.kt`
  (`portraitPath` rename, `titlesByKey`).
- `android/feature/catalog/src/test/kotlin/DestinationTest.kt`, `FakeCatalogRepository.kt`,
  `SearchViewModelTest.kt`, `FranchisesTest.kt`, `GenreIndexTest.kt`, `VisiblePeopleTest.kt`.
- `android/ui-common/src/main/kotlin/ui/LibraryPositions.kt` (5 frames),
  `ui/catalog/RememberLookups.kt` (4 new hooks).
- `android/ui-common/src/test/kotlin/ui/LibraryPositionsTest.kt`,
  `LibraryPositionsEncodingTest.kt` (new-frame + backward-compat coverage).
- `android/ui-mobile/src/main/kotlin/ui/LibraryFlowBranches.kt`,
  `android/ui-tv/src/main/kotlin/ui/tv/TvLibrary.kt` — the carve-out branch only.
- `android/ui-tv/src/test/kotlin/ui/tv/TvAppFixture.kt` — one added stub (see "Follow-up"
  below), lead-authorized.
- `plans/260926-1330-android-editorial-departments-parity/plan.md`, `phase-02-*.md`,
  `phase-03-*.md` — status → done.

### Created
- `android/core/model/src/main/kotlin/Credits.kt` (`Credit`, `TitleCredits`, `Person`,
  `FranchiseInfo`, `PersonHit`).
- `android/core/data/src/main/kotlin/PortraitRequestLog.kt` (+ test).
- `android/feature/catalog/src/main/kotlin/Departments.kt`, `PersonPage.kt`,
  `SearchGroups.kt`, `SeriesPageState.kt` (+ one test file each:
  `DepartmentsTest.kt`, `PersonPageTest.kt`, `SearchGroupsTest.kt`, `SeriesPageStateTest.kt`,
  `CatalogTabsTest.kt`).

## Commands run and results

- `./gradlew :core:model:test :core:data:testDebugUnitTest :feature:catalog:testDebugUnitTest
  :ui-common:testDebugUnitTest :feature:player:testDebugUnitTest` — **all green**, every new
  test included.
- `./gradlew :ui-mobile:compileDebugKotlin :ui-tv:compileDebugKotlin :app:compileDebugKotlin`
  — **clean** (this is what was broken before the carve-out branches; confirmed fixed).
- `./gradlew testDebugUnitTest lint :ui-tv:compileDebugAndroidTestKotlin` (whole tree, after
  the follow-up fix below and phase 8's own fixes landing) — **BUILD SUCCESSFUL**, **1389
  tests, 0 failed** (counted from every module's `TEST-*.xml`; `ui-mobile` 73/0,
  `ui-tv` 281/0, `TvSearchAndGenreTest` itself 10/0). `lint` clean across the whole tree.

## Follow-up: `TvSearchAndGenreTest` broken by `SearchViewModel`'s new people call

The lead reported 6 `ui.tv.TvSearchAndGenreTest` failures after phase 8's own fixes landed,
traced to this phase's `SearchViewModel`/`SearchUiState` change. Root cause: `TvAppFixture`
(`ui-tv/src/test/kotlin/ui/tv/TvAppFixture.kt`) wires the **real** `SearchViewModel` against
a strict `mockk<CatalogRepository>()` with each method it calls stubbed by hand
(`coEvery { repository.search(any()) } ...`, `.titleInfo`, `.posterPath`, etc.) — `search()`
now also calls `repository.searchPeople(text)` in the same round (this phase's own change,
to fetch people alongside hits), and MockK throws "no answer found" for that one unstubbed
call. `SearchViewModel.search()`'s catch-all turns that into `SearchUiState.Failed`, so every
test in the file that types into the search field failed the same way (a broken `Ready`
state, not a rendering bug) — nothing about the TV search screen itself reads the new
`people` field (confirmed: `TvSearchScreen`/`TvSearchResults` only ever read `state.hits`),
so there was no "TV read of a changed field" to adapt — the fixture's mock was simply
missing a stub for a call that did not exist before.

**Fix:** one line in `TvAppFixture.kt`, the test-fixture file itself (this phase's minimal,
lead-authorized fix — see the message that asked for this):

```kotlin
coEvery { repository.searchPeople(any()) } returns emptyList()
```

Matches today's TV behaviour exactly (search never showed people before, still shows none —
phase 6 is where the TV search screen gets redesigned to use them). No assertion in
`TvSearchAndGenreTest` changed; its intent was never obsolete, the fixture was just missing a
stub for a genuinely new call. Checked for the same exposure elsewhere: every other
`mockk<CatalogRepository>()` in the tree (`ui-mobile/LibraryFlowFixture.kt` and 4 player
fixtures) either never stubs `search` at all (no test there exercises `SearchViewModel`
through it) or isn't wired to a real `SearchViewModel` — confirmed none of them need the same
stub, and the lead's own report of 0 `ui-mobile` failures agrees.

## Concerns

- `catalogTabsOf`/`ShelfTabs`/`CatalogScreen`/`TvCatalogScreen`/`TvMasthead` still show the
  old 7-tab row (Collections not yet moved, Continue/Watchlist not yet moved out) — by
  design, since rewiring them is `ui-mobile`/`ui-tv` screen work outside this phase. Phase
  4/5/6 should read `mastheadSplitOf` and replace those Composables' tab source.
- `MediaSet.kt` was **not** touched by me — it already carried `collectionId`/`collectionName`/
  `seriesType`/`showStatus` from the earlier pure-rules session; this phase only wired
  `toMediaSet` to actually populate them from `SetSummary`, which had been left unwired
  (confirmed by reading that session's own report: it explicitly scoped `CoreClient`/
  `DefaultCoreClient` changes out as "phase 2's remaining view-model half").
- The phase-8 `setup.AppearanceViewModel`/`TvAppearanceBlockTest` failures noted in this
  report's first pass are gone in the final run above — that session fixed its own issue;
  pruned here rather than left as a stale concern.

**Status:** DONE
**Summary:** Phase 2's data layer (`CoreClient`/`DefaultCoreClient`/`CatalogRepository` +
5 core/model types) and its full view-model half (credits, person, franchises, search
grouping, departments, Latest/Genres, series page state, lazy portraits) are built and
tested; Phase 3's navigation (5 new `LibraryPositions` frames, `Destination` titles, the
masthead/utility split design) is built and tested. `ui-mobile`/`ui-tv` compile and every
test in the tree passes: `./gradlew testDebugUnitTest lint :ui-tv:compileDebugAndroidTestKotlin`
is **1389 tests, 0 failed**, lint clean.
**Concerns/Blockers:** none.
