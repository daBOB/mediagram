# Phase 04 — Status snapshot and route

**Priority:** fourth (the real work) · **Status:** complete

## Overview

Collect the facts `index.ts` computes and discards, add the few counters that
do not exist yet, and serve the lot to local viewers only.

## Key insights

- The catalog verdict, the encoder and the cache budget are computed once at
  startup and printed. They need collecting, not recomputing.
- 404 rather than 403 for a non-local client: a 403 confirms there is
  something there, and this API has no authentication of its own.
- `routes.ts` is already twice the 200-line limit. `state/routes.ts` is the
  precedent for adding a surface without touching it.

## Architecture

```
index.ts ──StartupFacts──▶ server ──▶ createStatusRouter
                                        │
                     reads live: cache counters, registry.list(),
                                 telegram.connected, process.uptime()
```

## Requirements

- `GET /api/status` → 200 JSON for a local client, 404 for any other.
- `HEAD` answers the same status with no body. Any other method → 405.
- Counters are plain integers; nothing allocates per request.
- Cache block is `null` when caching is disabled.

## Related code files

- Create: `web/src/status/facts.ts` (the `StartupFacts` type),
  `web/src/status/snapshot.ts` (pure builder),
  `web/src/status/routes.ts` (the router)
- Modify: `web/src/cache/store.ts`, `web/src/cache/reader.ts` (counters),
  `web/src/transcode/registry.ts` (`list()`),
  `web/src/index.ts`, `web/src/server.ts`, `web/src/routes.ts` (one delegation)
- Create: `web/test/status-snapshot.test.ts`, `web/test/status-http.test.ts`

## Implementation steps

1. Counters on `ChunkCache` (hits, misses, evicted) and `CachedReader`
   (fetched upstream), each exposed as one `stats()` reader.
2. `list()` on `TranscodeRegistry`, returning `Session` plus watcher count.
3. `StartupFacts`, filled in `index.ts` from what it already computes.
4. `buildSnapshot(facts, live)` — pure, no IO.
5. `createStatusRouter`, local-only, returning `null` off its own path.
6. Delegate from `createRouter`, one line beside the state router.
7. Tests: the builder on fixed input; the route for local 200, remote 404,
   `HEAD`, and a bad method.

## Todo

- [ ] cache counters
- [ ] `registry.list()`
- [ ] `StartupFacts` collected in `index.ts`
- [ ] `buildSnapshot`
- [ ] `createStatusRouter`
- [ ] delegation from `createRouter`
- [ ] tests

## Success criteria

`curl localhost:8770/api/status` from the host returns the full snapshot; the
same request with a remote client address returns 404. No file over 200
lines. `scripts/check.sh` green.

## Security considerations

Host paths, the DRM render node and the uptime are behind the local-only
check. The package URL and key are absent from the snapshot. No `chat_id`,
`message_id` or `doc_id` anywhere in it.

## Risks

`trustProxy` makes the client address header-derived; the existing
`clientAddress` already handles that and the status route reuses the address
`server.ts` resolved, rather than reading the header itself.
