# Spike: are channel updates delivered to another session of the account?

Date: 2026-09-22 22:32–22:37 · Phase 01 of this plan · Verdict: **GO**, with two design changes.

## Setup
- Two existing, distinct auth keys: the uploader's (grammers, `~/.local/share/mediagram/session.sqlite`)
  and the web player's (teleproto, `web/.env`). Fingerprints differed (`dc4:9282…` vs `dc4:d7d7…`),
  so no key was ever shared. Neither owner process was running (upload lock free, no player).
- Throwaway probes, both deleted afterwards: grammers `stream_updates` + raw `next_raw`, teleproto `Raw` handler.
- Writer sent one `#mlib-probe` text message to the library channel, edited it, pinned (silent), unpinned, deleted.

## Results

| Question | grammers 0.10 listener | teleproto 1.229 listener |
|---|---|---|
| New message by the other session | ✅ `NewChannelMessage`, +15 ms | ✅ `UpdateNewChannelMessage`, +41 ms |
| Edit | ✅ `EditChannelMessage`, +9 ms | ✅ `UpdateEditChannelMessage`, +12 ms |
| Pin / unpin | ✅ `PinnedChannelMessages` (+16 / +3 ms) | ✅ `UpdatePinnedChannelMessages` (+7 / +4 ms) |
| Delete | ✅ +3 ms | ✅ +7 ms |
| Own action echoed to the writer | – | ❌ none seen on the teleproto writer |
| Catch-up after ~40 s offline (`catch_up: true`) | ❌ only `ReadChannelInbox`; the missed new + edit never came | not tested (a `StringSession` stores no update state) |

No `getChannelDifference` "opening" call was needed: updates flowed on the first connect. A pin also produces a
`NewChannelMessage` carrying a `MessageService` (the "pinned a message" notice).

## Incidental findings
1. **Pins are flood-limited hard.** After 4 pin/unpin calls plus one real index pin from another uploader,
   `messages.updatePinnedMessage` answered `FLOOD_WAIT_633` (10.5 min). The uploader's `push_index` pin goes
   through `with_retry`, which handles this. The web player's `state-channel.ts:125` first-send pin does not:
   teleproto's default `floodSleepThreshold` is 60 s, so a long wait throws after the document was sent. The
   next round then finds no pin and sends another document. It's rare (only a device's first send), but it's a real gap.
2. **A pin leaves a service message** that deleting the pinned message doesn't remove. Irrelevant to the
   product, which never unpins state docs, but a probe must clean it up (done: 3094 and 3096 removed).
3. **Another uploader was active on the account during the spike.** A movie part (3098) and a newly pinned
   index (3099) arrived at 22:34:39–40 from outside this machine (no local process, local `library.db`
   unchanged since 22:06). Probably the homelab uploader finishing a long upload. It's the push path
   working for real: the round-3 listener was offline at that moment, and catch-up didn't deliver it either.

## Consequences for the plan
- **GO.** Both libraries receive every event type the plan needs, within tens of milliseconds.
- **Don't rely on catch-up.** Every (re)connect of the update loop runs one ordinary sync/refresh round.
  After that, live updates cover the rest. Android already does this (WatchSync `onForeground` → round);
  web: add the same round when the handler registers. `catch_up` stays `false`.
- **Keep the echo filter** (caption device ≠ own) anyway. Absence on one library in one run isn't proof, and it costs nothing.
- **`IndexChanged` must also match `NewChannelMessage` with an `#mlib-index` document**, not only the pin
  update. The two arrive together, so the debouncer folds them into one event.
- Add to 05 or a small fix now: wrap the state-channel first pin in a flood-wait retry (outside this plan's scope; flagged).

## Channel state after the spike
Probe messages 3093, 3095, 3097, 3101 and pin notices 3094 and 3096 deleted. Latest real content untouched: 3098 part,
3099 pinned index, 3100 its pin notice.
