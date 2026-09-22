# Phase 08 — List sync: watchlist, kids, collections in the record

**Gated on Q1.** Skip entirely if the user keeps lists per device; 09 then records the gap.

## Context links

- `web/src/state/sync-record.ts:14-20` — why lists were left out: a deleted watchlist row leaves no trace, so a merge resurrects it
- `web/src/state/sync-record.ts:78-117` — parser reads only known keys; unknown keys are ignored by old readers
- `web/src/state/store.ts:171-186,226-237,458-553` — every list read and write (enumerated below)
- `web/src/state/schema.ts:14,20-178` — `STATE_SCHEMA = 5`; `260922-2105-settings-menu` phase 01 also claims v6 (**Q5**)
- Phases 02/03 (Rust store, record, merge)

## Overview

- Priority: P2. Status: pending. Blocked by 03 and 07.
- Add removal-aware rows for watchlist, kids and collections to the record, on both surfaces, so the kept shelves are the same everywhere.

## Key insights

- **No format bump.** Additive optional keys; a format-1 reader that predates this ignores them (`sync-record.ts:78-117`) and keeps merging positions. Bumping would make every old web player drop Android's positions too.
- **Tombstones as rows, LWW per key**, same `keep()` tie-break as positions (`merge.ts:118-136`). A removal is a row with `removed: true` and its own `updatedAt`.
- Absence is still not removal (corrective import, `store.ts:370-376`); only a newer tombstone removes.
- Kids sits at the record top level (it has no profile). Watchlist and collections sit under the profile.
- Collections: LWW per whole list `{id, name, items[], updatedAt, removed?}`. Ids are UUIDs (`store.ts:480`), so identity is global. Two devices editing one list in the same 5 min: later edit wins whole; documented, accepted (KISS over per-item CRDT).

## Requirements

- Record (both sides): `profiles[].watchlist: [{setId, updatedAt, removed?}]`, `profiles[].collections: [{id, name, items, updatedAt, removed?}]`, top-level `kids: [{setId, updatedAt, removed?}]`.
- Store (both sides): keep a removed row instead of deleting it: `removed_at` column (nullable) on `watchlist`, `kids`, `collections`; `updated_at` on `collections`, bumped by rename and by any item change. Reads filter `removed_at IS NULL`. Re-adding clears it.
- Merge + import: LWW per key; importing a live row over a local tombstone (newer) restores it; a newer tombstone removes locally.
- Fixture `lists-merge.json` added to phase 01's directory, run by bun and cargo.

## Architecture

Web writes enumerated (all must stamp/respect `removed_at`): `store.ts:171-173` watchlist read, `:176-186` collections read, `:226-237` `setWatchlisted`, `:458` `kids()`, `:462` `setKids`, `:475` `createCollection`, `:493` `renameCollection`, `:505` `deleteCollection`, `:521` `addToCollection`, `:541` `removeFromCollection`. Export/import: new module `web/src/state/lists-exchange.ts` (keeps `store.ts` from growing further), called from `exportRecord`/`importMerged`.

## Related code files

- Create: `web/src/state/lists-exchange.ts`, `web/test/fixtures/watch-state/lists-merge.json`, `web/test/state-lists-sync.test.ts`, `crates/mediagram-core/src/state/lists_exchange.rs`.
- Modify (web): `web/src/state/schema.ts` (next free version: removed_at/updated_at), `web/src/state/store.ts` (sites above), `web/src/state/sync-record.ts` (optional fields + hostile parsing), `web/src/state/merge.ts` (LWW for the three kinds), `web/test/shared-watch-state-fixtures.test.ts`.
- Modify (rust): `state/schema.rs` (group 2), `state/rows.rs`, `state/lists.rs`, `state/record.rs`, `state/merge.rs`, `state/exchange.rs`, `tests/shared_watch_state_fixtures.rs`.
- File ownership: web half and rust half disjoint → may run in parallel; both depend on the fixture written first.

## Implementation steps

1. Write `lists-merge.json` (add, remove, re-add, tie, tombstone vs live in both orders, old-format doc without the keys changes nothing).
2. Web: migration, store sites, parser, merge, exchange; `bun test`.
3. Rust: same; `cargo test`; regenerate binding only if API changed (it should not).
4. Two-machine test on each side: remove on A, B converges and does not resurrect.
5. Rebuild core `.so`, install on tablet (09 validates).

## Todo

- [ ] lists-merge.json
- [ ] web migration + store + record + merge + exchange
- [ ] rust group 2 + same
- [ ] both fixture runners green
- [ ] two-machine no-resurrection tests

## Success criteria

- Remove a title from the watchlist on the web; after one round on each, the tablet no longer lists it and the next round does not bring it back.
- An unupgraded web player on the channel keeps syncing positions with an upgraded tablet (old-reader test in fixture).

## Risks

| Risk | L×I | Mitigation |
|---|---|---|
| Schema version collision with settings plan | M×M | Q5: take next free number at implementation; tests assert migration from 5 |
| Tombstones grow forever | L×L | Rows per title, KB; prune tombstones older than 180 days later if ever measured large |
| Whole-list LWW loses a concurrent edit | L×L | Documented; per-item merge is a later change if it bites |

## Security

Same channel, same doc; list names are user text, parsed as hostile (trim, non-empty, length cap 200).

## Rollback

Revert both halves together. Columns added are nullable; an older build ignores them.

## Next steps

09.
