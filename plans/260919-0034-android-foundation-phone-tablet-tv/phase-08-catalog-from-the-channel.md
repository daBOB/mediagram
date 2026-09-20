# Phase 8: The catalog from the channel

**Context:** [plan.md](plan.md) · [phase 7](phase-07-first-run-setup.md) · [spec §11, open question 3](../../docs/superpowers/specs/2026-09-19-android-foundation-design.md)

## Overview

- **Priority:** Blocking. The wizard's last step cannot be completed without it.
- **Status:** Complete.
- **Deliverable:** a signed-in device picks its library from a list and gets a catalog, with nothing hosted and nothing pasted.

## Why

The spec chose the published package for one reason: it carries poster art. The
cost was never examined properly until a real device reached the last step of
setup — a URL and a 44-character base64 key, typed on a tablet, and eventually
on a television with a D-pad. The user has now made the call: read the index
that is already pinned in the channel.

It needs no hosting, no `publish_cmd`, no key, and no `export-package` run. The
device is already signed in, and the uploader already pins exactly one index
snapshot after every completed set. The catalog is sitting there.

The cost is posters: the pinned `library.db` carries none, so cards show their
titles without art. Accepted deliberately. Posters can come back later through
the package mechanism, which stays in the code.

## The shape

The CLI already does every part of this:

- `crates/mediagram/src/commands/push_index.rs` pins each snapshot and unpins
  the one it replaces, so the channel holds exactly one.
- `crates/mediagram/src/commands/rescan.rs` calls `pinned_index_messages(tg)` to
  find it.

The Android core needs the same, plus one thing the CLI gets from its config
and the app does not have: **which channel**.

## Picking the channel without leaking an id

`docs/system-architecture.md` §7 is explicit — the UI is told what it may play,
never where the bytes live. No `chat_id` may cross into Kotlin, and the rule
has held through every phase of this round.

So the picker returns **titles and opaque handles**, never ids. The core lists
the channels the signed-in account can see as `(handle, title)`, Kotlin renders
the titles, the user picks one, and Kotlin sends back only the handle. The core
resolves the handle to a channel internally. A handle is meaningless outside
the core and must not be derivable from a `chat_id`.

This is better than typing on every surface, and on a television it is the
difference between a D-pad keyboard and pressing OK on a list.

## Storing the choice

The chosen library persists beside the Telegram credentials, in the same
encrypted store, so a relaunch goes straight to the catalog. Changing it later
is a settings action, and "start over" clears it with everything else.

## Refreshing

Refreshing re-reads the pinned index and installs it at the catalog path the
existing reader already uses, so `list_sets`, `poster_path` and the byte path
need no changes. Use the staging-and-atomic-swap the package reader already
does — a reader that dies mid-refresh must see one whole catalog or the other,
and that machinery exists rather than needing inventing.

Failure modes worth handling by name, because each says something different to
a person: the account can no longer see the channel; nothing is pinned there;
more than one index is pinned (`push-index` resolves this, and the CLI warns
about it, so say so rather than guessing); the pinned document is not an index.

## What stays

The package reader stays exactly as it is — built, reviewed, replay-defended,
and the only path that carries posters. The wizard simply stops asking for a
URL and a key. Do not delete it, and do not wire it into the new flow.

## Related code files

- Modify: `crates/mediagram-core/src/api/**` (the new listing, selection and refresh), `crates/mediagram-core/src/catalog.rs` if the install path needs it
- Modify: the setup feature and its mobile screens; the encrypted store
- Do not touch: `crates/mediagram/**` (the CLI is finished), `android/ui-tv/**`

## Success criteria

1. A signed-in device with no library chosen shows a list of channel titles, not a text field.
2. Picking one produces a catalog whose set count matches what `mediagram status` reports for that channel.
3. No `chat_id`, `message_id` or `doc_id` reaches Kotlin — in a value, a log, or an error.
4. A relaunch goes straight to the catalog without asking again.
5. Each named failure above produces a distinct, actionable message rather than one generic error.
6. Start over clears the chosen library along with everything else.
7. The package path still compiles and its tests still pass.

## Risk assessment

| Risk | Mitigation |
|---|---|
| A handle that is a `chat_id` in disguise | It must not be derivable from one. Assert it in a test, not in review. |
| A mid-refresh death leaving half a catalog | Reuse the package reader's staging and atomic swap; do not write a second one. |
| An account in hundreds of channels | The list is what the account can see. Order it usefully and say what the ordering is. |
| Silently picking the wrong pinned message | More than one pinned index is a real state the CLI warns about. Say it plainly. |

## What the live gate showed

Run against the real account and channel on the connected phone:

1. The step came up as a list of channel titles — `Mediagram`, then the
   series channels — with no text field anywhere on it.
2. Picking `Mediagram` installed 514 playable sets. `mediagram status`
   reports 514 for the same channel.
3. 129 log lines from the run hold no dialog id, no message id and no
   number long enough to be either.
4. A force-stop and relaunch went straight to the Movies shelf.
5. The first pick failed, and usefully: the channel had two index
   snapshots pinned, and the screen said so and said which command unpins
   the extras, with the list still there to pick from. `mediagram
   push-index` resolved it, and the next pick succeeded.
6. `files/libraries.json` exists, owner-only, at the path the reset
   deletes. Signing the real device out was not run; the behaviour is
   covered by the ViewModel and storage tests.
7. The package reader and its tests are untouched and green.

## Next steps

The TV surface renders this same list later. The selection logic is in
`feature/setup/Libraries.kt`, with no composables in it, so `ui-tv` reuses
it as it stands.
