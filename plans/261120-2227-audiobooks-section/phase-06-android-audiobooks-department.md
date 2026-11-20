# Phase 06 — Android: Audiobooks department on phone and TV

**Context:** spec § Android. Reference: Phase 04 (web). Model: Documentaries (`Departments.kt:58-99`, `DocumentariesDepartmentScreen.kt`, `TvDocumentariesDepartmentPage.kt`).
**Status:** todo. **Depends on:** Phase 01 (core keys), Phase 04 (decisions to match).

## Requirements

Match Phase 04 exactly, on phone/tablet and TV:
- `"book"` → `Kind.AUDIOBOOK` (`SetSummaryToMediaSet.kt:19-26`); books never land on Movies (`Shelves.kt:48`).
- `Department.AUDIOBOOKS("Audiobooks", "audiobook", "audiobook")` last; `Section("audiobooks", "Audiobooks")` last in `SECTIONS`; `sectionKey()` maps `AUDIOBOOK` → `"audiobooks"`.
- Page: hero (`Audiobooks`, count line, no lead art), **Continue listening**, one row per category ("Other" last), **Recently added**, **All audiobooks** by title. Empty: `No audiobooks yet.`
- Covers whole (`ContentScale.Fit` or a 1:1 frame) on book cards.
- Search: an `AUDIOBOOKS` filter and group, labelled `Audiobooks`.
- Home: a book with a position appears in Home's Continue row (phone and TV), as on the web; verify, add only if missing. No new Home band.
- Kids: `forKidsProfile` treats `kind == AUDIOBOOK && category == KIDS_BOOK_CATEGORY` (`"Kinderbücher"`) as safe at every limit.

## Files

- Modify (core): `android/core/model/src/main/kotlin/MediaSet.kt:128-149` (`AUDIOBOOK` with KDoc), `Sections.kt`, `AgeRating.kt` (`const val KIDS_BOOK_CATEGORY = "Kinderbücher"`); `android/core/data/src/main/kotlin/SetSummaryToMediaSet.kt`
- Modify (catalog): `android/feature/catalog/src/main/kotlin/{Department.kt,Shelves.kt,Departments.kt,LibraryPages.kt,SearchGroups.kt}`; `data class AudiobooksDepartment(itemCount: Int, resumeCards: List<SetCard>, recentlyAdded: List<MediaSet>, categories: List<CategoryRow<Entry>>, books: List<MediaSet>)` and `fun audiobooksDepartmentOf(books: List<MediaSet>, byId: Map<String, MediaSet>, watch: WatchState, heldIds: Set<String> = emptySet()): AudiobooksDepartment?` in `Departments.kt` (or a new `AudiobooksDepartment.kt` if `Departments.kt` passes 200 lines)
- Modify (exhaustive `when (department)`; the compiler lists them): `LibraryPages.kt:38`, `TitlePageCandidates.kt:20`, `ChromeCounts.kt:50`, `HomeShelves.kt:78` (no Home band), `ui-mobile/.../LibraryBranchSupport.kt:90`, `ui-mobile/.../catalog/DepartmentTab.kt:34`, `ui-tv/.../TvDepartmentPages.kt:72`, `ui-tv/.../TvCatalogBlend.kt:59`; `ui-common/.../catalog/DepartmentScrollStates.kt:20-34` (`audiobooks: LazyListState`); `ui-common/.../catalog/SearchLines.kt:22,35` (`AUDIOBOOK` like `DOCUMENTARY`); `SearchFilterChips.kt:71-80`, `TvSearchGroups.kt:91-137`, `SearchGroupsView.kt:69,95`
- Create: `android/ui-mobile/src/main/kotlin/ui/catalog/AudiobooksDepartmentScreen.kt` (from `DocumentariesDepartmentScreen.kt`, no collection rows); `android/ui-tv/src/main/kotlin/ui/tv/catalog/TvAudiobooksDepartmentPage.kt` (from `TvDocumentariesDepartmentPage.kt`, no collection rows)
- Modify: `web/test/fixtures/library-pages/cases.json` gains a book case; `android/feature/catalog/src/test/kotlin/LibraryPagesFixtureTest.kt:140-150` maps `"book"`
- Test: `SectionsMatchDepartmentsTest`, `core/model/src/test/kotlin/SectionsTest.kt`, `AgeRatingTest`, `core/data/src/test/kotlin/CatalogRepositoryTest.kt`, `ShelvesTest`, `DepartmentsTest`, `SearchGroupsTest`, `ui-tv/.../TvDepartmentPagesStateTest.kt`

## Interfaces

- Consumes: Phase 04 names (`audiobooks`, `Audiobooks`, `Kinderbücher`).
- Produces: `Kind.AUDIOBOOK`, `Department.AUDIOBOOKS`, `AudiobooksDepartment`, `audiobooksDepartmentOf`, `KIDS_BOOK_CATEGORY`. Phase 07 uses `Kind.AUDIOBOOK`.

## Steps

- [ ] **1: Failing tests**
  - `CatalogRepositoryTest.aBookIsAnAudiobook`: `setSummary(kind = "book")` → `Kind.AUDIOBOOK`.
  - `SectionsTest`: an audiobook's `sectionKey()` is `"audiobooks"`; `SectionsMatchDepartmentsTest` stays green with both lists ending in Audiobooks.
  - `AgeRatingTest.aKinderbuecherBookIsForEveryKid`: same three books and marks as the web test in Phase 04, same results.
  - `ShelvesTest.booksAreNeverFilms`: a book is on the AUDIOBOOKS shelf and not on MOVIES.
  - `DepartmentsTest.audiobooksPageRows`: books A/B (Sachbuch), C (no category), D with a position → categories `["Sachbuch", "Other"]`, resume cards `[D]`, `books` sorted by title.
  - `SearchGroupsTest.booksGroupAsAudiobooks`.
  - Fixture: the shared book case passes in `LibraryPagesFixtureTest` and in web `library-pages-fixtures.test.ts`.
- [ ] **2: Run, expect failures** (Android unit test command in plan.md).
- [ ] **3: Implement**; let the compiler list the `when` sites.
- [ ] **4: Android unit tests + web `bun test`** green.
- [ ] **5: Regression look:** `scripts/build-android-core.sh`, then install the debug build with `ANDROID_SERIAL=caad49da` (tablet) and on the Google TV emulator: the app starts and every existing department looks as before. No books exist in the channel yet, so the Audiobooks page itself is walked in Phase 09 against the real books. Navigate only; never change a setting on a device walk.
- [ ] **6: Commit** `feat(android): an Audiobooks department on phone, tablet and TV`.

## Success criteria

Unit tests green on every module in the command; the TV and tablet pages show the same rows as the web page.

## Risks

- `Department.AUDIOBOOKS.name.lowercase()` is the section key; a different name silently breaks hiding.
- `Shelves.kt:60`'s Documentaries shelf is the one without `takeIf { isNotEmpty() }`; give Audiobooks the empty-skipping form so an empty library shows no pill (as web shows its count only when non-zero, check and match).
