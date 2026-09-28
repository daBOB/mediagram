---
phase: 2
title: "Web: the rule, the shared fixture, and the Anime department"
status: completed
priority: P2
effort: 7h
dependencies: [phase-01]
---

# Phase 2 — Web: the rule, the shared fixture, and the Anime department

## Overview

The web player is the reference surface, so the rule and every presentation
decision are made here first. The server decides `anime` per catalog row (TS
rule + override table); the browser pulls anime out before `groupLibrary`, the
way documentaries are pulled out, and gets an `#/anime` department, a nav tab,
a search group, and correct behaviour on every page that looks a title up.
Ships alone; Android follows in phases 3–4.

## Requirements

- **Rule (TS, once):** `isAnime(kind, genres, originalLanguage, forced)` —
  kind `movie`|`ep` only; an override wins; else `originalLanguage === "ja"`
  and `genres` contains exactly `"Animation"`.
- **Shared fixture** `web/test/fixtures/anime/cases.json`, run by bun here and by
  cargo in phase 3.
- Catalog rows (`/api/sets`, `/api/search` hits) carry `anime: boolean`.
- Anime titles are in `library.anime = { collections, singles }` and in neither
  `library.movies` nor `library.series`.
- **Department views exclude anime; lookups include it** (the rule, see Architecture).
- Nav tab "Anime" after Series, count = titles (series + films), **hidden when
  the profile's visible count is 0**; `#/anime` still renders (empty state).
- Kids profiles: nothing new — the kids filter runs before grouping
  (`library-session.js:86`), so the Anime shelf is filtered like every other.
- An index without `original_language`/`anime_overrides` (pre-v11) shows no
  anime: everything stays in Movies/Series exactly as today.

## Architecture

**Data flow**

```
library.db ──> providerFactsByShow (+ originalLanguage, optional column)
          └──> animeOverrides (table optional; NULL rows dropped)
                     │
routes.ts forBrowser: anime = isAnime(set.kind, facts.genres, facts.originalLanguage, overrides.get(key) ?? null)
                     │  JSON /api/sets, /api/search
library-session applyCatalog: filtered(sets) [kids] ──> groupDepartments(visible)
   = { ...groupLibrary(non-anime), documentaries, anime: groupAnime(anime) }
                     │
app.js: nav count/hidden · #/anime dept · #/anime/<show> · openShow resolves "series"→"anime"
```

**Where anime appears (decision, both surfaces).** A department view is a
shelf; anime is shelved only in Anime. A lookup follows a title's identity or
relations and finds it wherever it is shelved.

| Excludes anime (department views) | Includes anime (lookups / the viewer's own progress) |
|---|---|
| Movies dept (hero, Featured, Genres row, Acclaimed, Recently added), `#/movies/page/N` | Anime dept (only anime) |
| Series dept (hero, Popular, New episodes, All shows, **Continue your series** — fixed to drop anime, it filtered by kind only) | Search (its own "Anime" group) |
| Latest page parts, home "Latest films/series" rows | Genre pages and the Genres index |
| Home editorial picks — cover, features, quote, "This month" (they draw from the Movies department, `app.js:205-212`; unchanged, like Documentaries) | Person pages, franchises (Collections page, franchise page, a film's franchise link) |
| Rail masthead counts, Movies/Series nav counts | Similar (film page: every film; series page: every show) |
| | Home Continue and Next up; what plays next after an episode |

Rationale for the home rule: Documentaries never reach the home picks either,
and the picks are the Movies department's front-page selection. Flipping it is
one argument (`movies: everyFilm(library)` at `app.js:206`) if the user wants
Ghibli back on the cover.

**Empty-tab rule (decision):** hide the Anime tab at 0. Documentaries stays
always-visible (web: static nav link, `index.html:75`; Android keeps it so its
empty state is reachable, `Shelves.kt:26-33`), and that empty state points at a
command that fills it (`add-docu`, `shelf-view.js:92`). Anime has no upload
command — titles file themselves — so an empty tab would be a dead end on every
library without Japanese animation and on a kids profile whose limit hides all
of it. Android already drops empty Movies/Series/Tutorials the same way
(`Shelves.kt:55-63`), so this is the one rule both surfaces can share. A deliberate
difference from the web's "never hide a department", written into the Anime
section of `docs/web-player.md`.

**Show addressing.** Anime series live at `#/anime/<show>`. Every place that
opens a show by name from outside a department (person, genre, search, Latest,
Similar, season links) already calls `openShow("series", name)`; instead of
touching each, `openShow` (`app.js:221`) asks `sectionForShow(library, section,
name)`, which answers `"anime"` for a name not on the Series shelf but on the
Anime shelf. One resolution point.

**Department page** `renderAnimeDept` (new `lib/catalog/anime-department.js`,
modelled on `renderDocumentariesDept`, `department-pages.js:80-124`):
1. Hero: kicker "Only in your library", title "Anime", line "four shows · 31
   films" (zero parts dropped); lead = most popular unwatched film or series
   lead episode with a backdrop; lead name/href = show page for a series,
   film page for a film.
2. "Continue watching": `resumeCards({ continues: …filter(set.anime), nextUp: …filter(entry.set.anime) })` — resume and next episode, like Series.
3. "Series": every anime series, `collectionGrid("anime", …, GRID)`, by name.
4. "Films": every anime film, `movieGrid(…, GRID)`, newest arrival first (the
   department doubles as anime's "latest", since Latest excludes it).
Empty: `emptyState("anime")` → "No anime yet. File one here with
`mediagram edit <set-id> --anime yes`." (kids: the existing FSK line).

## Related Code Files

**Create**
- `web/src/catalog/anime.ts` — `isAnime(...)`, `animeOverrides(db): Map<string, boolean>`
  (probe `sqlite_master` for the table; `WHERE source='tmdb' AND anime IS NOT NULL`;
  key `tmdb-${kind}-${id}`). Module doc names the Rust twin and the fixture.
- `web/test/fixtures/anime/cases.json` — `[{ name, kind, genres, originalLanguage, override, anime }]`:
  1. anime film (`movie`, `["Animation","Familie","Fantasy"]`, `ja`, null) → true
  2. anime series (`ep`, `["Animation","Sci-Fi & Fantasy","Mystery"]`, `ja`, null) → true
  3. Western animation (`movie`, `["Animation","Familie","Fantasy","Horror"]`, `en`) → false
  4. live-action Japanese (`movie`, `["Action","Drama"]`, `ja`) → false
  5. override in — donghua (`ep`, `["Animation"]`, `zh`, true) → true
  6. override out — Japanese animation that is not anime (`movie`, `["Animation"]`, `ja`, false) → false
  7. older index — no language (`movie`, `["Animation"]`, null, null) → false
  8. older index, overridden in (`ep`, `[]`, null, true) → true
  9. a documentary is never anime (`docu`, `["Animation"]`, `ja`, true) → false
  10. genre in another library language (`movie`, `["Animación"]`, `ja`) → false — pins the known ceiling
- `web/test/anime-rule.test.ts` — runs every fixture case through `isAnime`;
  `animeOverrides` on no table / NULL rows / 0 and 1.
- `web/public/lib/departments.js` + `departments.d.ts` — `groupAnime(sets)`,
  `groupDepartments(sets)`, `countAnime(anime)`, `everyFilm(library)`,
  `everyShow(library)`, `sectionForShow(library, section, name)`.
- `web/public/lib/catalog/anime-department.js` — `renderAnimeDept(main, cx)` (~55 lines).
- `web/test/departments.test.ts` — split, seasons kept, counts, everyFilm/everyShow, sectionForShow (incl. a name on both shelves keeps the given section).

**Modify (server)**
- `web/src/catalog/shows.ts` — `ProviderFacts.originalLanguage`; `optional()` union
  gains `"original_language"` (:95-96); select + map (`?.trim() || null`).
- `web/src/catalog/routes.ts` — `const overrides = animeOverrides(db);` beside `provider` (:44);
  `anime:` in `forBrowser` (:52-78).
- `web/test/catalog-read-failures.test.ts` — expected objects (:29-31, :36-38) gain
  `originalLanguage: null`; add a v11 step (`ALTER TABLE shows ADD COLUMN original_language TEXT`).

**Modify (browser)**
- `web/public/lib/library-session.js` — imports (:19-20) → `groupDepartments`; initial
  `library` (:40) → `groupDepartments([])`; `applyCatalog` (:87-88) → `library = groupDepartments(visible);`;
  module doc (:14-16) names `groupDepartments`. Net −2 lines (file is at the 200 limit).
- `web/public/lib/library.d.ts` — `CatalogSet.anime: boolean`; `Library.anime`.
- `web/public/lib/catalog/sections.js` — `anime: { label: "Anime", empty: "No anime yet.", extent: "title", noun: "episode", chapterNoun: "season" }`.
- `web/public/lib/catalog/shelf-view.js` — `UPLOAD_HINT.anime = ["File one here with ", "mediagram edit <set-id> --anime yes"]` (:88-93); JSDoc unions (:97, :210).
- `web/public/lib/address.js` — `"anime"` in `KNOWN_SECTIONS` (:26-29); `address.d.ts` (:10, :13) unions.
- `web/public/index.html` — `<a href="#/anime" data-section="anime" id="nav-anime" hidden>Anime<span class="n" id="n-anime"></span></a>` after Series (:74). `[hidden]` is `display:none !important` (`styles/theme.css:151`).
- `web/public/app.js` — import `departments.js` + `renderAnimeDept`; `:90` `groupDepartments([])`;
  onData (:117-120) sets `n-anime` and `nav-anime.hidden`; `:295` `similarTo(set, everyFilm(library), …)`;
  `:297` and `:479` `franchisesIn(everyFilm(library))`; `:479` also `animeShows: library.anime.collections`;
  route `department`/`anime` → `renderAnimeDept` (beside :637); show route (:641-645): shelf from
  `{ documentaries: …, anime: library.anime.collections }[section] ?? library[section]`, `renderSeries`
  for `series` and `anime`, Similar pool `everyShow(library)`; `openShow` (:221) → `sectionForShow`.
- `web/test/code-standards.test.ts` — raise the `public/app.js` ceiling (742) by the lines added (≤ 7), with a dated line in the CEILINGS comment.
- `web/public/lib/catalog/department-pages.js` — `renderShowsDept` Continue filters (:147-148) add `&& !set.anime` / `&& !entry.set.anime`.
- `web/public/lib/catalog/home-shelves.js` — Next up loop (:39) walks `library.anime.collections` too.
- `web/public/lib/playback/plays-next.js` — collection search (:29) adds `...library.anime.collections`.
- `web/public/lib/catalog/cast.js` — `titlesByKey` (:125-131) over `everyFilm`/`everyShow`.
- `web/public/lib/catalog/collections-page.js` — `franchisesIn(everyFilm(library))` (:65, :98).
- `web/public/lib/catalog/genres.js` — `genreShelf` (:35-41) over `everyFilm`/`everyShow`.
- `web/public/lib/catalog/utility-pages.js` — `titlesOf` (:66-68) over `everyFilm`/`everyShow`.
- `web/public/lib/catalog/search-view.js` — split hits on `hit.anime` before the kind split (:77-82);
  filter chip `["anime", "Anime", n]` after Series (:83-91); parts "Anime films" (movieGrid),
  "Anime series" (`collectionGrid("anime", matched, name => openShow("anime", name))`),
  "Anime episodes" (rows) under key `anime` (:102-110); takes `animeShows`. Stays < 200 lines.
- `web/public/lib/catalog/series-page.js` — use the `section` argument (:35, now `_section`):
  back link (:80) `href({ page: "department", section })`, `Back to ${SECTIONS[section].label}`;
  `openSeason` (:50) `open(section, …)`.
- `web/public/lib/catalog/film-page.js` — back link (:40) to Anime for `set.anime`.
- Tests: `web/test/support/catalog-set.ts` default `anime: false`;
  `plays-next.test.ts` helper (:12-18) → `groupDepartments`, + "an anime episode plays the next one";
  `home-shelves.test.ts` (:46) shape, + "Next up offers an anime series";
  `address.test.ts` `#/anime`, `#/anime/<show>`, `sectionOf`;
  `library-session.test.ts` + "a kids profile's Anime shelf holds only what it may see";
  `shelf-view.test.ts` + `emptyState("anime")`; `genres.test.ts`/`similar.test.ts` pools as needed.
- Docs: `docs/web-player.md` routes table (:249-250) + a short "Anime" section with the
  exclude/include table and the empty-tab rule; `docs/project-changelog.md`; version bump (minor).

**Delete** — none.

## Implementation Steps

1. Fixture + rule: write `cases.json`, `anime.ts`, `anime-rule.test.ts` (red → green).
2. Server projection: `shows.ts` column, `routes.ts` field; fix `catalog-read-failures.test.ts`.
   Check with `bun test test/catalog-read-failures.test.ts test/anime-rule.test.ts`.
3. `departments.js` + d.ts + `departments.test.ts`; switch `library-session.js` and
   the `plays-next`/`home-shelves` test helpers to `groupDepartments`.
4. Sections/address/empty state/nav markup; `address.test.ts`.
5. `anime-department.js`; app.js wiring (dept route, show route, counts + hidden,
   openShow resolution, everyFilm/everyShow pools); raise the app.js ceiling.
6. Lookups: `cast.js`, `collections-page.js`, `genres.js`, `utility-pages.js`,
   `home-shelves.js` Next up, `plays-next.js`, `series-page.js`, `film-page.js`,
   Series dept Continue filter.
7. Search view group.
8. `bun run lint && bun test` in `web/`, then `scripts/check.sh`.
9. Visual check with the stub harness (never the real player): after phase 1's
   `mediagram metadata`, `cd web && PREVIEW_INDEX=~/.local/share/mediagram/library.db bun run preview`
   and walk: Anime tab count (~35) and page (hero, Continue, 4 series, ~31 films);
   Movies/Series no longer list them; `#/anime/Dragonball` seasons and back link;
   search "Ghibli"/"Dragonball" → Anime group; person page for a Ghibli director;
   genre "Animation" page; a kids profile; dark and light. Screenshots to the user.
10. Docs, changelog, minor version bump; commit.

## Success Criteria

- [x] Every fixture case passes in `anime-rule.test.ts`.
- [x] `/api/sets` rows carry `anime`; a pre-v11 index yields `anime: false` for all and an unchanged Movies/Series.
- [x] Anime titles appear in the Anime department and nowhere in Movies/Series/Latest/home editorial; they still appear in search, genre, person, franchise and Similar results, and Next up / autoplay continue an anime series.
- [x] Anime tab hidden with zero visible anime (incl. a kids profile), shown with a count otherwise; `#/anime` renders the empty state when hidden.
- [x] Series department "Continue your series" shows no anime episode.
- [x] `web/test/code-standards.test.ts` green (library-session.js ≤ 200, search-view.js ≤ 200, app.js within its raised ceiling).
- [x] `scripts/check.sh` green; preview walk verified functionally against a synthetic fixture (server → `/api/sets` → `/api/search`, rule and override precedence) rather than with browser screenshots — no browser/screenshot tool was available to this agent; see the phase report's Deviations.

## Risk Assessment

| Risk | L×I | Mitigation |
|---|---|---|
| A page still reads `library.movies`/`library.series` where it should find anime (silent loss: autoplay, Next up, person page) | M×H | Every reader from `grep -rn 'library\.\(movies\|series\)' web/public` is listed above as changed or deliberately left (Movies dept, `#/movies/page`, Series dept, Latest, editorial, rail masthead); tests for plays-next, Next up, person/titlesByKey. |
| Same show name on both shelves (live-action "One Piece" + anime) | L×M | Route keeps the given section when the name is on it; `sectionForShow` only redirects `series` names absent from Series. Test case. |
| Genre-name match is library-language bound ("Animation" in en/de/fr; not es/it/ja) | L×M | Fixture case 10 pins it; this library is `de-DE` (verified). Upgrade path: store TMDB genre ids — only if a library in another language appears. |
| Overlap with the pending profile-roles plan (`plans/260928-0047-…` edits `app.js`, `library-session.js`) | M×L | Whichever lands second rebases; our `library-session.js` change is 3 lines; recompute the app.js ceiling on rebase. |
| app.js ratchet | L×L | Ceiling raise with a dated note (precedent in the CEILINGS comment). |

**Rollback:** revert the commit; the server simply stops sending `anime` and the
browser groups as before. The index data from phase 1 is inert without it.

## Security Considerations

No new routes; override values are integers from the index; names are rendered
through the existing `el()`/text-node paths (no HTML injection surface added).
