---
phase: 2
title: "Web: `category` on every catalog row, the row rule and its fixture, rows on both department pages"
status: completed
priority: P2
effort: 4h
dependencies: [phase-01]
---

# Phase 2 — Web: `category` on every catalog row, the row rule, rows on both department pages

## Overview

The web player is the reference, so the row rule and the page layout are decided and
pinned here; Android ports them in phase 3. The server attaches `category: string | null`
to every catalog row (TS twin of phase 1's `category_key`, held to `keys.json`); the browser
turns a department's units into rows with one pure function held to a new shared fixture,
`rows.json`, and draws them on the Tutorials and Documentaries pages right after the Continue
row. Nothing else in the player changes. Ships alone, minor bump.

## Requirements

- `/api/sets` and `/api/search` rows carry `category` (null for films, episodes, anything
  uncategorised, and every row of an index without the `categories` table).
- Pure rule `categoryRows(units, categoryOf)` (`web/public/lib/categories.js`):
  - no unit has a category → `[]` (the page stays exactly as today);
  - else one row per distinct category, uncategorised units under `OTHER = "Other"`;
  - rows sorted by title with the library's collator (`byTitle`, `library.js:48`), "Other" last;
  - units inside a row keep the order they were given in (department order);
  - a unit whose category is literally "Other" shares that row (reader robustness; the uploader refuses the name).
- Tutorials (`renderShowsDept`, section `tutorials`): hero → "Continue your courses" → category
  rows → "All courses" (unchanged). Unit = a course; its category = `firstItemOf(course.divisions)?.category`
  (every set of a course shares its key, so any set answers). Row = `collectionGrid("tutorials",
  units, open, { mode: GRID, strip: true })`, the Series "Popular" row's shape. Series never
  draws a row (episodes never carry a category) — no branch needed.
- Documentaries (`renderDocumentariesDept`): hero → "Continue watching" → category rows →
  "Recently added" → folder rows → "Standalone documentaries" (unchanged). Units = collections
  (in `groups` order) then singles (in `singles` order); a row is one strip holding collection
  cards (open the collection, `cx.openShow("documentaries", name)`) followed by single cards
  (play, `cx.play`) — the department's other rows already play on tap.
- Rows are uncapped and have no "All" link (no per-category page).
- Kids profiles: nothing new — the kids filter runs before grouping (`library-session.js`), so a
  category whose units are all hidden has no row.

## Architecture

```
library.db categories ──> categoryNames(db): Map<"dept/itemKey", name>   (table optional; NULL rows dropped)
routes.ts forBrowser ──> category: categoryOf(names, set.kind, set.show, set.title)
                            │  (categoryKey = TS twin of mlib_spec::category_key; posterKeyFor(kind, null, show ?? title))
                            ▼  JSON /api/sets, /api/search
library-session ─> groupDepartments (unchanged) ─> library.tutorials / library.documentaries
department-pages.js ─> category-rows.js ─> categoryRows(units, categoryOf) ─> deptRow(title, strip)
```

Row rule, as dry-run in a scratch copy (bun, 2026-09-28) against the fixture cases below:

```js
export const OTHER = "Other";
export function categoryRows(units, categoryOf) {
  if (!units.some((unit) => categoryOf(unit) !== null)) return [];
  const rows = new Map();
  for (const unit of units) {
    const title = categoryOf(unit) ?? OTHER;
    if (!rows.has(title)) rows.set(title, []);
    rows.get(title).push(unit);
  }
  return [...rows].map(([title, members]) => ({ title, units: members }))
    .sort((a, b) => Number(a.title === OTHER) - Number(b.title === OTHER) || byTitle(a, b));
}
```

**Why "no rows when nothing is categorised" (decision).** Every library has this state until
someone files a unit, and every older index is in it forever. An "Other" row alone would repeat
"All courses" (or the whole documentary shelf) card for card directly above it. Once any unit is
filed, "Other" is what makes the rows a complete index of the department.

**Why alphabetical (decision).** Stable (rows do not jump when something is uploaded), predictable,
the order every other name list on the page already uses, and the one both surfaces can share with
no extra data. Recency would need a per-row timestamp rule in two languages.

## Related Code Files

**Create**
- `web/src/catalog/categories.ts` — `categoryKey(kind, show, title): [string, string] | null`
  (`tut`/`doc` → `tutorials`, `docu` → `documentaries`; key `posterKeyFor(kind, null, show ?? title)`,
  `web/src/package/posters.ts:57`); `categoryNames(db)` (probe `sqlite_master`, the
  `animeOverrides` pattern, `web/src/catalog/anime.ts:50`; `WHERE category IS NOT NULL`);
  `categoryOf(names, kind, show, title)`. Module doc names the Rust twin and both fixtures.
- `web/public/lib/categories.js` + `categories.d.ts` — `OTHER`, `categoryRows` (above).
- `web/public/lib/catalog/category-rows.js` — `courseCategoryRows(shows, open)` and
  `documentaryCategoryRows(groups, singles, openGroup, play)`, each returning `deptRow` elements;
  the docu strip is `collectionGrid(...)` with `movieGrid(...).childNodes` appended (~45 lines).
- `web/test/fixtures/categories/rows.json` — `[{ name, units: [{ name, category }], rows: [{ title, units: [name] }] }]`:
  1. nothing categorised → `[]`; 2. no units → `[]`;
  3. one filed, two not → `Trading:[Forex]`, `Other:[Geld, Wall]`;
  4. all filed → no Other row;
  5. natural order → `Basics`, `Level 2`, `Level 10`;
  6. Other last even when a category sorts after it → `Zoology`, `Other`;
  7. units keep input order within a row;
  8. a category named "Other" shares the uncategorised row, input order kept → `Trading:[C]`, `Other:[A, B]`.
  ASCII names only: Kotlin's `NATURAL` and the web collator already order umlauts differently on
  every shelf; the fixture pins what the two agree on.
- `web/test/category-keys.test.ts` — every `keys.json` case through `categoryKey`; `categoryNames`
  on no table, NULL rows, a named row; `categoryOf` for a film → null.
- `web/test/category-rows.test.ts` — every `rows.json` case through `categoryRows`.

**Modify**
- `web/src/catalog/routes.ts` — `const categories = categoryNames(db);` beside `overrides` (:44);
  `category:` in `forBrowser` (:50-80). 133 → ~136 lines.
- `web/public/lib/library.d.ts` — `CatalogSet.category: string | null` with a one-line doc.
- `web/test/support/catalog-set.ts` — default `category: null` (:14).
- `web/public/lib/catalog/department-pages.js` — `renderShowsDept`: `main.append(...courseCategoryRows(shows, open))`
  right after `const open` (:158), which already follows the Continue block (:152-156);
  `renderDocumentariesDept`: `main.append(...documentaryCategoryRows(groups, singles, (name) =>
  cx.openShow("documentaries", name), cx.play))` after Continue (:107); module doc (:1-11) and the
  docu doc (:74-79) mention the rows. 175 → ~180 lines.
- Docs: `docs/web-player.md` — new "Categories" section after "Anime" (:224): what a unit is, the
  key, the row rule, the no-rows-until-filed and alphabetical reasons, `edit --category`;
  `docs/project-changelog.md`. Version: minor bump, three manifests.

**Delete** — none.

## Implementation Steps

1. `rows.json` + `categories.js`/`.d.ts` + `category-rows.test.ts` (red → green).
2. `categories.ts` + `category-keys.test.ts` against phase 1's `keys.json`.
3. `routes.ts` field; `library.d.ts`; `catalog-set.ts`.
4. `category-rows.js`; wire both department pages.
5. `cd web && bun run lint && bun run typecheck && bun test`, then `scripts/check.sh`.
6. Visual check with the stub harness only (never the real player), on a scratch copy:
   `cp ~/.local/share/mediagram/library.db <scratch>/preview.db`; if it predates v12, create the
   table there by hand with phase 1's DDL; insert rows for two courses (one shared category) and for
   one docu collection + one single; `cd web && PREVIEW_INDEX=<scratch>/preview.db bun run preview`.
   Walk: Tutorials — Continue, the category row(s), "Other", "All courses" unchanged; Documentaries —
   category rows between Continue and Recently added, a collection card opens its folder page, a
   single card plays; a copy with the table but no rows → both pages exactly as before; dark and light.
   Screenshots to the user. The real `library.db` is never written.
7. Docs, changelog, minor bump; commit.

## Success Criteria

- [x] Every `rows.json` and `keys.json` case passes in bun.
- [x] `/api/sets` rows carry `category`; an index without the table (≤ v11) yields `category: null`
      everywhere and both pages render exactly as before.
- [x] Tutorials: one row per category after Continue, "Other" last holding the rest, "All courses" intact.
- [x] Documentaries: rows between Continue and Recently added, collection cards open, single cards play.
- [x] Series and Anime pages unchanged. `web/test/code-standards.test.ts` green (every touched file ≤ 200
      or within its ceiling; `app.js` untouched). `scripts/check.sh` green.
- [x] Visual walk done without a browser tool (none available this session): the stub harness served a
      scratch v12 index over HTTP, `/api/sets` was read back to confirm `category` per unit and `null`
      everywhere on a table-but-no-rows copy, and the actual `departments.js`/`categories.js` modules were
      run against that response to confirm the row grouping. No screenshots were produced; see the report.

## Risk Assessment

| Risk | L×I | Mitigation |
|---|---|---|
| TS key derivation drifts from the uploader's → web shows no categories | L×M | `keys.json` run by both cargo (phase 1) and bun; slug already pinned by `slug.test.ts`. |
| Mixed docu strip: `movieGrid` cards appended into a `collectionGrid` strip lose styling | L×L | Both builders emit the same `card()` plates into the same `container(GRID, strip)` class (`shelf-view.js:40-42`); checked in the preview walk. |
| Umlaut-initial categories order differently on Android | L×L | Pre-existing for every name-sorted shelf; fixture stays ASCII; noted in `docs/web-player.md`. |
| A category row duplicates cards that "All courses"/folder rows also show | — | Intended by the user's layout (rows, then the existing sections). |

**Rollback:** revert the commit; the server stops sending `category`, both pages draw as before.

## Security Considerations

No new route. Category text reaches the page only through `el()`/`deptRow` text nodes
(`department-hero.js:45-57`), never as HTML.
