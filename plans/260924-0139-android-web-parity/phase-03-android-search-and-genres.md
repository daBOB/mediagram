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
Priority P1 · Status done (device check pending, see Todo) · First
viewer-visible parity win.

## Deviations from this file
- Genre chips (`TitleHeader`'s `genres` param) read `MediaSet.genres` — the
  catalog's own field, already the source `GenreShelf` matches against —
  rather than `TitleInfo.genres` (the provider string this screen showed
  before). Both hold the same data (`store.rs` backfills `SetSummary.genres`
  from the same `shows.genres` row `TitleInfo.genres` reads), but the set's
  own field is synchronous and is what the shelf actually matches on, so a
  tapped chip is guaranteed to land on a genre page that holds it.
- The search icon sits in `LibraryScaffold`'s bar, shown on every screen it
  renders except the search screen itself (catalog, a collection, a title,
  the system screen…) rather than the catalog root alone — matching the
  web, whose search box is part of the page header on every route, not
  just the movies shelf.
- A search hit for a document is shown and not tapped, the same rule
  `CollectionRows.kt`'s `ItemRow` already follows for one inside a course
  — the location line groups it with a lesson for the same reason.
- A blank field never leaves search on the keystroke itself; it shows the
  empty state and leaves only on the bar's own back arrow or the system
  gesture. The web moves its address bar back once the pause settles on
  nothing typed, but a touch keyboard has no such pause to wait out, and a
  screen that can vanish under a still-held backspace is worse than one a
  viewer always has to ask to leave.
- Genre chips appear on a film's own page and a show's or course's header,
  never on an individual episode or lesson row — the same restraint
  `film-page.js` and `series-header.js` keep; there is no per-episode genre
  link on the web either.

## Known limitation
- Scroll position is not restored when leaving a list (search results, a
  shelf, a season) to play something and coming back — true everywhere in
  this app already, not something this phase introduces or fixes.

## Review fixes (260924-0850, code-reviewer report in `reports/`)
Position tracking became a real stack (`LibraryPositions`'s `top`/`openX`/
`pop`, one string of encoded frames, still `rememberSaveable`) instead of
one slot checked in a fixed priority order — the priority order made a
genre page permanently outrank the title or series it had just opened,
so tapping a card on a genre page did nothing (C1). The stack also fixed
the three highs (a reopened search view model kept its previous query and
rows; a restored query did not re-run; a document hit could reach the
player) and every medium and low the report raised except the two
recorded above as deliberate and the scroll-position note above as a
known limitation. See the report for the full list and what each line
changed.

A same-day re-review, on top of confirming the above, found two issues the
stack rewrite itself introduced: a frame whose key stopped resolving
(catalog still loading, or a title/list deleted elsewhere) drew nothing at
all rather than falling back — fixed by a `Loading`/`Stale` outcome that
either shows a loading message or pops the frame — and the saved stack
string could crash `decode` on a pasted separator or an unknown frame
kind — fixed by sanitising at the write side and making `decode` skip
rather than throw on either. Also fixed: a one-frame flash of a previous
search session's results on reopen, and "nothing found" showing before a
restored query's catalog had loaded. Detail and tests for each in the
report's "Re-review" section.

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
`SearchViewModel` (feature/catalog) — query `MutableStateFlow` → `flatMapLatest`
(blank answers `Idle` at once; anything else delays 200 ms then asks
`CatalogRepository.search`) → `SearchUiState`. The join onto a `MediaSet` is
not the ViewModel's: `searchRowsOf(hits, catalogState)` runs where the
screen already holds the catalog, so a query never rebuilds it or re-checks
a poster's file. `GenreShelf.kt` (pure, port of `genreShelf`) over the
catalog state already in `CatalogViewModel`.

Navigation: `LibraryPositions` holds one saved string, decoded into a stack
of frames (`FrameKind` + a payload); `top` is what `LibraryBranches`
dispatches on, and every position (`titleId`, `genre`, …) reads the most
recent frame of its own kind rather than a single shared slot — which is
what lets the same kind recur (a title opened from a genre page opened
from another title) without one overwriting the other.

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
- [x] LibraryFlow split
- [x] GenreShelf + SearchWhy + tests
- [x] SearchViewModel + tests
- [x] SearchScreen/Row, GenreScreen, GenreLinks
- [x] navigation + back behaviour
- [x] check.sh, bump, changelog
- [x] device run 2026-09-24 on the tablet (0.43.0): "holle" finds "Zwischen Himmel und Hölle" as the web does (19 ms); "alien" gives the same 5 in the same order as the web (23 ms first search); genre → film, genre → series, season, menu → search and back chains each pop one frame; search reopens empty; the query and results come back after process death. A summary-only term (e.g. "steuer") finds nothing on web and phone alike: the channel index has no `assets` rows right now, so summary search can only be seen on device once the summaries are published again.

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
