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
- [ ] write-guard extraction
- [ ] PlaybackReports + validator + tests
- [ ] router POST path + http tests
- [ ] adapt-playback `health()`
- [ ] playback-report.js + tests
- [ ] player.js wiring (readPlayback DRY)
- [ ] snapshot `playback` + test

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
