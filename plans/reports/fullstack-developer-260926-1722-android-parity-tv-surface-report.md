# Phase 6 — Television surface: implementation report

Plan: `plans/260926-1330-android-editorial-departments-parity/plan.md`, `phase-06-tv-surface.md`
Work context: `/home/andre/Workspace/mediagram-android-parity` (worktree `feat/android-editorial-parity`)
File ownership respected: every change is under `android/ui-tv/**` (main + test). No edits to
`feature/catalog`, `core/*`, `ui-common`, or `ui-mobile`. Two new pure lookups I needed did not
exist as public API in `feature/catalog` — see "Files owned elsewhere, not touched" below for how
each was avoided without editing that module.

**Revision note:** after the first pass (below, largely unchanged), the lead asked for three
follow-ups — grouped search, series-page tabs, and a department-gating fix — closed in a second
pass. See "Follow-up: three gaps closed" at the end for what changed; the body above it is the
original report, corrected only where the follow-up made a sentence in it wrong (grouped search is
no longer a documented gap; the series page now has tabs; the >12 gate is narrower than first built).

## What's built

**Masthead split** — `TvCatalogScreen` now draws `catalog.mastheadSplitOf(shelves)`'s departments
(Home, shelves, Collections) as the visible tab row; `catalogTabsOf`'s original 8-wide index space
still backs `chosen` internally (zero change to `TvKeptTab`/`TvKeptWall`/dispatch), so Continue and
Watchlist are reached via two new sentinel restore keys (`TvContinueEntryKey`/`TvWatchlistEntryKey`
in `TvLibrary.kt`) the overflow menu's two new rows set, rather than by a masthead tab. A kept wall
that empties while a viewer has no masthead tab to fall back to (Continue/Watchlist) now falls
back to `mastheadFocus` (the row itself) instead of the removed tab's own `selectedTab`.

**Home magazine layout** (the plan's own decision item) — `TvHome` gained an optional `magazine:
MagazineHome?` param (`null` = old behaviour, so every pre-existing caller and test is untouched):
cover story (`TvCoverStory.kt`, D-pad rotation that pauses when Watch now/Details are focused,
D-pad's equivalent of the web's hover-pauses rule) and three feature cards (`TvFeatureStrip.kt`,
reusing `TvPlate`), then the magazine's own "Recently added" row joins the same row list `homeRowsOf`'s
plain rows already do (so restore-key/arrival-focus works on it for free); the caller drops the plain
grid's "Latest films" row once magazine is present, the same de-dup the phone's `CatalogScreen`
already does.

**Title page tabs** — `TvTitlePage` gained Overview/Cast/Similar/Details tabs (`TvSectionTabs.kt`,
pressed not focused, same reason the masthead's own tabs are). Cast and Similar tabs are gated on
having something to show. Details carries the technical/rating/network/status facts and the
editor's-choice toggle — ported to TV for the first time (phone-only before this phase), `null`
`onToggleEditorsChoice` hides it on a kids profile exactly as the phone gates it.

**Series/show page** — `TvCollection` gained Episodes/About/Cast/Similar tabs (`TvSectionTabs`, the
same pattern as the film page's own), with the `SeriesResumePick` pill in the Episodes tab's own
header, above the season wall or the rows — mirroring the film page's own Overview tab exactly:
neither repeats the header on the tabs beside it. Tab selection is `rememberSaveable`d against the
collection's own key, not against `credits`/`similar` arriving a moment after the page does, so a
body that refetches does not reset which tab is showing. *(Originally shipped as Cast/Similar/resume
inline in the header instead of tabs — see "Follow-up" at the end for why that changed.)*

**Departments** — `TvDepartmentPages.kt`: `TvMoviesDepartmentPage` (hero, Featured, Genres,
Acclaimed, Recently added, "All N films" → `TvMoviesPage`, the phase-3 `MOVIES_PAGE` frame's own
full wall) and `TvShowsDepartmentPage` (hero, Continue, Popular, New episodes, then every show as
one wall) for Series/Tutorials — matching the web's `department-pages.js`: the department page
itself always shows for a non-empty shelf; only Popular/New episodes gate themselves past a dozen
shows (`ShowsDepartment`'s own rule, unchanged). *(Phase 6's first pass added a >12 gate on the whole
front page, which was wrong — see "Follow-up".)* Both pages now carry their own arrival focus (first
plate of the first non-empty row, or `restoreKey`'s own plate) and the offline badge, neither of
which the department plates drew at all in the first pass — the small-fixture tests that exercise
Movies/Tutorials never reached real department content until the gate came off, so this was latent
until now.

**Collections, franchise, person, Genres, Latest** — `TvCollectionsPage.kt` (franchises grid +
`TvLists`, not nested inside one scroll — `TvLists` is its own `LazyColumn` and Compose refuses two
nested vertically-scrollable layouts), `TvFranchisePage.kt`, `TvPersonPage.kt` (nobody state: the
fixed sentence, never a name), `TvGenresIndex.kt`, `TvLatestPage.kt`. All five new `LibraryPositions`
frames from phase 3 (`PERSON`, `FRANCHISE`, `GENRES`, `LATEST`, `MOVIES_PAGE`) are now wired in
`TvLibrary.kt`'s `when (top)`, replacing the phase-3 carve-out that just `leave()`d.

**Lazy portraits** — `TvCastRow.kt`/`TvPersonPage`/search's own people rows use
`ui.catalog.rememberPortrait` + `data.PortraitRequestLog` exactly as phase 1/2 built them: fetched
once per person per session, only when the credit/person/hit carries no resolved path already.

**Grouped search** — `TvSearchGroups.kt` (new), `TvSearch.kt`/`TvSearchResults.kt` (rewritten):
`catalog.searchGroupsOf` drives the whole screen now, in the web's own order — Films, Series
(matched shows rolled up, never listed per-episode, then the matching episodes themselves),
Tutorials, People (round portraits, lazy, only those `visiblePeople` says this profile can see),
Collections (a franchise or the viewer's own list, matched by name) — each its own heading, with a
filter-chip row above them once there is more than one kind (`SearchGroups.filters`'s own gate,
unchanged). A person's row opens their own page ([PERSON] frame); a collection destination opens a
franchise ([FRANCHISE]) or a hand-built list ([LIST]), told apart by `SearchDestination.href`'s
`tmdb-` prefix. The flat row-ask/restore-key mechanism (`RowAsk`, a scroll-then-focus request) now
keys by [SearchEntry] rather than by a set id alone, so a franchise or a person restores its focus
after Back the same way a title row always did.

## Files owned elsewhere, not touched

- `TvCatalogExtras.kt` (new, `ui-tv/catalog`) — a small `@HiltViewModel` wrapping
  `data.CatalogRepository`'s `titleCredits`/`person`/`franchises`/`fetchPortrait` and
  `data.PortraitRequestLog.shouldRequest`. `catalog.CatalogViewModel` (feature:catalog, not mine)
  does not pass these through yet; adding them there is that module's own file to extend, and every
  screen in this phase needs them, so this lives beside the screens that call it instead.
- `DepartmentOrShelfWall` in `TvDepartmentPages.kt` rebuilds `feature:catalog`'s own `indexById` map
  locally (`byIdOf`), scoped to just the shows on one shelf — `indexById` is `internal` to that
  module and unreachable from `ui-tv`.
- Franchise search-grouping's own `franchisesIn(movies)` needed no repository call at all (it's pure,
  over the Movies shelf's own films) — used directly.

No new pure helper was added to `feature/catalog` — everything needed was either already public
there or small enough to duplicate locally without touching that module's files.

## Deliberate differences (for `docs/system-architecture.md` "Television differs from the web player")

1. **No "This month" row and no merged resume strip on TV Home.** The phone's `MagazineHome` also
   carries `thisMonth`/`resumeCards` for a single merged strip; TV's `homeRowsOf`-based rows already
   show Continue/Next up separately (unlike the phone, which strips them once a magazine is present),
   and building a second TV-only resume-strip component to match the phone's merged one was set aside
   as a real, small, documented gap rather than rushed.
2. **"All N films" doesn't carry its own focus-restore key back from `MOVIES_PAGE`.** Coming back
   from the full wall lands on the department front page's first row, not the "All N films" link
   itself — a minor, working, documented gap rather than a broken one.
3. **Series' Cast/Similar tabs are gated on content; About is not.** Matching the film page's own
   Cast/Similar gate and the lead's own instruction ("Cast (only with credits)"); About always shows,
   even with nothing but a rating to say — it is the show's counterpart to the film page's Details,
   minus the editor's-choice toggle, which is a film's own pin, not a show's.

## Files (first pass)

### Created (`ui-tv/src/main/kotlin/ui/tv/catalog/`)
`TvCatalogExtras.kt`, `TvCoverStory.kt`, `TvFeatureStrip.kt`, `TvSectionTabs.kt`, `TvCastRow.kt`,
`TvSimilarRow.kt`, `TvDepartmentPages.kt`, `TvCollectionsPage.kt`, `TvFranchisePage.kt`,
`TvPersonPage.kt`, `TvGenresIndex.kt`, `TvLatestPage.kt`.

### Created (tests)
`TvTitlePageTabsStateTest.kt`, `TvPersonPageStateTest.kt`.

### Modified (main)
`TvHome.kt` (`magazine` param), `TvTitlePage.kt` (tabs, Details, editor's choice), `TvCollection.kt`,
`TvCatalogScreen.kt` (masthead split, department dispatch, Collections tab, magazine wiring),
`TvMenuPage.kt` (four new rows: My List, Continue watching, Latest, Genres), `TvLibrary.kt` (five new
frame branches, `TvCatalogExtras`, sentinel restore keys), `TvLibraryBranches.kt` (threaded the three
new `TvCatalogScreen` callbacks through `TvCatalogRoot`).

### Modified (tests)
`TvAppFixture.kt`, `TvCatalogScreenStateTest.kt`, `TvKeptWallStateTest.kt`, `TvHousekeepingTest.kt`,
`TvTitlePageStateTest.kt`.

## Follow-up: three gaps closed

### 1. Grouped search
- **Created:** `TvSearchGroups.kt` (`SearchEntry`, `keyOf`, `SearchSection`, `sectionsFor`,
  `labelFor` — the pure flattening/labelling this screen needed, all `ui-tv`-local).
- **Rewritten:** `TvSearch.kt` (computes `movies`/`franchises`/`groups`/`filter`/`sections`/`entries`
  from `catalog.searchGroupsOf`; `RowAsk` now keys by `SearchEntry` via `keyOf`, not by a set id
  alone), `TvSearchResults.kt` (renders each section under its own heading, a filter-chip row
  (`●`/`○` markers, the same convention the cache-volume picker already uses elsewhere on this
  surface) once `filters.size >= 2`, and four row kinds: the existing film/episode/lesson row
  unchanged, a matched-show row, a round-portrait people row (`TvPortraitCircle`, new, lazy-fetched),
  and a franchise-or-list destination row).
- **Modified:** `TvLibraryFrames.kt` (`TvSearchBranch` gained `extras: TvCatalogExtras` and wires
  `onOpenCollection`/`onOpenPerson`/`onOpenFranchise`/`onOpenList`/portrait callbacks), `TvLibrary.kt`
  (call site), `TvAppFixture.kt` (`repository` promoted from a local `val` inside `init` to a class
  property, so a test can restub `searchPeople` per case), `TvSearchAndGenreTest.kt` (films fixture
  now carries a stable `posterKey` so a `PersonHit` can name one; **2 new tests**: filter chips narrow
  to People and a person's row opens their page; a person with no visible title never appears at
  all).

### 2. Series page tabs
- **Rewritten:** `TvCollection.kt` — Episodes (unchanged season wall/rows body, still the default
  tab)/About (rating, network, status)/Cast (gated)/Similar (gated), `rememberSaveable`d by
  `collection.key`. The `SeriesResumePick` pill moved from "always visible in a shared header" to
  "in the Episodes tab's own header", per the lead's explicit instruction.
- **Created (tests):** `TvCollectionTabsStateTest.kt` — 5 tests: tabs gated correctly, Cast opens a
  person, Similar opens a show, the resume pill plays its own episode, and tab selection survives
  credits arriving after the page (a `mutableStateOf` pushed in mid-test, not just on first
  composition).
- `TvCollectionStateTest.kt` — **unchanged, still green**: Episodes being the default tab means every
  existing assertion (season focus, restore-key landing, document gating) still holds without editing
  that file.

### 3. Department gating
- **Fixed:** `TvDepartmentPages.kt` — removed the >12 films/shows gate on the whole front page
  (`DepartmentFrontPageMinimum` deleted); the department page itself now always shows for a non-empty
  shelf, matching `department-pages.js#renderMoviesDept`/`renderShowsDept`. `ShowsDepartment`'s own
  Popular/New episodes gate (unchanged, in `feature:catalog`) is the only size floor left anywhere.
- **Fixed alongside it** (bugs the lifted gate exposed — small fixtures had never reached real
  department content before, so these were latent): `TvMoviesDepartmentPage` had no arrival-focus
  wiring at all (nothing took the remote on arrival) — added, landing on the first plate of the first
  non-empty row, or on `restoreKey`'s own plate; `DeptRow`'s plates carried no caption
  (`factsLine`) and no offline badge (`heldIds`) — added, matching `TvEntryPlate`'s own facts; the
  `.all` wall in `TvShowsDepartmentPage` was missing `heldIds` on its own plate call — added.
- **Test changes** (a small, or even one-film, shelf can now legitimately carry the same title under
  two headings — Featured and Recently added both — which the web does too; these assertions
  adapted to "the title is offered" rather than "offered exactly once", never to a weaker check of
  intent): `TvCatalogScreenStateTest.kt` (`aShelfWallOpensAFilmsTitleAndACollection`,
  `shelfPlatesCarryThePhonesCaptions`, `seeAllSelectsThatRowsShelf`), `TvHousekeepingTest.kt`
  (`aHeldTitleSaysOfflineOnContinueAndItsShelfButNotOnLatest`), `TvLibraryTest.kt`
  (`choosingAnotherTabLandsOnItsFirstPlateNotOneOpenedElsewhere`, disambiguated by `isFocused()`,
  which is still unambiguous with a duplicate on screen).

## Commands run

- `./gradlew :ui-tv:compileDebugKotlin :ui-tv:compileDebugUnitTestKotlin
  :ui-tv:compileDebugAndroidTestKotlin` — clean, after both the first pass and the follow-up.
- `./gradlew :ui-tv:testDebugUnitTest :ui-tv:compileDebugAndroidTestKotlin lint` (the lead's own
  command) — **BUILD SUCCESSFUL**, every test green. First pass: 297 (291 baseline + 6 new). Follow-up
  added 7 more (2 in `TvSearchAndGenreTest`, 5 in the new `TvCollectionTabsStateTest`) with none
  removed — **304 tests, 0 failed**.
- `./gradlew :ui-tv:lint` and whole-tree `./gradlew lint` — clean, both passes.

## Device install

`adb devices` showed only the real TV box (`192.168.0.35:5555`) attached, no emulator, in both
passes — per instructions, skipped installing (never the TV box, never a bare `installDebug`).

## Status/Summary/Concerns

**Status:** DONE

**Summary:** TV now has the magazine home layout, the masthead/utility split, six new screens
(person, franchise, department front pages ×2, Genres index, Latest, Movies full wall, and grouped
search with filters/people/collections) wired onto phase 3's frames, film-page tabs with
Cast/Similar/Details and the editor's-choice toggle, and series-page tabs with the resume pill in
Episodes' own header. Department front pages match the web's own always-shown rule; only
Popular/New episodes keep a size gate. All within `ui-tv/**`; no other module touched. `304/304` unit
tests green, lint clean, both compile checks clean.

**Concerns:** `TvCatalogExtras` is still a new, `ui-tv`-local `@HiltViewModel`; if `feature:catalog`'s
`CatalogViewModel` grows the same pass-throughs in a later phase, this one should be deleted in
favour of it rather than kept as a second copy. The two documented differences left (item 1 and 2 in
"Deliberate differences") are both small and named, not silent.

**Unresolved questions:**
- Is a TV-local `TvCatalogExtras` acceptable long-term, or should `CatalogViewModel` be extended in
  a coordinated cross-surface change (touches `feature:catalog`, both `ui-mobile` and `ui-tv`)?
