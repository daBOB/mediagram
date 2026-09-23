# Phase 02 — A catalog the server can swap while running

Priority: high · Status: todo

## Context
Everything derived from `db` is built once today (scout report, 2026-09-23):
- `web/src/index.ts:112` opens `db`; `:121` counts playable; `:225` `HeldSets(expectedChunks(db))`
- `web/src/routes.ts:331` closes over `db`; `:343` `SearchIndex` folded once
- `/api/player` and status `facts.catalog` are fixed values

## Design
One `LiveCatalog` holder: `{ db, search, expected, facts, version }`, replaced
whole on swap. Routes read `live.current` per request instead of closing over
`db`. `HeldSets` takes a getter for `expected`. On swap: open + build the new
one first, switch the reference, then close the old handle after a grace
period (streams already resolved their part locations at request start).
Posters stay where they are (plan open question 1).

## Files
- create `web/src/live-catalog.ts`
- modify `web/src/index.ts`, `web/src/routes.ts`, `web/src/server.ts`,
  `web/src/cache/held.ts`, `web/src/status/*`
- update tests that pass `{ db }` (`http`, `posters`, `shows`, `state-http`,
  `server-backpressure`, `cache-held`, `status-*`)

## Todo
- [ ] `LiveCatalog` with `swap(newDir)` returning whether sets changed
- [ ] routes + held + status read through it
- [ ] test: a request after a swap sees the new sets and the new search index;
      a stream opened before it finishes

## Success
Swapping mid-stream does not break the stream; `/api/sets` answers the new
catalog on the next request.

## Risks
Closing the old handle too early under a slow request — grace period plus
closing only when no route holds it.
