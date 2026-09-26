# Web player: streaming and startup performance

Branch `perf/web-streaming-and-startup`, worktree `../mediagram-web-perf` (isolated
from a concurrent magazine-restyle session working in the main checkout).

Source reviews: `plans/reports/perf-review-260925-2040-web-server-report.md`,
`plans/reports/perf-review-260925-2040-web-client-report.md`.
User picked all four groups (2026-09-25), including the two behaviour changes.

## Server (`web/src`) — owner: server implementer
- [ ] Cold read: serve fetched run before caching; cache writes in background
      (in-flight writes visible to `get` so a chunk is not fetched twice)
- [ ] Eviction: one scan at a time, coalesced; skip when a scan is running
- [ ] Presence check via `stat`, not reading 512 KiB chunks
- [ ] `partMedia`: bounded TTL cache + in-flight dedupe; on
      `FILE_REFERENCE_EXPIRED` drop entry and retry once (reverses the
      documented per-stream fetch — user approved)
- [ ] Startup: independent steps concurrent; startup sync not awaited
      (`held.refresh()` stays awaited)
- [ ] Index discovery: two independent reads concurrent
- [ ] gzip/brotli + ETag (304) for static JS/CSS/HTML and `/api/sets`; never
      for media streams

## Client (`web/public`) — owner: client implementer
- [ ] Startup requests: only profile state waits on the profile list
- [ ] Player modules loaded lazily / warmed in background, not before first shelf
- [ ] Stylesheets linked from `index.html` instead of `@import` chain; modulepreload hints
- [ ] Play opens without waiting on the watch-state refresh; resume applied when it lands
- [ ] HLS library fetched in parallel with the server's conversion request
- [ ] Bare URL: `history.replaceState` to `#/home` (no double render, Back correct)
- [ ] Shared `Intl.Collator` for title sorts

## Done when
`bun test` + `bun run lint` green, `scripts/check.sh` web parts pass, version bumped
(minor), changelog entry, merged to `main`.
