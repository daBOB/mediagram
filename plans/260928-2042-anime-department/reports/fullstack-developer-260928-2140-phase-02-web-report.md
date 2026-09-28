# Phase 2 implementation report — web: the rule, the shared fixture, and the Anime department

Branch `feat/anime-web`, created off local `main` at `fd487963` (0.76.0,
phase 1's schema-v11 commit — the worktree itself had been created from an
older point, so the branch was rebased onto `main`'s actual tip before any
work started). Steps 1–8 and 10 of
`phase-02-web-anime-department.md` done; step 9 (the manual preview walk)
done functionally rather than visually — see Deviations.

## Files changed

**Created**
- `web/src/catalog/anime.ts` — `isAnime(kind, genres, originalLanguage, forced)`,
  `animeOverrides(db)`.
- `web/test/fixtures/anime/cases.json` — the 10 shared cases.
- `web/test/anime-rule.test.ts`.
- `web/public/lib/departments.js` — `groupAnime`, `countAnime`, `everyFilm`,
  `everyShow`, `sectionForShow`, `groupDepartments`.
- `web/public/lib/departments.d.ts`.
- `web/public/lib/catalog/anime-department.js` — `renderAnimeDept` (69 lines).
- `web/test/departments.test.ts`.

**Modified (server)**
- `web/src/catalog/shows.ts` — `ProviderFacts.originalLanguage`; `optional()` gains
  `original_language`; select + map.
- `web/src/catalog/routes.ts` — `animeOverrides(db)` beside `provider`; `anime:` in
  `forBrowser`.
- `web/test/catalog-read-failures.test.ts` — `originalLanguage: null` on existing
  expectations; a v11 case.

**Modified (browser)**
- `web/public/lib/library-session.js` — `groupDepartments` replaces
  `groupLibrary`+`groupDocumentaries`; 198 lines (was 200).
- `web/public/lib/library.d.ts` — `CatalogSet.anime`, `Library.anime`.
- `web/public/lib/catalog/sections.js` — `anime` entry.
- `web/public/lib/catalog/shelf-view.js` — `UPLOAD_HINT.anime`; section unions;
  287 lines (ceiling raised from 285).
- `web/public/lib/address.js` / `address.d.ts` — `"anime"` in `KNOWN_SECTIONS`,
  `DepartmentSection`, `ShowSection`.
- `web/public/index.html` — `#nav-anime` link after Series, `hidden`.
- `web/public/app.js` — imports `renderAnimeDept`, `departments.js`; initial
  `library = groupDepartments([])`; `onData` sets `n-anime`/`nav-anime.hidden`;
  `everyFilm(library)` for Similar/franchise on the film page and in search;
  `animeShows` passed to search; `openShow` resolves via `sectionForShow`;
  `department`/`anime` route; show route picks the right collections dict and
  passes `pool: everyShow(library)`. 753 lines (ceiling raised from 742, by 11).
- `web/public/lib/catalog/department-pages.js` — `renderShowsDept`'s Continue
  filters gain `&& !set.anime` / `&& !entry.set.anime`.
- `web/public/lib/catalog/home-shelves.js` — Next up walks
  `library.anime.collections` too.
- `web/public/lib/playback/plays-next.js` — collection search adds
  `...library.anime.collections`.
- `web/public/lib/catalog/cast.js` — `titlesByKey` over `everyFilm`/`everyShow`.
- `web/public/lib/catalog/collections-page.js` — `franchisesIn(everyFilm(library))`.
- `web/public/lib/catalog/genres.js` — `genreShelf` over `everyFilm`/`everyShow`.
- `web/public/lib/catalog/utility-pages.js` — `titlesOf` over `everyFilm`/`everyShow`.
- `web/public/lib/catalog/search-view.js` — anime split before the kind split,
  "Anime" filter pill, "Anime films"/"Anime series"/"Anime episodes" parts,
  `animeShows` param. 199 lines.
- `web/public/lib/catalog/series-page.js` — `renderSeries`/`seriesPage` take
  `section` (used by the back link and `openSeason`) and `pool` (the Similar
  candidates, `everyShow(library)`, replacing the old `shelf` param); the
  Similar-tab opener stays hardcoded `"series"` on purpose (a result may be on
  either shelf; `openShow` is the one resolution point).
- `web/public/lib/catalog/film-page.js` — back link goes to Anime for
  `set.anime`.
- `web/test/support/catalog-set.ts` — default `anime: false`.
- `web/test/shelf-view.test.ts` — `emptyState("anime")` cases; the DOM stub
  gained a working `append`.
- `web/test/plays-next.test.ts`, `web/test/home-shelves.test.ts`,
  `web/test/shared-watch-state-fixtures.test.ts` — helpers switched to
  `groupDepartments`; two new anime cases added (the shared-fixtures file
  itself stayed fixture-only, no new case there).
- `web/test/address.test.ts` — `#/anime`, `#/anime/<show>`, `sectionOf`, the
  doc-table check.
- `web/test/genres.test.ts` — its hand-built `library` fixture gained an empty
  `anime` shelf (needed once `genreShelf` started reading it).
- `web/test/code-standards.test.ts` — `app.js` ceiling 742→753,
  `shelf-view.js` 285→287, both dated and reasoned.
- `docs/web-player.md` — routes table + a new "Anime" section (exclude/include
  table, the empty-tab rule).
- `docs/project-changelog.md` — new `0.77.0` entry.
- `Cargo.toml`, `web/package.json`, `android/app/build.gradle.kts` (`versionName`),
  `Cargo.lock` (5 workspace crates) — `0.76.0` → `0.77.0` (minor: new feature).
- `plans/260928-2042-anime-department/phase-02-web-anime-department.md`,
  `plan.md` — status `completed`.

## Tests added

- `anime-rule.test.ts`: all 10 fixture cases through `isAnime`; `animeOverrides`
  on no table, `NULL` rows, both true/false values.
- `departments.test.ts`: the split (film/show/mixed-with-plain), `groupAnime`,
  `countAnime` (titles not episodes), `everyFilm`/`everyShow`,
  `sectionForShow` (incl. a name shared by both shelves keeping the given
  section, and a non-series section passed through unchanged).
- `shelf-view.test.ts`: `emptyState("anime")`, kids and upload-hint paths.
- `plays-next.test.ts`: an anime episode plays the next one and preloads.
- `home-shelves.test.ts`: Next up offers an anime series the same way a plain
  one is offered.
- Existing suites (`catalog-read-failures`, `address`, `genres`) extended in
  place for the new field/section/shape.

## Deviations from the phase file

- **Worktree was behind `main`.** The agent's worktree branch started at
  `0.75.5`, before phase 1's commit. Rebased onto local `main` (`fd487963`,
  `0.76.0`) before any edit, per the task's own statement that phase 1 is
  already on main.
- **`series-page.js`'s `shelf` param renamed to `pool`.** The phase file kept
  calling it `shelf`; once the show-lookup dict and the Similar candidate pool
  are two different things (the lookup stays per-section, the pool is
  `everyShow(library)`), keeping one name for both would have hidden which is
  which. `renderSeries`'s Similar-tab opener was deliberately left calling
  `open("series", show, [])` unconditionally — not `open(section, …)` — since
  a Similar result can be on either shelf and `openShow` (app.js) is the one
  place that resolves a name onto Anime; the phase file did not call this
  distinction out explicitly.
- **Ceiling raises differ from the phase's estimate.** `app.js` needed +11
  lines (not "≤ 7"): the show-route's per-section collections lookup and the
  nav/count wiring came out longer than the phase's line-number guess
  implied. `shelf-view.js` needed +2, not mentioned in the phase at all — its
  JSDoc unions and the anime upload hint pushed it 2 lines past its existing
  285 ceiling. Both are dated, reasoned CEILINGS entries, the project's own
  pattern.
- **`shared-watch-state-fixtures.test.ts`** switched its helper to
  `groupDepartments` (needed once `homeShelves` reads `library.anime`) but
  gained no new *case* — that file runs fixtures shared with other languages,
  and it is not the place to add web-only behaviour.
- **Step 9 (manual preview walk) done functionally, not visually.** This
  agent has no browser/screenshot tool. Built a small synthetic v11 SQLite
  index (a plain film, an anime film, a plain show, a 2-episode anime show,
  plus an override flipping a non-Japanese film to anime) and ran
  `PREVIEW_INDEX=<fixture> bun run preview` against it (never the real data
  dir). Verified via `curl`: `/api/sets` carries the right `anime` flag per
  title including the override winning over the rule; `/api/search` returns
  `anime: true` hits for both the anime film and the anime series' episodes.
  This exercises the whole server-side pipeline this phase owns
  (`anime.ts` → `routes.ts` → the browser's own JSON); the browser-side
  rendering (hero, rows, nav count/hidden, `#/anime` routing) is covered by
  `departments.test.ts`, `anime-rule.test.ts`, `shelf-view.test.ts`,
  `address.test.ts` and the existing app.js/JS lint+typecheck, but was not
  walked in a real browser or screenshotted. No screenshot path to report.

## check.sh result

Green end to end: `cargo clippy --all-targets --all-features -- -D warnings`
(clean), `cargo test --all` (all crates, 0 failures), `bun run lint` (clean),
`bun test` (2343 pass, 1 pre-existing unrelated flake — see below),
`bun run typecheck` (clean, run separately from check.sh which does not call
it; both are green), `gradle testDebugUnitTest :core:model:test lint
:ui-tv:compileDebugAndroidTestKotlin :core:ffmpeg:compileDebugAndroidTestKotlin`
(BUILD SUCCESSFUL, lint baseline unchanged).

`test/telegram-stream-cancel.test.ts` logs an "Unhandled error between tests"
banner from a deliberately-thrown cleanup error; it reproduces identically
before any of this phase's changes and passes its own 5 assertions (0 fail)
when run in isolation — pre-existing test noise, not a regression from this
work.

## Left undone

None within phase 2's scope. Phases 3 (Rust core) and 4 (Android) are
separate phases; no Android files were touched.
