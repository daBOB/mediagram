# Preloads reachable once everything is finished

Commit: 56c5f93d `feat(android): Preloads stays in the menu while anything is held`

## What changed
- `CatalogUiState.showsPreloads(queueCount)` (feature/catalog): queue > 0 or `heldFilms()` non-empty. `heldFilms()` is the profile-filtered list, so a kid whose only held title is hidden gets no entry.
- Phone (`LibraryFlow.kt`) and TV (`tvMenuActions` in `TvMenuBranches.kt`) use it for `onPreloads`.
- Count choice: `preloadCount` stays the queue count. `MenuActions.preloadsLabel` reads "Preloads · n" while queued and plain "Preloads" when only held items exist (a "· 0" would read as "nothing there"). Both menus use it.

## Tests (new, 8)
- `ShowsPreloadsTest` (catalog, 4): queued only, held only, neither (and Loading), kid with hidden held title.
- `PreloadsHeldMenuFlowTest` (phone, 2): `preloadsShowsWhenSomethingIsHeldButNothingQueued` (opens the page, lists the film), `preloadsHiddenWhenNothingQueuedOrHeld`.
- `TvPreloadsHeldMenuTest` (TV, 2): same two.
- `LibraryFlowFixture` gained a `heldIds` parameter (file 264 -> 263 lines, did not grow).
- The full verify command passed (mobile, tv, catalog unit tests, both lints, app compile).

## Concerns
- RED was not observed separately: the new API does not exist before the change, so the tests could not compile; GREEN observed.
- Files over 200: LibraryFlowFixture.kt (263, pre-existing, shrank by one line). No other touched file is over 200.
