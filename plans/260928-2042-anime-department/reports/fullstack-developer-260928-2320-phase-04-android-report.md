# Phase 4 implementation report — Android: the Anime shelf on phone, tablet and TV

## Executed phase
- Phase: `phase-04-android-anime-department.md`
- Plan: `plans/260928-2042-anime-department`
- Worktree: `.claude/worktrees/agent-a815a40c7f2d404b6`
- Branch: `feat/anime-android`
- Status: completed

## Files changed

**Created**
- `android/feature/catalog/src/main/kotlin/Anime.kt` — `ANIME`, `AnimeLibrary`,
  `groupAnime`, `AnimeDepartment`, `animeDepartmentOf`.
- `android/ui-mobile/src/main/kotlin/ui/catalog/AnimeDepartmentScreen.kt` —
  `AnimeDepartment` wrapper + `AnimeDepartmentScreen` (`LazyVerticalGrid`: hero,
  Continue watching, Series, Films), `ANIME_DEPT_TEST_TAG`.
- `android/feature/catalog/src/test/kotlin/AnimeTest.kt`,
  `android/ui-mobile/src/test/kotlin/ui/catalog/AnimeDepartmentScreenTest.kt`.

**Modified (core: `feature/catalog`)**
- `Shelves.kt` — `shelvesOf` partitions `MediaSet.anime` out first; Anime shelf
  between Series and Documentaries, omitted at zero.
- `TitlePageCandidates.kt` — `filmsOf(shelves)` → `everyFilm(shelves)` (Movies +
  Anime films).
- `HomeShelves.kt` — Latest rows skip `ANIME` too (no "Latest anime", matching
  web).
- `Departments.kt` — `DEPARTMENT_ROW` made `internal`; Series department's
  Continue row now also filters `!set.anime` (kind alone let an anime episode
  leak in).
- `SearchGroups.kt` — `SearchFilter.ANIME` added between `SERIES` and
  `DOCUMENTARIES`; `SearchGroups` gained `animeFilms`/`matchedAnimeShows`/
  `animeEpisodes`; hits split on `anime` before the kind split.

**Modified (phone: `ui-mobile`)**
- `CatalogScreen.kt` — `ANIME ->` branch; Collections-tab franchise pool now
  `everyFilm(shelves)`.
- `HeroArtOf.kt` — `ANIME ->` lead backdrop via `animeDepartmentOf`.
- `LibraryBranchSupport.kt` — `ANIME -> deptScroll.anime` hero-scroll mapping.
- `DepartmentScrollStates.kt` — `anime: LazyGridState` added.
- `LibraryBrowseBranches.kt` — franchise-page film pool → `everyFilm`; deleted
  its private `moviesOf`.
- `SearchScreen.kt` — franchise pool → `everyFilm`; deleted its private
  `moviesOf`; empty-result check now also covers the three anime groups.
- `SearchGroupsView.kt` — "Anime films"/"Anime series"/"Anime episodes"
  sections + "Anime" filter-chip label.
- `TitleDetailScreen.kt` — Similar/franchise candidate pool → `everyFilm`.

**Modified (TV: `ui-tv`)**
- `TvDepartmentPages.kt` — `ANIME ->` falls through to the plain `TvShelfWall`,
  with the reason commented at the branch.
- `TvLibrary.kt` — `allFilms` (Similar/franchise pool) → `everyFilm(shelves)`;
  new `movieFilms` (Movies shelf only) feeds `MOVIES_PAGE`, unchanged.
- `TvCatalogBody.kt`, `TvSearch.kt` — Collections/search franchise pools →
  `everyFilm`.
- `TvSearchGroups.kt` — "Anime" label; "Anime films"/"Anime series"/"Anime
  episodes" sections.

**Tests (cases added to existing files)**
- `ShelvesTest.kt` — Anime's position and hide-at-zero, exclusivity from
  Movies/Series, `ANIME/`-prefixed key.
- `CatalogTabsTest.kt` — tab order with Anime present.
- `SearchGroupsTest.kt` — anime split before the kind split, one filter count
  for both anime films and episodes together.
- `HomeShelvesTest.kt` — no "Latest anime" row; an anime series feeds Next up.
- `RunForTest.kt` — an anime episode's run is found on the Anime shelf.
- `TitlePageCandidatesTest.kt` — `everyFilm` (renamed from `filmsOf`) collects
  Movies + Anime films; `showsOf` already collects an anime show unchanged.
- `DepartmentsTest.kt` — Series department's Continue row drops an anime
  episode sharing its kind.
- `CatalogViewModelTest.kt` — kids profile with no visible anime gets no Anime
  tab; a kids-rated anime title still gets one.
- `ChromeCountsTest.kt` — Anime's pill count reads like any other shelf.
- `TvDepartmentPagesStateTest.kt` — the Anime shelf draws the plain wall and
  keeps both its show and its film (what a department page would have
  dropped).

**Docs / version**
- `docs/web-player.md` — "## Anime" section gained the TV plain-wall note.
- `docs/project-changelog.md` — new `0.78.0` entry at the top.
- `Cargo.toml`, `Cargo.lock` (5 workspace crates), `web/package.json`,
  `android/app/build.gradle.kts` (`versionName`) — `0.77.1` → `0.78.0` (minor:
  new feature). `versionCode` untouched (18). `cargo metadata --locked
  --offline` succeeds.
- `plans/260928-2042-anime-department/phase-04-android-anime-department.md`,
  `plan.md` — status `completed`; plan's frontmatter `status: completed` (all
  four phases now done).

## Tests status
- `cargo clippy --all-targets --all-features -- -D warnings`: clean.
- `cargo test --all`: pass.
- `bun run lint` / `bun test`: pass.
- `./gradlew testDebugUnitTest :core:model:test lint :ui-tv:compileDebugAndroidTestKotlin :core:ffmpeg:compileDebugAndroidTestKotlin`: `BUILD SUCCESSFUL` — every module's unit tests green, including the new/extended ones above; lint baseline unchanged (1 pre-existing baselined error, 14 warnings, same as before this phase).
- `scripts/check.sh`: **all checks passed**, end to end.

## Native core / bindings
`.so` files for all four ABIs copied from the main checkout's
`android/core/rust/src/main/jniLibs/` before building, per instructions —
confirmed gitignored (`git check-ignore`), not staged, not committed. No
bindings regeneration needed: phase 3 already landed `MediaSet.anime` on
`android/core/model`/`android/core/data`, unchanged by this phase.

## Deviations from the phase file

- **No device install.** Hard limits for this run forbid `adb`/device
  installs. Step 9 (tablet + TV box walk, screenshots) was skipped entirely —
  not attempted, not simulated. Everything it would have checked visually is
  covered instead by the unit/Robolectric tests above (shelf membership,
  order, hide-at-zero, kids filtering, Continue exclusion, the TV wall
  keeping both a show and a film). The phase file's Success Criteria
  checkboxes are marked accordingly, with the device-walk one left open.
- **`docs/web-player.md`'s "Differences from Android" (:422) reference was
  stale.** That heading (now further down the file, after phase 2/3 growth)
  covers the System-menu/playback-stats plan, unrelated to departments. The
  Documentaries TV-wall precedent the phase cites as "already recorded" is
  not written down anywhere in this doc either — only in `TvDepartmentPages.kt`'s
  own comment. Followed the code: added the Anime TV-wall note to the doc's
  existing "## Anime" section instead (right after the paragraph that already
  says "Android follows the same rule" for the empty-tab behaviour), and left
  Documentaries' own doc gap alone — out of this phase's scope.
- **`DEPARTMENT_ROW` widened from `private` to `internal`** in `Departments.kt`
  so `animeDepartmentOf` (`Anime.kt`, same package) could reuse the same "12"
  the phase file names, rather than a second magic number.
- **`LibraryBrowseBranches.kt`'s franchise-pool read now goes through the
  shared `shelvesOrEmpty()` extension** (already in `LibraryBranchSupport.kt`,
  same package) instead of a second private `CatalogUiState -> List<Shelf>`
  unwrap — one fewer near-duplicate, no behavior change.
- Everything else matches the phase file's call-site table and architecture
  section as written; no other deviations.

## Left undone
Nothing within phase 4's scope. All four phases in this plan are now
`completed`.
