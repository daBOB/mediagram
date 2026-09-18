# Watch state, profiles, and the convenience features

`./scripts/check.sh`: clippy clean, 26 Rust tests, **534 bun tests**, 0 fail
(was 462 before this work).

## What shipped

| Feature | Where |
|---|---|
| Watch positions, resume, `Continue` shelf | `state/store.ts`, `resume-point.js` |
| Cross-device resume | fell out — the player is the server |
| Next episode, auto-advance with countdown | `nextAfter` in `library.js` |
| Preload the next title | `preloadNext` in `player.js` |
| Watchlist | `state/store.ts`, masthead shelf |
| Collections (hand-built, ordered) | `collections-view.js` |
| Profiles | `state/schema.ts` v2, `profile-picker.js` |

New: `src/state/{schema,store,routes}.ts`, `public/lib/{watch-state,
resume-point,collections-view,profile-picker}.js`. New suites:
`state-store`, `state-http`, `state-migration`, `resume-point`.

## Two decisions worth recording

**No Dropbox, and no sync at all.** Asked mid-build. The player is a server:
devices pointed at it share one `state.db`, so cross-device resume needs
nothing. It would only matter for a second player *host*, which there is not.
Syncing the file would also have been wrong — WAL-mode means three files
consistent only as a set, and two writers means whole-file last-writer-wins.

**Profiles reversed the "one viewer" answer 35 minutes in.** The plan had
predicted the cost ("a column and a default, not a rewrite") and that held.
The profile travels as a path segment rather than a query parameter because
`sendBeacon` — the only thing that survives a closing tab — cannot set headers,
and because a missing segment is then a 404 rather than a write landing in
somebody else's rows.

## The bug this found

`open()` applied **every** migration on **every** open. v1 survived it: every
statement was `CREATE TABLE IF NOT EXISTS`. v2 rebuilds three tables to add
`profile_id`, so the second open copied the live rows into a fresh table under
an invented "Everyone" profile, dropped the original, and renamed the copy
over it. It read as working — the data was there, under the wrong owner.

Caught by a round-trip test, not by review. Only groups above the recorded
version run now, each in a transaction. `state-migration.test.ts` covers a v1
database with rows, an empty one, a second open, and a fresh one opened three
times.

## Verified in the browser, against the real store and real routes

Profile chooser on a device that has never chosen; picking one loads that
profile's shelves. A position written while watching appears in the store
(`at: 18`, `duration: 2627`) and, from a browser with its storage cleared and
the profile re-picked, lands on `Continue` with two titles and progress rules
at the right fill (29% of 7142). Reopening shows "Carrying on from 23:20" and
seeks. `Up next` found S1E2 across the episode list, counted 8→6, and cancel
dismissed it. The v1 database on disk migrated to v2 and adopted nobody,
because it held nothing.

## Unresolved

- **The `Continue` shelf is only as honest as the runtimes.** A set whose
  `duration` is null in the index never counts as finished, so it stays on the
  shelf forever. `isFinished` refuses to guess; the fix is in the uploader.
- **Collections and profiles use `window.prompt`.** Deliberate — naming a list
  is one line and the platform's dialog is keyboard-reachable for free — but
  it is the least considered surface in a page that is otherwise carefully
  set, and it will look wrong on a television.
- **No reordering within a collection.** `position` is stored and honoured;
  nothing moves an item yet.
- **Preload is unconditional once the buffer is healthy.** It does not ask
  whether the link is metered, and on a phone that is somebody's data.
- Still open from before: the `cache-store.test.ts` eviction-order flake, and
  4 TypeScript errors in the untracked `web/test/posters.test.ts`.
