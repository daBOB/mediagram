# Phase 1: Snapshot plumbing and the process/host group

## Context links
- `web/src/index.ts:271-298`: the `live()` closure that feeds the status router today
- `web/src/status/routes.ts:98-124`: the route awaits the scans, then calls `buildSnapshot`
- `web/src/status/snapshot.ts:13-41` (`LiveFacts`), `:50-83` (`buildSnapshot`)
- `web/src/status/facts.ts:45-55` (`StartupFacts`)
- Tests: `web/test/status-snapshot.test.ts`, `web/test/status-http.test.ts`

## Overview
Priority P2 · pending. Moves the `live()` assembly out of `index.ts`, which is 339 lines, into `status/live-facts.ts` and makes it async. Then adds the host group: RSS, heap, event-loop lag, free disk under the cache and transcode directories, and the Bun version.

## Key insights (verified 2026-09-22, Bun 1.4.2)
- `perf_hooks.monitorEventLoopDelay` works in Bun 1.4.2. Probed: a 120ms busy loop shows up as max ≈110ms, in nanoseconds. No hand-written sampler is needed.
- `fs.statfs` works. Free space is `bavail * bsize`.
- `process.memoryUsage().heapUsed` is real, but `heapTotal < heapUsed` in Bun. So only `heapUsed` is reported.
- Today's figure is `process.memoryUsage.rss()` (`index.ts:295`). Keep RSS as it is and add heap alongside.
- `live` is sync today (`StatusRouterOptions.live`, `routes.ts:43`). Phases 3 and 4 need async reads (segment counts, pruning reports), so it becomes `() => Promise<...> | ...` and the route awaits it.

## Requirements
- Functional: the snapshot gains `host`:
  `{ rssBytes, heapBytes, loopLagMs: { p50, p99, max } | null, disks: [{ dirs: string[], freeBytes, totalBytes }], bun }`.
  `memoryBytes` moves into `host.rssBytes`. That is a breaking change to the JSON, and the only reader is `status-view.js`, which phase 5 updates (pre-release breaking changes are welcome).
- Loop lag is measured over the last **complete 10s window**, so one hiccup at startup does not stay in the figure forever.
- Disk: `statfs` runs on each directory. Directories on the same device (the same `stat().dev`) are merged into one row.
- Non-functional: the timer is `unref()`'d. The status route must not become slower than the longer of its existing scans.

## Architecture
```
index.ts ── readLiveFacts(deps) ─┐           (status/live-facts.ts, new)
status/loop-lag.ts  (new) ───────┤  window stats, 10 s rotate
status/disk-free.ts (new) ───────┤  statfs + dev dedupe
                                 ▼
routes.ts: await live() → buildSnapshot(facts, live) → JSON
facts.ts: StartupFacts.runtime = { bun: Bun.version }
```

## Related code files
- Create:
  - `web/src/status/live-facts.ts`: `readLiveFacts(deps)`, with the body moved from `index.ts:273-297`
  - `web/src/status/loop-lag.ts`: `startLoopLag({ windowMs })` returns `{ reading(), stop() }`
  - `web/src/status/disk-free.ts`: `diskFree(dirs)` returns `[{dirs, freeBytes, totalBytes}]`
  - `web/test/status-loop-lag.test.ts`, `web/test/status-disk-free.test.ts`
- Modify: `web/src/index.ts`, `web/src/status/routes.ts`, `web/src/status/snapshot.ts`, `web/src/status/facts.ts`, `web/test/status-snapshot.test.ts`, `web/test/status-http.test.ts`
- Delete: none

## Implementation steps
1. Create `live-facts.ts` and move the closure body into it, with no change in behaviour. `index.ts` passes `{cache, reader, transcodes, telegram, bytes}`. Run the tests and confirm they stay green.
2. Widen `StatusRouterOptions.live` to allow a Promise, and `await` it in the route (`routes.ts:108`).
3. `loop-lag.ts`: one histogram with `resolution: 20`. A 10s `setInterval(...).unref()` copies p50, p99 and max, converted from nanoseconds to ms, into `last`, then calls `reset()`. `reading()` returns `last`, or `null` before the first window completes. Take the clock as a parameter so it can be tested.
4. `disk-free.ts`: `Promise.all` of `stat` and `statfs` for each dir, grouped by `dev`. A missing dir returns no row and does not throw, the same as `dirBytes`.
5. `facts.ts`: add `runtime: { bun: string }`. `index.ts` fills it from `Bun.version`.
6. `snapshot.ts`: add `host`, and remove the top-level `memoryBytes`.
7. Tests: loop-lag window rotation, using a fake timer or an injected histogram. Disk-free dedupe on the same dir given twice, and a missing dir. Snapshot shape. The HTTP route still returns 404 to a caller outside the household.

## Todo
- [x] live-facts extraction (no behaviour change)
- [x] async `live`
- [x] loop-lag window + test
- [x] disk-free + test
- [x] Bun version fact
- [x] snapshot `host` + tests updated
- [x] `bun test` green, `bunx tsc --noEmit` clean

## Implementation notes (2026-09-26)

Done. Deviations from the plan, which was written 2026-09-22 against an
older tree:

- The `live()` closure the plan cites at `index.ts:271-298` had moved to
  `index.ts:310-339` by the time of implementation (unrelated streaming work
  landed in between). Moved from there instead; behaviour is unchanged.
- `host.bun` is assembled in `buildSnapshot` from `StartupFacts.runtime.bun`,
  not inside `live-facts.ts`'s reading — the version is a startup fact, fixed
  for the process's life, so it does not need re-reading every request. The
  live-computed part of the group is typed separately (`HostLiveFacts`) and
  `HostFacts` extends it with `bun`.
- `index.ts` did not shrink by 20 lines as the plan's success criterion
  expected: the inline `live()` closure (about 30 lines) was replaced by a
  shorter call into `readLiveFacts`, but the loop-lag timer now has to be
  wired through `resources.timers` at the call site so shutdown still clears
  it, which cost back most of what was saved. Net line count is unchanged;
  the closure itself is gone from `index.ts`, which was the actual point.
- `scripts/preview.ts` (the stub harness the plan calls `stub-offline.ts` —
  no file of that name exists in this tree) had no `status` route at all
  before this phase, so `/api/status` 404'd in the harness. Wired a minimal
  `StartupFacts` and a `live()` with no cache, no transcodes and no Telegram,
  so every group answers honestly that it has nothing to report. Verified by
  running the harness and polling `/api/status` directly: `host.rssBytes`,
  `heapBytes`, `disks` and `bun` are present immediately, and `loopLagMs`
  goes from `null` to a real `{p50,p99,max}` reading after the first 10s
  window completes.

## Success criteria
- `bun test` passes.
- The `host` object in the stub-harness `GET /api/status` has non-null `rssBytes`, `heapBytes` and `bun`. `loopLagMs` is non-null after 10s.
- `index.ts` shrinks by at least 20 lines.

## Risks
| Risk | L×I | Mitigation |
|---|---|---|
| `monitorEventLoopDelay` changes in a future Bun | L×M | `reading()` returns `null` if `enable()` throws. The row is then left out, as `block()` already does for null values |
| Cache and transcode dirs on one filesystem show up twice | M×L | Dedupe by `dev` |

## Security
Directory paths are already shown (`Directory` rows). Free and total bytes are not sensitive. The route keeps its own-network 404 (`routes.ts:103`).

## Next steps
Phase 2 adds `link` to the same two files.
