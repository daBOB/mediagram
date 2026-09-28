# Phase 03 — Documentaries department

## Context links

- Web: `web/public/lib/documentaries.js:1-27` (`groupDocumentaries`,
  `countDocumentaries`), `web/public/lib/catalog/department-pages.js:74-124`
  (`renderDocumentariesDept`), `web/public/app.js:116-123` (counts; tally omits docs).
- Android: `core/model/.../MediaSet.kt:102-113` (`Kind`, no documentary),
  `core/data/.../CatalogRepository.kt:171-177` (`"docu"` falls to `Kind.MOVIE`),
  `feature/catalog/Shelves.kt:35-39` (`shelvesOf`: Movies, Series, Tutorials),
  `feature/catalog/CatalogTabs.kt:14-63` (records the gap on purpose, lines 43-45),
  `ui/catalog/CatalogScreen.kt:157-211` (tab routing by title),
  `ui/catalog/DepartmentHero.kt`, the Tutorials `ShowsDepartment` grouping.
- Rust spelling: `crates/mlib-spec/src/kind_spelling.rs:19` (`docu`).

## Overview

Priority P2 · pending. Android files every documentary under Movies (the tablet's
"Recently added · 935" = web's 911 films + 24 documentaries). Give documentaries their
own `Kind`, shelf, tab and department page, as the web does. Removes the recorded
difference in `CatalogTabs.kt:43-45`.

## Requirements

- `Kind.DOCUMENTARY` for index kind `docu`; everything that switches on `Kind` handles
  it (exhaustive `when`s will point the way). Playback/resume treat it like a film.
- Grouping mirrors `groupDocumentaries`: sets with a show/collection id group into
  collections exactly like courses do; the rest are singles, sorted by title.
  Count = sum of collection counts + singles.
- Tab order: Home, Movies, Series, **Documentaries**, Tutorials, Collections; count in
  the pill bar. Hidden when empty? The web never hides a department (shows "0") —
  match it.
- Movies count, Movies wall, Recently Added count and the home's film picks exclude
  documentaries (check `EditorialPicks`: the web's cover/features draw from films —
  confirm whether it includes `docu`, and match).
- Department page = `renderDocumentariesDept`: `DepartmentHero` with kicker
  "Only in your library", title "Documentaries", line "24 documentaries" (spelled
  count helper), lead = most recently added with a backdrop; then "Continue watching"
  (documentaries only, if any), "Recently added" row (≤ ROW), one row per collection
  ("All N" → that collection's page, like a course/show), then "Standalone
  documentaries" (≤ ROW). Empty library → the same empty state other departments use.
- Tally stays films/shows/courses (web omits documentaries).

## Architecture

Data first (core/model, core/data, feature/catalog), then the ui-mobile page.
TV shares `shelvesOf`, `catalogTabsOf`, `mastheadSplitOf`: TV gets the tab too. TV's
kept-tab indices must derive from `firstKept` (verify `TvCatalogScreen.kt:103-105`);
TV routes tabs by title — give Documentaries TV's generic department wall if one
exists, else keep it out of TV's split with a comment naming the TV home plan as where
it lands. Do not build a TV page here.

## Related code files

- Modify: `core/model/MediaSet.kt`, `core/data/CatalogRepository.kt`,
  `feature/catalog/{Shelves,CatalogTabs,Departments,LibraryTally?}.kt`,
  `ui/catalog/CatalogScreen.kt` (routing), phase 01's `ChromeCounts`.
- Create: `feature/catalog/Documentaries.kt` (grouping + count, pure, tested),
  `ui/catalog/DocumentariesDepartment.kt`.
- Tests: `CatalogTabsTest`, `LibraryTallyTest` (`"Documentaries"` → empty at :42 still
  holds), new `DocumentariesTest`, a Robolectric test for the page, TV tests.

## Implementation steps

1. `Kind.DOCUMENTARY` + mapping; fix exhaustive `when`s.
2. `Documentaries.kt` grouping/count + tests (fixture: a folder of three + two singles).
3. Shelf + tab + counts; Movies/Recently Added exclude docs.
4. Department page + test.
5. `:core:data`, `:feature:catalog`, `:ui-mobile`, `:ui-tv` unit tests green;
   `RealCoreContractTest` untouched (no core change).
6. Tablet: Movies shows 911, Documentaries 24, page rows match the web's
   `#/documentaries` in the stub preview (`cd web && bun run preview`).
7. Minor bump (0.71.0), changelog, commit.

## Todo

- [x] Kind + mapping
- [x] grouping + count + tests
- [x] shelf, tab, counts; films exclude docs
- [x] department page + test
- [x] TV builds, tests green, no TV page added
- [x] tablet check vs web
- [x] 0.71.0, changelog, commit (bumped; not committed — orchestrator's call)

## Success criteria

Tablet pill bar reads Movies 911 · Documentaries 24 (same as the web on the same
index); the Documentaries page shows the web's rows in the web's order.

## Risks

- Watch-state keys are by set id, not kind — re-kinding must not orphan progress
  (verify: nothing keys on kind).
- Persisted UI state that stores a tab *index* (e.g. `rememberSaveable` chosen tab)
  shifts by one after the new tab: restore by title or reset on mismatch.

## Security

None.
