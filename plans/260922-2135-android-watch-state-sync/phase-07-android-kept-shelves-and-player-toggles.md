# Phase 07 — Kept shelves: Continue, Watchlist, Collections, Kids

## Context links

- `web/public/index.html:26-40` (masthead: Home, Movies, Series, Tutorials, then kept: Continue, Watchlist, Collections, Kids, set apart)
- `web/public/app.js:59-68` (`KEPT` labels + empty texts), `:421-457` (views), `:70` (`setsFor` drops unknown ids)
- `web/public/lib/player.js:898-934` (toggles: "Watchlist"/"On the list", "Kids"/"For kids", Add to list)
- `web/public/lib/collections-view.js:17-122` (lists: New list, rename, delete, Play all), `web/public/lib/collection-add.js`
- `web/src/state/schema.ts:115-128` (kids is per player, not per profile)
- `android/ui-mobile/src/main/kotlin/ShelfTabs.kt:32-84`, `CatalogScreen.kt:77-117`, `PlayerControls.kt:67`, `PlayerScreen.kt:52`

## Overview

- Priority: P2. Status: code complete, tablet validation pending. Blocked by 06.
- The masthead gains the web's four kept entries; each opens a wall like the web's view; the player gains the three controls that fill them.

## Key insights

- **How they are reached:** the web puts them in the masthead, after the catalog shelves, visually set apart. Android's masthead is the tab row (`ShelfTabs.kt:19-31` says it *is* the web's masthead). So: eight tabs in the web's order, `PrimaryScrollableTabRow`, a thin divider or muted style before Continue. Not the overflow menu — that holds System/TMDB/Start over, the things that are not shelves.
- Labels and empty texts verbatim from `KEPT`. Kids' empty text says "press Kids in the player", which is true once the toggle exists.
- **Kids is device-wide**, not per profile (`schema.ts:115-128`); the Android store already mirrors that (02).
- Toggles live in the player because "this is where a viewer is when they find out what a film actually is" (`player.js:928-929`).
- **Play all / a kids run are queues**; the Android player plays one set and has no queue yet (parity phase 7). Deferred with that phase; recorded in 09.
- `window.prompt` → Material `AlertDialog` with a text field.

## Requirements

- Functional:
  - Tabs: Home, Movies, Series, Tutorials, Continue, Watchlist, Collections, Kids. Saved selection survives rotation (existing `rememberSaveable`).
  - Continue wall: started titles (`resumeAt != null`), newest first, captions `resumeLine`.
  - Watchlist wall: newest added first. Kids wall: newest marked first.
  - Collections: list of lists (name, count), "New list"; open one → its sets in position order, rename, delete (confirm).
  - Player: Watchlist toggle, Kids toggle, "Add to list" dialog (checkbox per list + New list). Labels change with state as on the web.
  - Heading `Title · n` as rows already do; empty state text when none.
- Non-functional: every write is optimistic through the repository; failure invisible. Files < 200 lines (split screens).

## Architecture

```
CatalogScreen tabs ─► KeptWall(kind) ─► CatalogViewModel.Ready(shelves, watch) → sets by id
PlayerControls ─► PlayerViewModel.toggleWatchlist/toggleKids/setInList ─► WatchStateRepository
ListsScreen / ListScreen ─► CatalogViewModel list ops ─► WatchStateRepository  (no extra VM)
```

One source: every wall reads `Ready.watch`; no screen queries the core directly.

## Related code files

- Create: `ui-mobile/src/main/kotlin/KeptWall.kt` (Continue/Watchlist/Kids), `ui-mobile/src/main/kotlin/ListsScreen.kt`, `ui-mobile/src/main/kotlin/ListScreen.kt`, `ui-mobile/src/main/kotlin/ListNameDialog.kt`, `ui-mobile/src/main/kotlin/AddToListDialog.kt`, `ui-mobile/src/main/kotlin/PlayerMarks.kt` (the three player controls), `feature/catalog/src/main/kotlin/KeptShelves.kt` (pure: ids → sets, orderings, labels) + `feature/catalog/src/test/kotlin/KeptShelvesTest.kt`.
- Modify: `ui-mobile/src/main/kotlin/ShelfTabs.kt` (scrollable, divider, width), `CatalogScreen.kt` (eight entries, See all → Continue), `PlayerControls.kt` or `PlayerScreen.kt` (host `PlayerMarks`), `feature/player/src/main/kotlin/PlayerViewModel.kt` (toggle methods), `feature/catalog/src/main/kotlin/CatalogViewModel.kt` (list ops), `LibraryFlow.kt`/`LibraryPositions.kt` (open-list position saved like `collection`).
- Delete: none.

## Implementation steps

1. `KeptShelves.kt` pure mapping + tests (unknown ids dropped, orders, labels/empty texts equal `KEPT`).
2. Tabs: eight entries, scrollable, visual break before Continue; Home See all for Continue now targets its tab.
3. `KeptWall` for Continue/Watchlist/Kids, reusing `SetPlate` from 06.
4. Lists screens + dialogs; position of an open list kept across rotation.
5. Player marks + VM toggles; kept counts refresh through the snapshot flow.
6. Tests, lint, `:app:installDebug`.

## Todo

- [x] KeptShelves + tests
- [x] tabs (eight, scrollable, set apart)
- [x] Continue / Watchlist / Kids walls
- [x] Collections list + list screen + dialogs
- [x] player Watchlist / Kids / Add to list
- [ ] tablet walk-through

## Success criteria (tablet)

- Each of the four tabs shows the web's label; empty states read as on the web.
- Toggle Watchlist in the player → Watchlist tab shows the title, label reads "On the list"; toggle again → gone.
- Create a list, add two titles from the player, rename, delete: each step reflected immediately and after an app restart.
- (Q1 = yes, after 08) the same lists appear on the web player.

## Risks

| Risk | L×I | Mitigation |
|---|---|---|
| Eight tabs cramped on a phone | M×L | Scrollable row; tablet shows all |
| Viewer expects lists to sync without 08 | H×M | Q1 decides; if deferred, 09 records the gap on both surfaces |
| Player controls crowd the bar | M×L | Group the three in one overflow-style row in `PlayerMarks` |

## Security

None new.

## Next steps

08 (if Q1 = yes) makes these shelves travel; 09 validates.

## Implementation notes

- Parity fix while here: the start page's own row titles read "Latest movies"/"Latest series"/"Latest tutorials" (mechanically derived from the masthead's own shelf names); the web's `home-view.js` actually says "Latest films"/"Latest series"/"Latest courses" — literal, not derived. `latestTitleFor` in `HomeShelves.kt` now matches those three strings exactly; `HomeShelvesTest.kt` updated.
- `KeptWall`'s heading reads "Title · n" (the Home rows' own convention), not the web's spelled-out `countOf` wording ("three titles") — the Requirements section asks for this explicitly, so the two Android surfaces (start page, kept tab) agree with each other rather than one of them agreeing with the web's standalone-page wording.
- The player's "Add to list" is a checkbox per list plus inline "New list", not the web's numbered `window.prompt()` — called for by this phase's own Related Code Files (`AddToListDialog.kt`), and documented in that file: the web's Collections shelf, where a list is made, is one click away on the same page; the player has no such shelf on screen.
- Files touched beyond the phase's own list, all within `ui-mobile`/`feature:*` and needed to keep files under ~200 lines or to plumb the new `Destination.List`: `ShelfWall.kt` (new — `CatalogScreen.kt`'s catalog-shelf grid, extracted once the masthead grew past three shelves), `PlayerMarksState.kt` (new — the data `PlayerViewModel.marks` publishes), `AppChrome.kt` (new `Destination.List` case), `HomeScreen.kt`/`HomeShelvesTest.kt` (doc/test updates only), `FakeWatchStateRepository.kt` and `CatalogViewModelTest.kt`'s `FakeCatalogWatchState` (now record list writes and mutate their snapshot, needed to test the new wrappers).
- `PlayerViewModel.kt` is 207 lines (over the 200-line guideline) — the toggle/list methods share `openSetId`/`marks` state tightly enough that splitting them out would separate a method from the field it reads, judged worse than the overage.
