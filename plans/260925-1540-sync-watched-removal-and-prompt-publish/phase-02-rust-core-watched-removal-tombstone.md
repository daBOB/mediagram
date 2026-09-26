# Phase 2 — Rust core: watched tombstone, mirrored

**Revised after review** (C1): ports phase 1's *revised* design — a
separate `UnwatchedRow` on its own `unwatched` key, not a `removed` flag on
`WatchedRow`. See plan.md's design section.

**Re-review** (R1, R2): `rows::set_watched` gained the symmetric clamp on
marking, and `watched_exchange::import_unwatched` now tracks whether the
standing row is itself live or removed to apply the correct tie rule —
`Standing.removed`, checked in `watched_exchange_tests.rs`.

## Context

- `crates/mediagram-core/src/state/record.rs`, `record/parse.rs`,
  `merge.rs`, `merge/watched.rs`, `rows.rs`, `exchange.rs`,
  `watched_exchange.rs`, `schema.rs`
- `crates/mediagram-core/tests/shared_watch_state_fixtures.rs` (reads the
  same fixtures phase 1 extends)
- `android/core/data/src/main/kotlin/WatchStateRepository.kt` — confirm the
  UniFFI surface (`setWatched(profileId, setId, finished: bool)`) needs no
  signature change

## Overview

Priority P1. Port phase 1's design so the two engines agree byte for byte —
pinned by the shared fixtures, run in both directions.

## Requirements

Same points as phase 1, in Rust:

- `record::UnwatchedRow{set_id, updated_at, last_finished_at}`, a plain
  `Vec` field on `ProfileState` (`#[serde(default)]`, the pattern every
  other list here already uses); `record/parse.rs` gains `unwatched_row()`.
- `schema.rs` GROUPS gains a 4th group:
  `ALTER TABLE watched ADD COLUMN removed_at INTEGER`. `rows::watched_for`
  filters `removed_at IS NULL`.
- `rows::set_watched(finished=false)` tombstones, clamped to
  `MAX(now, finished_at + 1)` (M2).
- `merge/watched.rs::reconcile`: per title, a live row and its removal
  weighed against each other; a tie favours the removal, not a device-id
  tie-break (`tie_break.rs` still handles same-kind ties within each map).
- `watched_exchange.rs`: `export_watched` returns the live/removed split;
  `import_watched`/`import_unwatched` are each single-kind writers.

## Related code files

- Modify: `record.rs`, `record/parse.rs`, `merge.rs`,
  `merge/tie_break.rs`, `rows.rs`, `exchange.rs`, `schema.rs`
- Create: `merge/watched.rs`, `watched_exchange.rs`
- Tests: `exchange_tests.rs`, `migration_tests.rs`, `rows_tests.rs`,
  `kids_profile_tests.rs` (new `unwatched` field on `MergedProfile`
  literals), `watched_exchange_tests.rs` (new)

## No UI change

`api/state.rs::set_watched` already takes `finished: bool`; no UniFFI
signature or binding regeneration is needed for this phase.
`WatchStateRepository.setWatched(setId, finished: Boolean)` is already
general in Kotlin, but nothing in `android/feature/*` calls it with
`false` — no Android screen offers "un-mark watched" today. Left alone:
inventing that UI is out of scope, noted in the final report instead.

## Todo

- [x] `record.rs` / `record/parse.rs`: `UnwatchedRow` + parser
- [x] `schema.rs` v4 group (unchanged by the revision)
- [x] `rows.rs`: `set_watched` M2 clamp, `watched_for` filter (unchanged)
- [x] `merge/watched.rs`: reconcile with tie-goes-to-removal
- [x] `watched_exchange.rs`: export split; single-kind import per array
- [x] `exchange.rs` slimmed to delegate, back under 200 lines
- [x] update `MergedProfile`/`WatchedRow` literals across tests for the
      new field and the dropped `removed` field
- [x] migration test: a v3 `watched` row survives the v3→v4 ALTER
      (unaffected by the revision — same column, same migration)
- [x] `cargo test -p mediagram-core` (fixtures pass against phase 1's cases,
      including the mixed old/new and tie cases)
- [x] `scripts/generate-android-bindings.sh` only if the UniFFI surface
      changed — it does not here (`record::UnwatchedRow` carries no
      `uniffi::Record` derive); skip and note why in the report

## Success criteria

- `shared_watch_state_fixtures.rs` passes every case phase 1 adds, forward
  and reversed.
- `cargo clean -p mediagram-core -p mlib-spec` then rebuild is green if a
  "multiple crate versions" error appears.
