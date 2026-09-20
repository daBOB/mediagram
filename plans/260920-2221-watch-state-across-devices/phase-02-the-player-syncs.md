# Phase 02 — The player syncs

**Status:** done. The adapter is proved against the real channel: 14 checks, 0 failures.
**Needs authorisation:** this is the phase that writes to the channel.

## Context

- [phase-01](phase-01-the-record-and-the-merge.md)
- `web/src/telegram/client.ts` — `Telegram`, which today can only read:
  `partMedia(messageId)` is its one method
- `crates/mediagram/src/commands/push_index.rs` — how a document is sent,
  captioned and pinned; the conventions to follow rather than reinvent
- `web/src/index.ts` — where the player starts and where the session lives

## The message

`#mlib-state v=1 device=<id>` in the caption, a JSON document attached.

**One message per device, edited in place.** Telegram has no compare-and-swap,
so two devices editing one shared message would clobber each other with no way
to notice. A device writes only its own and reads everyone's.

Found by searching the channel for the caption marker, the same way `rescan`
finds the index. Not pinned: pins are how a reader finds *the* index, and a
handful of state messages competing for that space would make the index harder
to find rather than the state easier.

## When

**Pull** when the player starts, and every few minutes while it runs. A device
that has been off for a week should not need a restart to catch up.

**Push** when a title closes, on `pagehide`, and on the same timer — but only
when something actually changed. A player left open overnight should not
re-upload an identical document forty times.

Neither blocks anything. A sync that fails is a player that works exactly as
it does today, and that is the whole safety property: this is an addition to a
local database that remains the source of truth for the machine it is on.

## The device id

Stable per install, minted once and kept in `state_meta` beside the schema
version. Not the hostname — a rebuilt machine with the same name is a
different device, and two devices sharing a name would share a message.

## Files

**Modify** `web/src/telegram/client.ts` (send, edit, search), `web/src/index.ts`
(the schedule), `web/src/config.ts` (whether to sync at all)

**Create** `web/src/state/sync.ts`, `web/test/state-sync.test.ts`

## Todo

- [ ] `Telegram.findStateMessages()`, `putState()` — send once, edit after
- [ ] a device id in `state_meta`
- [ ] pull, merge, import on start
- [ ] push on close, on `pagehide`, on a timer, and only when changed
- [ ] a failure anywhere is logged and swallowed
- [ ] off by default until it has been run once by hand
- [ ] proved against the stub before anything is sent for real
- [ ] **ask before the first real push**
- [ ] `./scripts/check.sh`

## Success criteria

- Two players on two machines, pointed at one channel, agree about where a
  title was left off within one sync interval.
- A player with syncing off behaves exactly as it does today.
- A channel that cannot be reached costs nothing but a log line.
- No document is uploaded when nothing changed.

## Risks

| Risk | Mitigation |
|---|---|
| A bug uploads rubbish to the channel | Phase 01 is proved first; the first real push is authorised by hand |
| Two devices push at once | They write different messages; the merge does not care about order |
| The document grows without bound | It is one row per title watched, not per event; measured before it ships |


## What the real channel taught

Two bugs no test could have found, and one design reversed on measurement.

**`teleproto` rejects a web `File`** — "Cannot use [object Blob] as file". It
is a fork of GramJS, which predates `File` existing in Node. In-memory data
goes through `CustomFile`, which is also the only way to name a document with
no path on disk to take a name from.

**Caption search cannot find a new message.** Not slowly — a probe polled for
a full minute and it never appeared, while the same search returned index
messages days old immediately. Worse, the search was matching the wrong
messages the whole time: Telegram parses `#mlib-state` as the hashtag
`#mlib`, so it returned every `#mlib v=4` part document in the channel.
`deviceFromCaption` being strict is the only reason that presented as "found
nothing" rather than as something worse.

So discovery is the **pin list**, which is exact and immediate because it is
not full text. That reverses the original decision not to pin, and the
original reasoning does not survive contact with the code: both readers of the
pin list — `rescan.rs` and the Android core's `pick_index` — filter on
`#mlib-index` before counting anything, and core reads up to a hundred pins. A
handful of devices cannot crowd out an index. Pinning is silent and happens
once per device, on the first send; every later write edits in place.

`scratchpad/probe-state-channel.ts` is the proof, and it cleans up after
itself.
