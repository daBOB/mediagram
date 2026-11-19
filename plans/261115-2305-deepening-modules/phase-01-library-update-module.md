# Phase 01 — Library update module (Android, media line)

## Problem

`CatalogRepository.refresh()` works out Updated vs AlreadyCurrent
(`CatalogRepository.kt:131-139`) but returns only `Result<Int>`, so
`LibraryUpdateCoordinator` reads it back from `RefreshLog.last()` right after.
Its constructor defaults `refreshes = RefreshLog()`, so six test fixtures
(`TvAppFixture.kt:250`, `LibraryFlowFixture.kt:130`,
`FailureDiagnosticsTest.kt:197`, `CatalogViewModelTest.kt:64`,
`CatalogTogetherTest.kt:68`, `CatalogCastHouseholdTest.kt:47`) hold a log
nothing writes, and a start-up read in them never fetches artwork. The view
model drives the update through three callbacks it must order under the lock
(`CatalogViewModel.kt:217-301`); the update's state is split between
`coordinator.refreshing` and `CatalogEnrichmentFetcher.state`, and both menus
stitch them back with `updateDisabledReason(...)` (`LibraryFlow.kt:108`,
`TvMenuBranches.kt:56`, `TvCatalogScreen.kt:284`). `CatalogViewModel.showFetched`
and `FetchViewModel.fetch` have no callers outside tests.

Reference shape: the web's `CatalogFollower` (`web/src/application/catalog-follow.ts`):
one refresh, a typed outcome, posters fetched after every new list.

## Decided

- One module owns the library update: refresh → the fetch rule → artwork fetch
  → re-read of the installed sets. Interface: `update(kind)`, the installed
  library as a flow (changes on a new index or new artwork; carries a failed
  refresh so the view model can say so), one update state (reading; fetching
  with progress; last result).
- `refresh()` returns its outcome and keeps recording to `RefreshLog` for the
  System screen (`SystemViewModel.kt:116`, its only reader).
- The fetch rule does not change: Read fetches only when it installed a newer
  index; Published fetches unless the refresh failed; Manual always fetches.
  Quiet for Read and Published.
- `CatalogViewModel` collects the library and regroups (keeping shelves when
  the sets are unchanged, as `readCatalog` does now); the menus read the one
  state. Cold start still shows what is installed before a refresh downloads.
- Delete: the three callbacks, `showFetched`, `FetchViewModel.fetch` (keep
  `FetchViewModel` for the TMDB key screen), the `RefreshLog()` default.
- The fake repository answers AlreadyCurrent unless a test says Updated.
- Name in code follows the glossary term: library update.

## Steps

- [x] `refresh()` returns the outcome (plus the count, if anything reads it).
- [x] New module in `android/core/data` replacing `LibraryUpdateCoordinator`.
- [x] `CatalogViewModel` collects; callbacks gone; menus read one state.
- [x] Tests through the module's interface replace `LibraryUpdateCoordinatorTest`
      (same behaviours: read without update, read with update, manual on
      failure, published failure, artwork re-read only when posters/backdrops
      came, backdrop-only, cancellation in refresh and fetch, serialized updates,
      installed shown before download). Delete tests of removed members.
- [x] Fixtures construct the module with the repository's own log path.
- [x] Gradle: `:core:data`, `:feature:catalog`, `:feature:system`, `:ui-tv`,
      `:ui-mobile` unit tests, `:app:hiltJavaCompileDebug`, detekt/spotless as
      `scripts/check.sh` runs them.
- [x] Replace learning l-bfdf000e (`trail note --replaces`) if its file paths move.
- [x] Media patch bump; commit.

## Done when

No caller reads `RefreshLog` except the System screen; no callbacks cross the
seam; the fetch rule's tests use a fake repository's outcome, not a log.
