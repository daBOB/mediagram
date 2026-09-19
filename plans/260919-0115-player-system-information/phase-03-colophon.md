# Phase 03 — Colophon

**Priority:** third · **Status:** complete

## Overview

The footer says `N playable sets`. Make it say what the library adds up to,
and where the catalogue came from.

## Key insights

- Counts, runtime and bytes are arithmetic over `/api/sets`, which the page
  already holds. No new route for those.
- Only origin, publication date and schema come from the server, and
  `/api/player` is already the "facts about this session" route, fetched once.
- Publication date is safe for a remote viewer: it says when, never where.

## Requirements

- `/api/player` gains `catalog: { origin, publishedAt, schema }`.
- `origin` is `"package"` or `"local"`; `publishedAt` is `null` for local.
- The colophon renders from what the page has, and degrades to the old line
  if the catalog block is absent.

## Related code files

- Modify: `web/src/index.ts` (carry the pointer's `created_at` through),
  `web/src/routes.ts` (three fields on one existing response)
- Create: `web/public/lib/colophon.js`
- Modify: `web/public/app.js`
- Create: `web/test/colophon.test.ts`

## Implementation steps

1. Return the pointer's `created_at` from `openCatalog`, alongside the dir.
2. Thread a `catalog` descriptor into `createRouter` and onto `/api/player`.
3. `colophonLine(sets, catalog)` — pure, in its own module.
4. Call it in `app.js` where the footer is written.
5. Test the pure function.

## Todo

- [ ] `created_at` out of `openCatalog`
- [ ] `catalog` on `/api/player`
- [ ] `colophon.js`
- [ ] wired into `app.js`
- [ ] tests

## Success criteria

Footer reads e.g. `62 films · 108 episodes · 170 lessons · 412 hours ·
3.1 TB · catalogue published 3 days ago · schema 6`. `bun test` green.

## Risks

`/api/player` is fetched before the sets list in some paths — the colophon
renders on whichever arrives last rather than assuming an order.
