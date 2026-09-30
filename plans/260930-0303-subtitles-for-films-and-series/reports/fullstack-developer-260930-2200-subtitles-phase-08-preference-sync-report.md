# Phase 08 report: subtitle preferences sync

Status: DONE (cross-device check left to lead, needs user go-ahead).

## Changed
Web: new `web/src/state/preferences-record.ts` (parse/export/import/stamp, 4 synced names); `sync-record.ts` (314 lines, was 328: three list parsers folded into one `parseRows`); `merge.ts`; `store.ts` (797, was 799); `routes.ts` `writeWorthSyncing` (`/preferences` now syncs).
Core: `record.rs` + `record/preference_record.rs` (wire row, names, hostile parse) + `record/parse.rs`; `merge.rs` (still 200) + `merge/preferences.rs`; `merge/tie_break.rs`; `preferences_exchange.rs` (+ tests); `exchange.rs`; `preferences.rs` stamp `max(now, stored+1)`; `mod.rs`.
Fixtures: `record-parse.json` (+2), `merge.json` (+3), shared by both surfaces.
Docs: `docs/system-architecture.md` watch-state section.

## Deviations
- Merge fold lives at `state/merge/preferences.rs`, not `state/merge_preferences.rs`: `Held`/`keep` are `pub(super)` to the `merge` module, so a sibling of `merge.rs` could not use them.
- Touched outside the listed ownership, all mechanical: `crates/mediagram-core/tests/shared_watch_state_fixtures.rs` (sort preferences in `canonical`), `state/kids_profile_tests.rs` (new struct field), web `state-merge.test.ts` (merge now always fills `preferences: []`), `state-write-triggers-sync.test.ts` (preference PUT now fires a round).
- `/preferences` writes trigger a round for every name (route does not inspect the name), so an audio change also schedules one; debounced, harmless.
- Rust wire serialisation omits an empty `preferences`; web always exports the array. Both parse either.
- uniffi surface: UNCHANGED (no .so/bindings rebuild needed).

## Verification
- web: `bunx tsc --noEmit -p .` clean, `bun run lint` clean, `bun test` 2442 pass / 0 fail (ratchets hold).
- core: `cargo clippy --all-targets --all-features -- -D warnings` clean; `cargo test -p mediagram-core` all pass (shared fixtures run against both).
- Not run: `cargo fmt --check` repo-wide (baseline already dirty in untouched files); new Rust files rustfmt-clean.

## Changelog entry
**Added**

- Subtitle language and cue style (size, backing, offset) now follow the profile to every device through the existing watch-state sync: an optional `preferences` list on each profile in the `#mlib-state` document, merged newest-write-wins per scope and name with the device-id tie-break, on both the web player and the Rust core. Audio, speed and framing stay per device. No format bump: older builds drop the key and keep syncing positions. A local write is stamped no earlier than the row it replaces, and the web player now runs a debounced sync round after a preference write.

## Unresolved
- Untagged manual films/episodes are scoped differently on web (slugged) vs core (courses/docus only), so their per-show choices do not meet across surfaces; documented in system-architecture.
