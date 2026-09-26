# Phase 03 — Web live cache budget

## Context links
- `web/src/cache/store.ts:53` (`maxBytes` readonly), `:66` (`budget`), `:144-160` (`evict`)
- `web/src/index.ts:127` (cache built only when budget > 0), `web/src/config.ts:164` (`MEDIAGRAM_CACHE_MAX`, default 8G)
- `web/src/cache/held.ts` (`HeldSets.refresh`) — offline badges go stale after eviction
- `web/src/status/routes.ts:38,84` (held-bytes 15 s TTL)
- Phase 01 `state/settings.ts` (`cacheMaxBytes`)

## Overview
Priority P2. Status: **done 2026-09-26**. Budget changes apply immediately, evict down when
smaller, persist in the state DB, and override the env on next start. Built as specified:
`ChunkCache.maxBytes` is mutable, `setBudget(n)` sets then evicts through the existing coalesced
scan; `cache/budget.ts` adds `startBudget`, `validateBudget`, `applyBudget` (persist, then
apply, then refresh `held`). `status/routes.ts`'s held-bytes memoization gained an
`invalidateHeldBytes()` method on the returned route function (a property on the function
itself, not a second return value) so a shrink is reflected on the next poll rather than the
rest of the 15s TTL.

## Key insights
- Eviction is already LRU by atime over the real files (`store.ts:144`); shrinking is
  `maxBytes = n; await evict()` — no new algorithm.
- `evict()` walks every file; concurrent `put()`s also evict. Two concurrent walks are safe
  (`rm` with `force`), just wasteful — serialise the budget-triggered one.
- After eviction, `HeldSets` still claims titles are fully held until refreshed.
- Budget 0 at startup means no cache objects at all (`index.ts:127`); flipping to/from 0 live
  would mean constructing/destroying reader, held, thumbs. YAGNI: live floor 512 MiB; 0 stays env-only.

## Requirements
- F: `ChunkCache.setBudget(bytes)` → returns `{freedBytes}` after evicting to fit.
- F: precedence at start: `settings.cacheMaxBytes()` ?? env ?? 8G. Env 0 and no stored value → disabled as today.
- F: after a shrink: `held.refresh()`, status held-bytes cache invalidated.
- F: validation: integer, ≥ 512 MiB, ≤ 16 TiB; UI sends bytes (UI formats with existing `parseSize`-compatible units).
- NF: shrink is serialised (one at a time); a second request waits for the first.

## Architecture
`PUT /api/settings/cache {maxBytes}` (phase 05) → `settings.setCacheMaxBytes` → `cache.setBudget`
→ `held.refresh()` → response `{budget, heldBytes, freedBytes}`.
Persist first, then apply: a crash mid-evict still starts next time at the new budget.

## Related code files
Create: `web/src/cache/budget.ts` (resolve start budget from settings/env; validate; `applyBudget(cache, held, n)` serialised), `web/test/cache-budget.test.ts`.
Modify: `web/src/cache/store.ts` (`maxBytes` mutable, `setBudget`), `web/test/cache-store.test.ts`,
`web/src/status/routes.ts` (expose an `invalidateHeldBytes()` hook — small).
Delete: none. (Wiring into index.ts is phase 05.)

## Implementation steps
1. `store.ts`: drop `readonly` on `maxBytes`; `setBudget(n)` sets then `evict()`.
2. `budget.ts`: `startBudget(settings, envBytes)`, `validateBudget(n)`, `applyBudget(...)` with a promise-chain mutex.
3. Status: add invalidation of the TTL'd reading.
4. Tests (tmp dir, real files): shrink evicts oldest atime first to ≤ new budget; grow evicts nothing; concurrent applies serialise; validation bounds; start precedence table (db/env/default/0).

## Todo
- [ ] setBudget + tests
- [ ] budget module + tests
- [ ] status invalidation hook

## Success criteria
- Test: cache at 3 MiB, budget → 1 MiB ⇒ on-disk ≤ 1 MiB, LRU order respected.
- Test: stored value beats `MEDIAGRAM_CACHE_MAX`.

## Risks
| Risk | L×I | Mitigation |
|---|---|---|
| Evicting a chunk mid-read | L×L | reads load whole chunk into memory first (`store.ts` get); a vanished file is a miss |
| Big cache walk blocks settings response | M×L | respond after evict; UI shows "Freeing space…" |
| Budget larger than free disk | M×M | UI shows free space (statfs) as a hint; not enforced |

## Security
Route admin-gated (01). Value numeric only.

## Next steps
05 wires route + startup precedence; 08 mirrors on Android.
