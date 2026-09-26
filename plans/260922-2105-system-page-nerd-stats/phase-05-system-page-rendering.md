# Phase 5: System page rendering

## Context links
- `web/public/lib/status-view.js:28-43` (`block()` leaves out null rows), `:84-120` (`renderStatus`), `:129` (`watchStatus`)
- `web/public/lib/status-lines.js`: 154 lines of pure formatters (`ofBudget`, `throughput`, `transcodeRows`, …)
- `web/public/lib/format.js`: `humanSize`, `bitrateLabel`
- `web/public/style.css:1067-1105`: `.status-block`, `.status-row`
- Memory: the look of the player is the user's decision, an editorial catalogue. Do not change it. **Keep the rows of label and figure in the page's own faces. No monospace grid, no charts.**

## Overview
Priority P2 · pending. Turns the new snapshot fields into sentences. Two new blocks, **Telegram link** and **Watching now**, plus more rows in **Conversion** and **This player**.

## Key insights
- Every wording decision stays in pure, tested line modules. `status-view.js` only places nodes.
- `status-lines.js` would go over 200 lines, so the new formatters go into two new modules split by concern: link lines, and session lines (playback and conversions).
- Null rows are already dropped by `block()`. The formatters return `null` for "cannot say", and the page never shows a label next to a blank.
- `memoryBytes` moved to `host.rssBytes` in phase 1. The "Memory" row changes accordingly.

## Requirements (wording, as a sentence per row)
- **Telegram link**, one row per DC:
  - `DC 4`: `1,284 requests · 3.1 GB · 180 ms typical, 640 ms slow` (p50, p95)
  - `, 2 failed` is added only when there were failures
  - `Flood waits`: `none`, or `3, 47 s waited in all`
  - `Reconnects`: `none`, or `2`
  - Nothing requested yet: one row, `DC`: `nothing requested yet`
- **Watching now**, one row per viewer, labelled by title:
  - `h264 / aac · direct · 8.2 Mbps · 1:12 ahead · keeping up · 0 dropped of 41,200 · from 192.168.1.23`
  - `falling behind` / `starving` replace `keeping up` when the verdict says so
  - `paused` or `cached` are added when they apply
  - No viewers: `nobody`
- **Conversion**, each session row extended:
  - `HEVC copy · 14.2× realtime · 38 segments · 12% CPU, 1 watching`, or `re-encode with h264_vaapi · 1.1× · …`
  - New row `Since starting`: `5 re-encodes, 3 copies, 2 HEVC copies`, or `none`
- **This player**:
  - `Memory` becomes `412 MB resident, 96 MB heap`
  - `Event loop`: `2 ms typical, 38 ms worst` (last 10s)
  - `Disk free`: one row per device, `118 GB of 460 GB, cache and conversions`
  - `Runtime`: `Bun 1.4.2`

## Architecture
```
status-view.js ── block("Telegram link", linkRows(snapshot.link))        ← status-link-lines.js
               ── block("Watching now", playbackRows(snapshot.playback)) ← status-session-lines.js
               ── block("Conversion", [...encoder, ...transcodeRows(t)]) ← status-session-lines.js (moved)
               ── block("This player", [...existing, ...hostRows(host)]) ← status-link-lines.js
```
`transcodeRows` moves from `status-lines.js` to `status-session-lines.js` (DRY: one home for session wording). `status-lines.js` keeps the generic helpers and `pollStatus`.

## Related code files
- Create:
  - `web/public/lib/status-link-lines.js`: `linkRows`, `hostRows`. About 90 lines
  - `web/public/lib/status-session-lines.js`: `playbackRows`, `transcodeRows` (moved and extended), `modeLabel`. About 110 lines
  - `web/test/status-link-lines.test.ts`, `web/test/status-session-lines.test.ts`
- Modify:
  - `web/public/lib/status-view.js`: new blocks, Memory row. Stays under 200
  - `web/public/lib/status-lines.js`: `transcodeRows` removed
  - `web/test/status-lines.test.ts`: the `transcodeRows` cases move to the session-lines test
  - `web/public/style.css`: only if a long row wraps badly; the existing `.status-row` should be enough
- Delete: none

## Implementation steps
1. Move `transcodeRows` and its tests, with no change. Tests stay green.
2. `status-session-lines.js`: `modeLabel`, the extended session row, `startedLine`, `playbackRows`, `healthWord(state, fillRate)`. Reuse `bitrateLabel`, `clockTime` and `humanSize`.
3. `status-link-lines.js`: `linkRows(link)` and `hostRows(host)`. Group numbers with `toLocaleString`, fixed to `en` so tests don't depend on the locale. Check what `format.js` already does first and follow it.
4. `status-view.js`: add the blocks in this order: Catalogue, Cache, Upstream, **Telegram link**, **Watching now**, Conversion, This player.
5. Tests for every formatter: empty, null and edge cases (no DCs, a null p95, speed `null`, cpu `null`, a viewer with no codec).
6. Stub-harness check: take screenshots with gstack `/browse` (`screenshot --viewport`), including a real conversion on a held set and two viewer tabs.

## Todo
- [x] transcodeRows move
- [x] session lines + tests
- [x] link/host lines + tests
- [x] view wiring
- [x] stub-harness screenshots reviewed

## Implementation notes (2026-09-26)

Done. `status-view.js`/`status-lines.js` were at 151/154 lines against the
plan's own citations (`status-view.js:28-120`, `status-lines.js` "154
lines") — matched exactly, so no drift to record there. Real deviations:

- **`transcodeRows` needed the encoder name it did not have.** The new
  wording ("re-encode with h264_vaapi") names the actual encoder for an
  `encode` session, which lives in `facts.encoder.name` — a startup fact,
  not part of a session. `transcodeRows` now takes `encoderName` as a second
  argument; `status-view.js` passes `encoder.name`.
- **Disk-free rows needed a purpose, not just a path.** `host.disks[].dirs`
  carries the raw cache/transcode directory paths (from phase 1), not
  labels. Rather than plumb a label through the server side, `hostRows`
  takes `cacheDir`/`transcodeDir` as extra arguments and maps a path to
  `"cache"` or `"conversions"` for the sentence at render time — the paths
  themselves already appear elsewhere in the snapshot (`cache.dir`,
  `transcodes.dir`), so this names nothing that was not already visible.
- **Event loop row uses p50/max, not p50/p99** — "2 ms typical, 38 ms worst"
  reads as two numbers, and `p99` would have been a third with no place in
  the sentence. `loopLagMs.p99` is still in the JSON for anyone who wants it.
- **`browser-application.test.ts`'s `statusSnapshot()` fixture** (an
  integration test rendering the real page, not part of this phase's own
  file list) needed the four phases' new required fields — it was still
  shaped for the pre-phase-1 snapshot and its two tests failed once
  `renderStatus` started reading `link`/`playback`/`host`. Updated in place;
  not owned by this phase but broken by the cumulative schema change and
  worth fixing rather than leaving for phase 6.
- **Verified with real screenshots**, not just prose: `plans/.../visuals/`
  has desktop (1400×1200) and phone (390×844) captures via gstack's
  `browse`, including one with a live `Watching now` row (posted by hand
  with `curl` against the stub, since the real player cannot be started).
  Sentences read exactly as specified, e.g. `hevc / aac · HEVC copy · 8.2
  Mbps · 1:12 ahead · falling behind · 3 dropped of 41,200 · cached · from
  127.0.0.1`; at phone width this row wraps to three lines, right-aligned,
  the same as the existing `Watch state` row already did. No row shows a
  label next to a blank; the new blocks sit in the same serif/thin-rule
  editorial type as the rest of the page — nothing reads as a bolted-on
  dashboard. One incidental finding during the check: a stale, disconnected
  `browse` backend had a leftover tab open on an unrelated port (8796,
  someone/something else's session) — worked around by opening a fresh tab
  rather than reusing tab 1, so nothing of this task touched that session.

## Success criteria
- Every formatter has tests, and `bun test` passes.
- The screenshot shows the new blocks in the same type and rhythm as the existing ones. No row shows a label next to a blank.
- `status-view.js`, `status-lines.js` and both new files are each under 200 lines.

## Risks
| Risk | L×I | Mitigation |
|---|---|---|
| Rows too long for a phone | M×L | The sentences are ordered most-important-first. Check at phone width in the stub |
| The page reads as a dashboard | M×M | Sentences, not columns. No new typefaces. Review against the editorial look |

## Security
Rendering goes through `el()`/`textContent`. Strings that came from viewers (`title`) are never used as HTML.

## Next steps
Phase 6.
