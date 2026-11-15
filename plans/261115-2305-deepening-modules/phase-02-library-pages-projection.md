# Phase 02 — Library pages projection (Android, media line)

## Problem

`android/feature/catalog` exports ~20 pure page functions ported from the web;
TV (`android/ui-tv`) and phone (`android/ui-mobile`) assemble pages inside
composables, separately. `magazineHomeOf` runs at `TvCatalogScreen.kt:189`,
`CatalogShelves.kt:101` and again at `LibraryBranchSupport.kt:91` (without
`recentLimit`; harmless since that call reads only `cover`, but the duplicate
the TV screen's comment warns about). `resumeCardsOf` is called from six
department screens; the Latest page's 48 is two private constants
(`TvLatestPage.kt:47`, `LatestScreen.kt:41`); the Continue/Watchlist walls are
`remember`ed on TV and not on the phone. Only next-up and resume point are
held to the web's fixtures.

## Decided

- One projection module turns the ready library, the profile's view, held ids
  and the time into page models: Home (magazine home + latest rows), Latest,
  the four Department pages (with resume cards and underway rows), Continue and
  Watchlist walls.
- Computed in `CatalogViewModel`, part of its Ready state, on the `shelving`
  dispatcher, whenever library, view or held ids change. Screens only render.
- No midnight recompute (no surface does one; the web takes `Date.now()` per
  render). The time is read when the inputs change.
- Shared, hand-written JSON fixtures in `web/test/fixtures/` (like
  `watch-state/next-up.json`), read by the web's pure functions
  (`homeShelves`, `homeEditorial`, `departmentUnderway`, `groupDepartments`)
  and by a Kotlin test of the projection via `testing.webFixture()`.
- The web's department hero/lead selection stays inside its render code for now.

## Steps

- [ ] Page model types + projection function in `feature/catalog`.
- [ ] Ready state carries the pages; computed off the main thread.
- [ ] TV and phone screens read page models; no screen calls `magazineHomeOf`,
      `*DepartmentOf`, `resumeCardsOf`, `latestOf`, `continueWall`,
      `watchlistWall` directly. One Latest limit.
- [ ] Shared fixture(s) + web test + Kotlin fixture test.
- [ ] Tests at the projection replace screen-level tests that only checked
      grouping; rule tests of the page functions stay.
- [ ] Gradle `:feature:catalog`, `:ui-tv`, `:ui-mobile` unit tests;
      `cd web && bun test`.
- [ ] Media patch bump; commit.

## Done when

A page's contents are decided in one module, TV and phone render the same
models, and one test holds the projection to the web's fixture.
