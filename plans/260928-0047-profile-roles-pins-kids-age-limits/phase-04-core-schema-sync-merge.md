# Phase 04 — Core: schema, record, merge, exchange

## Context links

- Contract (authoritative): [shared-contract.md](shared-contract.md) §6 schema, §7 wire/merge/import/export, §10 fixtures
- Spec: `docs/superpowers/specs/2026-09-28-profile-roles-design.md` §3 (data and sync), §6 (core)
- Web reference (phase 01 creates it — plan: [phase-01](phase-01-web-schema-sync-merge.md)): `web/src/state/{schema,sync-record,merge,store}.ts`, fixture
  `web/test/fixtures/watch-state/profile-roles-merge.json`
- Core today: `crates/mediagram-core/src/state/{schema,record,merge,exchange,lists_exchange,rows,repair,mod}.rs`
- Fixture runner: `crates/mediagram-core/tests/shared_watch_state_fixtures.rs`
- Line limit: `crates/mediagram/tests/code_standards.rs` (200 per file under `crates/*/src`, `*_tests.rs` exempt)
- Bumping: [phase-08 § Bumping](phase-08-verify-docs-version.md)

## Overview

Priority P1 (05 and 06 build on it). Status: pending. Effort ~5h.
The data half of the core: schema v7 (web's v11 statements), the new optional
`ProfileState` keys and the Kids mark `age`, their merge (pinned to the web by
`profile-roles-merge.json`), export/import, and `rows::set_kids` with an age.
No uniffi surface changes here — the Kotlin bindings stay byte-identical, so this
phase is pushable on its own.

## Key insights (verified)

- **Two files are at the limit and must shrink before they grow.** `state/merge.rs` is exactly 200
  lines; its output structs `MergedProfile`/`MergedState` sit at `merge.rs:36-74` → move them to
  `merge/merged.rs` with `pub use` (paths `state::merge::MergedProfile` unchanged). `state/rows.rs`
  is 195; Kids (`rows.rs:165-192`) moves to `rows/kids_marks.rs` with `pub use` (callers
  `rows::kids`/`rows::set_kids` unchanged: `api/state.rs:100,159`, `lists_exchange_tests.rs:95`,
  `migration_tests.rs:74`, `sync_tests.rs:407`, `rows_tests.rs:132`).
- **Schema:** `GROUPS` at `schema.rs:15-134`, `VERSION = GROUPS.len()` (`:136`) is 6 → the new group
  makes it 7. Migrations run in one transaction (`state/mod.rs:122-145`).
- **Real failure mode the new group introduces:** `open()` runs `migrate` *then*
  `repair::add_missing_kids_column` (`state/mod.rs:111-112`, `repair.rs:172-180`). A pre-release file at
  v3 without `profiles.kids` would now fail v7's `UPDATE profiles SET kids_age = 12 WHERE kids = 1`,
  roll back, and leave the store unopenable. `upgrade_tests.rs:218-245`
  (`a_version_three_file_without_kids_gains_the_column_on_open`) will go red and prove it. Fix: repair
  runs *before* migrate and only when `user_version >= 3` (below 3 the v3 migration adds the column;
  a repair there would make that `ALTER` fail as a duplicate).
- **Wire:** `ProfileState` at `record.rs:67-89`, hostile parse `record/parse.rs:189-219` (147 lines;
  helpers `js_number`/`text_` in `record/hostile_json.rs:16-50`). `ListRow` at `record/list_record.rs:13-20`
  is shared by watchlist, Kids and editor's choice; `list_row` (`:36-49`) parses all three
  (`parse.rs:165-172`, `:210-213`). Kids needs its own `kids_row` so watchlist rows never carry `age`.
- **One struct for all four role keys, flattened.** `ProfileRoles { admin, kids_age, parent, pin }` with
  `#[serde(flatten)]` on `ProfileState` and on `MergedProfile`: one line per struct instead of four,
  one type reused by record, merge, exchange and the fixture runner. Only two `ProfileState` literals
  exist (`exchange.rs:35`, `parse.rs:192`); `MergedProfile` literals: `merge.rs:184`,
  `kids_profile_tests.rs:14` (lists every field — needs `roles`), `exchange_tests.rs:23,50,83,119,149`
  (already `..Default::default()`).
- **`ListRow` gains `age`** → 11 literal sites need `age: None`: `lists_exchange.rs:20`,
  `record/list_record.rs:44`, `record/list_record_tests.rs:18,26,34`, `lists_exchange_tests.rs:31,50,72,89`,
  `lists_exchange/editors_choice.rs:76,107`. (`lists.rs:31,50` and `migration_tests.rs:79,84` are the
  unrelated `lists::ListRow`.)
- **"Only the literal 6":** JS `age === 6` accepts JSON `6` and `6.0`, rejects `"6"`. Rust:
  `raw.get("age").and_then(Value::as_f64) == Some(6.0)` — same set (serde_json `Number(6.0) != json!(6)`,
  so `==` on `Value` would differ).
- **Tie-break reuse:** `keep` (`merge/tie_break.rs:237-257`) works for any `Timestamped`
  (`:216-229`); adding `KidsAge` and `PinRecord` to the macro list gives `kidsAge`/`pin` the exact
  newest-wins-then-device-id rule. `parent` mirrors `display_name`'s rule (`merge.rs:113-128`: first
  seen, replaced by a greater device id) and is output **only on a kid** (contract §7, amended).
  `admin` is the minimum `(claimedAt, normalName)` over **grown-up** merged viewers only (a claim on a
  kid viewer is ignored, §7 amended). Whether a viewer is a kid is only known once every document is
  in (`kids` is sticky across documents, `merge.rs:129-134`), so claims are gathered per viewer during
  the pass and the admin is chosen after it, from the merged profiles.
- **Web phase 01 settles what the fixture does not** (`phase-01-web-schema-sync-merge.md` § Interfaces
  "For the core port", Task 5.4, Task 6): a Kids mark's `age` is exported on **live** marks only
  (tombstones carry none); on import, an equal-time row is taken when it differs — removed where this
  is live, or live with another age — and a tombstone's leftover `age` column is never compared (the web
  already applies equal-time removal differences, `web/src/state/lists-exchange.ts:86-87`; the core's
  `import_kids` skips every tie, `lists_exchange.rs:58`, so a Kids tie with another age would never
  converge); a live import writes `age`, a removal leaves it; the import's kids upgrade sets
  `kids_age = COALESCE(kids_age, 12)`. All four are ported here.
- **Deliberate difference, written down:** the core stamps an in-place Kids age change at
  `MAX(now, last + 1)`; the web's `setKids` (phase 01 Task 5.3) stamps `Date.now()`. Not fixture-visible;
  the core's rule prevents a clock-skewed import from reverting the change. Raised for a decision
  (report); if the web does not adopt it, phase 08 records the difference in `docs/system-architecture.md`.
- **Old fixtures would break.** `merge.json:858-869` has a kids profile; the core runner compares
  whole `MergedState`s (`shared_watch_state_fixtures.rs:78-110`), so a merged kid now carrying
  `kidsAge {12, 0}` fails it. The web runner picks fields explicitly
  (`web/test/shared-watch-state-fixtures.test.ts:59-75`); the core's `canonical` does the same by
  clearing `roles` for `merge.json`/`lists-merge.json`. `profile-roles-merge.json` gets its own
  projection (contract §10: "compared on those fields only").
- **Import pattern:** `import_merged` resolves each merged viewer to a local id, creating unmet ones
  (`exchange.rs:73-90`, `profiles.rs:124-141`), inside one transaction (`:69`). `parent` and `admin`
  name *other* profiles, so they are applied after the loop, when every merged viewer exists locally.
- **Clock clamp precedent:** `rows::set_watched` stamps `MAX(now, last + 1)` so a local change beats an
  imported row carrying a faster clock (`rows.rs:98-102,114-118`). An in-place Kids age change needs the
  same, or a parent's "from 6" can be silently reverted by the next merge.
- **No uniffi change here:** `api/state.rs:156-162` keeps `set_kids(set_id, marked: bool)`; only its body
  adapts (`marked.then_some(12)`), no docstring change — uniffi docstrings land in the generated Kotlin,
  and CI diffs the bindings (`scripts/generate-android-bindings.sh:3-5`).
- `cargo clippy --all-targets --all-features -- -D warnings` is part of `scripts/check.sh`; workspace is
  edition 2024 with `rust-version = 1.87` — no let-chains.

## Requirements

Functional
- Schema v7 = contract §6 statements verbatim; existing kids → `kids_age = 12`; existing marks → `age NULL`.
- `ProfileState.roles: ProfileRoles` (wire keys `admin{claimedAt}`, `kidsAge{age,updatedAt}`, `parent`,
  `pin{hash,salt,updatedAt}`), each parsed alone: `claimedAt > 0`; `age ∈ {6,12}` (JSON number),
  `updatedAt >= 0`; `parent` non-empty trimmed; `hash` 64 / `salt` 32 lowercase hex, `updatedAt > 0`.
  A malformed sub-key is dropped, never the profile.
- `ListRow.age: Option<u8>`, read only on Kids rows, only from the number 6; never on watchlist/editor's choice.
- Merge per contract §7: `kidsAge` newest (device tie) on kids only, default `{12, 0}`; `pin` newest on
  grown-ups only; `parent` first seen then greater device id, output `normal_name`, on kids only;
  `admin` earliest `claimedAt` among grown-up viewers (claims on kid viewers ignored), ties by smaller
  normal name, on that viewer only; Kids mark `age` rides on the kept row.
- Export per §7, Kids mark `age: 6` on live marks only; import corrective per §7 (limit/PIN
  newer-or-equal-but-different; admin set here and cleared elsewhere only when the merge names one;
  `parent_id` only while NULL, by normal name, never overwritten; mark `age` written with a live row;
  an equal-time Kids row that differs is taken; a kids upgrade fills `kids_age` with 12 when empty).
- `rows::set_kids(conn, set_id, age: Option<u8>)` (`None` removes; `6` "from 6"; anything else "from 12";
  a live mark's age changes in place with a newer `marked_at`); `rows::kids_from_six`.

Non-functional
- Every file under `crates/*/src` ≤ 200 lines; clippy clean; Kotlin bindings byte-identical.

## Architecture

```
SQLite profiles(+7 cols) ─► exchange::profile_roles::export ─► ProfileState.roles ─┐
SQLite kids(+age) ───────► lists_exchange::export_kids ───────► SyncRecord.kids    ├─► JSON (channel)
                                                                                   │
channel JSON ─► record::parse_record (profile_roles(), kids_row()) ─► SyncRecord ──┤
                                                                                   ▼
merge_states: rows per viewer (merge.rs) ─► profile_roles::merge(records, &mut profiles)
                                             kidsAge/pin: keep(); parent: device tie (kids only);
                                             admin: earliest claim among grown-up viewers
          ─► MergedState ─► import_merged:
               per viewer: create/upgrade kids → profile_roles::import_own (kids_age, pin)
               after loop: profile_roles::import_links (parent_id by name, admin set/clear)
               import_kids writes age
```

Module and line budget (current → planned; all ≤ 200):

| File | Now | After |
|---|---|---|
| `state/merge.rs` | 200 | ~165 |
| `state/merge/merged.rs` (new) | – | ~50 |
| `state/merge/profile_roles.rs` (new) | – | ~90 |
| `state/merge/tie_break.rs` | 57 | 58 |
| `state/record.rs` | 127 | ~134 |
| `state/record/profile_roles.rs` (new) | – | ~110 |
| `state/record/parse.rs` | 147 | ~149 |
| `state/record/list_record.rs` | 83 | ~97 |
| `state/rows.rs` | 195 | ~170 |
| `state/rows/kids_marks.rs` (new) | – | ~60 |
| `state/exchange.rs` | 139 | ~146 |
| `state/exchange/profile_roles.rs` (new) | – | ~95 |
| `state/lists_exchange.rs` | 119 | ~138 |
| `state/schema.rs` | 146 | ~167 |
| `state/repair.rs` | 20 | ~25 |
| `state/mod.rs` | 194 | 194 (two lines swap) |
| `api/state.rs` | 180 | 180 (one body line) |

## Interfaces

**Consumes**
- Phase 01: `web/test/fixtures/watch-state/profile-roles-merge.json`, shape per contract §10:
  `[{ name, records: SyncRecord[], expect: { profiles: [{ name, admin?, kids?, kidsAge?, parent?, pin? }], kids: ListRow[] } }]`.
  If phase 01 also adds `record-parse.json` cases for the new keys, the existing runner
  (`record_parse_fixtures_match_the_web`) holds this phase to them.

**Produces** (Rust only; phase 05 builds on these — no Kotlin changes)

```rust
// crate::state::record (re-exported from record/profile_roles.rs)
pub struct AdminClaim { pub claimed_at: f64 }
pub struct KidsAge { pub age: u8, pub updated_at: f64 }
pub struct PinRecord { pub hash: String, pub salt: String, pub updated_at: f64 }
#[derive(Default)] pub struct ProfileRoles {
    pub admin: Option<AdminClaim>, pub kids_age: Option<KidsAge>,
    pub parent: Option<String>, pub pin: Option<PinRecord>,
}
pub struct ProfileState { /* … */ #[serde(flatten)] pub roles: ProfileRoles }
pub struct ListRow { pub set_id: String, pub updated_at: f64, pub removed: bool, pub age: Option<u8> }

// crate::state::merge
pub struct MergedProfile { /* … */ #[serde(flatten)] pub roles: ProfileRoles }

// crate::state::rows (re-exported from rows/kids_marks.rs)
pub fn kids(conn: &Connection) -> rusqlite::Result<Vec<String>>;          // every live mark
pub fn kids_from_six(conn: &Connection) -> rusqlite::Result<Vec<String>>; // live marks "from 6"
pub fn set_kids(conn: &Connection, set_id: &str, age: Option<u8>) -> rusqlite::Result<()>;
```

Schema v7 columns (read by phase 05): `profiles.kids_age`, `kids_age_updated_at`, `parent_id`,
`admin_claimed_at`, `pin_hash`, `pin_salt`, `pin_updated_at`; `kids.age`.

## Related code files

Modify
- `crates/mediagram-core/src/state/{schema,repair,mod,record,merge,exchange,lists_exchange,rows}.rs`
- `crates/mediagram-core/src/state/record/{parse,list_record}.rs`, `state/merge/tie_break.rs`,
  `state/lists_exchange/editors_choice.rs` (test literals)
- `crates/mediagram-core/src/api/state.rs` (one body line in `set_kids`)
- Tests: `state/upgrade_tests.rs`, `state/kids_profile_tests.rs`, `state/rows_tests.rs`,
  `state/lists_exchange_tests.rs`, `state/record/list_record_tests.rs`,
  `tests/shared_watch_state_fixtures.rs`

Create
- `state/merge/merged.rs`, `state/merge/profile_roles.rs`, `state/merge/profile_roles_tests.rs`
- `state/record/profile_roles.rs`, `state/record/profile_roles_tests.rs`
- `state/rows/kids_marks.rs`, `state/rows/kids_marks_tests.rs`
- `state/exchange/profile_roles.rs`, `state/exchange/profile_roles_tests.rs`

(all under `crates/mediagram-core/src/`)

Delete: none. Do not edit any fixture under `web/test/fixtures/` — the web is authoritative.

## Implementation steps

All commands from `/home/andre/Workspace/mediagram`. `CARGO_INCREMENTAL=0` keeps the target dir sane.

### Task 0: Preflight

- [ ] **Step 1:** `git fetch -q origin && git rebase origin/main` (another session commits to `main`).
- [ ] **Step 2:** `test -f web/test/fixtures/watch-state/profile-roles-merge.json && echo ok` — expected `ok`.
  If missing, phase 01 has not landed: stop (the runner would skip silently and prove nothing).
- [ ] **Step 3:** `cargo test -p mediagram-core -q` — expected: all green (baseline).

### Task 1: Make room in `merge.rs` and `rows.rs` (no behaviour change)

- [ ] **Step 1: create `state/merge/merged.rs`**

```rust
//! What `merge_states` answers: every viewer's reconciled rows, and the
//! household-wide marks. Split out of `merge.rs` to keep it under the line
//! limit; the rules that fill these in live there.

use serde::{Deserialize, Serialize};

use crate::state::record::{CollectionRow, ListRow, ProgressRow, UnwatchedRow, WatchedRow};
```

then move `merge.rs:36-74` (the `MergedProfile` and `MergedState` definitions with their doc
comments and attributes) below it verbatim.

- [ ] **Step 2: rewire `merge.rs`** — delete `use serde::{Deserialize, Serialize};` (`:28`) and the moved
  lines; after `mod watched;` add:

```rust
mod merged;
pub use merged::{MergedProfile, MergedState};
```

- [ ] **Step 3: create `state/rows/kids_marks.rs`**

```rust
//! Kids marks: titles a grown-up has said are fine for children, for
//! everyone on this player. Split out of `rows.rs` to keep it under the line
//! limit.

use rusqlite::{Connection, params};

use crate::state::profiles::now_ms;
```

then move `rows.rs:165-192` (`kids` and `set_kids` with their doc comments) below it verbatim. In
`rows.rs`, after `use super::profiles::now_ms;` add:

```rust
mod kids_marks;
pub use kids_marks::{kids, set_kids};
```

- [ ] **Step 4:** `cargo test -p mediagram-core -q && cargo test -p mediagram --test code_standards -q && wc -l crates/mediagram-core/src/state/{merge,rows}.rs`
  — expected: all green; `merge.rs` ≈ 163, `rows.rs` ≈ 169.
- [ ] **Step 5:** `cargo clippy -p mediagram-core --all-targets --all-features -- -D warnings` — expected: clean
  (an unused import left in `merge.rs` shows here).
- [ ] **Step 6: commit.** Bump the three manifests by pattern — see phase-08 § Bumping (patch, changelog entry), then:

```bash
git add crates/mediagram-core/src/state/merge.rs crates/mediagram-core/src/state/merge/merged.rs \
  crates/mediagram-core/src/state/rows.rs crates/mediagram-core/src/state/rows/kids_marks.rs \
  Cargo.toml Cargo.lock web/package.json android/app/build.gradle.kts docs/project-changelog.md
git commit -m "refactor(core): merged shapes and Kids marks in their own modules; release <next>"
```

### Task 2: Schema v7, and repair before migrate

- [ ] **Step 1: failing test** — append to `state/upgrade_tests.rs`:

```rust
/// A version-six file gains the role columns: every existing kid becomes
/// "from 12", every existing mark stays "from 12" (`age` NULL), and nobody is
/// an admin, has a parent, or has a PIN.
#[test]
fn a_version_six_file_gains_roles_with_every_kid_and_mark_at_twelve() {
    let dir = tempfile::tempdir().unwrap();
    {
        let conn = Connection::open(dir.path().join(STATE_FILE)).unwrap();
        for statement in schema::migrations_up_to(6) {
            conn.execute(statement, []).unwrap();
        }
        conn.pragma_update(None, "user_version", 6i64).unwrap();
        conn.execute_batch(
            "INSERT INTO profiles(id, name, created_at, kids) VALUES ('p1', 'André', 0, 0), ('p2', 'Mia', 1, 1);
             INSERT INTO kids(set_id, marked_at) VALUES ('family', 5);",
        )
        .unwrap();
    }

    let conn = open(dir.path()).unwrap();

    let row = |id: &str| -> (Option<i64>, i64, bool) {
        conn.query_row(
            "SELECT kids_age, kids_age_updated_at,
                    parent_id IS NULL AND admin_claimed_at IS NULL AND pin_hash IS NULL
                      AND pin_salt IS NULL AND pin_updated_at = 0
               FROM profiles WHERE id = ?1",
            [id],
            |r| Ok((r.get(0)?, r.get(1)?, r.get(2)?)),
        )
        .unwrap()
    };
    assert_eq!(row("p1"), (None, 0, true));
    assert_eq!(row("p2"), (Some(12), 0, true));
    let age: Option<i64> = conn
        .query_row("SELECT age FROM kids WHERE set_id = 'family'", [], |r| r.get(0))
        .unwrap();
    assert_eq!(age, None);
}
```

- [ ] **Step 2:** `cargo test -p mediagram-core --lib state::upgrade_tests -q` — expected FAIL:
  `no such column: kids_age`.
- [ ] **Step 3: implement** — append to `GROUPS` in `state/schema.rs`, after the v6 group:

```rust
    // v6 -> v7: who runs the household, who owns which kid, and each kid's
    // own limit — the web's v11 (`web/src/state/schema.ts`), statement for
    // statement, so both stores hold the same facts. A grown-up's PIN is its
    // salted hash, never the digits. `parent_id` is deliberately not a
    // foreign key: a kid whose parent was removed here, but still exists in
    // another device's document, must stay readable — it belongs to the
    // admin meanwhile. Every existing kid was FSK 12, the one limit there
    // was; every existing mark stays "from 12" (`age` NULL).
    &[
        "ALTER TABLE profiles ADD COLUMN kids_age INTEGER",
        "ALTER TABLE profiles ADD COLUMN kids_age_updated_at INTEGER NOT NULL DEFAULT 0",
        "ALTER TABLE profiles ADD COLUMN parent_id TEXT",
        "ALTER TABLE profiles ADD COLUMN admin_claimed_at INTEGER",
        "ALTER TABLE profiles ADD COLUMN pin_hash TEXT",
        "ALTER TABLE profiles ADD COLUMN pin_salt TEXT",
        "ALTER TABLE profiles ADD COLUMN pin_updated_at INTEGER NOT NULL DEFAULT 0",
        "UPDATE profiles SET kids_age = 12 WHERE kids = 1",
        "ALTER TABLE kids ADD COLUMN age INTEGER",
    ],
```

- [ ] **Step 4:** `cargo test -p mediagram-core --lib state:: -q` — expected: the new test passes and
  `a_version_three_file_without_kids_gains_the_column_on_open` FAILS (`no such column: kids`): the v7
  `UPDATE` runs before the repair that adds the column.
- [ ] **Step 5: fix the order** — in `state/mod.rs` `open()`, move `repair::add_missing_kids_column(&conn)?;`
  to the line *above* `migrate(&conn)?;`. Replace `state/repair.rs`'s function with:

```rust
/// Adds `profiles.kids` to a file that reached version 3 without it.
///
/// A pre-release build numbered its `preferences` table as step 3, where this
/// schema's step 3 is the `kids` column. A file that build migrated reports
/// version 3, so the runner skips the column as already applied. Checked on
/// each open, before the migrations — a later one reads `kids` and would fail
/// on exactly this file. Below version 3 the column is that migration's to
/// add, so a younger file is left to it.
pub(super) fn add_missing_kids_column(conn: &Connection) -> rusqlite::Result<()> {
    let at: i64 = conn.pragma_query_value(None, "user_version", |row| row.get(0))?;
    let present = conn
        .prepare("SELECT 1 FROM pragma_table_info('profiles') WHERE name = 'kids'")?
        .exists([])?;
    if at >= 3 && !present {
        conn.execute("ALTER TABLE profiles ADD COLUMN kids INTEGER NOT NULL DEFAULT 0", [])?;
    }
    Ok(())
}
```

- [ ] **Step 6:** `cargo test -p mediagram-core -q` — expected: all green (incl. `migration_tests`, whose
  `assert_migrated` checks `user_version == schema::VERSION`).
- [ ] **Step 7: commit.** Bump (phase-08 § Bumping, patch), then:

```bash
git add crates/mediagram-core/src/state/{schema,repair,mod,upgrade_tests}.rs \
  Cargo.toml Cargo.lock web/package.json android/app/build.gradle.kts docs/project-changelog.md
git commit -m "feat(core): state schema for profile roles, PINs and per-kid limits; release <next>"
```

### Task 3: Wire keys — `ProfileRoles` and the Kids mark `age`

- [ ] **Step 1: failing tests** — create `state/record/profile_roles_tests.rs`:

```rust
use super::*;
use crate::state::record::{ProfileState, parse_record};
use serde_json::json;

fn roles(value: serde_json::Value) -> ProfileRoles {
    profile_roles(value.as_object().unwrap())
}

#[test]
fn each_well_formed_key_is_read() {
    let (hash, salt) = ("a".repeat(64), "b".repeat(32));
    let read = roles(json!({
        "admin": { "claimedAt": 7 },
        "kidsAge": { "age": 6, "updatedAt": 0 },
        "parent": " Bea ",
        "pin": { "hash": hash, "salt": salt, "updatedAt": 9 },
    }));
    assert_eq!(
        read,
        ProfileRoles {
            admin: Some(AdminClaim { claimed_at: 7.0 }),
            kids_age: Some(KidsAge { age: 6, updated_at: 0.0 }),
            parent: Some("Bea".into()),
            pin: Some(PinRecord { hash, salt, updated_at: 9.0 }),
        }
    );
}

#[test]
fn a_malformed_key_is_dropped_on_its_own() {
    let salt = "b".repeat(32);
    for bad in [
        json!({ "admin": { "claimedAt": 0 } }),
        json!({ "admin": true }),
        json!({ "kidsAge": { "age": 7, "updatedAt": 1 } }),
        json!({ "kidsAge": { "age": "6", "updatedAt": 1 } }),
        json!({ "kidsAge": { "age": 6, "updatedAt": -1 } }),
        json!({ "parent": "   " }),
        json!({ "pin": { "hash": "abc", "salt": salt, "updatedAt": 1 } }),
        json!({ "pin": { "hash": "A".repeat(64), "salt": salt, "updatedAt": 1 } }),
        json!({ "pin": { "hash": "a".repeat(64), "salt": salt, "updatedAt": 0 } }),
    ] {
        assert_eq!(roles(bad.clone()), ProfileRoles::default(), "{bad}");
    }
}

#[test]
fn a_bad_role_key_never_costs_the_profile() {
    let record = parse_record(
        r#"{"format":1,"device":"tv","writtenAt":1,"profiles":[
            {"name":"Mia","kids":true,"kidsAge":"six","progress":[{"setId":"a","at":1,"updatedAt":2}]}]}"#,
    )
    .unwrap();
    let mia = &record.profiles[0];
    assert!(mia.kids);
    assert_eq!(mia.progress.len(), 1);
    assert_eq!(mia.roles, ProfileRoles::default());
}

#[test]
fn the_keys_are_written_in_the_webs_spelling_and_only_when_set() {
    let state: ProfileState = serde_json::from_value(json!({
        "name": "Mia", "kids": true, "kidsAge": { "age": 6, "updatedAt": 3 }, "parent": "bea"
    }))
    .unwrap();
    let written = serde_json::to_value(&state).unwrap();
    assert_eq!(written["kidsAge"], json!({ "age": 6, "updatedAt": 3.0 }));
    assert_eq!(written["parent"], json!("bea"));
    assert!(written.get("admin").is_none() && written.get("pin").is_none());
}
```

Append to `state/record/list_record_tests.rs`:

```rust
#[test]
fn a_kids_mark_reads_its_age_only_from_the_number_six() {
    let age = |value: serde_json::Value| kids_row(&value).unwrap().age;
    assert_eq!(age(json!({ "setId": "01A", "updatedAt": 1, "age": 6 })), Some(6));
    assert_eq!(age(json!({ "setId": "01A", "updatedAt": 1, "age": 6.0 })), Some(6));
    for other in [json!(12), json!("6"), json!(6.5), json!(null)] {
        assert_eq!(age(json!({ "setId": "01A", "updatedAt": 1, "age": other })), None);
    }
}

#[test]
fn a_watchlist_row_never_carries_an_age() {
    let row = list_row(&json!({ "setId": "01A", "updatedAt": 1, "age": 6 })).unwrap();
    assert_eq!(row.age, None);
}
```

- [ ] **Step 2:** `cargo test -p mediagram-core --lib state::record -q` — expected FAIL to compile
  (`profile_roles`, `ProfileRoles`, `kids_row`, field `age` unknown).
- [ ] **Step 3: implement `state/record/profile_roles.rs`**

```rust
//! The keys a viewer's place in the household rides on: the admin's claim, a
//! kid's limit, the grown-up who owns a kid, and a grown-up's PIN. Split out
//! of `record.rs` to keep it under the line limit; `sync-record.ts` is the
//! reference and `profile-roles-merge.json` pins the two.
//!
//! Each key is read on its own: a malformed one is dropped, and the profile
//! and every other key on it are kept.

use serde::{Deserialize, Serialize};
use serde_json::{Map, Value};

use super::hostile_json::{js_number, text_};

/// When a grown-up became the household's admin.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AdminClaim {
    pub claimed_at: f64,
}

/// A kid's limit — FSK 6 or FSK 12 — and when a parent last set it.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct KidsAge {
    pub age: u8,
    pub updated_at: f64,
}

/// A grown-up's PIN as stored: lowercase hex SHA-256 of `salt + pin`, and
/// the salt. Never the digits themselves.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PinRecord {
    pub hash: String,
    pub salt: String,
    pub updated_at: f64,
}

/// Every role key a profile entry may carry, each absent unless said.
#[derive(Debug, Clone, Default, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ProfileRoles {
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub admin: Option<AdminClaim>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub kids_age: Option<KidsAge>,
    /// The owning grown-up's name as its device stored it — a name, because
    /// the name is what identifies a viewer from one device to the next.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub parent: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub pin: Option<PinRecord>,
}

pub(super) fn profile_roles(row: &Map<String, Value>) -> ProfileRoles {
    ProfileRoles {
        admin: admin(row.get("admin")),
        kids_age: kids_age(row.get("kidsAge")),
        parent: text_(row.get("parent")),
        pin: pin(row.get("pin")),
    }
}

fn admin(raw: Option<&Value>) -> Option<AdminClaim> {
    let claimed_at = js_number(raw?.as_object()?.get("claimedAt"));
    (claimed_at.is_finite() && claimed_at > 0.0).then_some(AdminClaim { claimed_at })
}

/// `age` must be the number 6 or 12, not a string that looks like one — a
/// limit is not something to guess at. `updatedAt` may be 0: that is what an
/// older document's kid merges to, and saying so again is still well formed.
fn kids_age(raw: Option<&Value>) -> Option<KidsAge> {
    let held = raw?.as_object()?;
    let age = held.get("age").and_then(Value::as_f64);
    let age = if age == Some(6.0) {
        6
    } else if age == Some(12.0) {
        12
    } else {
        return None;
    };
    let updated_at = js_number(held.get("updatedAt"));
    (updated_at.is_finite() && updated_at >= 0.0).then_some(KidsAge { age, updated_at })
}

fn pin(raw: Option<&Value>) -> Option<PinRecord> {
    let held = raw?.as_object()?;
    let hash = lowercase_hex(held.get("hash"), 64)?;
    let salt = lowercase_hex(held.get("salt"), 32)?;
    let updated_at = js_number(held.get("updatedAt"));
    (updated_at.is_finite() && updated_at > 0.0).then_some(PinRecord { hash, salt, updated_at })
}

/// Exactly `len` characters of `0-9a-f` — what the hash and salt are written
/// as on both surfaces; anything else could never match a PIN anyway.
fn lowercase_hex(raw: Option<&Value>, len: usize) -> Option<String> {
    let text = raw?.as_str()?;
    let hex = text.len() == len && text.bytes().all(|b| matches!(b, b'0'..=b'9' | b'a'..=b'f'));
    hex.then(|| text.to_string())
}

#[cfg(test)]
#[path = "profile_roles_tests.rs"]
mod tests;
```

- [ ] **Step 4: wire it in**
  - `state/record.rs`: after `mod parse;` add `mod profile_roles;`; after `pub use parse::parse_record;` add
    `pub use profile_roles::{AdminClaim, KidsAge, PinRecord, ProfileRoles};`. In `ProfileState`, after the
    `kids` field:

```rust
    /// Who this viewer is in the household — admin, limit, parent, PIN. New
    /// keys rather than a format bump: a reader that predates them drops
    /// what it does not know and keeps merging the rest.
    #[serde(flatten)]
    pub roles: ProfileRoles,
```

  - `state/record/parse.rs`: import `use super::list_record::{collection_row, kids_row, list_row};` and
    `use super::profile_roles::profile_roles;`; the top-level Kids rows use `.filter_map(kids_row)`
    (`:165-168`); in `profile_state`, after `kids: …,` add `roles: profile_roles(row),`.
  - `state/record/list_record.rs`: in `ListRow`, after `removed`:

```rust
    /// A Kids mark only: `Some(6)` is "from 6". Absent is "from 12" — also
    /// what a reader that predates the key makes of every mark.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub age: Option<u8>,
```

    add `age: None,` to the literal in `list_row`, and below it:

```rust
/// A Kids mark: a `ListRow` that may say "from 6". Only the number 6 does;
/// anything else, 12 included, is absent — "from 12", the stricter reading.
pub(super) fn kids_row(raw: &Value) -> Option<ListRow> {
    let mut row = list_row(raw)?;
    row.age = (raw.get("age").and_then(Value::as_f64) == Some(6.0)).then_some(6);
    Some(row)
}
```

  - `age: None` at the remaining literal sites: `lists_exchange.rs:20` (`to_list_row`),
    `record/list_record_tests.rs:18,26,34`, `lists_exchange_tests.rs:31,50,72,89`,
    `lists_exchange/editors_choice.rs:76,107`.
- [ ] **Step 5:** `cargo test -p mediagram-core -q` — expected: all green, incl.
  `record_parse_fixtures_match_the_web` and `tests/kids_profile_record.rs` (an ordinary profile still
  writes no `kids` key and no role keys).
- [ ] **Step 6: commit.** Bump (patch), then:

```bash
git add crates/mediagram-core/src/state/record.rs crates/mediagram-core/src/state/record/ \
  crates/mediagram-core/src/state/lists_exchange.rs crates/mediagram-core/src/state/lists_exchange/editors_choice.rs \
  crates/mediagram-core/src/state/lists_exchange_tests.rs \
  Cargo.toml Cargo.lock web/package.json android/app/build.gradle.kts docs/project-changelog.md
git commit -m "feat(core): sync records carry profile roles and a Kids mark's age; release <next>"
```

### Task 4: Merge the role keys

- [ ] **Step 1: failing tests** — create `state/merge/profile_roles_tests.rs`:

```rust
use crate::state::merge::{MergedProfile, merge_states};
use crate::state::record::{KidsAge, PinRecord, SyncRecord, parse_record};

fn doc(device: &str, profiles: &str) -> SyncRecord {
    parse_record(&format!(
        r#"{{"format":1,"device":"{device}","writtenAt":1,"profiles":{profiles}}}"#
    ))
    .unwrap()
}

fn viewer(records: &[SyncRecord], name: &str) -> MergedProfile {
    merge_states(records).profiles.into_iter().find(|p| p.name == name).unwrap()
}

fn both_orders(a: &SyncRecord, b: &SyncRecord) -> [Vec<SyncRecord>; 2] {
    [vec![a.clone(), b.clone()], vec![b.clone(), a.clone()]]
}

#[test]
fn an_older_documents_kid_is_twelve_until_a_parent_sets_six() {
    let old = doc("tv", r#"[{"name":"Mia","kids":true}]"#);
    let twelve = Some(KidsAge { age: 12, updated_at: 0.0 });
    assert_eq!(viewer(std::slice::from_ref(&old), "mia").roles.kids_age, twelve);
    let set = doc("laptop", r#"[{"name":"mia","kids":true,"kidsAge":{"age":6,"updatedAt":5}}]"#);
    for order in both_orders(&old, &set) {
        assert_eq!(viewer(&order, "mia").roles.kids_age, Some(KidsAge { age: 6, updated_at: 5.0 }));
    }
}

#[test]
fn the_earliest_admin_claim_in_the_household_wins_and_no_one_else_keeps_one() {
    let tablet = doc("tablet", r#"[{"name":"André","admin":{"claimedAt":20}}]"#);
    let laptop = doc("laptop", r#"[{"name":"Bea","admin":{"claimedAt":10}},{"name":"andré"}]"#);
    for order in both_orders(&tablet, &laptop) {
        let admins: Vec<String> = merge_states(&order)
            .profiles
            .into_iter()
            .filter(|p| p.roles.admin.is_some())
            .map(|p| p.name)
            .collect();
        assert_eq!(admins, ["bea"]);
    }
}

#[test]
fn a_tied_admin_claim_goes_to_the_smaller_name() {
    let a = doc("a", r#"[{"name":"Zoe","admin":{"claimedAt":10}}]"#);
    let b = doc("b", r#"[{"name":"Anna","admin":{"claimedAt":10}}]"#);
    for order in both_orders(&a, &b) {
        assert!(viewer(&order, "anna").roles.admin.is_some());
        assert!(viewer(&order, "zoe").roles.admin.is_none());
    }
}

#[test]
fn a_pin_never_rides_on_a_kid_and_a_limit_never_on_a_grown_up() {
    let (hash, salt) = ("a".repeat(64), "b".repeat(32));
    let one = doc(
        "tv",
        &format!(
            r#"[{{"name":"Mia","kids":true,"pin":{{"hash":"{hash}","salt":"{salt}","updatedAt":3}}}},
                {{"name":"Bea","kidsAge":{{"age":6,"updatedAt":3}}}}]"#
        ),
    );
    let records = [one];
    assert_eq!(viewer(&records, "mia").roles.pin, None);
    assert_eq!(viewer(&records, "bea").roles.kids_age, None);
}

#[test]
fn the_newest_pin_wins_and_a_tie_goes_to_the_greater_device() {
    let pin = |hash: char, at: u32| {
        format!(r#"{{"hash":"{}","salt":"{}","updatedAt":{at}}}"#, hash.to_string().repeat(64), "b".repeat(32))
    };
    let older = doc("zz", &format!(r#"[{{"name":"Bea","pin":{}}}]"#, pin('a', 1)));
    let newer = doc("aa", &format!(r#"[{{"name":"Bea","pin":{}}}]"#, pin('c', 2)));
    for order in both_orders(&older, &newer) {
        assert_eq!(viewer(&order, "bea").roles.pin.map(|p| p.hash), Some("c".repeat(64)));
    }
    let tied = doc("zz", &format!(r#"[{{"name":"Bea","pin":{}}}]"#, pin('d', 2)));
    for order in both_orders(&tied, &newer) {
        let won: Option<PinRecord> = viewer(&order, "bea").roles.pin;
        assert_eq!(won.map(|p| p.hash), Some("d".repeat(64)));
    }
}

#[test]
fn the_parent_is_normalised_and_decided_by_the_greater_device() {
    let a = doc("a", r#"[{"name":"Mia","kids":true,"parent":"Bea"}]"#);
    let b = doc("b", r#"[{"name":"Mia","kids":true,"parent":" BEN "}]"#);
    for order in both_orders(&a, &b) {
        assert_eq!(viewer(&order, "mia").roles.parent.as_deref(), Some("ben"));
    }
}

#[test]
fn a_grown_up_carries_no_parent() {
    let records = [doc("a", r#"[{"name":"Bea","parent":"André"}]"#)];
    assert_eq!(viewer(&records, "bea").roles.parent, None);
}

#[test]
fn a_claim_on_a_kid_counts_for_nothing_even_when_another_device_says_kid() {
    let kid = doc("a", r#"[{"name":"Mia","kids":true,"admin":{"claimedAt":5}}]"#);
    let bea = doc("b", r#"[{"name":"Bea","admin":{"claimedAt":10}}]"#);
    for order in both_orders(&kid, &bea) {
        assert!(viewer(&order, "mia").roles.admin.is_none());
        assert!(viewer(&order, "bea").roles.admin.is_some());
    }
    // Zoe claimed as a grown-up on one device; another knows her as a kid.
    let claimed = doc("a", r#"[{"name":"Zoe","admin":{"claimedAt":5}}]"#);
    let known_kid = doc("b", r#"[{"name":"zoe","kids":true}]"#);
    for order in both_orders(&claimed, &known_kid) {
        assert!(merge_states(&order).profiles.iter().all(|p| p.roles.admin.is_none()));
    }
}

#[test]
fn a_kids_marks_age_rides_on_the_row_the_merge_keeps() {
    let mark = |device: &str, age: &str, at: u32| {
        parse_record(&format!(
            r#"{{"format":1,"device":"{device}","writtenAt":1,"kids":[{{"setId":"x","updatedAt":{at}{age}}}]}}"#
        ))
        .unwrap()
    };
    let six = mark("a", r#","age":6"#, 2);
    let twelve = mark("b", "", 1);
    for order in both_orders(&six, &twelve) {
        assert_eq!(merge_states(&order).kids[0].age, Some(6));
    }
}
```

Append to `tests/shared_watch_state_fixtures.rs` (and extend its imports to
`use mediagram_core::state::record::{ListRow, ProfileRoles, SyncRecord, parse_record};`):

```rust
/// One viewer as `profile-roles-merge.json` states it: the fields that file
/// is about and no others — its cases say nothing of positions or lists.
#[derive(Debug, PartialEq, Deserialize)]
struct RolesOf {
    name: String,
    #[serde(default)]
    kids: bool,
    #[serde(flatten)]
    roles: ProfileRoles,
}

#[derive(Debug, PartialEq, Deserialize)]
struct RolesMerged {
    profiles: Vec<RolesOf>,
    #[serde(default)]
    kids: Vec<ListRow>,
}

#[derive(Deserialize)]
struct RolesCase {
    name: String,
    records: Vec<SyncRecord>,
    expect: RolesMerged,
}

fn sorted(mut merged: RolesMerged) -> RolesMerged {
    merged.profiles.sort_by(|a, b| a.name.cmp(&b.name));
    merged.kids.sort_by(|a, b| a.set_id.cmp(&b.set_id));
    merged
}

fn roles_of(state: MergedState) -> RolesMerged {
    let profiles = state
        .profiles
        .into_iter()
        .map(|p| RolesOf { name: p.name, kids: p.kids, roles: p.roles })
        .collect();
    sorted(RolesMerged { profiles, kids: state.kids })
}

/// Admin, limit, parent and PIN across devices, and a Kids mark's age.
#[test]
fn profile_roles_merge_fixtures_match_the_web_in_both_orders() {
    let Some(cases) = load::<RolesCase>("profile-roles-merge.json") else {
        return;
    };
    assert!(!cases.is_empty(), "profile-roles-merge.json holds no cases");
    for case in cases {
        let expect = sorted(case.expect);
        assert_eq!(roles_of(merge_states(&case.records)), expect, "case: {} (forward)", case.name);
        let mut reversed = case.records.clone();
        reversed.reverse();
        assert_eq!(roles_of(merge_states(&reversed)), expect, "case: {} (reversed)", case.name);
    }
}
```

and in `canonical` (`:78-90`), inside the `for profile` loop:

```rust
        // `merge.json` and `lists-merge.json` predate the role keys and say
        // nothing about them; the web's runner leaves them out the same way.
        profile.roles = Default::default();
```

- [ ] **Step 2:** `cargo test -p mediagram-core -q` — expected FAIL to compile: no field `roles` on
  `MergedProfile`.
- [ ] **Step 3: implement**
  - `merge/tie_break.rs`: import `KidsAge, PinRecord` and extend the list:
    `timestamped_by_own_field!(ProgressRow, WatchedRow, UnwatchedRow, ListRow, CollectionRow, KidsAge, PinRecord);`
  - `merge/merged.rs`: import `ProfileRoles`; in `MergedProfile`, after `kids`:

```rust
    /// Admin, limit, parent and PIN, settled across the whole household —
    /// see `profile_roles`.
    #[serde(flatten)]
    pub roles: ProfileRoles,
```

  - create `merge/profile_roles.rs`:

```rust
//! Who each viewer is in the household — admin, limit, parent, PIN — across
//! every device's document. A port of the web's merge for the same keys
//! (`web/src/state/merge.ts`); `profile-roles-merge.json` pins the two.
//!
//! A limit and a PIN are last-writer-wins like any row, ties by device id: a
//! parent lowers a limit as often as it raises it, and an admin resets PINs.
//! The parent is set once, when the kid is made; two documents disagreeing is
//! a bug, and the device-id rule `display_name` uses keeps the answer
//! order-independent anyway. The admin is the earliest claim on any grown-up
//! in the household, ties by name, so two devices that each claimed before
//! hearing of the other end with one admin whichever order they wake up in.
//! A claim on a viewer that any device knows as a kid counts for nothing.

use std::collections::HashMap;

use crate::state::record::{AdminClaim, KidsAge, PinRecord, SyncRecord, normal_name};

use super::merged::MergedProfile;
use super::tie_break::{Held, keep};

/// A kid no document gives a limit: FSK 12 — the one limit there was before
/// limits were per kid — and older than any real change.
const UNSTATED: KidsAge = KidsAge { age: 12, updated_at: 0.0 };

pub(super) fn merge(records: &[SyncRecord], profiles: &mut [MergedProfile]) {
    let mut kids_age: HashMap<String, Held<KidsAge>> = HashMap::new();
    let mut pin: HashMap<String, Held<PinRecord>> = HashMap::new();
    let mut parent: HashMap<String, (String, &str)> = HashMap::new();
    // Each viewer's earliest claim. Which viewers are kids is known only once
    // every document is in, so the admin is chosen after this pass.
    let mut claims: HashMap<String, f64> = HashMap::new();

    for record in records {
        let device = record.device.as_str();
        for profile in &record.profiles {
            let Some(name) = normal_name(&profile.name) else {
                continue;
            };
            let roles = &profile.roles;
            if let Some(row) = &roles.kids_age {
                keep(&mut kids_age, name.clone(), row.clone(), device);
            }
            if let Some(row) = &roles.pin {
                keep(&mut pin, name.clone(), row.clone(), device);
            }
            if let Some(named) = &roles.parent {
                if parent.get(&name).is_none_or(|(_, from)| device > *from) {
                    parent.insert(name.clone(), (named.clone(), device));
                }
            }
            if let Some(claim) = &roles.admin {
                let earliest = claims.entry(name).or_insert(claim.claimed_at);
                *earliest = earliest.min(claim.claimed_at);
            }
        }
    }

    let admin = profiles
        .iter()
        .filter(|profile| !profile.kids)
        .filter_map(|profile| claims.get(&profile.name).map(|at| (*at, profile.name.clone())))
        .min_by(|a, b| a.0.total_cmp(&b.0).then_with(|| a.1.cmp(&b.1)));

    for profile in profiles.iter_mut() {
        let roles = &mut profile.roles;
        if profile.kids {
            roles.kids_age = Some(kids_age.remove(&profile.name).map_or(UNSTATED, |held| held.row));
            roles.parent = parent.remove(&profile.name).and_then(|(named, _)| normal_name(&named));
            roles.pin = None;
        } else {
            roles.kids_age = None;
            roles.parent = None;
            roles.pin = pin.remove(&profile.name).map(|held| held.row);
        }
        roles.admin = admin
            .as_ref()
            .filter(|(_, who)| *who == profile.name)
            .map(|(at, _)| AdminClaim { claimed_at: *at });
    }
}

#[cfg(test)]
#[path = "profile_roles_tests.rs"]
mod tests;
```

  - `merge.rs`: add `mod profile_roles;` after `mod merged;`; in the `MergedProfile` literal (`push`)
    add `roles: Default::default(),`; before the final `MergedState { … }` add
    `profile_roles::merge(records, &mut profiles);`.
  - `state/kids_profile_tests.rs:14`: add `roles: Default::default(),` to the literal.
  - If clippy flags `collapsible_if` on the `parent` block under this MSRV, fold the condition into
    one `if` with `&&` on a precomputed `bool` — no let-chains (`rust-version = 1.87`).
- [ ] **Step 4:** `cargo test -p mediagram-core -q` — expected: all green, including
  `profile_roles_merge_fixtures_match_the_web_in_both_orders`, `merge_fixtures_match_the_web_in_both_orders`,
  `lists_merge_fixtures_match_the_web_in_both_orders`. A fixture case that fails is a core bug — fix the
  core, never the fixture. A case that contradicts contract §7 → stop and ask the lead.
- [ ] **Step 5:** `cargo clippy -p mediagram-core --all-targets --all-features -- -D warnings && cargo test -p mediagram --test code_standards -q` — expected: clean.
- [ ] **Step 6: commit.** Bump (patch), then:

```bash
git add crates/mediagram-core/src/state/merge.rs crates/mediagram-core/src/state/merge/ \
  crates/mediagram-core/src/state/kids_profile_tests.rs crates/mediagram-core/tests/shared_watch_state_fixtures.rs \
  Cargo.toml Cargo.lock web/package.json android/app/build.gradle.kts docs/project-changelog.md
git commit -m "feat(core): merge admin, kid limits, parents and PINs as the web does; release <next>"
```

### Task 5: Kids marks with an age

- [ ] **Step 1: failing tests** — create `state/rows/kids_marks_tests.rs`:

```rust
use super::*;
use crate::state::StateDb;

fn db() -> (tempfile::TempDir, StateDb) {
    let dir = tempfile::tempdir().unwrap();
    let db = StateDb::new(dir.path().to_path_buf());
    (dir, db)
}

fn stored(db: &StateDb, set_id: &str) -> (i64, Option<i64>, Option<i64>) {
    db.with(|c| {
        c.query_row(
            "SELECT marked_at, removed_at, age FROM kids WHERE set_id = ?1",
            [set_id],
            |r| Ok((r.get(0)?, r.get(1)?, r.get(2)?)),
        )
    })
    .unwrap()
}

#[test]
fn a_mark_from_six_is_in_both_lists_and_one_from_twelve_only_in_kids() {
    let (_dir, db) = db();
    db.with(|c| set_kids(c, "six", Some(6))).unwrap();
    db.with(|c| set_kids(c, "twelve", Some(12))).unwrap();
    let mut all = db.with(kids).unwrap();
    all.sort();
    assert_eq!(all, ["six", "twelve"]);
    assert_eq!(db.with(kids_from_six).unwrap(), ["six"]);
}

#[test]
fn an_age_other_than_six_is_stored_as_from_twelve() {
    let (_dir, db) = db();
    db.with(|c| set_kids(c, "x", Some(7))).unwrap();
    assert_eq!(stored(&db, "x").2, None);
    assert_eq!(db.with(kids).unwrap(), ["x"]);
}

#[test]
fn a_new_age_on_a_live_mark_is_stamped_past_a_faster_clock() {
    let (_dir, db) = db();
    const AHEAD: i64 = 9_999_999_999_999;
    db.with(|c| c.execute("INSERT INTO kids(set_id, marked_at) VALUES ('x', ?1)", [AHEAD]))
        .unwrap();
    db.with(|c| set_kids(c, "x", Some(6))).unwrap();
    assert_eq!(stored(&db, "x"), (AHEAD + 1, None, Some(6)));
}

#[test]
fn marking_again_at_the_same_age_changes_nothing() {
    let (_dir, db) = db();
    db.with(|c| set_kids(c, "x", Some(6))).unwrap();
    let first = stored(&db, "x");
    db.with(|c| set_kids(c, "x", Some(6))).unwrap();
    assert_eq!(stored(&db, "x"), first);
}

#[test]
fn removing_a_mark_leaves_a_tombstone_whatever_its_age() {
    let (_dir, db) = db();
    db.with(|c| set_kids(c, "x", Some(6))).unwrap();
    db.with(|c| set_kids(c, "x", None)).unwrap();
    assert!(db.with(kids).unwrap().is_empty());
    assert!(db.with(kids_from_six).unwrap().is_empty());
    assert!(stored(&db, "x").1.is_some());
}
```

Append to `state/lists_exchange_tests.rs`:

```rust
#[test]
fn a_kids_marks_age_travels_out_and_back_in() {
    let (_dir, db) = db();
    db.with(|c| rows::set_kids(c, "six", Some(6))).unwrap();
    db.with(|c| rows::set_kids(c, "twelve", Some(12))).unwrap();
    let mut out = db.with(export_kids).unwrap();
    out.sort_by(|a, b| a.set_id.cmp(&b.set_id));
    assert_eq!(out.iter().map(|r| r.age).collect::<Vec<_>>(), [Some(6), None]);

    let (_other_dir, other) = self::db();
    other.with(|c| import_kids(c, &out)).unwrap();
    assert_eq!(other.with(rows::kids_from_six).unwrap(), ["six"]);
}

#[test]
fn a_newer_row_moves_a_mark_from_twelve_to_six() {
    let (_dir, db) = db();
    let row = |updated_at: f64, age: Option<u8>| ListRow { set_id: "x".into(), updated_at, removed: false, age };
    db.with(|c| import_kids(c, &[row(10.0, None)])).unwrap();
    db.with(|c| import_kids(c, &[row(20.0, Some(6))])).unwrap();
    assert_eq!(db.with(rows::kids_from_six).unwrap(), ["x"]);
}

/// At the same moment the merge has already chosen between the two by device
/// id, so the row it kept is taken when it differs here.
#[test]
fn a_tie_that_differs_takes_the_merges_row() {
    let (_dir, db) = db();
    let row = |age: Option<u8>, removed: bool| ListRow { set_id: "x".into(), updated_at: 10.0, removed, age };
    db.with(|c| import_kids(c, &[row(Some(6), false)])).unwrap();
    assert_eq!(db.with(|c| import_kids(c, &[row(Some(6), false)])).unwrap(), 0);
    assert_eq!(db.with(|c| import_kids(c, &[row(None, false)])).unwrap(), 1);
    assert!(db.with(rows::kids_from_six).unwrap().is_empty());
    assert_eq!(db.with(|c| import_kids(c, &[row(None, true)])).unwrap(), 1);
    assert!(db.with(rows::kids).unwrap().is_empty());
}

/// A removal says nothing about an age: the column may still hold the one the
/// mark had while live, and it is neither sent nor compared.
#[test]
fn a_tombstones_leftover_age_is_neither_sent_nor_compared() {
    let (_dir, db) = db();
    db.with(|c| rows::set_kids(c, "x", Some(6))).unwrap();
    db.with(|c| rows::set_kids(c, "x", None)).unwrap();
    let out = db.with(export_kids).unwrap();
    assert_eq!((out[0].removed, out[0].age), (true, None));
    assert_eq!(db.with(|c| import_kids(c, &out)).unwrap(), 0);
}
```

Change `rows_tests.rs:132` to `set_kids(conn, "01A", Some(12))`.

- [ ] **Step 2:** `cargo test -p mediagram-core -q` — expected FAIL to compile (`kids_from_six` unknown;
  `set_kids` expects `bool`).
- [ ] **Step 3: implement** — in `rows/kids_marks.rs`, replace `set_kids` and add `kids_from_six`; add
  `#[cfg(test)] #[path = "kids_marks_tests.rs"] mod tests;` at the end:

```rust
/// The live marks that say "from 6" — the part of `kids` a kid limited to
/// FSK 6 may see.
pub fn kids_from_six(conn: &Connection) -> rusqlite::Result<Vec<String>> {
    let mut stmt = conn.prepare(
        "SELECT set_id FROM kids WHERE removed_at IS NULL AND age = 6 ORDER BY marked_at DESC",
    )?;
    let rows = stmt.query_map([], |row| row.get(0))?;
    rows.collect()
}

/// Marks `set_id` "from 6" (`Some(6)`) or "from 12" (any other age — the
/// stricter reading, as the wire has it), or takes the mark off (`None`).
///
/// A removal is a tombstone, not a delete — the same reason and shape as
/// `set_watchlisted`. The same age again changes nothing. A new age is a new
/// mark, stamped at least one millisecond past whatever the row already
/// carries: an imported row can hold a clock running ahead of this one, and
/// the change still has to win the next merge — the rule `set_watched` keeps.
pub fn set_kids(conn: &Connection, set_id: &str, age: Option<u8>) -> rusqlite::Result<()> {
    let Some(age) = age else {
        conn.execute(
            "UPDATE kids SET removed_at = ?2 WHERE set_id = ?1 AND removed_at IS NULL",
            params![set_id, now_ms()],
        )?;
        return Ok(());
    };
    let from_six = (age == 6).then_some(6i64);
    conn.execute(
        "INSERT INTO kids(set_id, marked_at, removed_at, age) VALUES (?1, ?2, NULL, ?3)
           ON CONFLICT(set_id) DO UPDATE SET
             marked_at = MAX(excluded.marked_at, COALESCE(kids.removed_at, kids.marked_at) + 1),
             removed_at = NULL,
             age = excluded.age
             WHERE kids.removed_at IS NOT NULL OR kids.age IS NOT excluded.age",
        params![set_id, now_ms(), from_six],
    )?;
    Ok(())
}
```

  `rows.rs`: `pub use kids_marks::{kids, kids_from_six, set_kids};`.
  `lists_exchange.rs`:

```rust
/// "From 6" rides on a live mark only; from 12 is said by saying nothing,
/// which is all an older reader, dropping the key, will ever hear.
pub fn export_kids(conn: &Connection) -> rusqlite::Result<Vec<ListRow>> {
    let mut stmt = conn.prepare("SELECT set_id, marked_at, removed_at, age FROM kids")?;
    let rows = stmt.query_map([], |row| {
        let wire = to_list_row(row.get(0)?, row.get(1)?, row.get(2)?);
        let age: Option<i64> = row.get(3)?;
        Ok(ListRow { age: (age == Some(6) && !wire.removed).then_some(6), ..wire })
    })?;
    rows.collect()
}
```

  and in `import_kids` replace the `standing` read and skip (`lists_exchange.rs:51-60`) with the web's
  rule (`phase-01` Task 5.4 step 3):

```rust
        let standing: Option<(i64, bool, Option<i64>)> = conn
            .query_row(
                "SELECT CASE WHEN removed_at IS NOT NULL THEN removed_at ELSE marked_at END,
                        removed_at IS NOT NULL, age
                   FROM kids WHERE set_id = ?1",
                [&row.set_id],
                |r| Ok((r.get(0)?, r.get(1)?, r.get(2)?)),
            )
            .optional()?;
        // Newer news here stays. At the same moment the merge already chose by
        // device id, so a row that differs — removed where this is live, or
        // another age — is taken; a tombstone's leftover age is not compared.
        let keep_ours = standing.is_some_and(|(at, removed, age)| {
            let at = at as f64;
            at > row.updated_at
                || (at == row.updated_at
                    && removed == row.removed
                    && (row.removed || (age == Some(6)) == (row.age == Some(6))))
        });
        if keep_ours {
            continue;
        }
```

  The removal branch is unchanged (a removal leaves the `age` column alone). The live branch becomes
  `INSERT INTO kids(set_id, marked_at, removed_at, age) VALUES (?1, ?2, NULL, ?3) ON CONFLICT(set_id) DO UPDATE SET marked_at = excluded.marked_at, removed_at = NULL, age = excluded.age`
  with `params![row.set_id, row.updated_at as i64, row.age]`.
  `api/state.rs:159`: `.with(|conn| rows::set_kids(conn, &set_id, marked.then_some(12)))` — body only;
  the exported signature and its doc comment stay as they are.
- [ ] **Step 4:** `cargo test -p mediagram-core -q` — expected: all green, incl. `tests/api_surface.rs`
  (`set_kids(…, true/false)` unchanged at the boundary).
- [ ] **Step 5: commit.** Bump (patch), then:

```bash
git add crates/mediagram-core/src/state/rows.rs crates/mediagram-core/src/state/rows/ \
  crates/mediagram-core/src/state/rows_tests.rs crates/mediagram-core/src/state/lists_exchange.rs \
  crates/mediagram-core/src/state/lists_exchange_tests.rs crates/mediagram-core/src/api/state.rs \
  Cargo.toml Cargo.lock web/package.json android/app/build.gradle.kts docs/project-changelog.md
git commit -m "feat(core): a Kids mark says from 6 or from 12, and syncs it; release <next>"
```

### Task 6: Export and import the role keys

- [ ] **Step 1: failing tests** — create `state/exchange/profile_roles_tests.rs`:

```rust
use rusqlite::types::FromSql;

use crate::state::exchange::{export_record, import_merged};
use crate::state::merge::{MergedProfile, MergedState};
use crate::state::record::{AdminClaim, KidsAge, PinRecord, ProfileRoles};
use crate::state::{StateDb, profiles};

fn db() -> (tempfile::TempDir, StateDb) {
    let dir = tempfile::tempdir().unwrap();
    let db = StateDb::new(dir.path().to_path_buf());
    (dir, db)
}

fn make(db: &StateDb, name: &str, kids: bool) -> String {
    db.with(|c| profiles::create(c, name, kids)).unwrap().unwrap().id
}

fn sql(db: &StateDb, statement: &str) {
    db.with(|c| c.execute_batch(statement)).unwrap();
}

fn value<T: FromSql>(db: &StateDb, query: &str) -> T {
    db.with(|c| c.query_row(query, [], |r| r.get(0))).unwrap()
}

fn viewer(name: &str, kids: bool, roles: ProfileRoles) -> MergedProfile {
    MergedProfile { name: name.to_lowercase(), display_name: name.into(), kids, roles, ..Default::default() }
}

fn import(db: &StateDb, viewers: Vec<MergedProfile>) -> u64 {
    let merged = MergedState { profiles: viewers, ..Default::default() };
    db.with(|c| import_merged(c, &merged)).unwrap()
}

fn pin(hash: char, at: f64) -> PinRecord {
    PinRecord { hash: hash.to_string().repeat(64), salt: "b".repeat(32), updated_at: at }
}

#[test]
fn a_grown_up_exports_its_claim_and_pin_and_a_kid_its_limit_and_parent() {
    let (_dir, db) = db();
    let bea = make(&db, "Bea", false);
    let mia = make(&db, "Mia", true);
    let (hash, salt) = ("a".repeat(64), "b".repeat(32));
    sql(&db, &format!(
        "UPDATE profiles SET admin_claimed_at = 7, pin_hash = '{hash}', pin_salt = '{salt}', pin_updated_at = 9 WHERE id = '{bea}';
         UPDATE profiles SET kids_age = 6, kids_age_updated_at = 5, parent_id = '{bea}' WHERE id = '{mia}';"
    ));
    let record = db.with(|c| export_record(c, "tv")).unwrap();
    let roles = |name: &str| record.profiles.iter().find(|p| p.name == name).unwrap().roles.clone();
    assert_eq!(
        roles("Bea"),
        ProfileRoles {
            admin: Some(AdminClaim { claimed_at: 7.0 }),
            pin: Some(PinRecord { hash, salt, updated_at: 9.0 }),
            ..Default::default()
        }
    );
    assert_eq!(
        roles("Mia"),
        ProfileRoles {
            kids_age: Some(KidsAge { age: 6, updated_at: 5.0 }),
            parent: Some("Bea".into()),
            ..Default::default()
        }
    );
}

#[test]
fn a_kid_with_no_limit_stored_exports_twelve_and_a_parent_gone_is_left_out() {
    let (_dir, db) = db();
    let mia = make(&db, "Mia", true);
    sql(&db, &format!("UPDATE profiles SET kids_age = NULL, parent_id = 'gone' WHERE id = '{mia}'"));
    let record = db.with(|c| export_record(c, "tv")).unwrap();
    assert_eq!(
        record.profiles[0].roles,
        ProfileRoles { kids_age: Some(KidsAge { age: 12, updated_at: 0.0 }), ..Default::default() }
    );
}

#[test]
fn a_limit_is_taken_when_newer_or_as_new_but_different_and_never_when_older() {
    let (_dir, db) = db();
    let mia = make(&db, "Mia", true);
    sql(&db, &format!("UPDATE profiles SET kids_age = 6, kids_age_updated_at = 10 WHERE id = '{mia}'"));
    let limit = |age: u8, at: f64| {
        viewer("Mia", true, ProfileRoles { kids_age: Some(KidsAge { age, updated_at: at }), ..Default::default() })
    };
    let age = |db: &StateDb| value::<i64>(db, "SELECT kids_age FROM profiles");
    assert_eq!(import(&db, vec![limit(12, 5.0)]), 0);
    assert_eq!(age(&db), 6);
    assert_eq!(import(&db, vec![limit(12, 10.0)]), 1);
    assert_eq!(age(&db), 12);
    assert_eq!(import(&db, vec![limit(12, 10.0)]), 0);
    assert_eq!(import(&db, vec![limit(6, 11.0)]), 1);
    assert_eq!(age(&db), 6);
}

#[test]
fn a_newer_pin_replaces_the_local_one_and_an_older_one_does_not() {
    let (_dir, db) = db();
    make(&db, "Bea", false);
    let with_pin = |p: PinRecord| viewer("Bea", false, ProfileRoles { pin: Some(p), ..Default::default() });
    assert_eq!(import(&db, vec![with_pin(pin('c', 5.0))]), 1);
    assert_eq!(import(&db, vec![with_pin(pin('a', 4.0))]), 0);
    assert_eq!(value::<String>(&db, "SELECT pin_hash FROM profiles"), "c".repeat(64));
}

#[test]
fn the_merged_admin_is_set_here_and_cleared_on_every_other_profile() {
    let (_dir, db) = db();
    let andre = make(&db, "André", false);
    make(&db, "Bea", false);
    sql(&db, &format!("UPDATE profiles SET admin_claimed_at = 20 WHERE id = '{andre}'"));
    let claimed = ProfileRoles { admin: Some(AdminClaim { claimed_at: 10.0 }), ..Default::default() };
    import(&db, vec![viewer("André", false, Default::default()), viewer("Bea", false, claimed)]);
    assert_eq!(value::<i64>(&db, "SELECT COUNT(*) FROM profiles WHERE admin_claimed_at IS NOT NULL"), 1);
    assert_eq!(value::<String>(&db, "SELECT name FROM profiles WHERE admin_claimed_at = 10"), "Bea");

    // A merge that names no admin changes nothing.
    assert_eq!(import(&db, vec![viewer("André", false, Default::default())]), 0);
    assert_eq!(value::<String>(&db, "SELECT name FROM profiles WHERE admin_claimed_at IS NOT NULL"), "Bea");
}

#[test]
fn a_parent_is_linked_by_name_once_even_when_listed_after_its_kid_and_never_overwritten() {
    let (_dir, db) = db();
    let owned = |parent: &str| {
        viewer("Mia", true, ProfileRoles { parent: Some(parent.into()), ..Default::default() })
    };
    import(&db, vec![owned("bea"), viewer("Bea", false, Default::default())]);
    let parent = || value::<String>(&db, "SELECT p.name FROM profiles k JOIN profiles p ON p.id = k.parent_id WHERE k.name = 'Mia'");
    assert_eq!(parent(), "Bea");
    import(&db, vec![owned("cleo"), viewer("Cleo", false, Default::default())]);
    assert_eq!(parent(), "Bea");
}

/// A grown-up another device made a kid never reads as a kid with no limit.
#[test]
fn a_grown_up_upgraded_to_a_kid_starts_from_twelve() {
    let (_dir, db) = db();
    make(&db, "Mia", false);
    assert_eq!(import(&db, vec![viewer("Mia", true, Default::default())]), 1);
    assert_eq!(value::<i64>(&db, "SELECT kids_age FROM profiles"), 12);
}
```

- [ ] **Step 2:** `cargo test -p mediagram-core --lib state::exchange -q` — expected FAIL to compile
  (module `profile_roles` under `exchange` does not exist; `roles` not set on export).
- [ ] **Step 3: implement `state/exchange/profile_roles.rs`**

```rust
//! A profile's role, limit, parent and PIN on the sync record, out and back
//! in. A port of the web's export and import for the same keys
//! (`web/src/state/store.ts`).
//!
//! Import is corrective like every other row. A limit or a PIN is taken when
//! the merge's is newer, or as new but different — two devices can stamp the
//! same millisecond, and the merge already chose between them. A parent is
//! set only while this device has none: it is fixed when the kid is made. The
//! admin is whoever the merge names, set here and cleared on every other
//! profile; a merge that names nobody changes nothing, so a device that has
//! not heard of the claim yet cannot undo it.

use rusqlite::{Connection, params};

use crate::state::profiles;
use crate::state::record::{AdminClaim, KidsAge, PinRecord, ProfileRoles, normal_name};

pub(super) fn export(conn: &Connection, profile_id: &str) -> rusqlite::Result<ProfileRoles> {
    conn.query_row(
        "SELECT kids, kids_age, kids_age_updated_at, admin_claimed_at, pin_hash, pin_salt, pin_updated_at,
                (SELECT parent.name FROM profiles AS parent WHERE parent.id = profiles.parent_id)
           FROM profiles WHERE id = ?1",
        [profile_id],
        |row| {
            let kids = row.get::<_, i64>(0)? != 0;
            let age: Option<i64> = row.get(1)?;
            let age_at: i64 = row.get(2)?;
            let claimed_at: Option<i64> = row.get(3)?;
            let hash: Option<String> = row.get(4)?;
            let salt: Option<String> = row.get(5)?;
            let pin_at: i64 = row.get(6)?;
            Ok(ProfileRoles {
                admin: claimed_at.map(|at| AdminClaim { claimed_at: at as f64 }),
                // A kid with no limit stored predates limits: FSK 12, the
                // one limit there was.
                kids_age: kids.then_some(KidsAge {
                    age: if age == Some(6) { 6 } else { 12 },
                    updated_at: age_at as f64,
                }),
                parent: row.get(7)?,
                pin: hash.zip(salt).map(|(hash, salt)| PinRecord { hash, salt, updated_at: pin_at as f64 }),
            })
        },
    )
}

/// The keys that are this profile's own: its limit and its PIN.
pub(super) fn import_own(conn: &Connection, profile_id: &str, roles: &ProfileRoles) -> rusqlite::Result<u64> {
    let mut changed = 0;
    if let Some(limit) = &roles.kids_age {
        changed += conn.execute(
            "UPDATE profiles SET kids_age = ?2, kids_age_updated_at = ?3
               WHERE id = ?1 AND (kids_age_updated_at < ?3
                 OR (kids_age_updated_at = ?3 AND kids_age IS NOT ?2))",
            params![profile_id, limit.age, limit.updated_at as i64],
        )?;
    }
    if let Some(pin) = &roles.pin {
        changed += conn.execute(
            "UPDATE profiles SET pin_hash = ?2, pin_salt = ?3, pin_updated_at = ?4
               WHERE id = ?1 AND (pin_updated_at < ?4
                 OR (pin_updated_at = ?4 AND (pin_hash IS NOT ?2 OR pin_salt IS NOT ?3)))",
            params![profile_id, pin.hash, pin.salt, pin.updated_at as i64],
        )?;
    }
    Ok(changed as u64)
}

/// The keys that name another profile — a kid's parent, the household's
/// admin — settled once every viewer in the merge exists here.
pub(super) fn import_links(conn: &Connection, resolved: &[(String, &ProfileRoles)]) -> rusqlite::Result<u64> {
    let mut changed = 0;
    for (id, roles) in resolved {
        let Some(parent) = roles.parent.as_deref() else {
            continue;
        };
        let Some(parent_id) = local_id_named(conn, parent)? else {
            continue;
        };
        changed += conn.execute(
            "UPDATE profiles SET parent_id = ?2 WHERE id = ?1 AND parent_id IS NULL AND ?1 <> ?2",
            params![id, parent_id],
        )?;
    }
    let named = resolved.iter().find_map(|(id, roles)| roles.admin.as_ref().map(|claim| (id, claim)));
    if let Some((id, claim)) = named {
        changed += conn.execute(
            "UPDATE profiles SET admin_claimed_at = CASE WHEN id = ?1 THEN ?2 END
               WHERE (id = ?1 AND admin_claimed_at IS NOT ?2) OR (id <> ?1 AND admin_claimed_at IS NOT NULL)",
            params![id, claim.claimed_at as i64],
        )?;
    }
    Ok(changed as u64)
}

/// This device's id for the viewer `name` identifies, without making one.
fn local_id_named(conn: &Connection, name: &str) -> rusqlite::Result<Option<String>> {
    let wanted = normal_name(name);
    Ok(profiles::list(conn)?
        .into_iter()
        .find(|profile| normal_name(&profile.name) == wanted)
        .map(|profile| profile.id))
}

#[cfg(test)]
#[path = "profile_roles_tests.rs"]
mod tests;
```

- [ ] **Step 4: wire it into `exchange.rs`** — `mod profile_roles;` after the `use` block; in
  `export_record`, before `profiles.push(…)`: `let roles = profile_roles::export(conn, &profile.id)?;` and
  `roles,` in the `ProfileState` literal; in `import_merged`: `let mut resolved = Vec::with_capacity(merged.profiles.len());`
  before the loop, at the end of each iteration
  `changed += profile_roles::import_own(conn, &profile_id, &profile.roles)?;` then
  `resolved.push((profile_id, &profile.roles));`, and after the loop (before `commit`)
  `changed += profile_roles::import_links(conn, &resolved)?;`. The kids upgrade (`exchange.rs:88`)
  becomes `UPDATE profiles SET kids = 1, kids_age = COALESCE(kids_age, 12) WHERE id = ?1` — one
  statement, so it still counts as one change.
- [ ] **Step 5:** `cargo test -p mediagram-core -q` — expected: all green. `kids_profile_tests.rs`'
  `an_import_upgrades_an_existing_profile_and_never_downgrades` still counts 1 (its hand-built merge
  carries no `kids_age`).
- [ ] **Step 6: commit.** Bump (patch), then:

```bash
git add crates/mediagram-core/src/state/exchange.rs crates/mediagram-core/src/state/exchange/ \
  Cargo.toml Cargo.lock web/package.json android/app/build.gradle.kts docs/project-changelog.md
git commit -m "feat(core): export and import profile roles, limits, parents and PINs; release <next>"
```

### Task 7: Verify

- [ ] **Step 1:** `cargo test -p mediagram-core -q && cargo test -p mediagram --test code_standards -q` — green.
- [ ] **Step 2:** `cargo clippy --all-targets --all-features -- -D warnings` — clean.
- [ ] **Step 3:** `wc -l crates/mediagram-core/src/state/{merge,rows,record,exchange,lists_exchange,schema}.rs crates/mediagram-core/src/state/*/*.rs | sort -n | tail -5`
  — nothing above 200 (tests files are exempt).
- [ ] **Step 4: rebuild the native core, and prove the surface did not move**

```bash
CARGO_INCREMENTAL=0 ANDROID_NDK_HOME=/home/andre/android-sdk/ndk/28.2.13676358 scripts/build-android-core.sh
out=$(mktemp -d)
cargo run -q -p mediagram-core --features cli --bin uniffi-bindgen -- generate \
  --library android/core/rust/src/main/jniLibs/arm64-v8a/libmediagram_core.so \
  --language kotlin --no-format --out-dir "$out"
sed 's/[[:blank:]]*$//' "$out/uniffi/mediagram_core/mediagram_core.kt" \
  | diff - android/core/rust/src/main/kotlin/uniffi/mediagram_core/mediagram_core.kt && echo identical
```

Expected: `identical`. Anything else means a uniffi-visible item or docstring changed — undo it; that
belongs to phase 05.

## Todo list

- [ ] Task 0 preflight (fixture present, baseline green)
- [ ] Task 1 merged.rs + kids_marks.rs moves
- [ ] Task 2 schema v7 + repair before migrate
- [ ] Task 3 ProfileRoles wire keys + ListRow.age + kids_row
- [ ] Task 4 role merge + fixture runner + old-fixture projection
- [ ] Task 5 set_kids(age) / kids_from_six / Kids export-import age
- [ ] Task 6 role export/import
- [ ] Task 7 verify, .so rebuilt, bindings identical

## Success criteria

- `cargo test -p mediagram-core` green, including `profile_roles_merge_fixtures_match_the_web_in_both_orders`
  running real cases (not "skipping"), and the old `merge.json`/`lists-merge.json` runners.
- `code_standards` green; clippy `-D warnings` clean.
- No merged kid carries `pin` or `admin`, no merged grown-up carries `kidsAge` or `parent`; a Kids tie
  with another age converges on every device (`a_tie_that_differs_takes_the_merges_row`).
- A v6 file opens as v7 with kids at 12; a v3-without-`kids` file still opens.
- A record exported, merged with an older build's `{kids: true}` and a newer `kidsAge: 6`, imports as 6
  (review focus 4); two admin claims import as one (review focus 3).
- Bindings regenerated from the rebuilt `.so` are byte-identical to the committed ones.

## Risk assessment

| Risk | L×I | Mitigation |
|---|---|---|
| Web phase 01 still outputs `parent` on any viewer (its § Interfaces note predates the §7 amendment) | M×M | Core follows the amended contract (kids only); phase 01's fixture has no grown-up-with-parent case, so the runners agree; the web's own code must change too — raised in the report |
| Fixture from phase 01 disagrees with contract §7 elsewhere | L×M | Web is authoritative: fix the core; if the fixture contradicts the contract, stop and ask |
| `serde(flatten)` surprise (numbers through buffered content, key order) | L×M | `the_keys_are_written_in_the_webs_spelling…` round trip + fixture runner deserialize through it; publish compares serialized output, which stays deterministic |
| Pre-release v3 file breaks on v7 | M×H | Repair before migrate, version-guarded; existing test proves it |
| Old merge fixtures fail on the new `kidsAge` default | H×L | `canonical` clears `roles`, as the web runner picks fields |
| Clock skew reverts a local mark/limit change | M×M | `MAX(now, last + 1)` stamps (Kids marks here; limits and PINs in 05) — a deliberate difference from the web until it adopts the same (report) |
| Kids tie with another age never converges | M×M (was real) | Import takes an equal-time row that differs, the web's rule; `a_tie_that_differs_takes_the_merges_row` |
| A file crosses 200 lines | M×L | Budget table; `code_standards` at Tasks 1, 4, 7 |

Rollback: each task is one commit; revert newest-first. Schema v7 is additive (`ALTER ADD COLUMN`),
so an older build reading a v7 file skips nothing it needs; a reverted build would, however, re-run
nothing and stay at `user_version` 7 — harmless, as no v1–v6 statement reads the new columns.

## Security considerations

- `pin_hash`/`pin_salt` travel only inside the sync document (household's own channel) — as the
  contract says; nothing here exposes them on any other surface.
- Hostile input: every new key is validated on its own; a malformed one is dropped, never trusted,
  never fatal (`a_malformed_key_is_dropped_on_its_own`, `a_bad_role_key_never_costs_the_profile`).
- Import never overwrites a parent and never creates an admin the merge did not name.

## Next steps

Phase 05 (rules, PIN, wait, uniffi API) builds on `ProfileRoles`, schema v7, `rows::kids_from_six` and
`rows::set_kids(…, Option<u8>)`. Phase 06 is unaffected by this phase alone.
