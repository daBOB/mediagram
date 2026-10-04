# Address module (architecture review candidate F)

One module owns the web player's page address format in both directions,
`web/public/lib/address.js` (+ `.d.ts`). Branch `refactor/address` (worktree
`../mediagram-channel-index`), from main 62de3c78.

## Decisions (user-confirmed 2026-09-27, "F apply it")

| # | Decision |
|---|----------|
| Q1 | Format only: `parse(hash)` → typed address, `href(address)` → text; every link and `location.hash =` goes through it; `drawRoute` dispatches on the parsed value. Navigation state (`shownHash`, generations, `redraw.js`, `page-turn.js`) unchanged; no page registry |
| Q2 | The code is the truth (what people bookmarked): `#/film/<setId>`, `#/search/<query>`, `#/genre/<name>`, `#/series/<show>/<season>`, course folder trails. `docs/web-player.md`'s table is corrected, and a test parses every example it lists |
| Q3 | One opener, `go(address)`, replaces the three show-openers and the hand-written `location.hash = …` |
| Q4 | Round-trip test for every page kind: `parse(href(a))` equals `a`, with `/`, spaces, `%`, umlauts, nested folder trails |
| Q5 | 0.68.10 (refactor → patch); stub-preview check; merge to main when done |

## Today (facts)
- Parse: `drawRoute` (`app.js:585`), a 16-branch chain over `location.hash` split on `/`.
- Build: ~47 sites in ~16 files (`home-features`, `pager`, `home-view`, `genres`,
  `collections-page`, `home-break`, `home-cover`, `series-page`, `cast`,
  `film-page`, `department-pages`, `settings-view`, `app.js` …).
- Open a show: three signatures (`app.js:220`, `:273`, `:647/686`).
- Unknown sections fall back to `movies`; `movies/page/N`; `collections/tmdb-<id>`
  vs list UUID; kept pages (`continue`, `watchlist`, `system`).

Status reconciled 2026-10-04: completed — shipped 0.68.10 (`658def54`).

## Phases
| Phase | Status |
|-------|--------|
| 01 Failing tests: round trip per page kind, documented examples, unknown → movies | done |
| 02 `address.js`; `drawRoute` on the parsed value; every builder via `href`/`go` | done |
| 03 Docs table, changelog, 0.68.10; web suite, tsc, eslint; preview check; review | done |

## Risks
- A bookmark must keep working: every address the code accepts today parses to
  the same page (test the old spellings, including percent-encoding quirks).
- Line ceilings (`web/test/code-standards.test.ts`) only go down.

## Review (2026-09-27)
- Code review: a differential probe of main's `drawRoute` against `parse`
  over 30,240 generated hashes gave the same page, arguments, `data-page`
  and throw/no-throw everywhere, except one class. Sections named after
  built-in object properties (`#/constructor`) used to crash to a blank page;
  they now open Movies. Kept, and noted in the changelog.
- Applied from the review:
  - exact-text `href` checks (round trips alone let double-encoding pass);
  - the docs-table test now derives its hash from each row;
  - the two wrong Movies rows are fixed;
  - `pager.js`'s dead `parsePage` and unused `section` are gone;
  - `app.js`'s search-refine prefix now comes from `href`;
  - a dead `documentaries` branch is removed.
- Not applied: folding the seven plain-page `parse` lines into one set
  lookup. The explicit returns keep each page kind typed for `tsc`.
- Gates: web 2300/2300, tsc and eslint clean, Rust 1359/1359. Two
  mutations (double-encoded film id, `+` for a space in search) are now
  caught. Stub preview: the agent clicked through every page kind; the
  pager links and search refine were re-checked after the fixes.
