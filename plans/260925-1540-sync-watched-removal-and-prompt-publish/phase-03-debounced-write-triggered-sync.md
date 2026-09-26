# Phase 3 — debounced sync after a local write

**Revised after review** (H1, L1): the first pass fired `onWrite` on every
successful state write, including the player's ten-second progress
autosave — a round, and a Telegram message edit, roughly every ten seconds
for the length of playback. Narrowed to only the writes worth telling
another device about soon; see the updated Requirements. `WriteDebounce`
also gained a `stopped` latch (L1) so a write landing after `stop()` cannot
re-arm a timer against resources already closing.

**Re-review** (R3): inferring "final" from the HTTP method (POST vs PUT)
was fragile — an older browser refusing `sendBeacon` a JSON body falls back
to the same PUT the periodic tick uses, silently losing the trigger for
that browser. `flushProgress` now marks the flush explicitly, `?final=1`,
on both the beacon and its PUT fallback (`watch-state.js`); `writeWorthSyncing`
(`routes.ts`) reads that marker for a progress write and always counts a
`DELETE` (`PlayerRequest.final`, parsed in `server.ts`'s `describe()`).

## Context

- `web/src/routes.ts` (`createRouter`, `RouterOptions`, the one choke point
  every state mutation passes through)
- `web/src/index.ts` (where `sync`/`StateSync` and the 5-minute timer are
  wired)
- `web/src/application/lifecycle.ts` (`syncOnce`, `ApplicationResources`,
  `shutdownFor` — the round and shutdown ordering to reuse, not duplicate)
- `android/core/data/src/main/kotlin/WatchSync.kt` — `soon()`, the parity
  target this closes the gap with

## Overview

Priority P1. A browser edit can take up to 5 minutes to reach the phone.
Add a trailing debounce: a few seconds after the *last* local write, run
one sync round — through the existing `StateSync.once()` and its
no-overlap guard, never a second path.

## Requirements

- New small class, a fixed delay ("a few seconds" — 5 s, matching the
  comment style already used for `syncEveryMs`).
- `touch()` (re)arms the timer; firing calls `syncOnce(sync, "write")`.
- Injectable clock so a test can advance time without real timers.
- Wired only when `sync` exists (`config.syncState && state.remembers`);
  `touch()` is a no-op building block when there is nothing to debounce —
  simplest is to only construct it when sync is on.
- `RouterOptions` gains `onWrite?: () => void`; `createRouter` calls it once
  per request that mutated state *and* is worth a sync round
  (`writeWorthSyncing`, below) — one choke point, not one call per branch in
  `state/routes.ts`.
- `writeWorthSyncing`: a `/progress/` write only counts as a `POST` (the
  shape `flushProgress`'s `sendBeacon` sends on leaving a title or pausing
  — `player.js`'s `pause` handler now calls `saveProgress(true)`, matching
  `openPlayer`/`teardown`, so pausing is a final flush too); a plain PUT is
  always the periodic tick (or its rare PUT fallback) and never counts. A
  `/preferences` write never counts — per-device, unsynced. Every other
  state write (watched, watchlist, Kids, a profile) counts.
- `WriteDebounce.stop()` latches: `touch()` after `stop()` is a no-op, so a
  write landing between `stop()` and the resources it guarded actually
  closing cannot re-arm a timer against them.
- Shutdown cancels the pending timer before publishing its own final round
  (`shutdownFor` already does one `syncOnce(..., "stopping")`); a debounce
  firing after that would be a second, redundant round.

## Related code files

- Create: `web/src/application/write-debounce.ts`,
  `web/test/write-debounce.test.ts`
- Modify: `web/src/routes.ts` (`writeWorthSyncing`), `web/src/index.ts`,
  `web/src/application/lifecycle.ts` (resource + shutdown hook),
  `web/public/lib/playback/player.js` (`pause` flushes, not ticks)

## Todo

- [x] `WriteDebounce` class with injectable clock and a `stopped` latch
- [x] `RouterOptions.onWrite` + `writeWorthSyncing` + the one call site in
      `createRouter`
- [x] `player.js`: `pause` calls `saveProgress(true)`
- [x] wire in `index.ts`; add to `ApplicationResources`; `stop()` in
      `shutdownFor` before the final round
- [x] unit test: fake clock, burst of `touch()` collapses to one run,
      `stop()` cancels a pending run and refuses a later `touch()`
- [x] router test: periodic progress PUT never fires; final POST, watched,
      watchlist, Kids and a profile write do; a preference never does
- [x] confirm no double round: a `touch()` during an in-flight round only
      queues through `StateSync`'s own `again` flag, not a second call
- [x] `./scripts/check.sh`

## Success criteria

- A film playing sends no `sync (write)` round on the ten-second autosave
  tick. Un-marking a title, or leaving/pausing a playing title, is followed
  by exactly one `sync (write)` round a few seconds later.
- A player with `MEDIAGRAM_SYNC_STATE` off never starts the debounce timer.
- Shutdown's existing final round is unaffected; no duplicate round fires
  after it, even if a write lands during shutdown.
