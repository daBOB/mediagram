# Phase 1 — web: watched tombstone

**Revised after review** (C1): ships as its own key `unwatched` /
`UnwatchedRow`, not a `removed` flag on `WatchedRow` — see plan.md's design
section for why a flag is unsafe specifically for `watched` right now. The
requirements below are the design as shipped.

**Re-review** (R1, R2): marking watched is now clamped the same way
un-marking already was (`finished_at = MAX(now, removed_at + 1)`), and
`watched-exchange.ts`'s import skips a removal only when the local live
mark is *strictly* newer, matching `watched-reconcile.ts`'s tie rule
exactly instead of the generic `>=` every other same-kind import uses.

## Context

- `web/src/state/sync-record.ts`, `merge.ts`, `watched-reconcile.ts`,
  `store.ts`, `watched-exchange.ts`, `schema.ts`, `lists-exchange.ts` (the
  export/import pattern followed, not the wire shape)
- `web/test/fixtures/watch-state/merge.json`, `record-parse.json`

## Overview

Priority P1. Give `watched` a removal a merge can see, without letting a
build that predates it mistake that removal for a live mark — so an
un-mark propagates through a sync round between updated devices instead of
being re-imported from an older document.

## Requirements

- `UnwatchedRow{setId, updatedAt, lastFinishedAt}` on its own `unwatched`
  key; absent on an old record parses as absent, not empty.
- Local `watched` table gains `removed_at`; live reads (`snapshot`) filter
  it out; export splits live and removed rows onto their two wire arrays.
- `setWatched(id, setId, false)` tombstones instead of deleting, clamped to
  `MAX(now, finished_at + 1)` (M2), and — as today — never touches
  `progress` on this device.
- Merge (`watched-reconcile.ts`): a live row and its removal are weighed
  per title; a tie goes to the removal, not a device-id tie-break.
  `progress` is filtered against whichever side won — a live row's own
  time, or a removal's `lastFinishedAt`.
- Import (`watched-exchange.ts`): each kind is single-kind again; a live
  row supersedes progress ≤ its own time, a removal supersedes it ≤
  `lastFinishedAt` only.

## Related code files

- Modify: `sync-record.ts`, `merge.ts`, `store.ts`, `schema.ts`, `routes.ts`
  (H1 gating), `public/lib/playback/player.js` (H1: pause flushes)
- Create: `watched-reconcile.ts`, `watched-exchange.ts`
- Fixtures: `test/fixtures/watch-state/merge.json`,
  `test/fixtures/watch-state/record-parse.json`
- Tests: `test/shared-watch-state-fixtures.test.ts` (reads fixtures as-is),
  `test/state-store.test.ts`, `test/state-migration.test.ts`,
  `test/state-merge.test.ts`, `test/state-http.test.ts`,
  `test/state-write-triggers-sync.test.ts`

## Todo

- [x] `UnwatchedRow` + parser, on its own `unwatched` key
- [x] `schema.ts` v8: `ALTER TABLE watched ADD COLUMN removed_at INTEGER`
- [x] `watched-exchange.ts`: export splits live/removed; import is
      single-kind per array
- [x] `watched-reconcile.ts`: tie goes to the removal; `finishedAt` from
      whichever side won
- [x] `store.ts`: `setWatched` M2 clamp; delegates export/import
- [x] fixture cases: removal beats an older live mark (incl. from an
      old-shaped record with no `unwatched` key), re-mark beats an older
      removal, a same-time tie favours the removal regardless of device id,
      several old devices repeating a stale live mark still lose, a
      removal suppresses a position from before `lastFinishedAt` but not a
      rewatch after it
- [x] `./scripts/check.sh` (web portion)

## Success criteria

- Fixtures pass in both merge orders, including the mixed old/new cases.
- An old-format record (no `unwatched` key) still parses and merges.
- `state-http.test.ts`'s "is kept and can be taken back" still passes
  unmodified.
