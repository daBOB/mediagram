# Perf review: web server (web/src) vs framework-agnostic react-best-practices rules

Report-only. No code changed. Scope: web/src (server, http, cache, telegram, state, catalog, search, transcode, status, application, index.ts). web/public excluded.

## Measurements (read-only, real data on this machine)
- Chunk cache: 52,328 files / 26 GB (budget from `.env` `MEDIAGRAM_CACHE_MAX=64G`, so eviction never deletes; scan still runs).
- `ChunkCache.sizeOnDisk()` (= the scan `evict()` does) : 42–47 ms warm per call.
- `/api/sets` via `createCatalogRouter` on the 903-set local index: 5.6–7.5 ms, 485 KB JSON. Per-row asset queries ≈ 1.8 ms, `posters.has` ×2 ≈ 0.6 ms.
- `/api/search`: 0.5–3.5 ms.
Nothing on the Telegram side was exercised (no player started, per project memory).

---

## Findings, ranked by viewer impact

### 1. [HIGH] Cold-run bytes wait on 8 serial chunk writes, and each write does a full-cache scan
Rules: `async-defer-await`, `server-after-nonblocking` (bookkeeping before the response bytes).
- `cache/reader.ts:140` `await this.fillRun(...)` then yields at `:142-150`. Inside `fillRun`, `cache/reader.ts:205-213` does `await this.cache.put(...)` per chunk, sequentially.
- `cache/store.ts:111-136`: `put` awaits `writeChunk`, which awaits mkdir+writeFile+rename **and then `await this.evict()`** (`:135`), and `evict()` always runs `entries()` = full walk + stat of every chunk (`:151`), even when under budget (it returns only after summing).
- Impact: after the Telegram fetch of a run (up to `MAX_RUN_BYTES` = 8 chunks = 4 MiB) the viewer still waits ≈ 8 × (~45 ms scan + write) ≈ **0.4–0.5 s extra before the first byte of every cold run**, growing linearly with cache file count (52k now; the status comment in `status/routes.ts:32` already expects 40k+). Bites on every seek into uncached territory, every first play, and every sequential cold read that outruns readahead. Also: readahead (`warm`, `reader.ts:241-250`) and a foreground read run scans concurrently, each allocating ~52k stat promises on the event loop — CPU contention that delays `pump` writes of other streams.
- The `store.ts:178` comment already says "a viewer waits on that write"; the recent parallel walk shortened each scan, this finding is about how *often* and *where* it runs (not re-reporting the walk).
- Minimal fix (two independent parts, either helps):
  a. **Yield before persisting.** In `readStream`, yield the run's slices from `chunks` first, then persist; or have `fillRun` start the puts without awaiting (`Promise.all` them into a tracked set like `warming`, awaited by `stop()`/`settle()`).
  b. **Coalesce eviction.** Single-flight `evict()` + a dirty flag (at most one scan running, one follow-up), and/or skip the scan unless bytes-written-since-last-scan could have crossed the budget (keep last total in memory; rescan every N MiB or T seconds to stay true-to-disk).
- Behaviour/ordering changes to note:
  - (a) A chunk yielded but not yet on disk: a second reader of the same chunk misses and refetches from Telegram. Mitigate by having `ChunkCache.get` await `inFlight.get(path)` first (the map already exists, `store.ts:37`). Shutdown must await pending writes (else a stray `.tmp`, already tolerated by `held.ts:184` and `store.ts:186`).
  - (b) Budget can be exceeded transiently by the bytes written since the last scan. `put`'s doc (`store.ts:106-110`) already disclaims budget compliance on resolution, so contract-compatible. Tests that assert eviction immediately after `put` would need `await cache.evict()`.

### 2. [HIGH] Extra Telegram round trip (`channels.getMessages`) before every fetched run
Rule: `server-cache-lru` (plus in-flight dedup, the `cache()`-style request dedup idea).
- `telegram/source.ts:182` (`fetchPart`, cached path) and `:127` (uncached path) call `telegram.partMedia(messageId)` every time → `telegram/client.ts:85-102` invokes `channels.GetMessages` each call. It runs **inside** the download-gate slot (`source.ts:167`), so it also holds one of the 4 slots for that RTT.
- Documented reason (`client.ts:81-83`): file references expire and a stale one fails mid-download. Respected — but that argues for a short TTL + retry, not for no cache.
- Impact: +1 RTT (150–450 ms, per `cache/strategy.ts:6`) on time-to-first-byte of every cold seek, and one extra flood-counted request per 4 MiB run (readahead included) — ≈ one per 8 s of a 4 Mbit/s stream. At film start several readers (browser ranges, ffprobe audio probe, readahead — see `download-gate.ts:4-8`) each issue the same `GetMessages` for the same message concurrently.
- Minimal fix: in `Telegram` (the one file that knows MTProto), a small bounded map `messageId → {media, at}` (e.g. 64 entries, insertion-order eviction like `ReadaheadTracker`, TTL ~10–30 min), plus an in-flight `Map<messageId, Promise>` to dedup concurrent lookups. In `fetchPart`, on `FILE_REFERENCE_EXPIRED` (or any `FILE_REFERENCE_*`) invalidate the entry and retry once.
- Flood: this strictly **reduces** Telegram requests. Behaviour change: a stale reference now fails the first `getFile` and costs a retry instead of never happening; the retry must stay inside the same gate slot so it cannot add concurrency. Uncached path (`source.ts:127`) streams via `iterDownload` and can fail mid-stream — only apply the cache to `fetchPart`, or restrict retry to the start of the stream.

### 3. [MEDIUM] Presence checks read whole 512 KiB chunks from disk
Rules: `js-early-exit` / `async-defer-await` (do the cheap check, skip the expensive work).
- `cache/reader.ts:128-134` (run-extension probe), `:169` (`fill`, series preload), `:244` (`warm`, readahead) call `cache.get`, which reads the full file (`store.ts:86`) only to test `!== null`.
- Impact:
  - `warm`: with readahead=4 (config default) and a sequential viewer whose next chunks readahead already fetched, **every** browser range request re-reads up to 4 × 512 KiB from disk to discover they exist, then the viewer reads them again → ~2× disk reads during steady playback. Page cache usually absorbs it; still CPU + allocation of 2 MiB per request on the event loop.
  - `fill` (preload of next 2 episodes): for a partially cached episode it reads every held chunk fully (up to GBs) just to list the missing ones. `held.check` only short-circuits fully held sets.
  - Run-extension probe: on a hit it breaks and the outer loop reads the same chunk again (double read, and the hit is counted twice in `stats()` — flatters the hit rate on `/api/status`).
- Minimal fix: add `ChunkCache.has(setId, partIdx, index, expectedSize)` using `stat` (size compare, same rm-on-wrong-size rule) and use it at those three sites. For the run-extension probe, alternatively keep the bytes of the chunk that broke the run and yield it instead of re-reading.
- Behaviour change: `stat` does not bump atime (`get` does via `utimes`, `store.ts:96-97`). For `warm` that is arguably better (speculation should not refresh LRU); for `fill` decide whether preloaded-already-present chunks should be touched — if yes, `utimes` in `has` too. Hit/miss counters would stop counting probes (more accurate).

### 4. [MEDIUM] Startup: independent work awaited serially, and a full sync round before listen
Rules: `async-parallel`, `async-defer-await`, `server-after-nonblocking`.
- `index.ts:69-209` awaits in sequence: `io.connect` → `openCatalog` (needs telegram) → `cache.sizeOnDisk()` (`:106`, only for a log line; a full scan, cold-FS 52k stats can be seconds) → `io.detectEncoder()` (`:118`, spawns ffmpeg) → `rm(transcodeDir)` (`:124`) → `updates.start()` (`:193`, which awaits `syncOnce(sync,"start")` in `application/lifecycle.ts:53` = getMessages(pinned) + one download per device + maybe editMessage: several RTTs) → `held.refresh()` (`:207`).
- Impact: restart-only, but the page is unreachable for the sum. `sizeOnDisk`, `detectEncoder`, `rm` do not depend on Telegram or each other; the sync round's result is not needed to serve anything (local DB is source of truth, `state/sync.ts:4-8`; pulls are announced to open pages via `announcingPulls` → SSE `state`).
- Minimal fix: start `sizeOnDisk`/`detectEncoder`/`rm` promises before `io.connect` and await them where used (`Promise.all`). In `LibraryUpdates.start`, subscribe then `void syncOnce(...)` instead of awaiting (keep a handle so shutdown's `syncOnce("stopping")` still serialises via `StateSync.once` sharing). Keep `held.refresh()` awaited — documented (`held.ts:66-67`, `index.ts:203-204`) so the first page's offline badges are right; it can still run concurrently with the others.
- Ordering change: first page load may show pre-sync Continue state, corrected by the SSE `state` event seconds later. Startup error surfacing: a failed encoder probe still throws, just later in the await — fine inside the existing try.

### 5. [LOW-MEDIUM] Index discovery: two independent Telegram reads in series
Rule: `async-parallel` / `server-parallel-fetching`.
- `channel-index/find-newest-channel-index.ts:39` (pinned) then `:46` (marker search): neither uses the other's result until merge.
- Impact: +1 RTT (150–450 ms) on every catalog refresh — startup when following the channel, and on every index push event (catalog update latency to open pages).
- Minimal fix: `Promise.allSettled([pinned, marked])`; rethrow pinned failure, swallow marked failure as today. Merge order unchanged (pinned first, then marked-not-already-found).
- Flood: 2 concurrent read requests (not pins); negligible, but it is +1 concurrent request — acceptable, not a burst.

### 6. [LOW] Catalog response: per-row queries and full summary bodies for a boolean
Rules: `js-cache-function-results`, `js-index-maps` (N+1 → one map).
- `catalog/routes.ts:50-51`: per row `summary(db, setId) !== null` (fetches the whole summary text just for a null check) and `subtitleLanguages(db, setId)` → 2×903 queries per `/api/sets`; same for search hits.
- Measured ≈ 1.8 ms of the 5.6–7.5 ms total. Not worth much on its own.
- Minimal fix (if touched anyway): in `createCatalogRouter` (already per-catalog, immutable per router, like `provider` at `:34`) build once `Set<setId>` with a summary and `Map<setId, string[]>` of subtitle langs (two queries). Behaviour: none — router is rebuilt on catalog swap.
- Outside the rule set but bigger for remote viewers: 485 KB of uncompressed JSON per catalog load/refresh; no gzip/br anywhere in `src`. `Content-Length` must stay stated (`response.ts:88-101`), which is compatible with compressing a buffered body up front.

---

## Rules checked, nothing worth fixing
- `async-parallel` elsewhere: `status/routes.ts:116` already `Promise.all`; `held.ts:165-173` batched; `dir-bytes.ts:39` parallel; `telegram/state-channel.ts:58-61` serial downloads are **deliberate** (flood; respect). `transcode/server.ts` / `registry.ts` awaits are dependent (rm→mkdir→spawn→poll).
- `async-dependencies`: `pump` read→write chain inherently sequential (backpressure by design, `server.ts:89-99`, `source.ts:68-69`).
- `async-api-routes`: routes start no promise early that is later awaited; `routes.ts:63` awaits the status router per request, but it returns `null` after one regex test (one microtask). Artwork `sizeOf` → read is dependent. `sheets.ensure` is correctly fire-and-forget (`artwork-routes.ts:36`).
- `server-after-nonblocking` elsewhere: watch-state writes are sync SQLite single-row upserts, no sync push on the request path (sync runs on timer/push event). Preload is `202` + background worker. Cache `utimes` already fire-and-forget (`store.ts:97`).
- `server-cache-lru` elsewhere: `ReadaheadTracker` bounded (64); `AudioTrackReader.cache` bounded by catalog size (~900) — survives catalog swaps with stale keys, negligible; `HeldSets` TTL + single-flight; status scans memoised 15 s. Search index folded once per catalog.
- `server-parallel-fetching`: n/a beyond #5 (no component tree).
- `js-set-map-lookups`: `source.ts:107` `locations.find` per step — steps ≤ part count (single digits). `merge.ts` already uses Maps. `server.ts:158` 3-element `includes`. Fine.
- `js-combine-iterations`: `SearchIndex.search` single pass, sorts then slices; `excerpt` only for returned hits. Fine.
- `js-hoist-regexp`: route/path regexes hoisted (`routes.ts:21-26`, `catalog/routes.ts:15-19`, `state/routes.ts`). Literals inside `chunkPath` (`key.ts:60`), `SheetStore.path`, `waitForFirstSegment` are engine-cached literals, non-global, cold or cheap. `search/index.ts:111` `localeCompare(..., "de")` builds collator per compare — hoisting an `Intl.Collator("de")` is a micro-win (search is 0.5–3.5 ms); optional.
- `js-early-exit`: routers exit early; `HeldSets.isHeld` early-returns at `have >= want`. Only gap is #3.
- `js-cache-function-results`: `providerFactsByShow` per catalog; `hlsLibraryBytes` memoised; `posters.has` deliberately uncached (`posters.ts:40-49`, documented; measured 0.6 ms) — respect.

## Unresolved questions
- teleproto `iterDownload`: are the 8 × 512 KiB requests of one run issued sequentially? If yes, TTFB of a cold run is ~8 RTTs, which dwarfs #1/#2; shrinking the *first* run after a seek (e.g. 1–2 chunks, then grow) would be the next lever. Not verified — library internals, not read.
- File-reference lifetime for channel documents in practice (sets the TTL in #2). Needs a live measurement; do not probe with pins.
