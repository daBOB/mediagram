# Phase 1: Synced scene marks

Spec: `docs/superpowers/specs/2026-11-13-clip-a-scene-design.md` § The mark. Model to copy: the profile watchlist
(tombstone, LWW on `updatedAt`), but keyed by a mark `id` instead of `setId`, since one title holds many marks.

### Task 1: Scene marks in the web player's state and sync record

**Files:**
- Modify: `web/src/state/sync-record.ts` (add `SceneRow`, `ProfileState.scenes?`, `sceneRow` parser, call in `parseRecord`)
- Modify: `web/src/state/schema.ts` (append one new group at the end of `GROUPS`, after `...ROLES_GROUPS`; never reorder existing groups)
- Create: `web/src/state/scenes-exchange.ts` (`exportScenes`, `importScenes`)
- Modify: `web/src/state/store.ts` (`scenes`, `markScene`, `removeScene`; export/import wiring in `exportRecord`/`importMerged`; `StateSnapshot.scenes`)
- Modify: `web/src/state/merge.ts`, `web/src/state/merged.ts` (`scenes` per profile, LWW by `id`)
- Modify: `web/src/state/routes.ts` (two routes below)
- Test: `web/test/state-scenes-sync.test.ts`

**Interfaces:**
- Produces (wire): `interface SceneRow { id: string; setId: string; at: number; updatedAt: number; removed?: true }`; `ProfileState.scenes?: SceneRow[]`; `MergedProfile.scenes?: SceneRow[]`.
- Produces (store): `WatchState.markScene(profileId: string, setId: string, at: number): string | null` (returns the new id; null when the profile is unknown or `at` is not a finite number ≥ 0); `WatchState.removeScene(profileId: string, id: string): boolean`; `StateSnapshot.scenes: { id: string; setId: string; at: number; markedAt: number }[]`, live rows only, newest `markedAt` first.
- Produces (HTTP): `POST /api/profiles/{p}/scenes` body `{ setId, at }` → `201 {"id": "..."}`, 404 unknown profile or unplayable set, 400 bad `at`; `DELETE /api/profiles/{p}/scenes/{id}` → 204, 404 when no live mark with that id. The snapshot route already returns `StateSnapshot`, so `scenes` arrives with it.
- Table: `scenes(id TEXT PRIMARY KEY, profile_id TEXT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE, set_id TEXT NOT NULL, at_seconds REAL NOT NULL, marked_at INTEGER NOT NULL, removed_at INTEGER)` — match the `REFERENCES`/cascade style the `watchlist` table uses after its profile migration; if it has none, copy what it has.
- Id: 16 random URL-safe chars (`crypto.randomUUID()` without dashes, cut to 16, is fine).

- [ ] **Step 1: Write the failing tests** in `web/test/state-scenes-sync.test.ts` (copy the setup of `state-lists-sync.test.ts`):
  - `marking a scene shows it in the snapshot, newest first` — two marks on one set at 61.5 and 3600; snapshot `scenes` has both, ids distinct, `at` kept to the millisecond.
  - `removing a scene leaves a tombstone that the record carries` — `exportRecord` has the row with `removed: true` and `updatedAt` = removal time.
  - `a scene marked on another device arrives through importMerged` and `a later removal elsewhere takes it off here`.
  - `merge keeps the later of two edits to one id, removal included` (on `mergeStates` directly).
  - `parseRecord drops a bad scene row and keeps the rest` — rows with missing `at`, `at: -1`, `id: 5`, `setId: ""` dropped; a good one kept; `scenes` absent stays `undefined`.
  - Routes: `POST scenes returns the id; 404 for an unknown profile; 400 for at = "x"`; `DELETE an unknown id is 404`.
- [ ] **Step 2: Run** `cd web && bun test test/state-scenes-sync.test.ts` — expect FAIL (missing exports).
- [ ] **Step 3: Implement** the files above. Parser rule: `at` must be finite and ≥ 0, `updatedAt` must pass `isStamp`, `id`/`setId` non-empty text; `removed` only when `=== true`.
- [ ] **Step 4: Run** the new test file, then `cd web && bun test && bun run typecheck && bun run lint` — all pass.
- [ ] **Step 5: Commit** `feat(web): scene marks in the watch state and sync record`.

### Task 2: Scene marks in the Rust core (Android's state)

**Files:**
- Create: `crates/mediagram-core/src/state/record/scene_record.rs` (+ `scene_record_tests.rs`) — `SceneRow`
- Modify: `crates/mediagram-core/src/state/record.rs` (`ProfileState.scenes: Vec<SceneRow>`, `#[serde(default, skip_serializing_if = "Vec::is_empty")]`), `record/parse.rs` (tolerant parse like `preferences`)
- Modify: `crates/mediagram-core/src/state/schema.rs` (new migration appended, same table as Task 1), `schema_tests.rs` table list
- Create: `crates/mediagram-core/src/state/scenes.rs` (+ `scenes_tests.rs`) — rows
- Create: `crates/mediagram-core/src/state/exchange/scenes.rs` (+ tests); wire into `exchange.rs` `export_record`/`import_merged`
- Modify: `crates/mediagram-core/src/state/merge.rs`, `merge/merged.rs` (LWW by id)
- Modify: `crates/mediagram-core/src/api/state.rs` (`StateSnapshot.scenes`, `mark_scene`, `remove_scene`)

**Interfaces:**
- Consumes: Task 1's wire shape — JSON keys `id`, `setId`, `at`, `updatedAt`, `removed`.
- Produces: `pub struct SceneRow { pub id: String, pub set_id: String, pub at: f64, pub updated_at: f64, #[serde(default, skip_serializing_if = "is_false")] pub removed: bool }` (serde camelCase); `scenes::mark(conn, profile_id, set_id, at) -> rusqlite::Result<Option<String>>`; `scenes::remove(conn, profile_id, id) -> rusqlite::Result<bool>`; `scenes::live_for(conn, profile_id) -> rusqlite::Result<Vec<SceneMark>>`.
- Produces (UniFFI API on the core object): `pub async fn mark_scene(self: Arc<Self>, profile_id: String, set_id: String, at: f64) -> Option<String>`; `pub async fn remove_scene(self: Arc<Self>, profile_id: String, id: String) -> bool`; `StateSnapshot.scenes: Vec<SceneMark>` with `SceneMark { id, set_id, at, marked_at }` as a `uniffi::Record`, newest first.

- [ ] **Step 1: Write the failing tests:**
  - `scene_record_tests.rs`: `a_scene_row_reads_the_json_the_web_player_writes` — parse `{"id":"a1","setId":"S","at":61.5,"updatedAt":1700000000000}` and the same with `"removed":true`; `a_bad_scene_row_is_dropped_alone` (missing `at`, `at:-1`, `id:5`).
  - `scenes_tests.rs`: `marking_twice_on_one_title_keeps_both_newest_first`; `removing_keeps_a_tombstone`.
  - exchange tests: `scenes_round_trip_through_export_and_import`; `a_later_removal_from_another_device_wins`.
  - merge tests: `the_later_edit_to_one_scene_id_wins`.
- [ ] **Step 2: Run** `cargo test -p mediagram-core scene` — expect FAIL.
- [ ] **Step 3: Implement.** Mirror Task 1 behaviour exactly; the TS tests are the reference.
- [ ] **Step 4: Run** `cargo test -p mediagram-core` and `cargo clippy -p mediagram-core --all-targets -- -D warnings` — pass.
- [ ] **Step 5: Commit** `feat(core): scene marks in the synced state`.
