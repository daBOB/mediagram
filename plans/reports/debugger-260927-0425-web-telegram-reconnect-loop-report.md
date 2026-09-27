# `link.reconnects` climbs while idle — false-positive keepalive, not a real reconnect loop

## Executive summary

Root cause: teleproto 1.229.0's own `_updateLoop` (keepalive ping) misclassifies
every routine ~9s ping cycle as a "waking from sleep" event, because its
threshold for that heuristic (5000ms) is *smaller* than its own steady-state
ping interval (9000ms). The "just woke up" branch unconditionally emits a
synthetic `UpdateConnectionState.connected` on every successful ping —
indistinguishable from a genuine reconnect to any listener. `MeasuredClient
.watchReconnects()` (web/src/telegram/measured-client.ts:74-81) counts every
`.connected` after the first as a reconnect, so it counts one "reconnect"
roughly every 9.3s of pure idle. Verified with a live socket check: the single
main-DC TCP connection never closed or was recreated while the counter
climbed by 14. Not a regression, not caused by anything in this repo, and not
introduced by tonight's commits — the library has behaved this way since
before it was pinned; only the counter that surfaces it (`link.reconnects`) is
new (yesterday).

## 1. Rate measurement

Two samples, both read-only, both against the live process (pid 660734).

**Coarse (from the task's own earlier samples):** `reconnects` 23→27→30
between uptime ~200s and ~291s.

**My own tight sample**, `/api/status` polled every ~10s for ~140s
(`/tmp/claude-1000/-home-andre-Workspace-mediagram/status-poll.log`, columns:
uptime, reconnects, dcs[0].requests, flood, loopLagMs, transcodes.running):

```
uptime=331 reconnects=35 requests=206 flood={count:3} loopLag.p50=0.105 transcodes=0
uptime=341 reconnects=36 requests=206 ...
uptime=351 reconnects=37 requests=206
uptime=361 reconnects=38 requests=206
uptime=371 reconnects=39 requests=206
uptime=381 reconnects=40 requests=206
uptime=391 reconnects=42 requests=206
uptime=401 reconnects=43 requests=206
uptime=412 reconnects=44 requests=206
uptime=422 reconnects=45 requests=206
uptime=432 reconnects=46 requests=206
uptime=442 reconnects=47 requests=206
uptime=452 reconnects=48 requests=206
uptime=462 reconnects=49 requests=206
```

14 reconnects over 131s = **9.36s/reconnect average**, essentially constant
(range 7-11s per step, jitter is polling/RTT noise). `dcs[0].requests` is
**perfectly flat at 206** the entire window; `flood.count` flat at 3;
`transcodes.running` flat at 0; `loopLag.p50` flat at ~0.105ms (one 44ms and
one 39ms `max` spike, uncorrelated with reconnect timing, most likely GC).
No correlation with anything in the snapshot — this rules out event-loop
stalls, transcoding, and preload/download activity as causes. The period
matches `PING_INTERVAL` (9000ms, `teleproto/client/updates/dispatch.js:15`)
plus ping round-trip almost exactly.

## 2. App-level lifecycle — ruled out

`grep -rn "setInterval|setTimeout" web/src` (excluding tests) finds no timer
near 9-11s that touches the Telegram client:
- `index.ts:187` transcode reap, 60s, no Telegram touch.
- `index.ts:241` `syncOnce`, `config.syncEveryMs` (floor 60_000ms,
  `config.ts:209`), touches Telegram only through `StateSync`.
- `index.ts:285` / `loop-lag.ts:55` — `windowMs` default 10_000ms, but this
  only resets a `perf_hooks` histogram; it never calls into the Telegram
  client. Coincidental proximity to the observed period, not causal.
- `catalog-events.ts:92` SSE heartbeat, 25_000ms, browser-facing only.

`client.connect()` is called exactly once at startup
(`web/src/telegram/client.ts:83`, inside `Telegram.open`); the only other
call sites are the sign-in/login flows (`login/authenticate.ts:27`,
`settings/sign-in.ts:79`), which are not in play — this account is already
signed in and Settings was not touched during the observation window.
`TelegramConnection.restart` (`connection.ts:59`) is only invoked from
Settings actions (account switch, library switch); nothing in
`application/telegram-binding.ts` or `channel-events.ts` runs on a timer —
`listenForLibraryEvents` is push-driven (an `addEventHandler`, armed only by
an incoming update), confirmed by reading `web/src/telegram/channel-events.ts`
end to end. Conclusion: nothing this project's own code does explains the
climbing counter.

## 3. Library behaviour — root cause

Read `teleproto`'s vendored source directly (`web/node_modules/teleproto`,
package pinned at exactly `1.229.0`):

`network/MTProtoSender.js` shows `UpdateConnectionState.connected` is emitted
from exactly two places: `connect()` (line ~150, on every successful
`_connect()`, not gated on whether this is the *first* connect) and inside
`_reconnect()`'s call to `connect(newConnection, true)` after a real
transport failure. A real reconnect *does* recreate the `Connection` object
and redial (`_reconnect()`, ~line 733), so it would show up as a new local
TCP port — see §5.

But the actual driver here is a **third emission site**, outside
`MTProtoSender`, in `client/updates/dispatch.js`'s `_updateLoop` (the
keepalive/ping loop that every `TelegramClient` runs after connecting):

```
const PING_INTERVAL = 9000;              // steady-state ping cadence
const PING_TIMEOUT = 10000;
const PING_INTERVAL_TO_WAKE_UP = 5000;   // "did we just wake from sleep?" threshold
const PING_WAKE_UP_TIMEOUT = 3000;
const PING_WAKE_UP_WARNING_TIMEOUT = 1000;
```
(`dispatch.js:15-22`)

Each loop iteration sleeps `PING_INTERVAL` (9000ms), then computes
`lastInterval = pingAt - lastPongAt`. If `lastInterval` is undefined (very
first ping) or `< PING_INTERVAL_TO_WAKE_UP` (5000ms), it takes the ordinary
fast-path ping (bounded attempts/retries, `PING_TIMEOUT`, **no state
emission on success** — `dispatch.js:160-161`). Otherwise it takes the
"woke from sleep" branch (`dispatch.js:163-169`), which on success
**unconditionally** calls:

```
_handleUpdate(client, network_1.UpdateConnectionState.connected);
```
(`dispatch.js:166`)

Because the loop always sleeps a full 9000ms between pings, `lastInterval`
is *always* ≈9000ms+RTT on every iteration after the first — always ≥ the
5000ms wake-up threshold. So **every single keepalive ping after the very
first one takes the wake-up branch and re-announces `.connected`**, whether
or not anything actually reconnected. This is a threshold ordering bug in
the fork: `PING_INTERVAL_TO_WAKE_UP` (5000ms) needs to be *larger* than
`PING_INTERVAL` (9000ms) for the heuristic to only fire on a genuine gap
(e.g. laptop sleep), not on ordinary cadence.

`_handleUpdate` (`dispatch.js:49-64`) routes this straight through
`_dispatchUpdate`, the same path a real `UpdateConnectionState` takes, so
any `addEventHandler(..., new events.Raw({types: [UpdateConnectionState]}))`
listener — including `MeasuredClient.watchReconnects()`
(`measured-client.ts:76-80`) — cannot tell the two apart. That handler's own
doc comment ("the first `connected` does not count as one... a download-DC
sender is pooled and rebuilt without notice") assumed `.connected` only ever
fires on `connect()`/`_reconnect()`; it did not know about this ping-loop
emission, because at the time `watchReconnects` was written (`b7b0c419`,
yesterday) nobody had read this file.

The keepalive ping itself is sent via `client._sender.send(...)`
directly (`dispatch.js:151`), **not** through `client.invoke()` — the one seam
`MeasuredClient` overrides for counting (`measured-client.ts:53`). That is
why `dcs[0].requests` stayed exactly flat (206) for the whole window while
`reconnects` climbed: the ping traffic is invisible to the request counter
by construction, not coincidence.

## 4. New or long-standing?

Long-standing, not tonight's regression. `git log -p --follow -- web/package.json`
shows `"teleproto": "1.229.0"` unchanged across every commit that touches it
— the dependency has never been bumped. What *is* new is the counter that
surfaces the behaviour: `link.reconnects` was added in `b7b0c419` ("feat(web):
add per-DC Telegram link counters to the status snapshot", 2026-09-26,
yesterday). Before that commit nothing counted `UpdateConnectionState
.connected` events at all, so this ping-loop quirk produced no visible
symptom — not a log line, not a metric, nothing (the wake-up branch has no
`_log` call on its success path). `docs/web-player.md:395` only notes the
counters didn't used to exist; no earlier plan or report
(`plans/reports/fullstack-developer-260926-system-page-stats-report.md`, the
phase that added them) anticipated this false-positive. The download-gate
background lane and series-preload pacing (91f25545) are unrelated — confirmed
directly: `transcodes.running` and preload activity were 0 the entire
measurement window, so there was nothing for those code paths to touch.

## 5. Impact

Harmless noise, not a real disconnect, verified at the socket level:

```
04:23:53  rc=48  sock: local 42328 -> 149.154.167.92:443 (inode 23318855, ESTABLISHED)
04:24:44  rc=54  sock: local 42328 -> 149.154.167.92:443 (inode 23318855, ESTABLISHED)
```
Same local port, same socket inode, across 6 counter increments and 51s. A
real reconnect (`MTProtoSender._reconnect()`, `MTProtoSender.js:724-771`)
recreates the `Connection` object and redials — that would show up as a new
ephemeral local port. It did not. The wake-up branch that fires here never
calls `sender.reconnect()` or `sender.disconnect()` on its success path —
those only run in the *exception* branch (ping genuinely times out and,
even then, only if `Date.now() - client._lastReceivedAt` also exceeds
`PING_INTERVAL + PING_TIMEOUT`, `dispatch.js:181`). None of that happened
here: no errors, no flood-count growth, no request-count growth, no `dcs[*]
.errors` growth.

Downloads and catalog-event delivery are unaffected: the update pipeline,
the main sender, and the single TCP socket never actually reset, so no
in-flight `iterDownload`/`partMedia` call would see a dropped connection
from this.

One secondary risk worth flagging, not observed tonight but structurally
present: the wake-up branch arms a **1-second** warning timeout
(`PING_WAKE_UP_WARNING_TIMEOUT`, `dispatch.js:163-165`) that fires a
transient `UpdateConnectionState.disconnected` if the ping doesn't return
within 1000ms, before the full 3000ms wake-up timeout is given a chance.
DC4's own p95 latency tonight was ~134ms, comfortably under that — but under
load (heavy concurrent downloads, a busy encoder, GC pause) a ping round
trip could plausibly exceed 1s and produce a spurious "disconnected" flicker
alongside the spurious "reconnected" one, on this exact same misclassified
code path.

## 6. Root cause and recommendation

**One thing:** `teleproto` 1.229.0's `_updateLoop`
(`web/node_modules/teleproto/client/updates/dispatch.js:139-190`) treats
every ordinary ~9s keepalive ping as if the process had just woken from
sleep, because `PING_INTERVAL_TO_WAKE_UP` (5000ms) is smaller than the
loop's own `PING_INTERVAL` (9000ms) — the "just woke up" heuristic can never
*not* fire. On every successful ping this unconditionally emits a synthetic
`UpdateConnectionState.connected`, which `MeasuredClient.watchReconnects()`
has no way to distinguish from an actual reconnect, so it over-counts by
one false reconnect roughly every 9.3 seconds of runtime, whether idle or
busy.

**Minimal fix**, in order of how much it touches:
1. Cheapest and sufticient for the metric's purpose: in
   `web/src/telegram/measured-client.ts`, stop trusting a bare
   `UpdateConnectionState.connected` event as proof of a reconnect. There is
   no clean signal available from outside teleproto to separate "the
   ping-loop's wake-up branch fired" from "the sender actually redialed" —
   both dispatch the identical event through the identical pipeline. A
   local-only fix would have to either (a) accept this counter is really
   "how many times teleproto emitted `.connected`" and rename/redocument it
   rather than promise "reconnect", or (b) additionally track the DC's
   *socket* identity (not available through teleproto's public surface
   without reaching into `_sender._connection`) to corroborate a real
   reconnect before counting one.
2. Actual root fix, since `client.ts`'s own doc comment already names
   teleproto as "the file to port" if it ever needs replacing: patch the
   vendored fork so `PING_INTERVAL_TO_WAKE_UP` is set above `PING_INTERVAL`
   (e.g. 15000ms), so the heuristic only fires on a genuine gap (laptop
   sleep, backgrounded tab, long GC pause) rather than every steady-state
   cycle. This removes both the false reconnect count and the secondary
   1-second-flicker risk in §5 at the source.

No further controlled experiment is needed to settle this — the code path
is fully readable and the socket-level check already corroborates it
independently (§5). If additional confidence is wanted anyway: capture the
process's own stdout (currently going to an interactive `/dev/pts`, not a
file, so unavailable read-only without attaching to that terminal) across
one ping cycle and confirm no `_log` line appears for a wake-up-branch
success — only `dispatch.js`'s exception branch logs anything
(`Ping failed: ..., reconnecting`), so an all-quiet cycle with the counter
still incrementing would be the last independent confirmation.

## Unresolved questions

- Why did `dcs[0].requests` jump from 130 (task's baseline) to 141 (my first
  ad-hoc curl) then hold flat at 206 in the tight sample ~10s later? Some
  real `invoke()` traffic happened once, then stopped — plausibly a Settings
  page or account-info poll from an open browser tab; not investigated
  further since it is orthogonal to the reconnect counter and did not
  correlate with the reconnect cadence.
- Whether `PING_DISCONNECT_DELAY = 75` (`dispatch.js:19`, the delay-seconds
  field sent inside `PingDelayDisconnect`) is itself sized correctly is a
  separate question outside this investigation's scope; it did not
  contribute to the observed symptom (no actual disconnects occurred).

**Status:** DONE
**Summary:** `link.reconnects` climbing at idle (~9.3s/tick) is teleproto
1.229.0's keepalive loop mis-firing a synthetic `.connected` on every
routine ping because its wake-from-sleep threshold (5s) is below its own
ping interval (9s) — confirmed harmless by watching the actual TCP socket
stay identical (same port/inode) across 6+ counter increments. Not caused by
tonight's commits or any app-level timer; the pinned dependency version
never changed, only yesterday's new counter (`b7b0c419`) made this
pre-existing, silent quirk visible. Fix belongs in the vendored teleproto
fork's `PING_INTERVAL_TO_WAKE_UP` constant, not in this project's own code.
