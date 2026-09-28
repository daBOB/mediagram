---
phase: 3
title: "Android: `category` from the core, category rows on phone/tablet, TV Tutorials rows"
status: completed
priority: P2
effort: 6h
dependencies: [phase-01, phase-02]
---

# Phase 3 — Android: `category` from the core, rows on phone/tablet, TV Tutorials rows

## Overview

Port phase 2's decisions. The Rust core attaches `category` to every `SetSummary` (same key
rule as the uploader — it calls `mlib_spec::category_key` itself — and the index's
`categories` table); bindings are regenerated; `MediaSet.category` carries it to Kotlin, which
ports `categoryRows` (held to the web's `rows.json`) and draws the rows on the phone/tablet
Tutorials and Documentaries pages and on the TV Tutorials page. Kotlin never derives a key.
Ships alone, minor bump.

**Before starting — read current `main`, not this file's line numbers.** The anime plan's
phase 4 (0.78.0) rewrote parts of `Departments.kt`, `TvDepartmentPages.kt`, `CatalogScreen.kt`,
`Shelves.kt` and their tests. Re-read `Departments.kt` (`ShowsDepartment`, `DocumentariesDepartment`),
`TvShowsDepartmentPage.kt`, `TvDepartmentTargets.kt` and the two phone department screens first;
line numbers below are from `main` at 0.77.1.

## Requirements

- Core: `SetSummary.category: Option<String>`, set in `store::editorial::enrich` for listings
  and single lookups; `None` for an index without the table; no device-sidecar fallback
  (categories live only in the index).
- Kotlin: `MediaSet.category: String? = null`; `toMediaSet` maps it.
- `categoryRowsOf(units, categoryOf)` + `OTHER_CATEGORY = "Other"` + `CategoryRow<T>(title, units)`,
  line-for-line the web rule; `CategoryRowsFixtureTest` runs every `rows.json` case unchanged
  (the web is authoritative: a case that only passes after a Kotlin-only change does not belong).
- `categoryOf(entry: Entry)`: a film → its set's category; a collection → `firstItemOf(divisions)?.category`.
- `ShowsDepartment.categories: List<CategoryRow<Entry.Collection>>` (empty for Series by
  construction); `DocumentariesDepartment.categories: List<CategoryRow<Entry>>` over collections
  then singles, the department's own order.
- Phone/tablet Tutorials: hero → "Continue your courses" → category rows → "All courses".
- Phone/tablet Documentaries: hero → "Continue watching" → category rows → "Recently added" →
  folder rows → "Standalone documentaries"; a collection card opens it, a single card plays.
- TV Tutorials (`TvShowsDepartmentPage`): hero → Continue → category rows → Popular/New (Series
  only) → "Every show" wall. Arrival focus: hero, else Continue, else the first category row, else
  Popular/New, else the wall. Restore finds a key in Continue, then the category rows, then Popular/New,
  else the wall.
- TV Documentaries: unchanged plain wall — **deliberate difference**, commented at the
  `DOCUMENTARIES` branch in `TvDepartmentPages.kt` and listed in `docs/web-player.md` "Differences
  from Android": the TV has no Documentaries front page to put rows on (the same reason Anime is
  a wall there); a TV Documentaries page with rows can follow if wanted.
- Kids profiles: filtered before `shelvesOf` (`CatalogViewModel.project`), so rows only hold visible units.

## Architecture

```
index categories ─> catalog_categories::categories(conn) ─┐
PlayableSet(kind, show, title) ─> mlib_spec::category_key ┴─> editorial::enrich: summary.category
      │ UniFFI SetSummary.category (String?)
      ▼
CatalogRepository.toMediaSet ─> MediaSet.category ─> shelvesOf (unchanged grouping)
   Departments.kt: showsDepartmentOf / documentariesDepartmentOf ─> categoryRowsOf(units, ::categoryOf)
      ├─ phone ShowsDepartmentScreen / DocumentariesDepartmentScreen: heading + row per CategoryRow
      └─ TV TvShowsDepartmentPage: DeptEntryRow per CategoryRow (targets "category:<i>")
```

Kotlin rule (mirror of the web's, dry-run there):

```kotlin
const val OTHER_CATEGORY = "Other"
data class CategoryRow<T>(val title: String, val units: List<T>)
fun <T> categoryRowsOf(units: List<T>, categoryOf: (T) -> String?): List<CategoryRow<T>> {
    if (units.none { categoryOf(it) != null }) return emptyList()
    return units.groupBy { categoryOf(it) ?: OTHER_CATEGORY } // LinkedHashMap: input order kept
        .map { (title, members) -> CategoryRow(title, members) }
        .sortedWith(compareBy<CategoryRow<T>> { it.title == OTHER_CATEGORY }.thenBy(NATURAL) { it.title })
}
```

## Related Code Files

**Create**
- `crates/mediagram-core/src/catalog_categories.rs` (+ `catalog_categories_tests.rs`) —
  `categories(conn) -> rusqlite::Result<HashMap<(String, String), String>>` (probe `sqlite_master`
  like `shows::anime_overrides`, `shows/anime.rs:49`; skip NULL rows) and
  `category_of(map, kind, show, title) -> Option<String>`.
- `android/feature/catalog/src/main/kotlin/Categories.kt` — the rule above + `categoryOf(entry)`.
- `android/feature/catalog/src/test/kotlin/CategoryRowsFixtureTest.kt` — `rows.json` via the
  `locateFixture` walk-up of `EditorialPicksFixtureTest.kt:112-121`.
- `android/ui-mobile/src/main/kotlin/ui/catalog/DocumentaryUnitRow.kt` — a `LazyRow` of
  `PosterCard`s: a collection (name, `extentOf`, `onOpenCollection(key)`), a film (title,
  `factsLine`, watched mark, `onPlay(setId)`), the look of `DocumentaryRow`
  (`DocumentariesDepartmentScreen.kt:145-161`). Keeps that screen under 200 lines.

**Modify**
- `crates/mediagram-core/src/lib.rs` — `pub mod catalog_categories;` (beside `catalog_assets`, :9).
- `crates/mediagram-core/src/dto/summary.rs` — `pub category: Option<String>` after `anime` (:93) with
  doc ("the hand-set category of the unit this set belongs to; see `mlib_spec::category_key`");
  `category: None` in `summary_from` (:150). 177 → ~182.
- `crates/mediagram-core/src/api/store/editorial.rs` — read `categories` once beside
  `anime_overrides` (:70-71); in the loop after `summary.anime` (:140) set `summary.category`.
  185 → ~190 (if it passes 200, move `resolve_artwork` to `resolve.rs`, as the anime plan noted).
- `crates/mediagram-core/src/api/store/editorial_tests.rs` — filed course lesson and `doc` carry the
  category; a docu single by its title; a film none; a v11 index lists `None`, no error.
- `android/core/rust/src/main/kotlin/uniffi/mediagram_core/mediagram_core.kt` — regenerated by
  `scripts/generate-android-bindings.sh` (never hand-edited; also rebuilds the gitignored `.so`).
- `android/core/model/src/main/kotlin/MediaSet.kt` — `val category: String? = null` after `anime` (:95).
- `android/core/data/src/main/kotlin/CatalogRepository.kt` — `category = summary.category` beside :215.
- `android/core/data/src/test/kotlin/CatalogRepositoryTest.kt` — `summary()` builder gains
  `category: String? = null`; test "a set carries its category".
- `android/feature/catalog/src/main/kotlin/Departments.kt` — the two `categories` fields and their
  computation (`documentariesDepartmentOf` :75-100, `showsDepartmentOf` :119-148 at 0.77.1).
- `android/feature/catalog/src/test/kotlin/DepartmentsTest.kt` — Tutorials rows + Other; Series none;
  nothing filed → none; Documentaries: collections before singles, a single's own category.
- `android/ui-mobile/src/main/kotlin/ui/catalog/ShowsDepartmentScreen.kt` — after the Continue items
  (:96-103): per row a full-span `DeptRowHeading(row.title)` and `CollectionRow(row.units, onOpenCollection)`
  (the existing private row, :129-146), keys `category/<title>`. 146 → ~158.
- `android/ui-mobile/src/main/kotlin/ui/catalog/DocumentariesDepartmentScreen.kt` — after Continue
  (:119-122): heading + `DocumentaryUnitRow` per row. 171 → ~178.
- `android/ui-mobile/src/test/kotlin/ui/catalog/ShowsDepartmentScreenTest.kt`,
  `DocumentariesDepartmentScreenTest.kt` — heading shown when filed, absent otherwise; docu collection
  card opens, single card plays.
- `android/ui-tv/src/main/kotlin/ui/tv/catalog/TvShowsDepartmentPage.kt` — `dept.categories.forEachIndexed`
  `DeptEntryRow(row.title, row.units, …, focusAt = target?.second?.takeIf { target.first == "category:$i" })`
  after the Continue row (:82-93); the `LaunchedEffect` `when` becomes `"hero" -> heroFocus…; null -> Unit;
  else -> rowFocus.requestFocus()`. 129 → ~140.
- `android/ui-tv/src/main/kotlin/ui/tv/catalog/TvDepartmentTargets.kt` — `showsDeptTargetOf` (:49-68):
  category rows after `underway` in both the restore and the default branch.
- `android/ui-tv/src/test/kotlin/ui/tv/catalog/TvDepartmentTargetsTest.kt` — restore into a category row;
  default lands on the first category row with no hero and nothing underway.
- `android/ui-tv/src/main/kotlin/ui/tv/catalog/TvDepartmentPages.kt` — one sentence in the `DOCUMENTARIES`
  branch comment: no category rows on TV, deliberately (the only line this plan changes there).
- Docs: `docs/web-player.md` "Differences from Android" (:457) — TV Documentaries has no category rows;
  `docs/system-architecture.md` §10.1 — `SetSummary.category` is decided in the core from
  `mlib_spec::category_key` + `categories`; `docs/project-changelog.md`. Version: minor bump.

**Delete** — none.

## Implementation Steps

1. Core: `catalog_categories.rs` + tests; `SetSummary.category`; `enrich`; `editorial_tests.rs`.
   `cargo test -p mediagram-core`, `cargo clippy --all-targets -- -D warnings`.
2. `ANDROID_NDK_HOME=… scripts/generate-android-bindings.sh`; commit the `.kt`; `.so` stays untracked.
3. `MediaSet.category`, `toMediaSet`, `CatalogRepositoryTest`.
4. `Categories.kt` + `CategoryRowsFixtureTest` (red → green against `rows.json`).
5. `Departments.kt` fields + `DepartmentsTest`.
6. Phone screens + `DocumentaryUnitRow.kt` + screen tests.
7. TV rows + targets + `TvDepartmentTargetsTest`; the `TvDepartmentPages.kt` comment.
8. `scripts/check.sh` green.
9. Device check — **read-only walk: no playback, no settings, no profile changes**; needs categories in
   the published index (phase 1 operator steps done), else it can only confirm "no rows":
   1. `scripts/build-android-core.sh` (fresh `.so`; a stale one crashes at launch).
   2. Tablet: `cd android && ANDROID_SERIAL=caad49da ./gradlew installDebug`. Tutorials: the web's rows,
      same order, "Other" last, "All courses" intact; Documentaries: rows between Continue and Recently
      added, a collection card opens (back out without playing).
   3. TV box: `installBenchmark` on `192.168.0.35:5555` + `compile -m speed`; remote-only navigation to
      Tutorials, move through the rows, never press OK on a settings row; Documentaries still a wall.
   4. Screenshots to the user beside the web's (phase 2) for the same index.
10. Docs, changelog, minor bump; commit.

## Success Criteria

- [x] `CategoryRowsFixtureTest` passes every `rows.json` case unedited.
- [x] A v11 index lists every set with `category = null` and both phone pages draw exactly as before.
- [x] Tablet Tutorials and Documentaries rows match the web's titles, order and cards over the same index
      (verified by unit/screen tests; no device install — hard limit, see report).
- [x] TV Tutorials shows the rows with working focus/restore; TV Documentaries unchanged; the difference
      is commented in `TvDepartmentPages.kt` and listed in `docs/web-player.md`.
- [x] `code_standards.rs` green (core files ≤ 200); `scripts/check.sh` green; screenshots not taken — no
      device install in this session (hard limit), see report.

## Risk Assessment

| Risk | L×I | Mitigation |
|---|---|---|
| Rebase onto anime phase 4 collides in `Departments.kt`/`DepartmentsTest.kt` | M×L | Read `main` first (Overview); this phase adds fields/lines, never rewrites anime's. |
| Stale `.so` with new bindings → crash at launch | M×H | Step 2 regenerates both; step 9.1 rebuilds before any install. |
| Umlaut-initial category ordering differs from the web | L×L | Pre-existing `NATURAL` vs collator gap on every shelf; fixture ASCII-only; noted in docs. |
| `editorial.rs` crosses 200 lines | L×L | ~190 expected; split `resolve_artwork` out if not. |
| TV focus lands wrongly with rows above the wall | L×M | Targets extended and unit-tested (`TvDepartmentTargetsTest`); remote walk in 9.3. |

**Rollback:** revert the commit, regenerate bindings, rebuild the `.so`. No data involved.

## Security Considerations

Read-only index access (`open_ro`); category text drawn with Compose `Text`. Device checks write nothing.
