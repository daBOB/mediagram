---
phase: 4
title: "Android: the Anime shelf on phone, tablet and TV"
status: completed
priority: P2
effort: 7h
dependencies: [phase-02, phase-03]
---

# Phase 4 — Android: the Anime shelf on phone, tablet and TV

## Overview

Port phase 2's decisions to Android over phase 3's `MediaSet.anime`. The web is
the reference: same shelf position, same membership, same exclude/include table,
same rows on the department page, same search group, same hidden-when-empty
rule. One deliberate difference: the TV draws Anime as its plain poster wall, as
it already does Documentaries.

## Requirements

- `shelvesOf` pulls anime out first; shelf order **Movies, Series, Anime,
  Documentaries, Tutorials** (web nav order); Anime omitted when empty (like
  Movies/Series/Tutorials, `Shelves.kt:55-63`).
- Anime entries: anime series as `Entry.Collection` (`CollectionKind.SHOW`, so
  season pages, autoplay and Similar-shows work unchanged) keyed `ANIME/<name>`,
  then anime films as `Entry.Film`.
- The exclude/include table from phase 2, per call site below.
- Phone/tablet department page: hero, Continue (resume + next episode, anime
  only), Series (every anime series), Films (every anime film, newest first).
- Search: `SearchFilter.ANIME` group between Series and Documentaries.
- Kids profiles: `CatalogViewModel.project` filters before `shelvesOf`
  (`CatalogViewModel.kt:167`), so Anime is filtered like every shelf; a kids
  profile with no visible anime gets no Anime tab (test it).

## Architecture

```
CatalogRepository.sets() ─> MediaSet.anime ─> (kids filter, CatalogViewModel) ─> shelvesOf
   shelvesOf: (anime, rest) = sets.partition { it.anime }
      rest ─> Movies · Series · Documentaries · Tutorials   (unchanged rules)
      anime ─> groupAnime ─> Shelf(ANIME, shows + films).takeIf { isNotEmpty }
phone:  CatalogScreen when(shelf.title) ANIME ─> AnimeDepartment(animeDepartmentOf(...))
TV:     DepartmentOrShelfWall ANIME ─> TvShelfWall (deliberate)
```

**Call sites (from the Android inventory, verified):**

| Site | Today | Change |
|---|---|---|
| `feature/catalog/.../TitlePageCandidates.kt:16-17` `filmsOf(shelves)` (film Similar `TitleDetailScreen.kt:184`, franchise `:85`) | Movies shelf | Rename `everyFilm(shelves)` = Movies + Anime films (web `everyFilm`) |
| `ui-mobile/.../catalog/SearchScreen.kt:182-184` `moviesOf` (search franchises) | Movies shelf | `everyFilm` |
| `ui-mobile/.../LibraryBrowseBranches.kt:181-183` `moviesOf` (franchise page) | Movies shelf | `everyFilm` |
| `ui-mobile/.../catalog/CatalogScreen.kt:209` (Collections tab franchises) | Movies shelf | `everyFilm` |
| `ui-tv/.../catalog/TvCatalogBody.kt:95-100` (Collections tab franchises) | Movies shelf | `everyFilm` |
| `ui-tv/.../catalog/TvSearch.kt:114-120` (search franchises) | Movies shelf | `everyFilm` |
| `ui-tv/.../TvLibrary.kt:86-88` `allFilms` (Similar, title franchise, franchise page) | Movies shelf | `everyFilm`; **but** `FrameKind.MOVIES_PAGE` (`:132`) gets the Movies shelf's films only |
| `ui-mobile/.../LibraryBrowseBranches.kt:122` "All N films" wall | Movies shelf | unchanged (department view) |
| `feature/catalog/.../MagazineHome.kt:41` editorial picks + Recently added | Movies shelf | unchanged (web draws picks from Movies) |
| `feature/catalog/.../HomeShelves.kt:103` Latest rows | skips Documentaries | also skip `ANIME` (web has no "Latest anime"; covers phone `LatestScreen.kt:25` and TV `TvLatestPage.kt:46`, `TvCatalogBody.kt:56`) |
| `feature/catalog/.../HomeShelves.kt:124-125` Next up collections | all but Documentaries | unchanged — anime series feed Next up, as on web |
| `feature/catalog/.../RunFor.kt:17-33` autoplay run | every shelf | unchanged — works for `ANIME/` collections |
| `feature/catalog/.../Departments.kt:128-130` Series Continue | filters by kind only | `&& !set.anime` on continues and next up (web fix in phase 2) |
| `feature/catalog/.../GenreShelf.kt`, `GenreIndex.kt`, `PersonPage.kt`, `SeriesPageState.kt` similarShows | every shelf | unchanged — already include anime, matching web's phase-2 lookups |
| `feature/catalog/.../SearchGroups.kt:9,47-53,71-80` | split by kind | split on `anime` first; `ANIME` filter + `animeFilms`, `animeShows`, `animeEpisodes` |
| `ui-mobile/.../catalog/SearchGroupsView.kt:69-100,130-138`; `ui-tv/.../catalog/TvSearchGroups.kt:34-43,54-79` | exhaustive `when` | Anime label + sections ("Anime films", "Anime series", "Anime episodes") |
| `ui-mobile/.../chrome/ChromeCounts.kt:53` | `entries.size` | unchanged — counts titles, as the web does |
| `feature/catalog/.../LibraryTally.kt:34` | unknown shelves dropped | unchanged — web's rail masthead lists no Anime either |

**TV difference (deliberate, written down):** `TvDepartmentPages.kt:79-108` has
department pages for Movies and Series/Tutorials; Documentaries falls through to
`TvShelfWall`. Anime does the same — a wall of show and film posters — because
the Shows branch (`:99-106`) would drop every film (`filterIsInstance<Entry.Collection>()`)
and a TV department page with a Continue row duplicates the TV home's own
Continue. Comment at the new branch + `docs/web-player.md` "Differences from
Android" (:422). A TV hero page can follow if wanted.

## Related Code Files

**Create**
- `android/feature/catalog/src/main/kotlin/Anime.kt` — `const val ANIME = "Anime"`;
  `data class AnimeLibrary(val shows: List<Entry.Collection>, val films: List<MediaSet>)`;
  `groupAnime(sets)` (episodes → `collections(…, CollectionKind.SHOW, "Unknown show")`
  re-keyed `ANIME/${name}` — the `Documentaries.kt:23-29` reason: `CatalogUiState.collection(key)`
  scans every shelf, and a live-action and an anime show can share a name; films by
  title, `NATURAL`); `AnimeDepartment` + `animeDepartmentOf(library, byId, watch)`
  (lead by popularity with a backdrop among unwatched films and series leads;
  `underwayOf(library.shows, byId, watch, 12)` narrowed to `anime`; films newest first).
- `android/ui-mobile/src/main/kotlin/ui/catalog/AnimeDepartmentScreen.kt` —
  `AnimeDepartment(shelf, state, columns, onOpenTitle, onOpenCollection, gridState, onPlay)`
  wrapper + screen, a `LazyVerticalGrid` modelled on `ShowsDepartmentScreen.kt:86-125`:
  `DepartmentHero` (kicker "Only in your library", title "Anime", line "four shows · 31 films"),
  "Continue watching" `ResumeStrip`, "Series" `EntryCard`s, "Films" `EntryCard`s; test tag
  `ANIME_DEPT_TEST_TAG`.
- `android/feature/catalog/src/test/kotlin/AnimeTest.kt`; `android/ui-mobile/src/test/kotlin/ui/catalog/AnimeDepartmentScreenTest.kt`
  (mirror `DocumentariesDepartmentScreenTest.kt`).

**Modify**
- `android/feature/catalog/src/main/kotlin/Shelves.kt` — partition, Anime shelf
  after Series (:55-63), class doc (:9-16 list of special cases, :26-33 rule) names Anime and the hide-when-empty reason.
- `android/feature/catalog/src/main/kotlin/TitlePageCandidates.kt` — `filmsOf` → `everyFilm` (+ callers `TitleDetailScreen.kt:22,85,184`).
- `android/feature/catalog/src/main/kotlin/HomeShelves.kt` — skip `ANIME` at :103 (comment: web draws no Latest anime).
- `android/feature/catalog/src/main/kotlin/Departments.kt` — Series Continue filter (:128-130).
- `android/feature/catalog/src/main/kotlin/SearchGroups.kt` — enum, fields, split, filters.
- `android/ui-mobile/src/main/kotlin/ui/catalog/CatalogScreen.kt` — `ANIME ->` branch (:222-246); `:209` → `everyFilm`.
- `android/ui-mobile/src/main/kotlin/ui/HeroArtOf.kt` — `ANIME ->` lead backdrop (:32-48).
- `android/ui-mobile/src/main/kotlin/ui/LibraryBranchSupport.kt` — `ANIME` → `deptScroll.anime` (:106-114).
- `android/ui-mobile/src/main/kotlin/ui/catalog/DepartmentScrollStates.kt` — `anime: LazyGridState` (:19-31).
- `android/ui-mobile/src/main/kotlin/ui/catalog/SearchScreen.kt`, `LibraryBrowseBranches.kt` — `moviesOf` → `everyFilm` (delete both private copies).
- `android/ui-mobile/src/main/kotlin/ui/catalog/SearchGroupsView.kt` — Anime sections + label.
- `android/ui-tv/src/main/kotlin/ui/tv/catalog/TvDepartmentPages.kt` — `else if (shelf.title == ANIME)` → wall, with the reason (:87-108).
- `android/ui-tv/src/main/kotlin/ui/tv/TvLibrary.kt` — `allFilms` → `everyFilm(shelves)`; `MOVIES_PAGE` (:132) → Movies shelf films.
- `android/ui-tv/src/main/kotlin/ui/tv/catalog/TvCatalogBody.kt`, `TvSearch.kt`, `TvSearchGroups.kt` — `everyFilm`, Anime search sections.
- Tests (add cases): `ShelvesTest.kt` (anime split, order, omitted when empty, `ANIME/` key, never in Movies/Series),
  `CatalogTabsTest.kt:22` (tab order), `SearchGroupsTest.kt` (group + chip), `HomeShelvesTest.kt`
  (no Latest Anime row; Next up offers an anime series), `RunForTest.kt` (anime episode run),
  `TitlePageCandidatesTest.kt` (everyFilm), `DepartmentsTest.kt` (Series Continue drops anime),
  `CatalogViewModelTest.kt` (~:811, kids profile + anime), `ChromeCountsTest.kt`,
  `TvDepartmentPagesStateTest.kt:81` (anime → wall), `TvSearchResultsStateTest.kt`.
- Docs: `docs/web-player.md` "Differences from Android" (TV wall); `docs/project-changelog.md`; minor version bump.

**Delete** — the two private `moviesOf` copies (`SearchScreen.kt:182-184`, `LibraryBrowseBranches.kt:181-183`).

## Implementation Steps

1. `Anime.kt` (`groupAnime`, `animeDepartmentOf`) + `AnimeTest.kt`.
2. `shelvesOf` partition + `ShelvesTest`/`CatalogTabsTest` cases.
3. `everyFilm` rename and the six film-pool call sites; TV `MOVIES_PAGE` keeps Movies only.
4. `HomeShelves.kt:103` skip; Series Continue filter; tests.
5. Search groups (feature + both UIs) + tests.
6. Phone department screen, routing, hero art, scroll state + screen test.
7. TV branch + state test.
8. `scripts/check.sh` green.
9. Device check — **read-only walk, no playback, no settings, no profile changes**
   (household-wide sync, see `.claude/agent-memory/planner/profile-data-spreads-household-wide.md`):
   1. `scripts/build-android-core.sh` (fresh `.so`).
   2. Tablet: `cd android && ANDROID_SERIAL=caad49da ./gradlew installDebug`.
      Anime tab after Series with the web's count; hero, Continue (if any), 4 series, films;
      open Dragonball → seasons; Movies/Series no longer list anime; search "Totoro" → Anime group.
   3. TV box: `installBenchmark` on `192.168.0.35:5555` (+ `compile -m speed`), navigate
      to the Anime wall with the remote only — never press OK on a settings row.
   4. Screenshots to the user.
10. Docs, changelog, minor bump; commit.

## Success Criteria

- [x] Anime tab absent on a library/profile with none (`ShelvesTest`, `CatalogViewModelTest`); count matches the shelf's own entries the same way every other shelf's pill does (`ChromeCountsTest`) — verified by unit test, not against a published index (no device install in this run, see below).
- [x] No anime title on Movies/Series tabs, their department rows, "All films", Latest or the magazine picks; present in search, genre, person, franchise, Similar; Next up and autoplay continue an anime series.
- [x] Series department Continue shows no anime episode.
- [x] TV shows the Anime wall; the difference is commented in `TvDepartmentPages.kt` and in `docs/web-player.md`.
- [x] `scripts/check.sh` green.
- [ ] Device walk screenshots — **not done**: this run's hard limits forbid `adb`/device installs; step 9 was skipped entirely. Left for a manual pass.

## Risk Assessment

| Risk | L×I | Mitigation |
|---|---|---|
| A Movies-shelf reader missed → anime film loses franchise/Similar on one screen | M×M | All eight readers listed from the inventory (`grep 'title == "Movies"'`); one `everyFilm` helper replaces five duplicates. |
| Exhaustive `when` on `SearchFilter` breaks two UIs | — | Compile error, not a silent gap: fixed in step 5. |
| Phone tab restore keyed by title (`LibraryFlowBranches.kt:87-90`) lands on a different tab when Anime appears/disappears | L×L | Keyed by title, not index — restoring "Series" still finds Series. Covered by `RestoredTabIndexTest`. |
| Stale `.so` on device | M×H | Step 9.1 always rebuilds first. |
| Overlap with the pending profile-roles plan (`CatalogScreen.kt`, `CatalogViewModel`) | M×L | Rebase whichever lands second; our `CatalogScreen.kt` change is one `when` branch and one line. |

**Rollback:** revert the commit; `MediaSet.anime` stays (phase 3) and is ignored.

## Security Considerations

None new. Device checks are read-only by rule (no profile, PIN, admin or settings writes).
