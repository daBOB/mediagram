# Phase 04 — Server-sent events to the page

Priority: high · Status: todo

## Context
- No server→page push exists today (scout report).
- `web/src/server.ts:216` already streams a `ReadableStream` body (`pump`);
  shutdown's `closeAllConnections` ends streams, which is right for SSE.
- `web/public/app.js`: `loadCatalog` compares the answer and redraws only on
  change; `shelfStale` defers a redraw while a title plays.

## Requirements
- `GET /api/events`: `text/event-stream`, `event: catalog` with the new version
  on every swap, a comment heartbeat so proxies keep it open.
- Page: one `EventSource`; on `catalog` and on every (re)connect →
  `loadCatalog()` → redraw or defer, as now. Refresh `catalogOf()` facts too.
- Remove the tab-return refresh (plan open question 3).

## Files
- create `web/src/catalog-events.ts` (subscriber set + SSE response)
- modify `web/src/routes.ts`, `web/public/app.js`
- tests: an SSE subscriber receives `catalog` after a swap; closed
  connections are dropped from the set.

## Success
A page left open shows a new upload without being touched.
