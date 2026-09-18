# Watch state, next episode, watchlist, collections

Five convenience features that all need the same thing first: somewhere to
write. The player has never written anything.

## The constraint that shapes all of it

The index is opened `readonly` and `refreshCatalog` swaps the whole file when
a package updates. Every existing player directory is a **cache** under
`~/.cache`, disposable by design. So watch state cannot live in any of them:
it is the one thing here that would hurt to lose, and the only thing the
uploader does not own.

It gets its own database, at `MEDIAGRAM_STATE_DB`, defaulting to
`~/.local/share/mediagram-player/state.db` — a data directory, not a cache.
Keyed by `set_id`, which is a ULID minted at upload and stable across every
catalog refresh.

## Decisions taken (user, 2026-09-18)

1. ~~**One viewer.**~~ **Reversed mid-build, at the user's request: profiles.**
   The original answer was one viewer, on the grounds that the player sits
   behind a proxy that authenticates one person. The note against it said
   adding viewers later would be "a column and a default, not a rewrite",
   which held: `profile_id` joined three primary keys and the routes moved
   under `/api/profiles/<id>/`. A profile is a **convenience, not a login** —
   it keeps two people's places and lists apart and checks nothing, and the
   chooser says so.
2. **Auto-advance with a countdown**, cancellable.
3. **Collections are hand-built named lists**, ordered, any title in any list.
   The watchlist stays its own thing rather than becoming a built-in list —
   one flag is simpler than a list of one.

## Phases

| # | Phase | Status |
|---|---|---|
| 1 | [The state store](phase-01-the-state-store.md) | done |
| 2 | [Writing over HTTP](phase-02-writing-over-http.md) | done |
| 3 | [Resume and continue watching](phase-03-resume-and-continue.md) | done |
| 4 | [Next, auto-advance, preload](phase-04-next-and-preload.md) | done |
| 5 | [Watchlist and collections](phase-05-watchlist-and-collections.md) | done |
| 6 | [Profiles](phase-06-profiles.md) | done |

## Dependencies

- 2 needs 1. 3, 4 and 5 need 2.
- 4's "next" is derived from the tree `library.js` already builds; no server.
- 4's preload warms the same `ChunkCache` the Range route already fills.

## Sync, considered and not built

Asked mid-build whether to sync the state database between devices through
Dropbox. It is not needed: the player is a *server*, so a phone and a laptop
pointed at it are reading the same rows and cross-device resume falls out with
nothing added. It would only matter for a second player **host**, and the user
confirmed there is one.

Worth recording why file sync would have been the wrong answer even then. The
store is WAL-mode, so `state.db`, `-wal` and `-shm` are consistent only as a
set, and a file syncer copies them independently on its own schedule. Two
hosts writing means whole-file last-writer-wins: one host's positions silently
overwrite the other's. The shape that works is a row journal with per-row
timestamps merged last-write-wins — at which point the transport is incidental.

## What this changes about the threat model

The API gains writes. It still has no authentication, so anyone who can reach
the port can already stream the whole library — but they could now also
scribble on watch state. Writes are same-origin checked and require a JSON
content type, which stops a drive-by form post from another page. This is
documented rather than solved: the answer remains a proxy in front.
