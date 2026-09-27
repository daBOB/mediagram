# Address module implementation report

Plan: `/home/andre/Workspace/mediagram/plans/260927-1303-address-module/plan.md`
Work context: `/home/andre/Workspace/mediagram-channel-index` (worktree, branch `refactor/address`)
Not committed — lead commits.

## Status: DONE

## What shipped

**Tests first.** `web/test/address.test.ts` written before `address.js` existed;
confirmed it failed on "Cannot find module" before implementing. 88 assertions,
three groups:
- Round trip (`parse(href(a)) === a`) for every page kind — home, movies dept,
  movies page N, series/tutorials/documentaries dept, a show/course by name (all
  three show-bearing sections), a series season, a course's 3-level nested folder
  trail, film by setId, genre by name, genres, latest, person by id, search by
  query, collections, list by UUID (twice — a real UUID and one carrying the full
  weird-character battery), a franchise, continue, watchlist, settings, system.
  Names use a constant `WEIRD = "Foo/Bar 100% #tag? Ünïcode 🎬"` covering `/`,
  space, `%`, `#`, `?`, umlaut, emoji together.
- `parse` compatibility with everything `drawRoute` used to parse by hand,
  verified against a hand-traced Node script of the *old* logic before writing
  the new module (`/tmp/.../trace.mjs`, not committed): empty hash, `#`, `#/`,
  an unrecognised section, and the non-obvious case an unrecognised section
  followed by `/page/N` still opens Movies page N (the old code reused
  `name`/`folders` from the raw parts after falling back `known` to "movies").
  Also the two real strings from `browser-application.test.ts`
  (`#/series/A%2FB/Season%202`, `#/tutorials/Course/Basics/Advanced`).
- `docs/web-player.md`'s address table, extracted by regex from the file itself
  and checked against a template→page dictionary that also asserts the doc lists
  *exactly* those templates (catches an added/removed row, not just a changed one).

**`web/public/lib/address.js` + `address.d.ts`** (126 / 44 lines). `parse(hash)`,
`href(address)`, `go(address)`, `sectionOf(address)` — a discriminated union
(`department`, `moviesPage`, `show`, `film`, `genre`, `genres`, `latest`,
`person`, `search`, `settings`, `system`, `continue`, `watchlist`,
`collections`, `franchise`, `list`, `home`). `parse` is a literal transliteration
of the old `drawRoute` dispatch (same branch order, same fallback-to-movies
quirk) rather than a redesign, specifically to avoid subtly changing what an
already-bookmarked hash opens. Two deliberate non-round-tripping asymmetries
kept because they're what the app already does: a person id and a franchise
id are template-literal'd in `href`, not `encodeURIComponent`'d — matching
`cast.js`'s and `collections-page.js`'s existing builders exactly.

**Rewired every builder.** `drawRoute` (`app.js`) now dispatches on
`parse(location.hash)`/`sectionOf(address)` instead of splitting the hash by
hand. Every hand-built `#/…` string and `location.hash =` across
`app.js`, `pager.js`, `genres.js`, `cast.js`, `home-break.js`,
`department-pages.js`, `collections-page.js`, `home-features.js`,
`film-page.js`, `series-page.js`, `shelf-view.js` (`crumbs()`), `home-cover.js`,
`home-view.js`, `settings-page.js`, `search-view.js` now calls `href`/`go`. The
three separate show-openers in `app.js` (home view, department context, the
route's own `openers`) are one function, `openShow(section, name, folders)`,
used everywhere a show/course opens. `pager.js`'s `pageHash` delegates to
`href({ page: "moviesPage", n })`, keeping its own `(section, page)` signature
(and `pager.test.ts`) unchanged since only Movies paginates today.
`address.js` does not import `pager.js` (or vice versa isn't needed either) —
it carries its own one-line page-number normalisation rather than share
`parsePage`, to keep the dependency one-directional (`pager.js` → `address.js`,
never the reverse). Confirmed by `grep -rn '#/' web/public --include=*.js` and
`grep -rn 'location\.hash\s*='`: the only remaining `#/…` template literals live
inside `address.js` itself or in doc comments; the only `location.hash =` is
inside `go()`. `app.js`'s dead `PAGES` set and `KEPT.collections` (both only
existed for the old recognition check) were removed.

**Docs.** `docs/web-player.md`'s address table corrected to match the code:
search is a path segment not a query string, the franchise/list `#/collections/…`
row was listed both right and wrong, a season's route was described as an
episode's, and four real pages were missing (`#/documentaries`, `#/continue`,
`#/watchlist`, `#/system`). A note says the table is checked by
`address.test.ts`. `docs/project-changelog.md` gets a new top entry,
`## 0.68.9 — the web player's addresses have one home` (Internal), naming the
doc corrections. Versions bumped 0.68.8 → 0.68.9 in `Cargo.toml` (workspace),
`web/package.json`, `android/app/build.gradle.kts`; `cargo check -q -p mediagram`
run so `Cargo.lock` follows (5 lines changed).

**Line ceilings** (`web/test/code-standards.test.ts`): `app.js` shrank
786 → 772 (parsing logic moved out, three openers became one); lowered.
`shelf-view.js` shrank 288 → 285 (`crumbs()` rewritten shorter using `href`);
lowered 289 → 285.

## Mutation checks (two round-trip cases, both reverted)

1. Dropped `encodeURIComponent` from the `show` case's `href` template. 5 tests
   failed: 3 round-trip cases threw `URIError` inside `decodeURIComponent`
   (an un-encoded `/` in a folder name split into extra segments, so
   `folders.map(decodeURIComponent)` choked on `100%` etc.), 2 more failed on
   value mismatch.
2. Off-by-one on the franchise id slice (`slice(4)` instead of `slice(5)`,
   eating the `-` of `tmdb-`). 2 tests failed with a clear id diff (`"1"` vs
   `"-1"`). Reverted; `diff` against the pre-mutation file confirmed byte-identical
   restoration; re-ran — 88/88 pass again.

## Tests status

- `bun test test/address.test.ts`: 88/88 pass.
- `bun test` (whole suite): 2282/2283 pass. The one failure,
  `teleproto-ping-patch.test.ts` ("the wake-up threshold only fires on a genuine
  gap"), is pre-existing and unrelated — confirmed by `git stash` back to clean
  `main` 62de3c78 and re-running just that file: identical failure there too.
- `bunx tsc --noEmit -p .`: clean.
- `bunx eslint public --max-warnings 0`: clean.

## Preview smoke check

`bun run preview` on 8795 (stub, copies only). Via headless `browse`, clicked
through, from real links, with a console/network check after each: home →
a film (The Wrecking Crew) → Cast tab → a person (Dave Bautista, `#/person/543530`)
→ a series season (Pushing Daisies, season select → `#/series/Pushing%20Daisies/Season%202`)
→ a course front page and a nested folder via a folder-row click and the crumb
back up (`#/tutorials/Mentfx%20Course%202026/1.%20Introduction`, then the
"Mentfx Course 2026" crumb) → typed search (`#/search/Wrecking`, results
rendered) → Genres → a genre (Action) → Collections → a franchise
(James Bond, `#/collections/tmdb-645`) → created a list via "+ New list"
(dialog-accept, since the first attempt auto-accepted an empty name and got a
400 — that was the test harness, not the app) → the new list by UUID
(`#/collections/956c9e22-…`) → Settings → System.

No console errors beyond: the expected `/api/events` 404 (SSE not served in
preview) and one `/api/settings` HEAD 404 (settings-probe gate, also expected
off a served network) — both present before I touched anything, both named as
expected in the task. The one 400 (empty-name prompt) was my own test input,
not a bug, and the retry with dialog text succeeded cleanly. Server left
running until this check completed, then killed by its recorded PID; port 8795
free afterward. Port 8770 (the real dev server) was never touched.

## Deviations from a literal reading of the plan / task text

- Left `shownHash.startsWith("#/search/")` / `location.hash.startsWith("#/search/")`
  (the `turnPage` refining check) as raw string comparisons rather than routing
  through `parse`. This is a read, not a hash builder, and it's tightly coupled
  to navigation-state timing the plan explicitly says stays as-is
  (`shownHash`, `navigationGeneration`). Converting it to
  `parse(hash).page === "search"` would be a real (if tiny) behaviour change: a
  bare `#/search` with no trailing slash (never produced by any builder, only
  possible via a hand-typed URL) would newly count as "refining". Did convert
  `location.hash.startsWith(href({ page: "settings" }))` and the `#/home`
  literal in `history.replaceState`, since those source their string from
  `href()` while keeping the exact same runtime comparison — zero behaviour
  change, not the search case's asymmetry.
- Address kind names (`department`, `moviesPage`, `show`, `franchise`, `list`,
  …) are my own choice, not specified beyond the two examples the task gave
  (`{ page: "film", setId }`, `{ page: "show", section, name, folders }` — both
  used verbatim). Chose one discriminant per real page kind rather than
  overloading `department` with a `page` sub-number, so `moviesPage` is its own
  kind.

## Files changed

Production: `web/public/app.js`, `web/public/lib/address.js` (new),
`web/public/lib/address.d.ts` (new), `web/public/lib/catalog/{cast,collections-page,
department-pages,film-page,genres,home-break,home-cover,home-features,home-view,
pager,search-view,series-page,settings-page,shelf-view}.js`.
Docs: `docs/web-player.md`, `docs/project-changelog.md`.
Versions: `Cargo.toml`, `Cargo.lock`, `web/package.json`,
`android/app/build.gradle.kts`.
Tests: `web/test/address.test.ts` (new), `web/test/code-standards.test.ts`
(ceiling updates).

## Unresolved questions

None.
