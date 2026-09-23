# Watch state across devices

The Continue shelf is right about one machine and knows nothing about any
other. This puts the answer where the library already lives.

## What is true now

| | |
|---|---|
| Two browsers, one player | **Already shared.** `store.ts` keeps state server-side for exactly this reason. |
| Two machines, each running `mediagram` | **Not shared.** Two `~/.local/share/mediagram-player/state.db` files that never meet. |
| The Android app | **Keeps nothing at all.** No DataStore, no Room, no sqlite anywhere in the Kotlin sources — closing it forgets where you were, even locally. |

## The decision

The private channel is already the shared source of truth: it holds the media,
the pinned index, the posters. Watch state becomes another thing it holds.

Every device merges on open and pushes on close. Nothing has to be switched on
for anything else to work, Android keeps the no-server design the foundation
chose, and a reinstalled machine gets its history back.

The cost is that a position is stale until something pushes it, and that there
are merge rules to get right. Both are addressed below.

## The shape

**One message per device, never one shared message.** Telegram has no
compare-and-swap, so two devices editing one message would silently clobber
each other. A device owns its own message and edits only that; a reader takes
them all and merges. Each writer owning its own row is what makes the merge
deterministic rather than a race.

**Last writer wins, per title.** Not per device and not per push: the record
for one title with the highest `updatedAt` is the one that counts. Watching
S1E4 on the phone and S1E9 on the laptop leaves both correct.

## Phases

| | Phase | Status |
|---|---|---|
| 01 | [The record, and the merge](phase-01-the-record-and-the-merge.md) | done |
| 02 | [The player syncs](phase-02-the-player-syncs.md) | built, off by default — first real push not yet run |
| 03 | [Android joins](phase-03-android-joins.md) | Superseded — done by [android watch-state sync](../260922-2135-android-watch-state-sync/plan.md) |

## What needs your say-so

**Phase 02 writes to the channel.** Everything this project has written there
so far has been a deliberate `push-index`, run by hand. A player that uploads
on its own is a new kind of thing, and the first real push is yours to
authorise — 01 is built and proved against the stub before anything is sent.

## Open question, carried into 01

**How a profile is the same person on two machines.** A profile id is a UUID
minted by whichever player created it, so "André" on the laptop and "André" on
the desktop are two different ids for one viewer. Phase 01 has to settle
whether the sync record is keyed by that id, by the name, or by something
derived — see its Identity section.
