# Phase 4: TV catalog, shelves and title pages

**Context:** [plan.md](plan.md) · [web map](reports/research-260924-2239-web-player-reference-surface-report.md) · phone refs: `ui/LibraryFlow.kt`, `ui/catalog/*` · web refs: `lib/catalog/home-shelves.js`, `shelf-view.js`, `plate.js`, `season-wall.js`, `film-page.js`, `course-view.js`

## Overview

- **Priority:** High — this is what the TV is for.
- **Status:** pending
- **Deliverable:** browse the real library by D-pad: masthead of sections, Home rows, shelf walls, Continue/Watchlist/Collections/Kids, series → seasons → episodes, courses, title page with Play.

## Key insights (decisions carried from the web)

- **Masthead, not a side drawer** — web uses a masthead; tv `TabRow` at the top: Home · catalog shelves (Movies, Series, Tutorials, …as `CatalogUiState` gives them) · Continue, Watchlist, Collections, Kids (visually set apart, as web `kept`) · System · profile name. Order from the moved tab-ordering function — the same one phone uses.
- **Home** = `homeRowsOf` (Continue, Next up, Latest films, Latest series, Latest courses), **max 6 plates, no sideways scroll**, heading "Title · total", "See all" as the 7th focusable item leading to that shelf. Courses row is text rows, as web (no art).
- **Walls of 2:3 plates** on shelves (Open Question 1 default), initials when no poster (`initialsOf`), watched-fraction tick. Column count fixed for TV (6 at 960dp) — no `WindowSizeClass`.
- **Grouping is not decided here** — the shelves arrive already grouped from `feature:catalog`; unknown kinds already land on Movies. TV just draws them.
- **Series**: >1 season → season wall; 1 season → episode list (`seasonPlatesOf`, same rule as web/phone). Courses folder by folder (`rowsOf`). Document rows disabled with the phone's reason.
- **Title page**: art beside facts (`factsLine`, `ratingLabel`, `technicalLine`), TMDB info via `rememberTitleInfo`, **Play/Resume focused on arrival**, resume line from `resumeLine`.
- **Navigation** = the moved position model + back order. Back walks it; at the catalog root Back moves focus to the masthead, a second Back leaves the app.
- **Focus restoration** is the TV-specific rule the web never needed: coming back from a page restores focus to the plate that opened it. Store the last-opened key per destination alongside the positions (saveable), request focus on it when the wall recomposes.
- **Kids profile**: nothing TV-specific — `CatalogViewModel` already filters; `KidsEmpty` state gets its own message as on phone.
- Collections: list of user lists → a list's wall; rename/delete via `TvTextQuestion`/`TvConfirmDialog` from phase 3.
- **Carried from phase 1:** the six position keys are declared five times across `catalog.LibraryPositions` and `ui.LibraryPositionsHolder` (class, holder constructor, `snapshot()`, `applyFrom()`, `rememberLibraryPositions()`). Before TV adds a seventh concern (the focus-restore key), fold them into one immutable data class held in a single `rememberSaveable` with a `Saver`, so a missed field cannot silently skip a clear.

## Requirements

- Functional: every shelf and page from the phone is reachable; Up from any content reaches the masthead; Back never dead-ends.
- Non-functional: `LazyVerticalGrid`/`LazyColumn` with stable keys so focus restoration and long walls (hundreds of films) stay smooth; posters through coil with a TV-sized request.

## Related code files

- Create: `android/ui-tv/src/main/kotlin/ui/tv/catalog/{TvLibrary.kt,TvMasthead.kt,TvHome.kt,TvPlate.kt,TvWall.kt,TvKeptWall.kt,TvLists.kt,TvCollection.kt,TvSeasonWall.kt,TvTitlePage.kt}`, tests alongside in `src/test`
- Modify: `TvApp.kt` (replace library stub)

## Implementation steps

### Task 1: Plate and wall
- [ ] **1.1** `TvPlate(card, onOpen)`: tv `Card`, 2:3, shared focus treatment, caption under, initials fallback, progress tick. Test: focused plate reports focused; centre calls `onOpen`.
- [ ] **1.2** `TvWall(entries, restoreKey, onOpen)`: fixed 6 columns, `Overscan` padding, first plate (or `restoreKey`) focused on entry. Tests: initial focus; D-pad right moves along; restore focuses the given key.
- [ ] **1.3** Commit — `feat(android): poster plates and walls for television`.

### Task 2: Masthead + Home
- [ ] **2.1** `TvMasthead` with section order from the shared function; selected section = active imprint colour; focus = shared treatment.
- [ ] **2.2** `TvHome`: rows from `homeRowsOf`, ≤ 6 plates + "See all". Test: row never exceeds 6 plates; Down from masthead lands on first plate of first row.
- [ ] **2.3** Commit — `feat(android): television home rows and sections`.

### Task 3: Kept shelves, lists, collections, seasons, title page
- [ ] **3.1** `TvKeptWall` (Continue/Watchlist/Kids via `continueWall`/`watchlistWall`/`kidsShelf`), `TvLists` + list wall.
- [ ] **3.2** `TvCollection` (tree rows), `TvSeasonWall`, `TvTitlePage` (Play focused).
- [ ] **3.3** `TvLibrary` wires positions → screens with the shared priority; `FetchResultDialog` equivalent via `TvConfirmDialog`-style dialog.
- [ ] **3.4** Robolectric flow test (pattern: `LibraryFlowTest`): open a series from Home → season → episode title → Back ×3 returns focus to the originating plate.
- [ ] **3.5** Commit — `feat(android): browse shows, courses and lists on a television`.

### Task 4: Emulator walk on `TV test` profile
- [ ] **4.1** Key events only: every masthead section, a series with seasons, a course, a film title page, Back to root and out.
- [ ] **4.2** Switch to a kids profile (create `TV kids` if none) and back: shelves re-filter, no restart. Delete `TV kids` afterwards if created.
- [ ] **4.3** Screenshots of Home, a wall, a title page into `plans/260924-2239-android-tv-surface/reports/`.

## Todo list
- [ ] Plate + wall with focus restore
- [ ] Masthead + Home (≤ 6, no sideways scroll)
- [ ] Kept, lists, collections, seasons, title page
- [ ] Flow test incl. focus after Back
- [ ] Emulator walk incl. kids switch

## Success criteria
Every phone destination exists on TV; Back from any page lands focus where the viewer left; Home matches the web's rows and limits.

## Risk assessment
| Risk | Mitigation |
|---|---|
| Focus lost after recomposition (catalog refresh) | Stable keys + restore key; flow test triggers a refresh mid-walk |
| Big walls stutter on emulator | Lazy grid, fixed-size coil requests; measure on emulator before optimising further |
| Masthead unreachable from deep in a wall | Up at top row moves to masthead (focus group); tested |

## Security considerations
None beyond phone.

## Next steps
Phase 5.
