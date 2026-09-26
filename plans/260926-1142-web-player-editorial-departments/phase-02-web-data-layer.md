# Phase 2 — Web data: v9 fields, portraits, similar, people search

**Priority:** P1. **Status:** done (0.62.0, 2026-09-26). The web tolerates a v8 index throughout.

## Context
`web/src/catalog/shows.ts` (optional-column probe, line 93), `web/src/catalog/routes.ts`,
`web/src/search/index.ts`, `web/src/package/posters.ts`, `web/public/lib/library.js`.

## Requirements
- `shows.ts`: `collectionId`, `collectionName` and `seriesType` are read if the column exists.
  Credits are read if the table exists (`sqlite_master` probe). Otherwise they are empty.
- `GET /api/titles/:kind/:id/credits` → `{cast:[{personId,name,role,portrait}], crew:[…]}`.
- `GET /api/people/:id` → the person's name, portrait and titles in *this library* only.
- Portrait serving reuses the poster route (`/api/posters/tmdb-person-{id}.jpg`).
- Search: add people (by name) and franchises to the index. Results stay grouped
  by type: `{titles, people, collections}`.
- Similar (client, `lib/catalog/similar.js`): the same franchise first, then the
  titles sharing the most genres, unwatched first, at most 12. **Pure function +
  unit test.**
- Runtime: films take `sets.duration`. Series take the season and episode counts they
  already have.

## Todo
- [ ] shows.ts fields  - [ ] credits + people routes  - [ ] search groups  - [ ] similar.js + test

## Success criteria
`bun test` is green with a v8 fixture (empty cast) and a v9 fixture (cast and a franchise).

## Security
New routes are read-only GETs, under the same profile and kids filtering as `/api/sets`.
**Person pages must not list titles a kids profile cannot see.**
