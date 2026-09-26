# Code review: feat/editorial-departments (uncommitted, base main a03a2bae)

Scope: 51 modified + ~40 new files (web/public catalog modules, web/src v9 reader, crates schema v9 credits/franchises).
Checks run: `bun test` 1945 pass / 0 fail; `bun run lint` clean; `cargo test --workspace` all ok.
No innerHTML anywhere in public/lib (XSS clean: every new module builds via `el()`/textContent).

## High

### H1. Kids profile sees every credited person in search, with whole-library title counts — CONFIRMED (traced)
- `web/src/catalog/credits.ts:81-96` builds `peopleSearch` over all credits; `web/src/catalog/routes.ts:80` returns `people` unfiltered.
- `web/public/app.js:460-466` filters `hits` by `byId` but passes `people` through as-is; `web/public/lib/catalog/search-view.js:106-108` renders every person with `countOf(person.titles, "title")` (server-side count across the whole catalogue). Total/pill counts (`:68`, `:86`) include them too.
- Scenario: kids profile types "Tarantino" or any adult-film lead: gets a portrait + name + "7 titles" card; clicking opens `#/person/<id>`, which still prints name + portrait with "0 in your library" (`cast.js:256-270`). The rest of the page already hides these titles, so this is the one place they leak.
- Fix: return each person's title keys in the search payload (or reuse `personFor`), then on the client keep only people with at least one title `titlesByKey(library)` resolves, and count those. In `renderPerson`, when `films.length + shows.length === 0`, show the "nobody by that number" empty state instead of the name and portrait.

### H2. "Part of <franchise>" link opens an error page when the library holds only one film of it — CONFIRMED (traced)
- `web/public/lib/catalog/film-page.js:83-88` links any film with `collectionId` to `#/collections/tmdb-<id>`.
- `web/public/lib/catalog/collections-page.js:141-149` (`franchisesIn`) drops franchises with fewer than two films, and `renderFranchise` (`:208-213`) then prints "That collection is not in the library."
- Scenario: one Star Trek film is held (or a kids profile sees 1 of 3 films in a franchise). The film page offers "Part of Star Trek Collection", and the link leads to an error. This is common: most films in a small library are the only one of their franchise that is held.
- Fix: render the link only when `franchisesIn(library.movies)` contains that id (pass a `Set` of eligible ids into `filmPage`), or let `renderFranchise` accept a single film when it is reached directly.

### H3. Any watch-state change rebuilds title pages: selected tab resets to the first one, the Cast tab disappears and comes back, focus goes to body — CONFIRMED (traced)
- `list-toggle.js:19` → `setWatchlisted` → `state.subscribeChanges` (`app.js:382`) → `invalidateShelf` → `route()` → `drawRoute` rebuilds `main`; `tabs.js:95` always `select(0)`; `offerCast` refetches credits and inserts the Cast tab again later.
- Scenario: the viewer is on the film's Cast/Similar/Details tab and presses "My List" in the hero, or pins the film from the ⋯ menu, or another device's position arrives over SSE (`app.js:776`). The page jumps back to Overview, the tab row shifts when Cast is added again, and the button just pressed is replaced, so keyboard focus drops to `<body>`. Old pages had no tabs, so this is new with this change.
- Fix (smallest): keep the selected tab label in a module-level variable keyed by `location.hash` and pass it to `tabbed` as the initial selection. Re-select it in `addTab` once the late Cast tab arrives. After a redraw, restore focus to the element with the same `data-*` identity (the list toggle, for example), as `shelfToggle` already intends. Better: have the list toggle update its own `aria-pressed` and label instead of triggering a full route redraw on title pages.

## Medium

### M1. Async pages blank and lose scroll on every redraw (person page, franchise overview) — CONFIRMED (traced)
- `drawAt` (`redraw.js:114-121`) clears `main` synchronously; `renderPerson` (`cast.js:256-259`) refills it only after `/api/people/<id>` resolves. A redraw (SSE state event, catalog refresh, pin) therefore empties the page, the document collapses, scroll resets to the top, and `adoptImages` runs before any `<img>` exists, so portraits reload. The franchise overview (`collections-page.js:228-233`) is likewise re-fetched and re-appended with a layout shift.
- Fix: cache the person/franchise JSON by id in a module-level `Map` and render synchronously when it is cached. Only a first visit then goes async.

### M2. Season picker: every change is a full navigation that destroys the focused `<select>` — CONFIRMED for focus loss; arrow-key behaviour PLAUSIBLE (browser-dependent)
- `series-page.js:259` `change` → `openSeason` → new hash → full rebuild. Focus is lost after each change. In Chromium, Arrow keys on a closed select fire `change` on every step, so a keyboard user can move only one season before focus is gone, and each step pushes a history entry.
- Fix: after the route draws, focus `.season-pick select` when the previous hash was the same show. Alternatively, swap the season block in place and update the URL with `history.replaceState`, then push a history entry only on blur/commit.

### M3. `credits::upsert` is DELETE + N INSERTs with no transaction — CONFIRMED (read)
- `crates/mediagram-core/src/credits.rs:20-43`; caller `metadata.rs:55-58` runs in autocommit.
- Failure: if the run is killed mid-title, the title keeps a partial cast, `credits::has` (`:47`) returns true, and `backfill_credits` never repairs it. Performance: roughly 15 autocommits (each an fsync) per title over the whole library on the first v9 `metadata` run.
- Fix: `let tx = conn.unchecked_transaction()?; … tx.commit()` inside `upsert` (it already takes `&Connection`).

### M4. Mixed-version uploaders strip v9 data from the channel — PLAUSIBLE (known pattern, not new code)
- A v8 uploader's `pull-index` ignores `credits`/`franchises` (its `shared_columns` knows no such tables) and its next `push` replaces the channel snapshot with a v8 file. Every reader then loses Cast/People/franchise pages until the v9 machine pushes again. Separately, installed Android builds have `READABLE_SCHEMAS=[6,7,8]` and refuse a v9 package pointer (`package/reader.rs:22`). Channel snapshots are unbounded above, but packages are checked by membership.
- Action: upgrade both uploaders and the Android installs (phone, TV box) before the first v9 `export-package` / `push`. Write that into phase-08.

## Low

- L1 `web/src/catalog/credits.ts:86,92`: people are matched on `fold(name)` only. Titles are indexed under `variants()` (both umlaut spellings), so the query "mueller" misses "Müller", despite the comment saying titles follow the same rule. Use `variants(row.name)` and match if any variant contains every word. PLAUSIBLE.
- L2 `backfill_credits` (`title_details.rs:34-50`): TMDB answering with an empty cast/crew writes zero rows, so `has` stays false and the title is fetched again on every run. The cache absorbs it, but a `--refresh-older-than` run pays the network cost each time. Record a sentinel or accept it.
- L3 `tmdb_types.rs` `CollectionRef.name` / `CreatedBy.name` are required `String`. One `null` there fails the whole `DetailsResponse`, which costs that title its description *and* its posters (`posters_for` parses the same type). Before this change those fields were ignored. `#[serde(default)]` on the struct fields is enough. PLAUSIBLE.
- L4 `cast.js:256-258`: if `res.json()` rejects, `renderPerson` rejects unhandled and the page stays blank. Wrap it in try/catch and fall through to the empty state.
- L5 `tabs.js`: Home/End keys are not handled, and tabpanels have no `tabindex="0"`. The Similar "empty" panel is a bare `<p>` with nothing focusable, so Tab skips past it. Both are small ARIA-pattern gaps.
- L6 `department-pages.js:108`: `byLead(popular)` dereferences `firstItemOf(...)`. A show with no divisions (cannot happen with current builders) would throw `b.popularity` on `undefined`. Use `?.popularity`.
- L7 Versions not bumped in `Cargo.toml`, `web/package.json` or `android/app/build.gradle.kts` (CLAUDE.md § Versioning). This is a minor bump plus a schema bump. Presumably phase-08, but flagged so it is not missed.
- L8 Surface parity: Android has no Cast/People/franchise/department surfaces. CLAUDE.md treats that as a defect in the newer surface unless written down. Record the deliberate gap in the plan.

## Verified OK (no finding)

- `tabs.addTab` index math: splice/insertBefore/`number()` keep ids aligned; `select(at <= selected ? selected+1 : selected)` keeps the selected tab and its focused button; out-of-range `at` clamps safely. A late `offerCast` into a detached page is skipped (`isConnected`).
- Routing: `#/movies` → department, `#/movies/page/N` → paged shelf, and `pageHash` now always numbers page 1 (`pager.js:51`). The old `#/movies` bookmark lands on the department on purpose. Season URLs `#/series/<show>/<division.title>` match the old `seasonNamed` exactly. `#/genre/X` is unchanged. Franchise ids (`tmdb-` prefix) cannot collide with list ids (UUIDs, `store.ts:585`).
- Portrait keys: `POSTER_PATH`/`KEY` regexes in `artwork-routes.ts:11` and `posters.ts:21` are anchored with `\d{1,12}`. Rust downloads re-check `poster_key_is_valid`, and `is_image_path` gates `credits.profile` read from foreign snapshots. Packages are staged separately (`export/stage.rs`), so portraits never enter a package. `PosterStore.count` excludes them.
- SQL: every web query is parameterised. `optional()` splices only typed literals. `merge_credits` splices column names only from the intersection with main's own `pragma_table_info`.
- Schema compat: the v9 web reader tolerates a v8/v6 index (tables and columns optional, tested in `credits.test.ts`/`franchises.test.ts`). The sidecar migrates forward inside one transaction. Merge copies whole missing titles and franchises. The TMDB details request is unchanged: credits and franchise are separate paths with an empty query, so the disk cache still hits.
- XSS: taglines, names, roles and overviews all go through `el(..., text)`/`textContent`.
- Kids filtering elsewhere: department, genre, latest, collections, franchise and person-page title lists all derive from the profile-filtered `library`.

## Unresolved questions

1. For H1, should a person with zero visible titles be hidden for adult profiles too (credits for held-but-pending sets), or only for kids?
2. H3 (tab reset on redraw): was a full rebuild on My List accepted as intended for title pages, or should title pages opt out of redraws for watchlist-only changes?

**Status:** DONE_WITH_CONCERNS
**Summary:** Tests and lint are green. There are three high-severity behavioural defects: people search leaks to kids profiles, a franchise link dead-ends for single-film franchises, and title-page tabs reset and lose focus on every watch-state redraw. There are also a non-transactional credits write and redraw blanking on async pages. Security (key validation, SQL, XSS) and v8/v9 compatibility hold up.
**Concerns/Blockers:** Fix H1–H3 before shipping. The v9 package breaks installed Android readers until they update (M4). Versions are not bumped yet.
