# Phase 2: Telegram link counters

## Context links
- `web/src/telegram/client.ts:43-73` (`Telegram.connect`), `:87-102` (`partMedia` → `client.invoke`)
- `web/src/telegram/source.ts:120` (`iterDownload`, no-cache path), `:149` (`partFetcher` → `iterDownload`)
- `web/src/login.ts:27,102-109`: an existing `baseLogger` from `teleproto/extensions`
- teleproto **1.229.0** (exact pin, `web/package.json:17`). Upstream source was read at tag `v1.229.0` (sha `9b0ebd1`):
  - `client/downloads.ts:798-806`: `iterDownload` calls `client.invoke(new Api.upload.GetFile(...), dcId)`
  - `client/TelegramClient.ts:2925-2930`: `invoke(request, dcId)` is a public instance method
  - `client/users.ts:66-84`: a FLOOD_WAIT at or under `floodSleepThreshold` (60s) is **slept inside invoke**. It is visible only as a log line (`Sleeping for ${s}s on flood wait (Caused by …)`). A longer wait is thrown as `FloodWaitError` with `.seconds`
  - `extensions/Logger.ts:35,96-104`: `Logger.handler`, when set, receives every record at or above the level
  - `network/MTProtoSender.ts:257-264` + `TelegramClient.ts:3184-3192`: only the **main** sender emits `UpdateConnectionState`, through `_handleUpdate`

## Overview
Priority P2 · pending. Adds per-DC request counts, latency p50/p95, bytes, errors, the FLOOD_WAIT count and total wait, and main-connection reconnects.

## Key insights
- The one seam is a subclass that overrides `invoke`. Every download request passes through it: `iterDownload` calls `client.invoke` on the instance, and so does `partMedia`. Per-DC bytes are counted from `upload.File.bytes.length`. These are wire bytes, including head-drop and readahead, which is why they differ from `fetchedBytes`.
- DC label: `dcId ?? client.session.dcId`, where undefined means the home DC.
- Latency is measured around `super.invoke`. It includes lease wait and any flood sleep, which is what the viewer actually waits. The UI says "per request".
- Cost per request: two `performance.now()` calls, a Map lookup and a ring write. No per-chunk work.
- `teleproto/network` has no `exports` map, so the deep import of `UpdateConnectionState` resolves.

## Requirements
- Snapshot `link`:
  `{ dcs: [{ dc, requests, errors, bytes, p50Ms, p95Ms }], flood: { count, totalSeconds }, reconnects }`, sorted by dc.
- Percentiles come from the last 256 samples per DC, held in a ring `Float64Array(256)`. They are sorted on read, not on write.
- Reconnects: main-connection `connected` events after the first one.
- The logger handler must keep the terminal output **identical** to today's default: `console.log(color + logger.format(msg, level) + reset)`, plus `console.error(error)`.

## Architecture
```
TelegramClient ← MeasuredClient (telegram/measured-client.ts)
   invoke(req,dc): t0 → super.invoke → stats.request(dc, ms, bytes) | stats.failed(dc, err)
   baseLogger.handler: /^Sleeping for (\d+)s on flood wait/ → stats.flood(s); then print
   addEventHandler(Raw{types:[UpdateConnectionState]}): state===connected && seen → stats.reconnect()
LinkStats (telegram/link-stats.ts, pure) → snapshot() at poll time
Telegram.link(): LinkStats snapshot → live-facts → buildSnapshot.link
```

## Related code files
- Create:
  - `web/src/telegram/link-stats.ts`: pure accumulator, about 90 lines
  - `web/src/telegram/measured-client.ts`: the subclass, logger and reconnect handler, about 80 lines
  - `web/test/telegram-link-stats.test.ts`
  - `web/test/teleproto-pin.test.ts`: asserts that `package.json` pins exactly `1.229.0`. Its failure message names the three seams, so an upgrade has to re-verify them
- Modify:
  - `web/src/telegram/client.ts`: construct `MeasuredClient` and expose `link()`. Update the file header, which says it is "the only file that knows MTProto", to name its sibling
  - `web/src/status/live-facts.ts`, `web/src/status/snapshot.ts`, `web/test/status-snapshot.test.ts`

## Implementation steps
1. `LinkStats`:
   - `request(dc, ms, bytes)`, `failed(dc, err)` (a flood error adds to `flood` too), `flood(seconds)`, `reconnect()`
   - `snapshot()` computes the percentiles with nearest-rank on a sorted copy
2. `MeasuredClient extends TelegramClient`: override `invoke(request, dcId?)`. Bytes: `result instanceof Api.upload.File ? result.bytes.length : 0`. Rethrow every error unchanged.
3. Logger: `new Logger()` with the default level INFO, as today. Set a `handler` that tests the flood regex and then prints the way the default does. Pass it as `baseLogger`.
4. Reconnects: after `connect()`, call `client.addEventHandler(fn, new events.Raw({ types: [UpdateConnectionState] }))`.
5. `Telegram.connect` uses `MeasuredClient` and keeps `connectionRetries: 3`. Add `link()`.
6. Wire the result into `live-facts.ts` and `snapshot.ts`.
7. Tests:
   - `LinkStats`: percentiles from known samples, ring wrap after 256, a flood from the log path and from a thrown error, sorting by dc
   - The pin test
   - The `MeasuredClient` invoke path gets **no** test, because it needs a live client (see Risks). `LinkStats` covers the logic.

## Todo
- [x] link-stats + tests
- [x] measured-client (invoke, logger, reconnect)
- [x] client.ts uses it; header updated
- [x] pin test
- [x] snapshot `link` wired + test

## Implementation notes (2026-09-26)

Done. The three seams the plan's research named against teleproto 1.229.0
were re-verified by reading the installed package directly (its `.d.ts` and
`.js` under Bun's module cache, not `node_modules` — this tree vendors
nothing there) rather than trusting the plan's line numbers, which had
drifted: `invoke<R extends Api.AnyRequest>(request: R, dcId?: number):
Promise<R["__response"]>` in `client/TelegramClient.js`, the flood log line
in `client/users.js` (`Sleeping for ${e.seconds}s on flood wait...`, logged
via `_log.info`, no error object), and `UpdateConnectionState.connected = 1`
in `network/UpdateConnectionState.js`, dispatched through
`client.addEventHandler` with `new events.Raw({ types: [...] })`. All three
matched the plan exactly. `Logger`'s default `log()` was also read directly:
setting `.handler` fully replaces the built-in `console.log` print rather
than running alongside it, confirming the plan's own risk note, and gave the
exact color codes (`error` red, `warn` magenta, `info` yellow, `debug` cyan)
reproduced in `countingLogger`.

Deviations:
- `MeasuredClient`'s constructor builds its own `LinkStats` and passes
  `countingLogger(stats)` as `baseLogger` itself, rather than `client.ts`
  wiring the logger separately — `baseLogger` has to be set before
  `TelegramClient`'s own constructor runs, so the subclass is the only place
  that can do both. A caller-supplied `baseLogger` (none today) would still
  win, since it spreads after the default.
- A `telegram-measured-client.test.ts` was added beyond the plan's own test
  list, covering `countingLogger` directly (flood-line detection, ordinary
  lines still printing, and the error-object console.error). The plan
  reasoned the `invoke` path itself needs a live client to test and left
  `LinkStats` to cover the logic; `countingLogger` doesn't need one and had
  no coverage otherwise.
- `errors.FloodWaitError`'s constructor takes `{ request, capture }` (capture
  becomes `.seconds`), not `{ seconds }` as the test file first assumed —
  corrected against the real `ErrorArgs` type.
- Stub harness (`scripts/preview.ts`, not `stub-offline.ts`) has no Telegram
  client, so its `telegram` dependency for `live-facts.ts` is a stub
  returning `link: () => ({ dcs: [], flood: {...}, reconnects: 0 })`.
  Verified `/api/status` returns exactly that shape.

## Success criteria
- Unit tests pass.
- In the stub harness, `link.dcs` is `[]` and the route stays healthy, because the stub has no Telegram.
- On the next real run, which **the user starts** (not the agent): after a play, at least one DC row shows `requests > 0`, and the terminal log looks unchanged.

## Risks
| Risk | L×I | Mitigation |
|---|---|---|
| A teleproto upgrade moves a seam (the invoke path, the log text, the event) | M×M | The exact pin plus the pin test. Each seam fails soft: no counts, never a broken playback |
| The logger handler changes the terminal output | L×M | Reproduce the default print exactly, and test the formatter with a captured console |
| `addEventHandler` triggers one `getMe` (`dispatch.ts` `_dispatchUpdate`) | L×L | One round trip, once. Acceptable |
| Subclassing breaks the `TelegramClient` constructor contract | L×H | Pass the constructor args through unchanged; the typecheck enforces it |

## Security
Only counts and timings are exposed. No message ids, file references or session strings. The auth key is never touched.

## Next steps
Phase 3.
