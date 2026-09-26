# Phase 4: Playback session reports (browser → server)

## Context links
- `web/public/lib/player.js:735-753` (`refreshPreload`: already reads ahead, fill rate, dropped frames and held), `:247` (`stop`), `:265-293` (`convert`, `onStarted({copied})`), `:756` (`openPlayer`), `:127` (`capBits`)
- `web/public/lib/adapt-playback.js:103-113`: the verdict is worked out and then thrown away; `:156` exposes only `fillRate`
- `web/public/lib/buffer-health.js:164-234`: verdict states `ok|behind|starving`
- `web/public/lib/playable.js:98` (`decidePlayback`), `web/src/transcode/video-copy.ts` (HEVC copy only when negotiated)
- `web/src/state/routes.ts:244-259`: `refuseUnsafe` (same-origin check + `application/json`)
- `web/src/server.ts:45-53`: bodies are capped at 64 KiB before routing
- `web/src/status/routes.ts:22`: `STATUS = /^\/api\/status$/`

## Overview
Priority P2 · pending. Each open player sends its own live stats. The System page on another device (a phone looking at the TV) shows every active viewer. The data can't stay in the browser, because the System page and the player are usually on different devices.

## Key insights
- The simplest workable path is a periodic `POST`. The server keeps an in-memory map with a 15s TTL. No websocket, no persistence, no merging with watch-state.
- Nearly every figure is already worked out on the page (`refreshPreload`). DRY: extract `readPlayback()` in `player.js` and use it in both `refreshPreload` and the reporter.
- Mode is derived on the client: `direct` when the element plays the file; otherwise `copied` from `onStarted`, which becomes `hevc-copy` when `set.vcodec` is HEVC, and `transcode` when not copied.
- A viewer outside the household gets a 404 from `/api/status/playback`, and the reporter then **stops for that page's lifetime**. It does not retry.

## Requirements
- Client report, every 5s while the player dialog is open, including while paused or stalled:
  `{ viewer, setId, title, mode, videoCodec, audioCodec, bitrateBits, ahead, health, fillRate, dropped, frames, paused, held }`
  - `viewer` is `crypto.randomUUID()` per page load and is not persisted
  - `bitrateBits` = `capBits ?? sourceBitrate(set)`
- Server validation:
  - Strings are clamped (`title` 200, codecs 16, ids 64)
  - Numbers must be finite and ≥ 0, otherwise null
  - `mode` and `health` must be one of their enum values, otherwise 400
  - At most **16** viewers; the stalest is evicted
  - The server stamps `from` (`request.client`) and `at`
- The snapshot gets `playback: [...]`. Each entry has `from` and `ageSeconds` but not `viewer`, following the "ids left out" rule at `snapshot.ts:44-49`.
- Closing the player stops the timer. The row expires within 15s. No DELETE is sent (KISS).

## Architecture
```
player.js ─ readPlayback() ─┬─ refreshPreload (existing HUD)
                            └─ playback-report.js: reportPlayback({read, post, schedule}) → stop()
                                   POST /api/status/playback (json, same-origin)
status/routes.ts: /api/status/playback → isOwnNetwork → refuseUnsafe → PlaybackReports.put(parse(body), client)
status/playback-reports.ts: Map<viewer,{report,from,at}>, prune on read/put (TTL 15 s, cap 16)
live-facts.ts → snapshot.playback
```

## Related code files
- Create:
  - `web/public/lib/playback-report.js`: pure `playbackReport(fields)` builder, plus the `reportPlayback` loop with fetch and schedule injected and stop-on-404. About 80 lines
  - `web/src/status/playback-reports.ts`: `validateReport(unknown)`, `class PlaybackReports { put, list(now) }`. About 100 lines
  - `web/src/write-guard.ts`: `refuseUnsafe` **moved** from `state/routes.ts:244`, so both routes share one guard
  - Tests: `web/test/playback-report.test.ts`, `web/test/status-playback-reports.test.ts`
- Modify:
  - `web/public/lib/player.js`: extract `readPlayback()`, start the reporter in `openPlayer` and stop it in the close path. About +15 net lines, and no other growth
  - `web/public/lib/adapt-playback.js`: keep `lastState` and expose `health: () => lastState` next to `fillRate`
  - `web/src/state/routes.ts`: import `refuseUnsafe`
  - `web/src/status/routes.ts`: the second path and POST handling. `StatusRouterOptions.playback`
  - `web/src/status/live-facts.ts`, `web/src/status/snapshot.ts`, `web/src/index.ts` (construct `PlaybackReports`)
  - Tests: `web/test/status-http.test.ts` (POST cases), `adapt-playback.test.ts`, `status-snapshot.test.ts`

## Implementation steps
1. Move `refuseUnsafe` to `write-guard.ts`, with no behaviour change. The state tests stay green.
2. `playback-reports.ts`: validator and store. `list(now)` prunes and sorts by `setId` and then `from`.
3. Status router:
   - Match `/api/status/playback`
   - Order: own network (404), then the method must be POST (405), then `refuseUnsafe`, then validate (400), then `put`, then 204
   - `GET /api/status` is unchanged
4. `adapt-playback.js`: record `verdict.state` in `look()`, reset it in `begin`, and expose `health()`.
5. `playback-report.js`: `reportPlayback({ read, post, intervalMs = 5000, schedule, cancel })`. It stops on a 404, 403, 405 or 415 answer; network errors are ignored until the next tick. It follows the same stop discipline as `pollStatus` (`status-lines.js:130`).
6. `player.js`: `readPlayback()` returns the fields. `refreshPreload` uses it. The reporter starts after `playing = set` and is stopped wherever `stop()` ends a title for good (close, and the next title opening).
7. Tests:
   - Validator: bad enum, huge strings, NaN, missing viewer
   - TTL expiry and the 16-viewer cap
   - HTTP: an outsider POST gets 404, a cross-origin POST 403, `text/plain` 415, a valid POST 204 and then shows in `GET`
   - Reporter: stops after 404, and no post after stop
   - `health()` accessor

## Todo
- [x] write-guard extraction (already done ahead of this plan — see notes)
- [x] PlaybackReports + validator + tests
- [x] router POST path + http tests
- [x] adapt-playback `health()`
- [x] playback-report.js + tests
- [x] player.js wiring (readPlayback DRY)
- [x] snapshot `playback` + test

## Implementation notes (2026-09-26)

Done. Deviations from the plan, which was written 2026-09-22 against an
older tree:

- **`refuseUnsafe` had already been extracted**, under a different name and
  location: `src/http/browser-write.ts` exporting `refuseUnsafeBrowserWrite`,
  used by `state/routes.ts` already. No `write-guard.ts` was created; the
  status route imports the existing function directly. Nothing to move.
- **`player.js` had no headroom at all.** The ratchet
  (`test/code-standards.test.ts`) lists it at exactly 996 lines, which is
  also its current size — not the plan's cited 1195. The plan's own
  DRY instruction ("extract `readPlayback()` in player.js") was followed in
  spirit but not literally: the field-reading logic moved to a new
  `playback-report.js` (`playbackFields`, `playbackMeta`, `playbackReport`,
  `reportPlayback`), and player.js keeps only the wiring — starting and
  stopping the reporter, and the `copiedOutput`/mode bookkeeping `onStarted`
  needs. Comments were also tightened at several of the new lines. Net
  result: player.js ends at 994 lines, under ceiling; `index.ts` needed the
  same compacting after wiring `PlaybackReports` (386 → 378, ceiling 381).
- **`mode` values match the plan** (`direct`/`copy`/`hevc-copy`/`transcode`)
  but the HEVC check could not reuse `playable.js`'s `normalize()`, which is
  not exported; a small local `HEVC_NAMES` set in `playback-report.js`
  duplicates the two spellings (`hevc`, `h265`) rather than exporting a new
  name from a file outside this phase's ownership.
- Server-side clamping in `playback-reports.ts` and the client-side mirror in
  `playback-report.js`'s `playbackReport()` intentionally duplicate the same
  rules (clamp strings, null out non-finite/negative numbers) — the server
  is the actual boundary and re-validates regardless; the client copy only
  avoids sending a body the server would 400 on for an ordinary reason
  (a codec name longer than expected, a transient `NaN` mid-metric).
- Verified end-to-end in the stub harness (`scripts/preview.ts`, which now
  wires a real `PlaybackReports` and `status` route — it had neither before
  phase 1): POSTed a report by hand with `curl`, saw it in the next `GET
  /api/status` with `from`/`ageSeconds` and no `viewer`, and watched it
  expire after 15s. Cross-origin POST 403, `text/plain` 415. Not verified:
  the real player posting from an actual open title, since the stub has no
  media path and the real player must not be started per the hard
  constraint — covered instead by `playback-report.test.ts` and the
  player.js wiring being typechecked and linted.

## Success criteria
- Tests pass.
- Stub harness: open a held title in one browser tab and the System page in another. Within 5s the page's snapshot lists one viewer with a mode, codecs and health. Close the player and the entry is gone within 15s.
- The player's own HUD readout is unchanged; `preload-readout.test.ts` stays green.

## Risks
| Risk | L×I | Mitigation |
|---|---|---|
| `player.js` grows further | H×L | Only the extraction and ≤15 lines. The logic lives in `playback-report.js` |
| A misbehaving LAN client floods reports | L×L | The 64 KiB body cap already exists, plus the 16-entry cap and TTL. Memory is bounded |
| Reporter left running after close | M×L | The stop function is called on every exit path. The TTL cleans up anything missed |

## Security
- Own network only (404 otherwise), same-origin and JSON-only (`refuseUnsafe`).
- Every field is clamped and validated. Rendering is `textContent` only (`dom.js` `el`), so a hostile title cannot inject markup.
- Viewer ids are not sent back out.

## Next steps
Phase 5 renders the four groups.
