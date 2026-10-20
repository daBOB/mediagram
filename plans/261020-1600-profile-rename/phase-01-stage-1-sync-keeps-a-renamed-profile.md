# Phase 01 — Stage 1: sync keeps a renamed profile as one person

**Design:** sections 1 (wire), 2 (merge), 3 (import), 4 (reserved names), 7 (older builds), 8 (testing). **Status:** pending.
**What ships:** nothing a viewer can see. A device on this build takes a rename made elsewhere in by renaming its own row, folds two rows of one person into one, and refuses a former name for a new profile. Nothing here can rename.
**Words used throughout:**
- *entry*: one profile object in one device's document.
- *person*: every entry whose names (current or former, normalised) connect.
- *winner*: the person's entry with the highest `renamedAt`, then the greater device id, then the one met first.
- *name keys*: `{ renamedAt?: number, formerNames?: string[] }`, written only when `renamedAt > 0` / the list is not empty.

### Task 1: Web — the wire keys, the fixture, the merge pre-pass

**Files**
- Create `web/src/state/name-record.ts` (≤80 lines):
  - `export interface NameKeys { renamedAt?: number; formerNames?: string[] }`;
  - `export const MAX_FORMER_NAMES = 32`;
  - `export function sortedNames(names: Iterable<string>): string[]`: each once, UTF-8 byte order (`Buffer.compare`);
  - `export function parseNameKeys(row: Record<string, unknown>, own: string): NameKeys`, read as hostile:
    - `renamedAt` kept only when `isStamp(numberFromScalar(...))`;
    - `formerNames`: not an array, or more than 32 entries as written, says nothing. Otherwise each entry on its own must be a string; trimmed with runs of space collapsed (as `cleanName`, without its cut); not empty; at most 120 code points. It is kept as `normalName` of that and dropped when equal to `normalName(own)`. Then `sortedNames`, and the key is omitted when empty.
- Create `web/src/state/renames.ts` (≤120 lines): `export function resolveRenames(records: SyncRecord[]): { records: SyncRecord[]; people: Map<string, NameKeys> }`.
  1. Over every entry of every record, join the entry's `normalName(name)` with `normalName` of each former name (a union-find over names; entries whose name does not normalise are skipped, as `mergeStates` skips them).
  2. Per person, the winner (see above; `renamedAt` absent is 0).
  3. Return new record objects (never mutate the input) in which:
     - every entry's `name` is the winner's `name.trim()`;
     - every `parent`, and every household's `admin.name`, whose normalised name belongs to a person is the winner's spelling;
     - a name that belongs to no person stays as it is.
  4. `people` is keyed by the winner's normalised name. Its value is `renamedAt` (only when > 0), and `formerNames`: every name of the person except the current one, `sortedNames`, first 32, only when not empty.
- Modify `web/src/state/sync-record.ts` (297, ceiling 297, +2):
  - import from `./name-record`;
  - `ProfileState extends StatsRows, RoleKeys, NameKeys`;
  - in `parseRecord`, `...parseNameKeys(row, name),` beside `...parseRoleKeys(row)`.
- Modify `web/src/state/merged.ts` (50): `MergedProfile extends StatsRows, MergedRoles, NameKeys`.
- Modify `web/src/state/merge.ts` (169, limit 200):
  - `mergeStates(given)` begins `const { records, people } = resolveRenames(given);`, and everything after it (rows, `mergeStats`, `mergeRoles`, `mergeHouseholds`) uses the rewritten `records`;
  - each pushed profile gains `...people.get(name)`.
  
  Spread only defined keys, so every existing fixture and test still holds.
- Create `web/test/fixtures/watch-state/profile-renames.json`, shaped like `households-sync.json`:
  - `parse: [{ name, input: "<document text>", expect: { profiles: [{ name, renamedAt?, formerNames? }] } }]`;
  - `merge: [{ name, records: SyncRecord[], expect: { profiles: [...], households?: [...] } }]`.
  
  The merge projection, which both runners build: profiles sorted by `name`, each `{ name, displayName, renamedAt?, formerNames?, parent?, progress?: setId[], watchlist?: [{setId, removed?}], titleStats?: [{setId, device}] }`, each list sorted and omitted when empty. Then `households` (only when some household has an admin): `[{ id, admin: admin.name }]`.
- Modify `web/test/shared-watch-state-fixtures.test.ts`: `describe("profile-renames parse fixtures")` and `describe("profile-renames merge fixtures")`. The second runs every case on `records` and on `[...records].reverse()`. Extend the header comment's list of fixtures that pin new rules.
- Modify `web/test/code-standards.test.ts`: `sync-record.ts` ceiling 297→299, with a dated sentence ("Raised 2026-10-2x for `src/state/sync-record.ts`, by 2, to read a profile's renames — the import and the line that fills them.").

**Fixture cases** (the `name` strings are the test names)

Parse:
- "a renamed profile reads back when it took its name and the names it had"
- "a profile nobody renamed says neither key"
- "a renamedAt that is not a stamp is absent" (one profile each: -5, 0, 9007199254740992, "soon", {})
- "former names are cleaned, normalised, sorted by their bytes, each once" (`[" CELINE ", "celine", "Ann  Marie", "Émile"]`)
- "a former name that is not a name, or longer than 120 characters, is dropped on its own" (42, "", "   ", null, {}, 121 × "x", 120 × "y", "ok")
- "a former name that is the profile's own name is dropped" (name "Celi", `["CELI", "celine"]`)
- "32 former names are kept, and a list of 33 is dropped whole"
- "a formerNames that is not a list says nothing"
- "neither key can drop the profile" (`renamedAt: {}`, `formerNames: 7`, and a progress row)

Merge:
- "nobody renamed: one viewer typed two ways keeps the greatest device's spelling, with neither key"
- "a rename and an older build's document under the old name are one person, named as renamed"
- "an older build's document never wins the name, even from the greater device id"
- "a chain A to B to C joins the partial documents into C" (C `["a","b"]`, a stale B `["a"]`, an older build's A)
- "two renames at once: the newer wins and the other name becomes a former name"
- "two renames at the same moment: the greater device id wins"
- "renaming back: the newer rename names the person, and stale documents still join"
- "a case-only rename wins its spelling everywhere" (the newer `Celi` from the smaller device id beats `celi`)
- "a removal made under the old name counts for the renamed person" (watchlist tombstone)
- "a kid's parent named by the old name is the renamed grown-up"
- "a household's admin named by the old name is the renamed grown-up"
- "viewing stats under both names are one viewer's"
- "a parent no document names stays as it is"
- "two people stay two"
- "a person with more than 32 names keeps the first 32 as former names"
- "a profile only an older build's document names keeps its old name"

**Commands:**
- `cd web && bun test test/shared-watch-state-fixtures.test.ts`
- `bun test test/code-standards.test.ts`
- `bun run typecheck`

**Commit:** `feat(web): carry a profile's renames in the sync record and merge them as one person`

### Task 2: Web — schema v17, export, take a rename in, fold, reserved names

**Files**
- Modify `web/src/state/roles-schema.ts` (81): `RENAMES_GROUP` (v16→v17) with a doc comment:
  - `ALTER TABLE profiles ADD COLUMN renamed_at INTEGER NOT NULL DEFAULT 0`;
  - `ALTER TABLE profiles ADD COLUMN former_names TEXT NOT NULL DEFAULT '[]'` (a sorted JSON list, as `hidden_sections` is).
  
  Append it to `ROLES_GROUPS`; `schema.ts` is untouched.
- Create `web/src/state/names-exchange.ts` (≤150 lines):
  - `exportNames(db): Map<string, NameKeys>`, by profile id: `renamedAt` when > 0, `formerNames` when not `'[]'`.
  - `takeIn(db, profile: MergedProfile): { id: string; kids: boolean; changed: number } | null`, replacing `findOrCreateProfile` in `importMerged`:
    1. Candidates: `firstOfEachName(db)` (`roles-exchange.ts`) at the merged name and at each merged former name, each row once.
    2. None: `findOrCreateProfile(db, profile.name, profile.displayName, profile.kids === true)`, counted once.
    3. Otherwise keep the oldest (smallest `createdAt`, then id). `foldProfile` every other candidate into it, each counted.
    4. Rename the kept row: set `name = profile.displayName` and `renamed_at = profile.renamedAt` when `normalName(row.name) !== profile.name` or `(profile.renamedAt ?? 0) > row.renamed_at`. Set `former_names = JSON.stringify(profile.formerNames ?? [])` when that differs from the stored text. Count one for any write.
    
    A spelling difference with no newer rename leaves the row's own spelling, as today.
- Create `web/src/state/profiles-fold.ts` (≤60 lines): `foldProfile(db, fromId, intoId)`.
  - For `progress`, `watched`, `watchlist`, `collections`, `preferences`, `stats_titles` and `stats_days`: `UPDATE OR IGNORE <table> SET profile_id = ?into WHERE profile_id = ?from`. A key the kept row already has stays the kept row's; the rows import that follows writes the newest, because this device's own export was part of the merge.
  - `UPDATE profiles SET parent_id = ?into WHERE parent_id = ?from`; `UPDATE households SET admin_id = ?into WHERE admin_id = ?from`.
  - `DELETE FROM profiles WHERE id = ?from` (its leftovers cascade).
  - Role columns are not copied: the role import that follows applies whatever the merge chose.
- Modify `web/src/state/store.ts` (740, ceiling 740, +3):
  - `exportRecord` spreads `...names.get(profile.id)` from `const names = exportNames(this.db)`;
  - `importMerged` calls `takeIn(db, profile)` where `findOrCreateProfile` was, and `changed += matched.changed` replaces `if (matched.created) changed += 1`;
  - imports adjusted.
  
  Raise the ceiling with a dated sentence in `code-standards.test.ts`.
- Modify `web/src/state/profiles-insert.ts` (96): `takenNames(db, exceptId?: string): string[]`: every profile's name and every name in its `former_names`, leaving out `exceptId`'s own. Stage 1 never passes `exceptId`.
- Modify `web/src/state/profiles-manage.ts` (194, ceiling 194, **no growth**): line 145 becomes `nameTaken(takenNames(this.db), name)`, with `takenNames` added to the existing `./profiles-insert` import. Extend the `unusable` doc comment only if that adds no line.

**Tests**
- `web/test/state-migration.test.ts`, `describe("v16 to v17")`: "every profile already stored was never renamed" (`renamed_at` 0, `former_names` `'[]'`, `schema_version` "17").
- Create `web/test/state-profile-rename-import.test.ts` (renames are set up with SQL on the store, as `state-households-exchange.test.ts` does):
  - "a store nobody renamed exports neither key" (no `renamedAt`/`formerNames` anywhere in `JSON.stringify(exportRecord(...))`)
  - "a rename taken in renames the row in place: the same id, its history, the new stamp and former names"
  - "a second import of the same rename changes nothing" (returns 0)
  - "a case-only rename takes the new spelling"
  - "without a rename, this device keeps its own spelling of a name"
  - "a viewer this device never met is made with the merged stamp and former names"
  - "two rows of one person fold into the older: its id, and the newest of each row"
  - "a fold moves the folded row's lists, so their ids never clash"
  - "a fold points a kid's parent and a household's admin at the kept row"
  - "a fold leaves the roles to the merge, not to the folded row"
- `web/test/state-profiles-manage.test.ts`: "a new profile may not take a name another profile had before" (grown-up and kid both answer `name-taken`).

**Commands:**
- `cd web && bun test test/state-profile-rename-import.test.ts test/state-migration.test.ts test/state-profiles-manage.test.ts test/code-standards.test.ts`
- then the full web checks.

**Commit:** `feat(web): take a rename in by renaming the row, and fold two rows of one person`

### Task 3: Core — the wire keys and the merge pre-pass, held to the fixture

Ports Task 1 line for line; `name-record.ts` and `renames.ts` are the reference.

**Files**
- Create `crates/mediagram-core/src/state/record/name_record.rs`:
  - `#[derive(Debug, Clone, Default, PartialEq, Serialize, Deserialize)] #[serde(rename_all = "camelCase")] pub struct NameKeys { renamed_at: f64, former_names: Vec<String> }`, each field `#[serde(default, skip_serializing_if = ...)]` (0 and empty write nothing);
  - `pub(crate) const MAX_FORMER_NAMES: usize = 32`;
  - `pub(crate) fn sorted_names(...)`;
  - `pub(crate) fn name_keys(row: &Map<String, Value>, own: &str) -> NameKeys`, with the web's hostile rules, using `hostile_json`'s `js_number` and `record.rs`'s `is_stamp`/`normal_name`.
- Modify `src/state/record.rs` (172): `mod name_record; pub use name_record::NameKeys;` and `ProfileState` gains `#[serde(flatten)] pub names: NameKeys`.
- Modify `src/state/record/parse.rs` (175): `names: name_keys(row, &name)`, computed before `name` moves.
- Modify `src/state/exchange.rs` (164): `names: NameKeys::default()` in `export_record` for now (Task 4 fills it).
- Modify `src/state/record/parse_tests.rs` if a `ProfileState` literal there needs the field.
- Modify `src/state/merge/merged.rs` (67): `MergedProfile` gains `#[serde(flatten)] pub names: NameKeys`.
- Create `src/state/merge/renames.rs` (≤150): `pub(super) fn resolve(records: &[SyncRecord]) -> (Cow<'_, [SyncRecord]>, HashMap<String, NameKeys>)`, the web's `resolveRenames`. It borrows when no name, parent or admin changes, so an install nobody renamed merges without a copy.
- Modify `src/state/merge.rs` (177): `mod renames;`; `merge_states` starts `let (records, mut people) = renames::resolve(records); let records: &[SyncRecord] = &records;`; each `MergedProfile` gets `names: people.remove(&name).unwrap_or_default()`.
- Create `src/state/record/name_record_tests.rs`:
  - `a_profile_nobody_renamed_writes_neither_key`
  - `a_renamed_profile_round_trips_through_the_wire`
- Modify `crates/mediagram-core/tests/shared_watch_state_fixtures.rs`:
  - `profile_renames_parse_fixtures_match_the_web`
  - `profile_renames_merge_fixtures_match_the_web_in_both_orders`
  
  Both over `profile-renames.json`, building the same projection as the web runner (a `serde_json::Value`), forward and reversed.

**Commands:**
- `cargo test -p mediagram-core --test shared_watch_state_fixtures`
- `cargo test -p mediagram-core name_record`
- `cargo test -p mediagram --test code_standards`
- clippy.

**Commit:** `feat(core): carry and merge a profile's renames, held to the web's fixture`

### Task 4: Core — schema v13, export, take a rename in, fold, reserved names

Ports Task 2; `names-exchange.ts`, `profiles-fold.ts` and `takenNames` are the reference.

**Files**
- Modify `src/state/schema/roles.rs` (64): `pub(super) const RENAMES: &[&str]` (v12→v13), the web's `RENAMES_GROUP` statement for statement, with the doc comment naming the web's v17. `src/state/schema.rs` (178) lists `roles::RENAMES` last in `GROUPS`.
- Create `src/state/exchange/names.rs` (≤170):
  - `pub(super) fn export(conn, id) -> NameKeys`;
  - `pub(super) fn take_in(conn, profile: &MergedProfile) -> rusqlite::Result<Option<(String, u64, bool)>>` (id, changes, kids). The web's `takeIn`; `first_of_each_name` (`exchange/roles.rs`) gives the candidates, and `profiles::profile_named_with_creation` makes a viewer never met.
- Create `src/state/exchange/names/fold.rs` (≤70): `fold(conn, from, into)`, the web's `foldProfile`, plus `UPDATE state_meta SET value = ?into WHERE key = 'chosen_profile' AND value = ?from`.
- Modify `src/state/exchange.rs` (164):
  - `mod names;`;
  - the export fills `names: names::export(conn, &profile.id)?`;
  - the import calls `names::take_in` where `profile_named_with_creation` was, and adds its count.
- Modify `src/state/profiles/role_rows/insert.rs` (91): `pub(crate) fn taken_names(conn, except: Option<&str>) -> rusqlite::Result<Vec<String>>`, re-exported from `role_rows.rs` beside `insert`.
- Modify `src/state/profiles/manage/checks.rs` (117): `unusable` collects `role_rows::taken_names(self.conn, None)?`.
- Modify `src/state/profiles.rs` (151): its header's "Rename stays deferred…" paragraph now says that sync carries a rename and this device takes it in by renaming its row (`exchange/names.rs`), and that this module still offers none.

**Tests**
- `src/state/upgrade_tests.rs`: `a_store_from_before_renames_reads_as_never_renamed`.
- Create `src/state/exchange/names_tests.rs`:
  - `a_store_nobody_renamed_exports_neither_key`
  - `a_rename_taken_in_renames_the_row_in_place`
  - `a_second_import_of_the_same_rename_changes_nothing`
  - `a_case_only_rename_takes_the_new_spelling`
  - `without_a_rename_this_device_keeps_its_own_spelling`
  - `a_viewer_never_met_is_made_with_the_merged_stamp_and_former_names`
  - `two_rows_of_one_person_fold_into_the_older`
  - `a_fold_moves_the_folded_rows_lists_without_a_key_clash`
  - `a_fold_points_a_kids_parent_and_a_households_admin_at_the_kept_row`
  - `a_fold_moves_the_chosen_profile_to_the_kept_row`
  - `a_fold_leaves_the_roles_to_the_merge`
- `src/state/profiles/manage/checks_tests.rs`: `a_new_profile_may_not_take_a_name_another_profile_had_before`.

**Commands:**
- `cargo test -p mediagram-core -- names upgrade checks`
- `cargo test -p mediagram --test code_standards`
- clippy.

**Commit:** `feat(core): take a rename in by renaming the row, and fold two rows of one person`

### Task 5: Both — two devices agree; docs, version, merge, Android release

**Tests** (the rename is made with SQL on the laptop's store: `name = 'Omi', renamed_at = 2000, former_names = '["oma"]'`; documents go through `parseRecord(JSON.stringify(...))` / the core's `parse_record`, as `state-households-exchange.test.ts:173-193` does)
- Create `web/test/state-profile-rename-sync.test.ts`:
  - "two devices agree on a rename, and a second round changes nothing": the TV has one profile, its id unchanged, named Omi, with Oma's history and `formerNames ["oma"]`; the next round returns 0 on both.
  - "an older build's document under the old name is filed under the new one, and makes no profile": strip both keys from a document and give it a new Oma position; the laptop files it under Omi.
  - "an older build's own merge shows both names": `mergeStates` of the stripped records lists oma and omi.
  - "an older build that holds the person's list cannot take the new name in, and keeps every row": the stripped merge imported into a store holding Oma's list throws, and every row is as before. This pins the build order.
- `crates/mediagram-core/src/state/exchange_tests.rs`, the same four:
  - `two_devices_agree_on_a_rename_and_a_second_round_changes_nothing`
  - `an_older_builds_document_under_the_old_name_is_filed_under_the_new_one`
  - `an_older_builds_own_merge_shows_both_names`
  - `an_older_build_holding_the_persons_list_cannot_take_the_new_name_in`

**Docs**
- `docs/system-architecture.md`, the paragraph "Neither surface renames…" (≈ line 694): sync now carries renames (the two keys, the pre-pass, rename in place and fold, former names reserved); neither surface offers a rename yet; what an older build does (both names; a person with a list stalls its sync until it updates).
- `docs/project-changelog.md`: a new minor entry, "Fixed/Changed: sync keeps a renamed profile as one person" (no viewer-visible change).

**Version and merge.** Bump the minor (Global Constraints). Run `scripts/check.sh`. Merge to main with the user's word.

The live player runs from main and migrates its `state.db` to v17 on reload. Copy that file aside before merging.

**Android release** (with the user's word): `scripts/release-android.sh`, which rebuilds the `.so`. Afterwards, check the box installed it compiled (`install-dm`). The tablets are updated over `adb` by the user's schedule (`ANDROID_SERIAL` pinned). Then the **Gate** in `plan.md`.

**Commits:**
- `test: two devices agree on a rename, and an older build's document folds in`
- `docs: sync keeps a renamed profile as one person`
- `chore: 0.1xx.0, sync keeps a renamed profile as one person`
