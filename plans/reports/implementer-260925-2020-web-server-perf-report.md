# Web server performance fixes: implementation report

Plan: `plans/260925-2020-web-streaming-and-startup-perf/plan.md`, "Server" section.
Source review: `plans/reports/perf-review-260925-2040-web-server-report.md`.
Worktree: `/home/andre/Workspace/mediagram-web-perf`, branch `perf/web-streaming-and-startup`.

## Files changed

Source (`web/src`):
- `cache/store.ts` — write-behind visibility (`pending`), `has()`, coalesced eviction
- `cache/reader.ts` — background chunk writes, `has()` at the three presence-check sites
- `telegram/client.ts` — `partMedia` routed through a cache, `forgetPartMedia`
- `telegram/media-cache.ts` (new) — bounded TTL cache with in-flight dedupe
- `telegram/source.ts` — retry-once-on-expired-file-reference, both download paths
- `application/lifecycle.ts` — `LibraryUpdates.start()` no longer awaits the initial sync round
- `channel-index/find-newest-channel-index.ts` — pinned/marker reads run concurrently
- `index.ts` — `sizeOnDisk`/`detectEncoder`/`rm(transcodeDir)` run concurrently
- `http/compression.ts` (new) — gzip/br negotiation + strong ETag/304, memoized by content hash
- `http/contracts.ts` — `acceptEncoding`, `ifNoneMatch` on `PlayerRequest`
- `http/static-files.ts` — routes through `negotiatedResponse`
- `catalog/routes.ts` — shared `json()` helper routes through `negotiatedResponse`
- `server.ts` — populates the two new request fields from headers

Tests (`web/test`, all new or extended):
- `cache-store.test.ts` — `has()`, pending-write visibility, eviction coalescing (+13 tests)
- `cache-write-behind.test.ts` (new) — proves a read does not await its own persistence
- `cache-source.test.ts`, `cache-shutdown.test.ts` — no behavioural changes needed; two tests
  in `cache-store.test.ts`'s old body gained `reader.settle()` before asserting on the
  now-backgrounded write/eviction (see Deviations)
- `telegram-media-cache.test.ts` (new) — TTL, dedupe, invalidate, bound
- `telegram-file-reference-retry.test.ts` (new) — retry-once on both download paths, no
  retry once bytes are on the wire, no retry on unrelated errors, no double retry
- `channel-index-discovery.test.ts` — unchanged, passes against the concurrent version
- `application-lifecycle.test.ts` — added a test proving `start()` resolves without waiting
  on a slow sync round
- `application-startup.test.ts` — unchanged, passes against the concurrent startup ordering
- `http-compression.test.ts` (new) — unit coverage of `negotiatedResponse`
- `static-file-failures.test.ts`, `http.test.ts` — integration coverage: real gzip round trip
  and 304 against `/app.js` and `/api/sets`, and proof a stream response is never compressed

## What changed and why

**Cold-read path.** `fillRun` no longer awaits `cache.put`; it tracks the write in a new
`persisting` set (`settle`/`stop` await it, same as `warming`). `ChunkCache` keeps the bytes
of an in-flight write in a `pending` map, consulted first by both `get` and the new `has`, so
a concurrent reader of the same chunk is served from there rather than missing and refetching
from Telegram. `ChunkCache.evict()`/the write-triggered scan now share one running scan via
`scheduleEviction()`: a write landing mid-scan sets `evictAgain` rather than starting a second
walk, and the shared promise's internal loop reruns once more before resolving. `evict()` was
changed from `async evict()` to a plain method returning `scheduleEviction()`'s promise
directly — an `async` wrapper would have handed back a fresh Promise object each call even
though both delegate to the same run, breaking the sharing the tests verify by reference.

**Presence checks.** `ChunkCache.has()` is a `stat`, not a read: no bytes, no hit/miss
counting, no `utimes`. Used at the run-extension probe, `fill`, and `warm`. This also fixes
the double-read/double-hit-count the report flagged at the run-extension probe: the chunk
that stops a run is no longer read there, it is read once by the outer loop's next pass.

**`partMedia`.** `Telegram` now holds a `MediaCache` (256 entries, 30 min TTL, insertion-order
eviction, in-flight dedupe — same trade `ReadaheadTracker` makes). `forgetPartMedia` drops an
entry. Both `TelegramSource.bytesOf`'s uncached path and `fetchPart` (the cached path's
fetcher) catch a `FILE_REFERENCE*` error (matched on `.errorMessage`, teleproto's field for
the literal server string) and retry once with a forgotten+refetched reference. The uncached
path only retries if no byte has been yielded yet for that step, since a retry after bytes
are already on the wire would duplicate or skip them; `fetchPart` collects into a buffer
before returning anything, so it can always retry the whole call. Both stay inside the same
`DownloadGate` slot — the retry is sequential continuation of the same async call, not a new
one.

**Startup.** `cache.sizeOnDisk()`, `io.detectEncoder()`, and `rm(transcodeDir)` now run via
one `Promise.all` instead of three sequential awaits. `LibraryUpdates.start()` fires the
initial sync round with `void syncOnce(...)` instead of awaiting it — subscription still
precedes it, but a page is servable without waiting on a round trip to the channel; shutdown's
own round still coalesces with it via `StateSync.once`'s existing single-flight sharing.
`held.refresh()` is untouched (still awaited, per its own comment).

**Index discovery.** The pinned and marker reads in `findNewestChannelIndex` now fire together
and are awaited in sequence rather than each being awaited before the next starts; only the
pinned read can still fail the whole call, the marker read is still `.catch`-swallowed.

**Compression.** `http/compression.ts` gzips or brotlis (brotli preferred) a body when
`Accept-Encoding` allows it, the content-type is text (html/js/css/json/svg), and the body is
≥1 KiB. Every response through it carries a strong ETag (sha1 of the body); a matching
`If-None-Match` short-circuits to a bodiless 304 before anything is encoded. Compressed forms
are memoized in a bounded (64-entry) `Map` keyed by the body's own content hash rather than by
path or route: a static file reread from disk or a catalog JSON body rebuilt after a swap gets
the same cached compression the instant its bytes are unchanged, with no invalidation logic
needed. Wired into `staticResponse` (all of `/`, `/app.js`, `/lib/**`, `/lib/hls.mjs`) and into
`catalog/routes.ts`'s shared `json()` helper (covers `/api/sets`, `/api/search`, `/api/shows/*`,
`/api/sets/*/held`, `/api/sets/*/audio`). Media streams, byte ranges, and `font/woff2` never
go through this path — `stream.ts`'s routes build their own `PlayerResponse` directly and
never call `negotiatedResponse`, and woff2 falls out of the compressible-type list.

## Deviations from the review's suggested minimal fixes

1. **Startup parallelization scope.** The review suggested starting
   `sizeOnDisk`/`detectEncoder`/`rm` "before `io.connect`". `test/application-startup.test.ts`'s
   "catches an index published after the initial read and before subscription" test relies on
   `detectEncoder`'s mock mutating shared state at a specific point in the sequence — strictly
   after the first channel-index read and strictly before subscription. Starting these three
   before `connect()` would let that mutation happen before the first read ever runs,
   collapsing the two-read race the test exists to prove. Implemented instead: the three run
   concurrently with each other, in their existing position (after catalog/db setup, before
   `updates.start()`) — removes two of the three serial waits without moving anything across
   the ordering boundary the test encodes. Verified: the existing test passes unchanged.
2. **`evict()` signature.** Changed from `async evict(): Promise<number>` to a plain method.
   Not in the plan text, but required for the "callers that await it still get the freed-bytes
   result" / coalescing contract to hold by reference — an `async` method always wraps its
   return value in a new Promise, so `cache.evict() === cache.evict()` was false even though
   both delegated to the same scan. Caught by a test asserting that equality.

## Test evidence

```
bun test
 1855 pass
 0 fail
 12365 expect() calls
Ran 1855 tests across 136 files.

bunx tsc --noEmit -p .
2 pre-existing errors, both outside this change's scope and present before it
(test/browser-application.test.ts, test/search-shared-fixtures.test.ts — confirmed via
git stash comparison; the former belongs to the concurrent client-side implementer's
in-progress edits in this shared worktree)

bun run lint   (covers web/public only, per package.json; unaffected — no public/ files touched)
$ eslint public --max-warnings 0
(clean)
```

## Unresolved / handed off

- The two pre-existing tsc errors are outside my file ownership (`test/browser-application.test.ts`
  is being actively edited by the client-side implementer in this same worktree;
  `test/search-shared-fixtures.test.ts` predates this work entirely). Not touched.
- `web/public/**` untouched, per ownership boundary.
- No version bump, docs, or commit — left to the lead per instructions.

**Status:** DONE
