# Phase 03: Android — search screen and genre pages

## Context links
- `web/public/lib/search-view.js:4-92` (flat ranked rows, location line `:27-38`, "why" badge `:18-24`, row click plays `:90`)
- `web/public/app.js:599-610,744-754` (200 ms debounce, `#/search/<q>`, empty box → back)
- `web/public/lib/genres.js:17-40` (`genreShelf`: films then series; series by first episode; no sort, no minimum)
- `web/public/app.js:211-232` (`viewGenre`: sub-headings only when both kinds present; empty text)
- `web/public/lib/film-page.js:76-81`, `series-header.js:101` (genre links)
- Android nav: `android/ui-mobile/src/main/kotlin/LibraryPositions.kt:57-86`, `LibraryFlow.kt:141-264`, top bar actions `AppChrome.kt:144-147`
- Genre text today: `android/ui-mobile/src/main/kotlin/TitleDetailScreen.kt:103-108`
- Label helper already ported: `android/feature/catalog/src/main/kotlin/ResumeLine.kt:48` (`episodeLabel`)

## Overview
Priority P1 · Status pending · First viewer-visible parity win.

## Key insights
- Web search results are **rows, not cards**, one flat ranked list; a row plays
  the set directly. The phone copies that (no grouping into shows).
- Web has **no genre shelves on the home page**; genres are links on the film
  page / series header leading to a genre page. Copy exactly — no home shelf.
- Series on a genre page are answered by their first episode (`firstItemOf`);
  Android's collection model (`feature/catalog/.../Collection.kt`) has the same "first item".
- `LibraryFlow.kt` is 277 lines: extracting the `when` branches is a
  prerequisite, not optional.

## Requirements
- Search icon in the top bar (catalog screens); opens a search field with
  focus + keyboard; 200 ms debounce; empty field → back to where it came from.
- Result row: title, location line (show · episode label / folder path / year),
  excerpt when matched in summary, "why" badge text as web (`search-view.js:18-24`),
  progress bar from watch snapshot. Tap = play (as web).
- Empty result: web's empty wording.
- Genre links (chips) on `TitleDetailScreen` replace the plain text; tap →
  genre page: films grid then series grid, headings only when both, empty text.
- Kids profile rules: results/genre pages use the same visible catalog the shelves use (no age leak).

## Architecture
`SearchViewModel` (feature/catalog) — query `MutableStateFlow` → `debounce(200)` →
`core.search` → join ids against `CatalogRepository` sets → `SearchUiState`.
`GenreShelf.kt` (pure, port of `genreShelf`) over the catalog state already in `CatalogViewModel`.
Navigation: `LibraryPositions` gains `search: String?` and `genre: String?`
(saveable); `LibraryFlow` branch order: player > menu > search > genre > title > ...

## Related code files
Create:
- `android/feature/catalog/src/main/kotlin/SearchViewModel.kt`, `SearchUiState.kt`
- `android/feature/catalog/src/main/kotlin/GenreShelf.kt` (+ `GenreShelfTest.kt` porting `web/test/genres.test.ts`)
- `android/feature/catalog/src/main/kotlin/SearchWhy.kt` (badge text, pure) + test
- `android/ui-mobile/src/main/kotlin/SearchScreen.kt`, `SearchRow.kt`, `GenreScreen.kt`, `GenreLinks.kt`
- `android/ui-mobile/src/main/kotlin/LibraryFlowBranches.kt` (split out of `LibraryFlow.kt`)
- `android/feature/catalog/src/test/kotlin/SearchViewModelTest.kt`
Modify:
- `android/ui-mobile/src/main/kotlin/LibraryFlow.kt` (shrink below 200), `LibraryPositions.kt`, `AppChrome.kt` (search action, `Destination` entries), `TitleDetailScreen.kt`
- `android/feature/catalog/src/test/kotlin/FakeCatalogRepository.kt` (search fake)

## Implementation steps
1. Split `LibraryFlow.kt` (no behaviour change) — commit-internal, verify tests still green.
2. `GenreShelf.kt` + tests; `SearchWhy.kt` + tests.
3. `SearchViewModel` with debounce and cancellation (`mapLatest`), tests with a fake core.
4. Screens; search action in `LibraryScaffold`; genre chips on title page.
5. Positions + flow branches; back from search restores the prior screen.
6. Device: search "steuer", an umlaut spelled out, a summary-only term; open a genre from a film.

## Todo
- [ ] LibraryFlow split
- [ ] GenreShelf + SearchWhy + tests
- [ ] SearchViewModel + tests
- [ ] SearchScreen/Row, GenreScreen, GenreLinks
- [ ] navigation + back behaviour
- [ ] check.sh, bump, changelog, device run

## Success criteria
- Same query on web and phone lists the same titles in the same order (spot-check 3 queries).
- `GenreShelfTest` mirrors every `genres.test.ts` case.
- Rotating mid-search keeps query and results (saveable state).

## Risks
- Keyboard + edge-to-edge insets: use `imePadding`; check on tablet landscape.
- Search while no catalog installed: core returns empty; screen shows empty text, no error.

## Security
None new; query stays on device.

## Next steps
10 adds the offline badge to search rows.
