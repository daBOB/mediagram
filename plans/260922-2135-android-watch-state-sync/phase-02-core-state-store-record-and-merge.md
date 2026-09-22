# Phase 02 — Core state store, record, merge

## Context links

- `web/src/state/schema.ts:20-178` (tables), `web/src/state/store.ts:206-470` (operations, `exportRecord` 350, `importMerged` 379, `profileNamed` 442, `deviceId` 313)
- `web/src/state/sync-record.ts`, `web/src/state/merge.ts`
- `crates/mediagram-core/src/api/mod.rs:127-133` (`Core`), `crates/mediagram-core/Cargo.toml` (rusqlite `bundled` on Android)
- Phase 01 fixtures

## Overview

- Priority: P1. Status: pending. Blocked by 01.
- A local store in `mediagram-core`, same tables and names as the web's, plus the record and merge ported line for line. UniFFI methods to read and write it. No network.

## Key insights

- **Why the core, not Room:** the merge and record exist once in TS; a Kotlin copy would be a third dialect. The core already bundles SQLite and serde_json and will own the Telegram half (03). Kotlin gets thin calls.
- **Separate file** `data_dir/state.db`, never inside `catalog/` — a library refresh replaces that directory (`refresh::install_staged`), and the web separates its state DB for the same reason (`schema.ts:1-12`).
- **Kids has no profile** (`schema.ts:115-128`); watched and positions do. Keep that.
- Preferences table (web v5) is out of scope; parity phase 6 adds it.
- Rust `str::to_lowercase` handles final sigma like JS; `trim` differs from JS on U+FEFF only. Fixture decides; add `unicode-normalization` for NFC (new dep, pure Rust).

## Requirements

- Functional: profiles (list, create, choose/chosen), progress (set/clear), watched (set/unset), watchlist, kids, collections (create/rename/delete/set member, ordered by `position`), snapshot per profile, device id, export record, import merged.
- Record export byte-shape: `{format:1, device, writtenAt, profiles:[{name, localId, progress:[{setId, at, duration, updatedAt}], watched:[{setId, updatedAt}]}]}` — camelCase, ms timestamps, seconds positions.
- `import_merged` corrective: never deletes a row absent from the merge; a completion deletes positions with `updated_at <=` it; creates profiles by display name for unknown viewers.
- Non-functional: **nothing throws to Kotlin from a write**; writes swallow and log, reads return empty. Every file < 200 lines.

## Architecture

```
crates/mediagram-core/src/state/
  mod.rs        StateDb { Mutex<Option<Connection>> }, open lazily at data_dir/state.db, migrations
  schema.rs     GROUPS (version 1 = web v1..v4 tables minus preferences, final shape)
  profiles.rs   list/create/choose, profile_named (normalised match)
  rows.rs       progress, watched, watchlist, kids
  lists.rs      collections + items
  record.rs     SyncRecord serde types, parse_record (hostile, JS Number() semantics), normal_name
  merge.rs      merge_states (keep/tie-break/tombstone), canonical order
  exchange.rs   export_record, import_merged
crates/mediagram-core/src/api/state.rs   #[uniffi::export] impl Core { ... } thin wrappers
crates/mediagram-core/tests/shared_watch_state_fixtures.rs   reads ../../web/test/fixtures/watch-state/{record-parse,merge}.json
```

Data flow: Kotlin call → `Core` → `StateDb` (sync `std::sync::Mutex`, short critical sections) → SQLite. Lifetime: one `StateDb` per `Core`, one `Core` per process (`CoreProvider`). No per-request state added to shared structs beyond the lazily opened connection.

UniFFI surface (names follow the web store): `profiles()`, `create_profile(name) -> Option<Profile>`, `chosen_profile() -> Option<String>`, `choose_profile(id)`, `snapshot(profile_id) -> StateSnapshot`, `set_progress(profile_id, set_id, at, duration: Option<f64>)`, `clear_progress`, `set_watched(profile_id, set_id, finished)`, `set_watchlisted`, `set_kids(set_id, marked)`, `create_collection`, `rename_collection`, `delete_collection`, `set_in_collection`. Records: `Profile{id,name}`, `ProgressRow{set_id, at, duration, updated_at}`, `WatchedRow{set_id, finished_at}`, `ListRow{id, name, items}`, `StateSnapshot{progress, watched, watchlist, kids, collections}`. Plain `Vec`/`Option` only (lesson: fixed-size arrays are not bindable).

## Related code files

- Create: files listed above.
- Modify: `crates/mediagram-core/src/lib.rs` (add `pub mod state;`), `crates/mediagram-core/src/api/mod.rs` (`mod state;`, `StateDb` field on `Core`, constructor), `crates/mediagram-core/Cargo.toml` (`unicode-normalization`), `crates/mediagram-core/tests/api_surface.rs` if it enumerates exports.
- Delete: none.

## Implementation steps

1. `record.rs` + `merge.rs` first, driven by the fixture test until every merge and parse case passes, including reversed order.
2. `schema.rs` + `mod.rs`: `PRAGMA user_version` migrations shaped like `schema.ts` `GROUPS`; foreign keys on.
3. `profiles.rs`, `rows.rs`, `lists.rs` mirroring `store.ts` SQL (same `ON CONFLICT` upserts, `INSERT OR IGNORE`, position renumbering).
4. `exchange.rs`: export/import ported from `store.ts:350-440`. Test: export → JSON → web `parseRecord` shape equality via a golden JSON in the fixture dir (add `export-golden.json` produced by the web store from the same rows).
5. `api/state.rs` wrappers. Regenerate the committed binding: `cargo build -p mediagram-core && cargo run -p mediagram-core --features cli --bin uniffi-bindgen -- generate --library target/debug/libmediagram_core.so --language kotlin --out-dir android/core/rust/src/main/kotlin` (command from `src/bin/uniffi_bindgen.rs:1-4`); commit `mediagram_core.kt` with the Rust change. `.so` rebuild (`scripts/build-android-core.sh`) happens in 04 when Kotlin first calls it.
6. `cargo clippy --all-targets -- -D warnings`, `cargo test --all`.

## Todo

- [ ] record.rs + parse fixture green
- [ ] merge.rs + merge fixture green (both orders)
- [ ] schema + migrations
- [ ] profiles / rows / lists
- [ ] export/import + golden
- [ ] UniFFI wrappers + regenerated binding
- [ ] clippy + tests

## Success criteria

- `cargo test -p mediagram-core` runs all fixture cases; zero skips when `web/` is present.
- Unit tests: position survives a catalog refresh (state.db untouched by `install_staged`); import never deletes an absent row; completion removes an older position.
- App builds and behaves exactly as before (nothing calls the new methods yet).

## Risks

| Risk | L×I | Mitigation |
|---|---|---|
| Rust/TS merge drift | M×H | Fixture from 01 is authoritative; test is required, not optional |
| SQLite called on Kotlin main thread | M×M | Document on each method; 04 wraps in `Dispatchers.IO` |
| Two sqlite3 copies on Android | L×H | Reuse the existing `rusqlite` dependency; no new sqlite crate (`Cargo.toml` comment explains) |
| Clock skew between devices decides LWW | M×L | Same as web; documented, not fixed |

## Security

State is local, unencrypted, app-private (`filesDir`), like the catalog. Contains titles watched and profile names — no secrets. Profile is a convenience, not auth (`profile-picker.js:1-10`).

## Next steps

03 adds the channel and `sync_state`. 04 consumes the surface.
