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
- Modify: `TvApp.kt` (replace library stub), `profile/TvProfileGate.kt` (hand name + reopen to content), `ui-common/.../ui/LibraryPositions.kt` + `feature/catalog/.../LibraryPositions.kt` (task 1 fold)

## Implementation steps

Every task below also binds to this file's **Key insights** and the plan's Global Constraints. Phone reference files live in `android/ui-mobile/src/main/kotlin/ui/`; shared rules in `android/feature/catalog/src/main/kotlin/` and `android/ui-common/`. TV building blocks already present: `ui/tv/TvFocus.kt` (focus scale/border treatment), `TvTheme.kt`, `TvTextRow.kt`, `TvSafeArea`/`TvShell` in `TvApp.kt`, `setup/TvTextQuestion.kt`, `setup/TvConfirmDialog.kt`, `profile/TvProfileGate.kt`. Test patterns: Robolectric Compose tests in `android/ui-tv/src/test/kotlin/ui/tv/` (see `TvAppTest.kt`, `TvAppFixture.kt`, `FakeWatchState.kt`); instrumented focus tests in `src/androidTest` use `LeavesTouchModeRule`.

### Task 1: One saveable positions value
Carried from phase 1: the six position keys are declared five times across `catalog.LibraryPositions` (`feature/catalog/.../LibraryPositions.kt`) and `ui.LibraryPositionsHolder` (`ui-common/.../ui/LibraryPositions.kt`: class, constructor, `snapshot()`, `applyFrom()`, `rememberLibraryPositions()`).
- [ ] **1.1** Fold them so the key list is declared once: the holder keeps a single `rememberSaveable` state of one value (with a `Saver`) instead of six `MutableState`s; `snapshot()`/`applyFrom()` copy that one value. Public holder API used by `ui-mobile/.../LibraryFlow.kt` (`setId`, `titleId`, `collection`, `season`, `listId`, `menuScreen` get/set, `snapshot()`, `leaveFrom()`, `toCatalog()`) stays source-compatible. `resolve`/`leave`/`toCatalog` semantics unchanged. No `*ViewModel.kt`/`*UiState.kt` edits.
- [ ] **1.2** Test (`ui-common/src/test`): the holder survives `StateRestorationTester` save/restore with all six keys set, and `leaveFrom` still clears what `leave` decides. Existing `feature:catalog` and `ui-mobile` suites pass unchanged.
- [ ] **1.3** Commit — `refactor(android): hold the library position keys as one saveable value`.

### Task 2: Plate and wall
- [ ] **2.1** `ui/tv/catalog/TvPlate.kt` — `TvPlate(title, posterPath: File?, watchedFraction: Float?, onOpen)`: tv-material `Card` (or `Surface` with `onClick`), 2:3 art, `TvFocus` treatment, caption under, `initialsOf(title)` fallback when no poster, progress tick from `watchedFractionOf`. Posters via coil `AsyncImage` over the `File` from `rememberPosterPath` (as phone `PosterArt` does), with a fixed TV-sized request. Test: focused plate reports focused; centre (Enter/DPAD_CENTER) calls `onOpen`.
- [ ] **2.2** `ui/tv/catalog/TvWall.kt` — `TvWall(items, key, restoreKey, onOpen, plate)`: `LazyVerticalGrid(GridCells.Fixed(6))`, stable keys, `Overscan` as `contentPadding` (see `TvShell` doc), first plate — or the item whose key == `restoreKey` — focused on entry via `FocusRequester`. Tests: initial focus on first; D-pad right moves to second; with `restoreKey` the named plate is focused (including one beyond the first screenful).
- [ ] **2.3** Commit — `feat(android): poster plates and walls for television`.

### Task 3: Masthead, Home and shelf walls
- [ ] **3.1** `TvMasthead`: tv-material `TabRow` with tabs from `catalogTabsOf(shelves)` (same function phone `ShelfTabs` uses): Home, catalog shelves, then the four kept entries visually set apart (web `kept`), then the chosen profile's name at the end. Selecting the profile entry calls the gate's reopen (`ProfileViewModel.reopen`, as phone `ProfileGate` hands `ProfileBarState.onChoose`) — extend `TvProfileGate` to pass `name` + `onChoose` to its content, mirroring phone. System/Settings are **not** in this phase (phase 6).
- [ ] **3.2** `TvHome`: rows from `homeRowsOf` (same args as phone `HomeScreen.kt`), each row ≤ 6 plates in a non-scrolling `Row`, heading "Title · total", "See all" as the 7th focusable item selecting that row's masthead tab. Course rows are text rows (`TvTextRow`), no art — as web. Tests: a row with 10 entries shows 6 plates + See all; Down from masthead lands on the first plate of the first row; Up from the top row returns to the masthead.
- [ ] **3.3** Shelf tabs show their shelf as a `TvWall` (entries grouped already by `feature:catalog`; collections open the collection, sets open the title). Selected tab kept `rememberSaveable` like phone `CatalogScreen`. Loading/Empty/KidsEmpty/Failed get centred messages with phone's exact strings.
- [ ] **3.4** Commit — `feat(android): television home rows and sections`.

### Task 4: Kept shelves, lists, collections and seasons
- [ ] **4.1** Continue/Watchlist/Kids tabs via `continueWall`/`watchlistWall`/`kidsShelf` as walls (phone `KeptWall.kt` is the reference for what each shows and its empty text).
- [ ] **4.2** Collections tab: `TvLists` — user lists as focusable rows plus a "New list" row → `TvTextQuestion`; a list opens `TvList` — its sets as a wall plus Rename (`TvTextQuestion`) and Delete (`TvConfirmDialog`) actions, and remove-from-list per phone `ListScreen.kt`.
- [ ] **4.3** `TvCollection` for a show/course: facts header + `rowsOf` rows (phone `CollectionScreen.kt`): >1 season → season wall (`seasonPlatesOf`) via `TvWall`; one season → episode rows; course folders as headed text rows; document rows disabled with phone's reason text. `TvSeason` for one opened season: episode rows.
- [ ] **4.4** Robolectric tests for each: first focusable focused on arrival; kept empty texts match phone.
- [ ] **4.5** Commit — `feat(android): browse shows, courses and lists on a television`.

### Task 5: Title page and library wiring
- [ ] **5.1** `TvTitlePage`: art beside facts (`factsLine`, `ratingLabel`, `technicalLine`, TMDB overview via `rememberTitleInfo`), resume line from `resumeLine`; **Play/Resume focused on arrival**. Play sets the player position; the player itself is phase 5 — until then the Player branch shows a centred stub line and Back leaves it.
- [ ] **5.2** `TvLibrary`: replaces the `"library"` stub in `TvApp.kt`. Wires `rememberLibraryPositions()` → `resolve(catalogState)` → screens with the same branch priority as phone `LibraryFlow.kt` (Menu branch omitted until phase 6). Back = `leaveFrom(resolved)`; at the catalog root, Back moves focus to the masthead first, a second Back leaves the app (`BackHandler` disabled → activity finishes). Fetch result dialog equivalent shown on every branch but the player, via a tv dialog like `TvConfirmDialog`.
- [ ] **5.3** Focus restoration: remember the last-opened key per destination (saveable, alongside positions — not in `feature:*`), pass it as `restoreKey` so Back lands on the plate/row that was opened.
- [ ] **5.4** Robolectric flow test (pattern: phone `LibraryFlowTest`/`TvAppTest`): open a series from Home → season → episode title → Back ×3 returns focus to the originating Home plate; a catalog refresh mid-walk does not lose the position.
- [ ] **5.5** Commit — `feat(android): open titles and walk back on a television`.

### Task 6: Emulator walk
Emulator only (`ANDROID_SERIAL=emulator-5554`). Never the phone.
- [ ] **6.1** From the masthead profile entry, create and choose **`TV test`** (closes phase 3's deferred 4.3). Nothing is played in this phase.
- [ ] **6.2** Key events only: every masthead section, a series with seasons, a course, a film title page, Back to root and out.
- [ ] **6.3** Switch to a kids profile (create `TV kids` if none) and back: shelves re-filter, no restart. Delete `TV kids` afterwards if created (only if the app offers deletion; otherwise record it).
- [ ] **6.4** Screenshots of Home, a wall, a title page into `plans/260924-2239-android-tv-surface/reports/`.

## Todo list
- [ ] Positions held as one saveable value
- [ ] Plate + wall with focus restore
- [ ] Masthead (incl. profile switch) + Home (≤ 6, no sideways scroll) + shelf walls
- [ ] Kept, lists, collections, seasons
- [ ] Title page, library wiring, flow test incl. focus after Back
- [ ] Emulator walk incl. `TV test` and kids switch

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
