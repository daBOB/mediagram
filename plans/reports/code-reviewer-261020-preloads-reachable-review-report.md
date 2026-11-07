# Review: 56c5f93d "Preloads stays in the menu while anything is held"

Verdict: Approved (no Critical or Important findings).

## Checks

1. Condition. `showsPreloads` (`android/feature/catalog/src/main/kotlin/CatalogUiState.kt:134`) is `queueCount > 0 || heldFilms().isNotEmpty()`. `heldFilms()` is the same profile-filtered list the Preloads page renders (`LibraryBrowseBranches.kt:170`, `TvPreloadsFrame.kt:40`). It resolves films from the shelves and episodes through `mediaSet`, never raw `heldIds`. So the entry shows exactly when the page has something held to show, and a kid with only a hidden held title gets none. Phone and tablet share `LibraryFlow.kt:92`; TV uses `TvMenuBranches.kt:58`. Both surfaces are covered.
2. Queue count. `preloadCount` is still the `resolvableQueueRows` size (`LibraryFlow.kt:65`, `TvLibrary.kt:129`), unchanged. `preloadsLabel` (`MenuActions.kt:70`) gives "Preloads · n" only when `n > 0`, else plain "Preloads". The label is shared by the phone menu (`OverflowMenu.kt:117`) and the TV (`TvMenuPage.kt:101`).
3. TV focus. The Preloads row is the last row in the column, with no focus requester and nothing keyed after it. Landing focus goes to the keyed `MenuRow`s or the rail, so a row appearing or disappearing at the bottom cannot shift or steal focus. The column scrolls. The one residual case is that the row vanishes while focused if the queue empties and the last held title goes at the same moment. That needs a removal made from this same menu, so it is practically unreachable and not a regression.
4. Tests.
   - `ShowsPreloadsTest` covers queued only, held only, neither, `Loading`, and the kid with a hidden held title.
   - The phone and TV flow tests drive the real menu: the label is plain with no number, tapping opens the page, and the film is listed. The negative case asserts no "Preloads" substring at all.
   - Neither flow test covers a queue count beside held items. The label rule is trivial, so this is minor.
5. Hygiene. Every touched Kotlin file is under 200 lines except `LibraryFlowFixture.kt` (263 lines, was 264). It shrank because the long comment was shortened. `OverflowMenu.kt` stays at 169. The commit is conventional with no AI references or plan labels. The `FakeHeldSets` import in the fixture exists in `core/testing`.

## Low / Nits

- `CatalogUiState.kt:134` runs `heldFilms()` (a sort plus a catalogue scan per held episode id) on every recomposition of `LibraryFlow.kt:92` and `tvMenuActions`. It is not wrapped in `remember`, unlike the Preloads frames (`remember(catalogState)`). The cost is small, but a `remember(catalogState, queueCount)` would match the neighbouring code.
- Stale docs still say "Preloads · n" or "gate for showing it at all": `TitlePreloadViewModel.kt:62`, `LibraryPositions.kt:242`, `TvMenuPage.kt:36`. Comment only.

Not run: the build and the tests (read-only review).
