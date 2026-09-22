# Spike redo: channel update delivery (clean run)

Date: 2026-09-22 23:06–23:10 · Phase 01, re-run at the user's request · Verdict: **GO**, same as `spike-260922-2240-update-delivery-report.md`, with the catch-up result now shown by a fair test.

## Why a redo
The first run (22:32–22:37) was started by another session from this session's probe files, beginning the same minute this session's own run ended. The two collided in shared log files, so its results couldn't be tied to one clean run. This redo ran alone. Before every round it checked that no probe, player or upload was live (`pgrep`, free `upload.lock`).

## Setup
- Keys: uploader `session.sqlite` (grammers 0.10) and `web/.env` (teleproto 1.229). They're distinct (sha256 prefixes `dc4:9282…` vs `dc4:d7d7…`). No key had two clients at once.
- **No pins.** Pins are flood-limited (the first run hit `FLOOD_WAIT_633`) and are how the library finds its index. Only send, edit and delete were tested. Pin delivery rests on the first run plus the real pin observed below.
- Timestamps: epoch-seconds mod 100 000, same clock for both probes.

## Results

| Round | Writer → listener | New | Edit | Delete |
|---|---|---|---|---|
| A | grammers (uploader) → teleproto (web) | +5 ms | +5 ms | +5 ms |
| B | teleproto (web) → grammers (uploader) | +12 ms | +6 ms | +0 ms |

- **Echo:** the teleproto writer received no update for its own send, edit or delete (round B, logged in the same process). That matches the first run. Keep the device filter anyway.
- **Catch-up (grammers, `catch_up: true`):**
  - Round C (send, edit, delete while offline, then reconnect): nothing replayed. This test was unfair, since the message no longer existed when the listener came back. The first run's catch-up test had the same flaw.
  - Round D (fair): message 3121 was sent and edited while the listener was offline and **left in place** through the reconnect, then deleted afterwards. The reconnect delivered only `ReadChannelInbox`. **Catch-up does not replay missed channel messages.** That's now shown properly.
- **Real traffic, observed live (round D listener):** the external uploader (not on this machine) finished a part at 11403.6. The listener received `NewChannelMessage` 3122 (`#mlib v=4`), `NewChannelMessage` 3123 (`#mlib-index`), `PinnedChannelMessages [3123] pinned=true`, the pin service message, and `PinnedChannelMessages [3116] pinned=false` (the previous index unpinned), all within ~1 s.

## Consequences for the plan (unchanged from the first report, now on firmer ground)
1. GO. Both libraries deliver every event type the plan uses within milliseconds, with no "open the channel" call.
2. Don't rely on catch-up. Every (re)connect of the update loop runs one ordinary sync/refresh round. `catch_up` stays `false`.
3. `IndexChanged` = `NewChannelMessage` with an `#mlib-index` caption **or** `PinnedChannelMessages` for the channel. A push arrives as a burst (new index, pin, service notice, unpin of the old one), so the 5 s debounce folds it into one event. The unpin (`pinned=false`) must not count as a separate event.
4. Echo filter stays, as a cheap guard.

## Channel state after the redo
Probe messages 3118–3121 deleted by the probes. Pin notice 3090, left over from this session's first run (it pinned probe 3088), was deleted by a guarded mode that refuses anything that isn't a pin notice. The latest content is real: 3122 part, 3123 pinned index, 3124 its notice. Probe files removed from `web/`. The Rust probe lives only in the session scratchpad.

## Unresolved questions
- Why the first run's writer showed `listen 50` and message ids 3093+ is explained by the other session. Nothing about it affects these results.
- Catch-up on teleproto is untested: a `StringSession` carries no update state, so there's nothing to catch up from. The plan doesn't need it (point 2).
