# Phase 03 — Core: record, sync, summary, uniffi

**Goal.** The Rust core (Android's state engine) records watch time on its own
position writes, syncs it as bounded rows under the new `titleStats`/`dayStats`
keys, sums it into a `StatsSummary`, and hands that to Kotlin. It is pinned to
the web by the phase-01 fixtures. Phase 04 builds the Android page on top of it.

**Architecture (data flow).**

```
Kotlin save tick ── set_progress(profile, set, at, dur, local_day) ──▶ Core::set_progress   (api/state.rs)
   └─▶ StateDb::set_progress_counted(…, now_ms)                                           (state/stats.rs)
        ticks[(profile,set)] ─▶ step_seconds(prev, at, now) ─▶ ONE transaction:
            had_progress? / watched live?  ─▶ again
            rows::set_progress (position, unchanged)
            stats_titles(profile,set,THIS device)  += step, started_at on insert, again_at if again
            stats_days(profile,local_day,THIS device) += step   (only when step > 0)
        commit ─▶ ticks[(profile,set)] = {at, now}
Kotlin finish ── set_watched(…, true) ──▶ forget_tick + rows::set_watched
sync round:  export_record ─ ProfileState.titleStats/dayStats = EVERY device's rows (gossip)  ─▶ channel
             parse_record (row-by-row hostile) ─▶ merge_states (newest per (key, row device)) ─▶
             import_merged ─▶ stats::exchange::import (newer-or-missing upsert; never deletes,
                              never touches ticks, never records)
Kotlin stats page ── stats(profile, today) ──▶ export rows + rows::watched_for ─▶ summarize ─▶ StatsSummary
```

**Context.**
- Contract (every name, key, rule): [shared-contract.md](shared-contract.md) §1–§5, §8. Decisions: [plan.md](plan.md) (1–11, user).
- Format precedent: `plans/261002-0213-android-self-update/phase-03-core-latest-app-release-and-verified-download.md`.
- Precedent for a new optional wire key: commit `fce5a757` (`unwatched`), `state/record.rs:11-25`, `state/record/parse.rs:80-83`.
- Code this phase builds on (re-verified 2026-10-03):
  - `state/schema.rs:15` `GROUPS`, `:133` last group (v6), `:136` `VERSION = GROUPS.len()`; `state/mod.rs:123-146` `migrate` (PRAGMA `user_version`).
  - `state/mod.rs:46-49` `StateDb` (one per `Core`, `api/mod.rs:78`: process lifetime), `:79` `with` (returns `None` on any failure).
  - `state/rows.rs:48` `set_progress` (195 lines), `:103` `set_watched`, `:24` `WatchedRow`.
  - `api/state.rs:110-122` uniffi `set_progress`, `:133-139` `set_watched` (180 lines); `api/mod.rs` 199 lines (not touched).
  - `state/record.rs:69-95` `ProfileState`; `state/record/parse.rs:63-97` `profile_state`; `state/record/hostile_json.rs` `text_` (= web `text_`/`idOf`: string, trimmed, non-empty) and `as_array`; its `js_number` (= web `Number()`) is deliberately **not** used for stats numbers.
  - `state/merge.rs:39-77` `MergedProfile`/`MergedState` (file is 200/200), `merge/tie_break.rs:31-38` `timestamped_by_own_field!`, `:46` `keep` (ties on the *document* device).
  - `state/exchange.rs:21-59` `export_record`, `:71-107` `import_merged`; `state/sync.rs:137` own fresh export joins every merge.
  - `state/sync/device.rs:11` `device_id(conn)` — the same id `api/state_sync.rs:31` hands Kotlin and every sync round uses.
  - `tests/shared_watch_state_fixtures.rs:22` `FIXTURES`, `:33` `load` (missing file → skipped), `:78` `canonical`.

**Global constraints (contract §8 + this crate).**
- No plan references (phase/decision numbers, finding codes) in code, test names or commit messages.
- ≤ 200 lines per non-test `.rs` (`cargo test -p mediagram --test code_standards`). Measured after this phase: `state/mod.rs` 199, `state/rows.rs` 197, `api/state.rs` 190, `state/schema.rs` 173, `state/merge.rs` 167 (was 200), `state/record/parse.rs` 161, `state/stats.rs` 156, `state/stats/summary.rs` 156, `state/exchange.rs` 150, `state/record.rs` 143.
- No `chrono`/date crate: the local day comes from Kotlin; the core's own day arithmetic is `state/stats/calendar.rs`.
- No injectable clock: `now_ms` is a parameter of `StateDb::set_progress_counted` and `step_seconds`; only the uniffi wrapper reads `profiles::now_ms()`.
- `SYNC_FORMAT` stays 1. Keys omitted when empty. Nothing here throws to Kotlin.
- Version: patch, once, on the last commit (Task 3.8), all three manifests by pattern.
- Branch `feat/viewing-stats`, worktree off `main`.

**Fixture shapes**, as phase 01 (`phase-01-web-stats-rules-and-shared-fixtures.md`) writes them. Reconciled 2026-10-03, and the Rust code below was run against that plan's fixture JSON:

| File | Case shape | Rust side |
|---|---|---|
| `stats-step.json` | `{name, fn: "stepSeconds" \| "againNow", args, expect}` — the `resume-point.json` shape; `stepSeconds` args `[{at, wallMs} \| null, at, nowMs]`, `againNow` args `[hadProgress, watchedLive]` | `StepCase { fn, args: Vec<Value>, expect: Value }` dispatching to `step_seconds` / `again_now` |
| `stats-record-parse.json` | `{name, input: string, expect: SyncRecord \| null}` (as `record-parse.json`) | existing `RecordParseCase` |
| `stats-merge.json` | `{name, records: SyncRecord[], expect: MergedState}` (as `merge.json`) | existing `MergeCase`; `canonical()` sorts `titleStats` by (setId, device), `dayStats` by (day, device) |
| `stats-summary.json` | `{name, input: {today, titles, days, watched: [{setId, finishedAt}]}, expect: Partial<StatsSummary>}` — **only the keys a case names are compared** | `SummaryExpect` with every field `Option` (`deny_unknown_fields`), compared field by field |

Rules the contract (as amended 2026-10-03, §1/§3) and phase 01 pin, matched here:
- Stats numbers must be JSON numbers. No `Number()` coercion, unlike the older rows: `"600"`, `null` and `true` drop the row.
- `againAt: null` reads as absent (row kept, no restart); a negative or string `againAt` drops the row.
- `day` is checked for shape only and is **not** trimmed (`2026-13-45` kept, `" 2026-10-03"` dropped). Ids are trimmed like every other id.
- Own rows stamp `updated_at = MAX(now, stored + 1)` (title and day).
- The stats-merge runner compares only `name`, `displayName`, `titleStats`, `dayStats` per viewer, in both orders.
- Merge key `JSON.stringify([setId, device])` (here: length-prefixed, equally injective), tie on the document device.
- Code-unit sort, binary-exact seconds, UTC day arithmetic (here: pure day numbers).

**Before you start.**
- Phases 01–02 merged into `feat/viewing-stats`; `ls web/test/fixtures/watch-state/stats-{step,record-parse,merge,summary}.json` lists four files. If any is missing the runner prints `skipping:` and pins nothing — stop and ask.
- `cargo test -p mediagram-core` green on the branch before Task 3.1.

## Review focus

| Concern | Test | Task |
|---|---|---|
| A sync import never records watch time, and leaves no tick | `state::stats::tests::importing_a_merge_records_no_watch_time` | 3.6 |
| A document without the keys reads, merges and writes exactly as before | `record::stats_record::tests::a_profile_without_stats_writes_no_stats_keys`; `stats::exchange::tests::a_store_without_stats_exports_a_document_without_the_keys`; `merge.json`/`lists-merge.json`/`record-parse.json` re-run unchanged | 3.2, 3.3, 3.5 |
| A hostile day (or title) row is dropped on its own, never the document | `record::stats_record::tests::hostile_stats_rows_are_dropped_one_by_one` (+ `stats-record-parse.json`) | 3.2 |
| A process restart mid-title counts 0 for its first write, keeps `started_at` | `state::stats::tests::a_restarted_process_counts_nothing_for_its_first_write` | 3.6 |
| `set_watched(true)` clears the tick (engine and uniffi wiring) | `state::stats::tests::starting_a_finished_title_over_is_watched_again_once`; `tests/viewing_stats_api.rs::watching_finishing_and_starting_over_read_back_as_history` (mutation-checked: dropping `forget_tick` fails it) | 3.6, 3.7 |
| Position and stats commit together or not at all | `state::stats::tests::a_position_and_its_watch_time_land_together_or_not_at_all` | 3.6 |
| Merge order-independent, a minute never counted twice across rounds | `merge::stats::tests::each_devices_newest_row_survives_and_counts_once`; `sync::tests::viewing_stats_on_two_machines::minutes_from_both_machines_sum_once_on_both` (+ `stats-merge.json` both orders) | 3.3, 3.7 |
| A merge key cannot be forged by where (setId, device) split | `merge::stats::tests::a_key_cannot_be_forged_by_where_its_halves_split` | 3.3 |
| "Watched again" set once per start-over; not after an un-mark | `starting_a_finished_title_over_is_watched_again_once`, `a_title_finished_before_it_played_here_starts_as_watched_again`, `a_title_taken_back_from_finished_is_not_watched_again` | 3.6 |
| Own stamps never move backwards (`MAX(now, stored + 1)`), so a newer local row is never undone by an import | `state::stats::tests::this_devices_stamps_never_move_backwards` | 3.6 |
| Midnight: a step counts on the day of the write that ends it | `a_step_across_midnight_counts_on_the_day_of_the_write_that_ends_it` | 3.6 |
| Older stores migrate; a profile's stats cascade with it | `state::upgrade_tests::a_store_from_before_stats_gains_empty_stats_tables` | 3.1 |
| Gossip: export carries every device's rows; a reinstall's own newer row comes back | `export_carries_every_devices_rows_in_a_fixed_order`, `this_devices_own_newer_row_comes_back_too` | 3.5 |

Verified on 2026-10-03 against a scratch copy of `main` @ `4b67e8c5`. Every new file and test block was extracted verbatim from this plan, and the four stats fixtures verbatim from phase 01's plan (step 17 cases, record-parse 16, merge 10, summary 14). Results: `cargo test -p mediagram-core` lib 478 passed (32 new); `shared_watch_state_fixtures` 7 passed with no `skipping:`; `viewing_stats_api` 2; `api_surface` 11. `cargo clippy -p mediagram-core --all-targets --all-features -- -D warnings`: 0 warnings.

## Task 3.1: Schema v7 — the two stats tables

**Files:**
- Modify: `crates/mediagram-core/src/state/schema.rs` (append a group after `:133`)
- Modify: `crates/mediagram-core/src/state/upgrade_tests.rs` (append one test)

**Interfaces:** Produces tables `stats_titles`, `stats_days` (contract §2, verbatim — the core's `progress` uses `REFERENCES profiles(id) ON DELETE CASCADE` too, `schema.rs:25-32`); `schema::VERSION` becomes 7.

- [ ] **Step 1: Failing test** — append to `crates/mediagram-core/src/state/upgrade_tests.rs`

```rust

/// A store from before viewing stats gains both stats tables, empty, keeps
/// every row it held, and drops a profile's stats with the profile.
#[test]
fn a_store_from_before_stats_gains_empty_stats_tables() {
    let dir = tempfile::tempdir().unwrap();
    {
        let conn = Connection::open(dir.path().join(STATE_FILE)).unwrap();
        for statement in schema::migrations_up_to(6) {
            conn.execute(statement, []).unwrap();
        }
        conn.pragma_update(None, "user_version", 6i64).unwrap();
        conn.execute("INSERT INTO profiles(id, name, created_at) VALUES ('p1', 'André', 0)", []).unwrap();
        conn.execute(
            "INSERT INTO progress(profile_id, set_id, at_seconds, duration, updated_at)
               VALUES ('p1', 'set1', 12.5, 90.0, 0)",
            [],
        )
        .unwrap();
    }

    let db = StateDb::new(dir.path().to_path_buf());

    assert_eq!(db.with(|conn| rows::progress_for(conn, "p1")).unwrap().len(), 1);
    let count = |table: &str| -> i64 {
        db.with(|conn| conn.query_row(&format!("SELECT COUNT(*) FROM {table}"), [], |row| row.get(0)))
            .unwrap()
    };
    assert_eq!((count("stats_titles"), count("stats_days")), (0, 0));
    db.with(|conn| {
        conn.execute(
            "INSERT INTO stats_days(profile_id, day, device, seconds, updated_at)
               VALUES ('p1', '2026-10-03', 'phone', 60.0, 1)",
            [],
        )?;
        profiles::delete(conn, "p1")
    })
    .unwrap();
    assert_eq!(count("stats_days"), 0, "a profile's stats go with it");
}
```

- [ ] **Step 2: Run, expect FAIL**

Run: `cargo test -p mediagram-core --lib a_store_from_before_stats_gains_empty_stats_tables`
Expected: FAIL — `state: a read or write did not complete (no such table: stats_titles)` then a panic on `Option::unwrap()` of `None`.

- [ ] **Step 3: Implement** — in `crates/mediagram-core/src/state/schema.rs`, replace the closing of `GROUPS`

```rust
    &["ALTER TABLE watched ADD COLUMN removed_at INTEGER"],
];
```
with
```rust
    &["ALTER TABLE watched ADD COLUMN removed_at INTEGER"],
    // v6 -> v7: viewing stats — how long, and when, each profile watched.
    // One row per (title, device) and per (local day, device). A device
    // writes only rows that name it; the others' arrive by sync import and
    // sit beside them, so a total is a sum over devices and no merge can
    // count a minute twice. Kept forever: bounded by the titles watched and
    // by the days, about 365 day rows a year per profile.
    &[
        "CREATE TABLE IF NOT EXISTS stats_titles(
       profile_id TEXT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
       set_id TEXT NOT NULL,
       device TEXT NOT NULL,
       started_at INTEGER NOT NULL,
       last_watched_at INTEGER NOT NULL,
       seconds REAL NOT NULL,
       again_at INTEGER,
       updated_at INTEGER NOT NULL,
       PRIMARY KEY(profile_id, set_id, device)
     )",
        "CREATE TABLE IF NOT EXISTS stats_days(
       profile_id TEXT NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
       day TEXT NOT NULL,
       device TEXT NOT NULL,
       seconds REAL NOT NULL,
       updated_at INTEGER NOT NULL,
       PRIMARY KEY(profile_id, day, device)
     )",
    ],
];
```

- [ ] **Step 4: Run, expect PASS**

Run: `cargo test -p mediagram-core --lib state::`
Expected: all pass, including `a_store_from_before_stats_gains_empty_stats_tables` and the existing `migration_tests`/`upgrade_tests` (none hard-code a version; `migration_tests.rs:125` compares with `schema::VERSION`).

- [ ] **Step 5: Commit**

```bash
git add crates/mediagram-core/src/state/schema.rs crates/mediagram-core/src/state/upgrade_tests.rs
git commit -m "feat(core): state schema v7 adds the viewing stats tables"
```

## Task 3.2: Wire rows and their hostile parse

**Files:**
- Create: `crates/mediagram-core/src/state/record/stats_record.rs`, `crates/mediagram-core/src/state/record/stats_record_tests.rs`
- Modify: `crates/mediagram-core/src/state/record.rs`, `crates/mediagram-core/src/state/record/parse.rs`, `crates/mediagram-core/src/state/exchange.rs` (struct literal only), `crates/mediagram-core/tests/shared_watch_state_fixtures.rs`

**Interfaces:** Produces `state::record::{TitleStatRow, DayStatRow}` (serde camelCase: `setId, device, startedAt, lastWatchedAt, seconds, againAt?, updatedAt` / `day, device, seconds, updatedAt`), `ProfileState.title_stats` / `.day_stats` (keys `titleStats`/`dayStats`, omitted when empty), `pub(crate) fn is_day(&str) -> bool` (re-exported in Task 3.4 when first used).

Parse rules (contract §3 as phase 01 pins them), row by row, a bad row dropped:
- `setId`/`device`: strings, non-empty after trim (`text_`, as every other id).
- `startedAt`, `lastWatchedAt`, `seconds`, `updatedAt`: JSON numbers, finite, ≥ 0. No coercion.
- `againAt`: absent or `null` → none; otherwise a number as above, or the row goes.
- `day`: a string matching `^\d{4}-\d{2}-\d{2}$` (ASCII digits), untrimmed. A day row with `seconds > 86400` is dropped.
- A key that is not an array reads as absent (`as_array`).

- [ ] **Step 1: Failing tests** — create `crates/mediagram-core/src/state/record/stats_record_tests.rs`

```rust
use serde_json::{Value, json};

use super::*;
use crate::state::record::{SyncRecord, parse_record};

fn parsed(profile: Value) -> SyncRecord {
    let body = json!({ "format": 1, "device": "phone", "writtenAt": 1, "profiles": [profile] });
    parse_record(&body.to_string()).expect("the document itself is sound")
}

/// A sound row with `changes` laid over it.
fn with(mut row: Value, changes: Value) -> Value {
    row.as_object_mut()
        .unwrap()
        .extend(changes.as_object().unwrap().clone());
    row
}

fn title(changes: Value) -> Value {
    let sound = json!({ "setId": "01A", "device": "phone", "startedAt": 10,
                        "lastWatchedAt": 20, "seconds": 42.5, "updatedAt": 20 });
    with(sound, changes)
}

fn day(changes: Value) -> Value {
    let sound = json!({ "day": "2026-10-03", "device": "phone", "seconds": 600, "updatedAt": 20 });
    with(sound, changes)
}

#[test]
fn sound_rows_read_back_as_written() {
    let record = parsed(json!({ "name": "André",
        "titleStats": [title(json!({ "againAt": 15 })), title(json!({ "setId": "01B", "againAt": null }))],
        "dayStats": [day(json!({}))] }));
    let profile = &record.profiles[0];
    assert_eq!(
        profile.title_stats[0],
        TitleStatRow {
            set_id: "01A".into(),
            device: "phone".into(),
            started_at: 10.0,
            last_watched_at: 20.0,
            seconds: 42.5,
            again_at: Some(15.0),
            updated_at: 20.0,
        }
    );
    assert_eq!(profile.title_stats[1].again_at, None, "a null againAt is no restart");
    assert_eq!(
        profile.day_stats,
        [DayStatRow { day: "2026-10-03".into(), device: "phone".into(), seconds: 600.0, updated_at: 20.0 }]
    );
    let body = serde_json::to_string(&profile.title_stats[1]).unwrap();
    assert!(!body.contains("againAt"), "never started over writes no key: {body}");
}

/// Each bad row goes on its own; the document, its sound rows and its
/// positions all stay.
#[test]
fn hostile_stats_rows_are_dropped_one_by_one() {
    let bad_titles = [
        json!({ "setId": "" }),
        json!({ "device": 7 }),
        json!({ "seconds": -1 }),
        json!({ "seconds": "600" }),
        json!({ "seconds": null }),
        json!({ "seconds": true }),
        json!({ "startedAt": "soon" }),
        json!({ "lastWatchedAt": [1] }),
        json!({ "updatedAt": "x" }),
        json!({ "againAt": -5 }),
        json!({ "againAt": "1789000300000" }),
    ];
    let bad_days = [
        json!({ "day": "2026-10-3" }),
        json!({ "day": " 2026-10-03" }),
        json!({ "day": "2026-10-03T00:00" }),
        json!({ "day": "２０２６-10-03" }),
        json!({ "day": 20261003 }),
        json!({ "seconds": 86_401 }),
        json!({ "seconds": "600" }),
        json!({ "seconds": -1 }),
        json!({ "device": "" }),
        json!({ "updatedAt": -1 }),
    ];
    let titles: Vec<Value> = bad_titles.into_iter().map(title).chain([title(json!({}))]).collect();
    let days: Vec<Value> =
        bad_days.into_iter().map(day).chain([day(json!({ "seconds": 86_400 }))]).collect();
    let record = parsed(json!({ "name": "André",
        "progress": [{ "setId": "01B", "at": 5, "updatedAt": 20 }],
        "titleStats": titles, "dayStats": days }));
    let profile = &record.profiles[0];
    assert_eq!(profile.title_stats.len(), 1, "only the sound title row is left");
    assert_eq!(
        profile.day_stats.iter().map(|row| row.seconds).collect::<Vec<_>>(),
        [86_400.0],
        "a whole day is the most one device can watch"
    );
    assert_eq!(profile.progress.len(), 1, "the rest of the document is untouched");
}

#[test]
fn keys_that_are_not_lists_read_as_absent() {
    let record = parsed(json!({ "name": "André", "titleStats": { "setId": "01A" }, "dayStats": "lots" }));
    assert!(record.profiles[0].title_stats.is_empty());
    assert!(record.profiles[0].day_stats.is_empty());
}

/// Every document written before stats — and every profile with none —
/// reads and writes exactly as it did: no keys, not empty lists.
#[test]
fn a_profile_without_stats_writes_no_stats_keys() {
    let record = parsed(json!({ "name": "André", "progress": [] }));
    let body = serde_json::to_string(&record).unwrap();
    assert!(!body.contains("titleStats") && !body.contains("dayStats"), "{body}");
}

#[test]
fn a_day_is_four_two_two_ascii_digits() {
    assert!(is_day("2026-10-03"));
    for bad in ["", "2026-10-3", "2026/10/03", "2026-10-03 ", "２０２６-10-03", "abcd-ef-gh"] {
        assert!(!is_day(bad), "{bad:?}");
    }
}
```

In `crates/mediagram-core/tests/shared_watch_state_fixtures.rs`, turn the record-parse test into a runner and add the stats file — replace `:49-63` (`#[test] fn record_parse_fixtures_match_the_web() { … }`) with:

```rust
fn run_record_parse_fixture(file: &str) {
    let Some(cases) = load::<RecordParseCase>(file) else {
        return;
    };
    assert!(!cases.is_empty(), "{file} holds no cases");
    for case in cases {
        assert_eq!(
            parse_record(&case.input),
            case.expect,
            "case: {}",
            case.name
        );
    }
}

#[test]
fn record_parse_fixtures_match_the_web() {
    run_record_parse_fixture("record-parse.json");
}

/// Title and day rows: each bad one dropped on its own, never the document.
#[test]
fn stats_record_parse_fixtures_match_the_web() {
    run_record_parse_fixture("stats-record-parse.json");
}
```
(Until Step 3 this fixture test passes vacuously — serde ignores the unknown keys on both sides. The unit tests above are the failing ones.)

- [ ] **Step 2: Wire the module, run, expect a compile failure**

In `crates/mediagram-core/src/state/record.rs` add `mod stats_record;` after `mod preference_record;` (`:33`), and create `crates/mediagram-core/src/state/record/stats_record.rs` holding only
```rust
#[cfg(test)]
#[path = "stats_record_tests.rs"]
mod tests;
```
Run: `cargo test -p mediagram-core --lib state::record::stats_record`
Expected: FAIL — `cannot find struct, variant or union type 'TitleStatRow'`, `cannot find function 'is_day'`, no field `title_stats` on `ProfileState`.

- [ ] **Step 3: Implement**

`crates/mediagram-core/src/state/record/stats_record.rs` (whole file):

```rust
//! Viewing stats on the sync record: one row per (title, device) and per
//! (day, device), each written by the device it names and passed on by
//! every other. New keys on a profile, not a format bump — a build that
//! predates them drops them and keeps merging. Pinned to the web by
//! `stats-record-parse.json`.

use serde::{Deserialize, Serialize};
use serde_json::Value;

use super::hostile_json::text_;

/// The most one device can watch in one day; a row claiming more is not one
/// any player wrote.
const DAY_SECONDS: f64 = 86_400.0;

/// One device's viewing of one title. Times are epoch milliseconds.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct TitleStatRow {
    pub set_id: String,
    pub device: String,
    /// Its first play on that device.
    pub started_at: f64,
    pub last_watched_at: f64,
    pub seconds: f64,
    /// Its latest start-over after being finished, if it had one.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub again_at: Option<f64>,
    pub updated_at: f64,
}

/// One device's watch time on one of its own local days.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DayStatRow {
    /// `YYYY-MM-DD`, the watching device's date.
    pub day: String,
    pub device: String,
    pub seconds: f64,
    pub updated_at: f64,
}

/// `YYYY-MM-DD` in ASCII digits: the shape, not whether the date exists.
pub(crate) fn is_day(day: &str) -> bool {
    let bytes = day.as_bytes();
    bytes.len() == 10
        && bytes.iter().enumerate().all(|(i, byte)| {
            if i == 4 || i == 7 {
                *byte == b'-'
            } else {
                byte.is_ascii_digit()
            }
        })
}

/// A count or a time a row carries: a JSON number, finite, never negative.
/// No coercion, unlike the older rows: no writer ever put a string, `null`
/// or a boolean in these keys.
fn amount(value: Option<&Value>) -> Option<f64> {
    value?.as_f64().filter(|n| n.is_finite() && *n >= 0.0)
}

pub(super) fn title_stat_row(raw: &Value) -> Option<TitleStatRow> {
    let row = raw.as_object()?;
    // Absent or `null` means never started over; anything else has to be a
    // time like the rest, or the row goes.
    let again_at = match row.get("againAt") {
        None | Some(Value::Null) => None,
        given => Some(amount(given)?),
    };
    Some(TitleStatRow {
        set_id: text_(row.get("setId"))?,
        device: text_(row.get("device"))?,
        started_at: amount(row.get("startedAt"))?,
        last_watched_at: amount(row.get("lastWatchedAt"))?,
        seconds: amount(row.get("seconds"))?,
        again_at,
        updated_at: amount(row.get("updatedAt"))?,
    })
}

pub(super) fn day_stat_row(raw: &Value) -> Option<DayStatRow> {
    let row = raw.as_object()?;
    // Shape only, and untrimmed: `2026-13-45` is kept, ` 2026-10-03` is not.
    let day = row.get("day").and_then(Value::as_str).filter(|day| is_day(day))?;
    Some(DayStatRow {
        day: day.to_string(),
        device: text_(row.get("device"))?,
        seconds: amount(row.get("seconds")).filter(|seconds| *seconds <= DAY_SECONDS)?,
        updated_at: amount(row.get("updatedAt"))?,
    })
}

#[cfg(test)]
#[path = "stats_record_tests.rs"]
mod tests;
```

`crates/mediagram-core/src/state/record.rs` — after `pub use preference_record::{SYNCED_NAMES, SyncPreference};` add
```rust
pub use stats_record::{DayStatRow, TitleStatRow};
```
and at the end of `ProfileState` (after `pub preferences: Vec<SyncPreference>,`, `:94`):
```rust
    /// Viewing stats, every device's rows (`stats_record.rs`). New keys like
    /// `preferences`, written only when there are rows, so a document
    /// without stats reads exactly as it did before them.
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub title_stats: Vec<TitleStatRow>,
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub day_stats: Vec<DayStatRow>,
```

`crates/mediagram-core/src/state/record/parse.rs` — after `use super::preference_record::preference_row;` add
```rust
use super::stats_record::{day_stat_row, title_stat_row};
```
and in `profile_state`, after the `preferences: …collect(),` field (`:92-95`):
```rust
        title_stats: as_array(row.get("titleStats"))
            .iter()
            .filter_map(title_stat_row)
            .collect(),
        day_stats: as_array(row.get("dayStats"))
            .iter()
            .filter_map(day_stat_row)
            .collect(),
```

`crates/mediagram-core/src/state/exchange.rs` — `ProfileState` is built exhaustively at `:37-47`; after `preferences,` add (Task 3.5 replaces these with the real rows):
```rust
            title_stats: Vec::new(),
            day_stats: Vec::new(),
```
(`ProfileState { … }` literals: only `exchange.rs:37` and `parse.rs:66`, verified by grep.)

- [ ] **Step 4: Run, expect PASS**

Run: `cargo test -p mediagram-core --lib state::record` and `cargo test -p mediagram-core --test shared_watch_state_fixtures`
Expected: lib — all pass, 5 of them in `state::record::stats_record`; fixtures — `4 passed`, no `skipping:` line for `stats-record-parse.json`.

- [ ] **Step 5: Commit**

```bash
git add crates/mediagram-core/src/state/record.rs crates/mediagram-core/src/state/record crates/mediagram-core/src/state/exchange.rs crates/mediagram-core/tests/shared_watch_state_fixtures.rs
git commit -m "feat(core): read and write viewing stats rows on the sync record"
```

## Task 3.3: Merge — newest row per (key, device)

**Files:**
- Create: `crates/mediagram-core/src/state/merge/merged.rs`, `crates/mediagram-core/src/state/merge/stats.rs`, `crates/mediagram-core/src/state/merge/stats_tests.rs`
- Modify: `crates/mediagram-core/src/state/merge.rs` (200/200 → 167: the two answer types move out verbatim), `crates/mediagram-core/src/state/merge/tie_break.rs`, `crates/mediagram-core/src/state/kids_profile_tests.rs` (`:14-24` lists every `MergedProfile` field), `crates/mediagram-core/tests/shared_watch_state_fixtures.rs`

**Interfaces:** Produces `MergedProfile.title_stats: Vec<TitleStatRow>`, `.day_stats: Vec<DayStatRow>` (`#[serde(default, skip_serializing_if = "Vec::is_empty")]`). Path `crate::state::merge::{MergedProfile, MergedState}` unchanged (re-exported). Key = (setId | day, **row** device), length-prefixed like `merge/preferences.rs:19`; tie = `keep()` on the **document** device (`tie_break.rs:46-56`), the same as every other row.

- [ ] **Step 1: Failing tests** — create `crates/mediagram-core/src/state/merge/stats_tests.rs`

```rust
use serde_json::{Value, json};

use crate::state::merge::merge_states;
use crate::state::record::{SyncRecord, parse_record};

fn record(device: &str, title_stats: Value, day_stats: Value) -> SyncRecord {
    let body = json!({ "format": 1, "device": device, "writtenAt": 1,
        "profiles": [{ "name": "André", "titleStats": title_stats, "dayStats": day_stats }] });
    parse_record(&body.to_string()).unwrap()
}

fn title(set_id: &str, device: &str, seconds: f64, updated_at: f64) -> Value {
    json!({ "setId": set_id, "device": device, "startedAt": 1, "lastWatchedAt": updated_at,
            "seconds": seconds, "updatedAt": updated_at })
}

fn day(device: &str, seconds: f64, updated_at: f64) -> Value {
    json!({ "day": "2026-10-03", "device": device, "seconds": seconds, "updatedAt": updated_at })
}

/// The laptop's own row, and an older copy of it the phone still passes on:
/// in either order the laptop's newest counts once, and the phone's own row
/// stands beside it.
#[test]
fn each_devices_newest_row_survives_and_counts_once() {
    let laptop = record("laptop", json!([title("01A", "laptop", 600.0, 20.0)]), json!([day("laptop", 600.0, 20.0)]));
    let phone = record(
        "phone",
        json!([title("01A", "laptop", 300.0, 10.0), title("01A", "phone", 120.0, 15.0)]),
        json!([day("laptop", 300.0, 10.0)]),
    );
    for records in [vec![laptop.clone(), phone.clone()], vec![phone, laptop]] {
        let merged = merge_states(&records);
        let mut titles: Vec<(String, f64)> = merged.profiles[0]
            .title_stats
            .iter()
            .map(|row| (row.device.clone(), row.seconds))
            .collect();
        titles.sort_by(|a, b| a.0.cmp(&b.0));
        assert_eq!(titles, [("laptop".to_string(), 600.0), ("phone".to_string(), 120.0)]);
        let days: Vec<f64> = merged.profiles[0].day_stats.iter().map(|row| row.seconds).collect();
        assert_eq!(days, [600.0]);
    }
}

/// `01A` on device `bc` and `01Ab` on device `c` join to the same text; they
/// are still two rows.
#[test]
fn a_key_cannot_be_forged_by_where_its_halves_split() {
    let a = record("a", json!([title("01A", "bc", 1.0, 1.0)]), json!([]));
    let b = record("b", json!([title("01Ab", "c", 2.0, 1.0)]), json!([]));
    assert_eq!(merge_states(&[a, b]).profiles[0].title_stats.len(), 2);
}
```

In `crates/mediagram-core/tests/shared_watch_state_fixtures.rs`, extend `canonical()` — after the `profile.preferences.sort_by(…)` statement (`:87-89`):
```rust
        profile
            .title_stats
            .sort_by(|a, b| (&a.set_id, &a.device).cmp(&(&b.set_id, &b.device)));
        profile
            .day_stats
            .sort_by(|a, b| (&a.day, &a.device).cmp(&(&b.day, &b.device)));
```
change the merge import to `use mediagram_core::state::merge::{MergedProfile, MergedState, merge_states};`, and append (phase 01's runner compares only `name`, `displayName` and the two stats keys, so a case may carry positions it does not spell out in `expect`; so does this one):
```rust

/// The stats keys only, per viewer, as the web's own runner compares them:
/// a case about stats rows need not spell out the positions beside them.
fn stats_only(state: MergedState) -> Vec<MergedProfile> {
    canonical(state)
        .profiles
        .into_iter()
        .map(|profile| MergedProfile {
            name: profile.name,
            display_name: profile.display_name,
            title_stats: profile.title_stats,
            day_stats: profile.day_stats,
            ..Default::default()
        })
        .collect()
}

/// Viewing stats rows: the newest copy per (title, device) and per (day,
/// device), in either order.
#[test]
fn stats_merge_fixtures_match_the_web_in_both_orders() {
    let Some(cases) = load::<MergeCase>("stats-merge.json") else {
        return;
    };
    assert!(!cases.is_empty(), "stats-merge.json holds no cases");
    for case in cases {
        let expect = stats_only(case.expect);
        let mut records = case.records;
        assert_eq!(stats_only(merge_states(&records)), expect, "case: {} (forward)", case.name);
        records.reverse();
        assert_eq!(stats_only(merge_states(&records)), expect, "case: {} (reversed)", case.name);
    }
}
```

- [ ] **Step 2: Run, expect a compile failure**

Add `mod stats;` to `merge.rs` beside `mod preferences;`, then run: `cargo test -p mediagram-core --lib state::merge`
Expected: FAIL — `file not found for module 'stats'` (and, once created, no field `title_stats` on `MergedProfile`).

- [ ] **Step 3: Implement**

`crates/mediagram-core/src/state/merge/stats.rs` (whole file):

```rust
//! The viewing-stats half of `merge_states`: per viewer, the newest copy of
//! each device's row per title and per day. A device writes only rows that
//! name it, so keeping one per (key, device) and summing across devices can
//! never count a minute twice. Split out to keep `merge.rs` under the line
//! limit.

use std::collections::HashMap;

use super::tie_break::{Held, keep};
use crate::state::record::{DayStatRow, ProfileState, TitleStatRow};

#[derive(Default)]
pub(super) struct Kept {
    titles: HashMap<String, Held<TitleStatRow>>,
    days: HashMap<String, Held<DayStatRow>>,
}

/// Folds one device's document in; an exact tie goes to the greater
/// document device id, as for every other kept row.
pub(super) fn absorb(into: &mut Kept, profile: &ProfileState, device: &str) {
    for row in &profile.title_stats {
        keep(&mut into.titles, key(&row.set_id, &row.device), row.clone(), device);
    }
    for row in &profile.day_stats {
        keep(&mut into.days, key(&row.day, &row.device), row.clone(), device);
    }
}

/// Length-prefixed, as `preferences::absorb` does: either half may hold any
/// text, so a plain join could make two different keys collide.
fn key(first: &str, device: &str) -> String {
    format!("{}:{first}{device}", first.len())
}

pub(super) fn rows(kept: Kept) -> (Vec<TitleStatRow>, Vec<DayStatRow>) {
    (
        kept.titles.into_values().map(|held| held.row).collect(),
        kept.days.into_values().map(|held| held.row).collect(),
    )
}

#[cfg(test)]
#[path = "stats_tests.rs"]
mod tests;
```

`crates/mediagram-core/src/state/merge/merged.rs` (whole file — `merge.rs:39-77` moved verbatim, plus the two fields):

```rust
//! What `merge_states` answers with. Split out of `merge.rs` only to keep
//! it under the line limit; the rules that fill these live there.

use serde::{Deserialize, Serialize};

use crate::state::record::{
    CollectionRow, DayStatRow, ListRow, ProgressRow, SyncPreference, TitleStatRow, UnwatchedRow,
    WatchedRow,
};

/// Everything the devices agree on, once they have been reconciled.
#[derive(Debug, Clone, Default, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct MergedProfile {
    /// The normalised name, which is what identifies a viewer across
    /// machines.
    pub name: String,
    /// The name as typed; the identity is normalised, a name is not.
    pub display_name: String,
    /// A kids profile if any device's document says so.
    #[serde(default, skip_serializing_if = "crate::state::record::is_false")]
    pub kids: bool,
    // `#[serde(default)]`: a fixture's `expect` names only what it tests.
    #[serde(default)]
    pub progress: Vec<ProgressRow>,
    #[serde(default)]
    pub watched: Vec<WatchedRow>,
    #[serde(default)]
    pub unwatched: Vec<UnwatchedRow>,
    #[serde(default)]
    pub watchlist: Vec<ListRow>,
    #[serde(default)]
    pub collections: Vec<CollectionRow>,
    #[serde(default)]
    pub preferences: Vec<SyncPreference>,
    /// Viewing stats: the newest copy of each device's row, per title and
    /// per day. Omitted when empty, like the record's own keys.
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub title_stats: Vec<TitleStatRow>,
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub day_stats: Vec<DayStatRow>,
}

#[derive(Debug, Clone, Default, PartialEq, Serialize, Deserialize)]
pub struct MergedState {
    pub profiles: Vec<MergedProfile>,
    /// Not scoped to a profile — see `schema.rs` on why `kids` alone has
    /// none.
    #[serde(default)]
    pub kids: Vec<ListRow>,
    /// Household-wide too, and kept per title the same way; see
    /// `schema.rs`'s v5.
    #[serde(default, rename = "editorsChoice")]
    pub editors_choice: Vec<ListRow>,
}
```

`crates/mediagram-core/src/state/merge.rs` — apply exactly this diff (the removed block is what `merged.rs` now holds; `serde` and `SyncPreference` would otherwise be unused imports, which `-D warnings` rejects):

```diff
@@ -19,63 +19,24 @@
 //! weighs a live row against its removal (`record.rs` explains the key).
-//! Watchlist, Kids, collections and preferences need no such trick — each
-//! row carries its own timestamp (and `removed` flag), reconciled by `keep`.
+//! Watchlist, Kids, collections, preferences and viewing stats need no such
+//! trick — each row carries its own timestamp (and `removed` flag),
+//! reconciled by `keep`.
 
 use std::collections::HashMap;
 
-use serde::{Deserialize, Serialize};
-
 use super::record::{
-    CollectionRow, ListRow, ProgressRow, SyncPreference, SyncRecord, UnwatchedRow, WatchedRow,
-    normal_name,
+    CollectionRow, ListRow, ProgressRow, SyncRecord, UnwatchedRow, WatchedRow, normal_name,
 };
 
+mod merged;
 mod preferences;
+mod stats;
 mod tie_break;
 mod watched;
+pub use merged::{MergedProfile, MergedState};
 use tie_break::{Held, keep};
 
-/// Everything the devices agree on, once they have been reconciled.
-… (delete through the end of `pub struct MergedState { … }`, old lines 39-77) …
-
 struct ViewerState {
@@ -87,6 +48,7 @@
     collections: HashMap<String, Held<CollectionRow>>,
     preferences: preferences::Kept,
+    stats: stats::Kept,
 }
@@ -123,6 +85,7 @@
                 preferences: HashMap::new(),
+                stats: stats::Kept::default(),
             });
@@ -151,6 +114,7 @@
             preferences::absorb(&mut held.preferences, &profile.preferences, device);
+            stats::absorb(&mut held.stats, profile, device);
         }
@@ -180,6 +144,7 @@
 
+        let (title_stats, day_stats) = stats::rows(held.stats);
         profiles.push(MergedProfile {
@@ -190,6 +155,8 @@
             preferences: preferences::rows(held.preferences),
+            title_stats,
+            day_stats,
         });
```

`crates/mediagram-core/src/state/merge/tie_break.rs`:
```diff
 use crate::state::record::{
-    CollectionRow, ListRow, ProgressRow, SyncPreference, UnwatchedRow, WatchedRow,
+    CollectionRow, DayStatRow, ListRow, ProgressRow, SyncPreference, TitleStatRow, UnwatchedRow,
+    WatchedRow,
 };
@@
-/// watchlist or Kids mark, a collection, or a preference.
+/// watchlist or Kids mark, a collection, a preference, or a stats row.
@@
     CollectionRow,
-    SyncPreference
+    SyncPreference,
+    TitleStatRow,
+    DayStatRow
 );
```

`crates/mediagram-core/src/state/kids_profile_tests.rs` — after `preferences: vec![],` (`:23`) add
```rust
            title_stats: vec![],
            day_stats: vec![],
```

- [ ] **Step 4: Run, expect PASS**

Run: `cargo test -p mediagram-core --lib state::` and `cargo test -p mediagram-core --test shared_watch_state_fixtures`
Expected: lib — all pass (2 in `state::merge::stats`); fixtures — `5 passed`; `merge.json`, `lists-merge.json` unchanged and green in both orders.

- [ ] **Step 5: Commit**

```bash
git add crates/mediagram-core/src/state/merge.rs crates/mediagram-core/src/state/merge crates/mediagram-core/src/state/kids_profile_tests.rs crates/mediagram-core/tests/shared_watch_state_fixtures.rs
git commit -m "feat(core): merge viewing stats rows, newest per title or day and device"
```

## Task 3.4: `summarize` — the stats page's numbers, pure

**Files:**
- Create: `crates/mediagram-core/src/state/stats.rs` (module root; grows in 3.5–3.6), `crates/mediagram-core/src/state/stats/calendar.rs`, `crates/mediagram-core/src/state/stats/summary.rs`, `crates/mediagram-core/src/state/stats/summary_tests.rs`
- Modify: `crates/mediagram-core/src/state/mod.rs` (`pub mod stats;`), `crates/mediagram-core/src/state/record.rs` (`is_day` re-export), `crates/mediagram-core/src/state/rows.rs` (`WatchedRow` reads from fixtures), `crates/mediagram-core/tests/shared_watch_state_fixtures.rs`

**Interfaces:** Produces (`uniffi::Record`/`Enum`; `SummaryInput`, `DayBar`, `HistoryEntry`, `HistoryKind` also `Deserialize`, for the fixtures — the `MergedProfile` precedent):
- `state::stats::summary::SummaryInput { today: String, titles: Vec<TitleStatRow>, days: Vec<DayStatRow>, watched: Vec<rows::WatchedRow> }` — `watched` reuses `rows::WatchedRow` (`{setId, finishedAt}` once it gains `serde(rename_all = "camelCase")`), exactly the contract's element shape.
- `StatsSummary { week_seconds: f64, month_seconds: f64, all_seconds: f64, last30: Vec<DayBar>, history: Vec<HistoryEntry> }`
- `DayBar { day: String, seconds: f64 }` — the contract leaves the `last30` element anonymous; `DayBar` is the name phase 04 already assumes (`phase-04-android-stats-rail-and-page.md` § Assumed generated Kotlin names).
- `HistoryEntry { kind: HistoryKind, set_id: String, at: i64, seconds: f64 }`, `enum HistoryKind { Started, Finished, Again }` (contract order; sort rank is a separate fn).
- `pub fn summarize(input: &SummaryInput) -> StatsSummary`.

Rules (contract §4): week = Monday of today's ISO week .. today; month = today's `YYYY-MM-` .. today; all = every day row (future ones too); `last30` = 30 entries oldest first ending today, zero-filled, each the sum over devices; history per set: started = min `startedAt`, again = max `againAt` if any, finished = live `watched.finishedAt` (also without stats rows, seconds 0); sort `at` desc, `setId` asc, kind finished < again < started. Sums start from `0.0` (`Iterator::sum` for `f64` starts at `-0.0`, verified on rustc 1.98). `today` not a date → week/month 0, `last30` empty, all and history still computed (contract silent; Kotlin always passes `LocalDate.now()`).

- [ ] **Step 1: Failing tests** — create `crates/mediagram-core/src/state/stats/summary_tests.rs`

```rust
use super::super::calendar::{day_name, day_number, monday_of};
use super::*;

fn day(day: &str, device: &str, seconds: f64) -> DayStatRow {
    DayStatRow { day: day.into(), device: device.into(), seconds, updated_at: 1.0 }
}

fn title(set_id: &str, device: &str, started_at: f64, again_at: Option<f64>, seconds: f64) -> TitleStatRow {
    TitleStatRow {
        set_id: set_id.into(),
        device: device.into(),
        started_at,
        last_watched_at: started_at,
        seconds,
        again_at,
        updated_at: 1.0,
    }
}

fn finished(set_id: &str, finished_at: i64) -> WatchedRow {
    WatchedRow { set_id: set_id.into(), finished_at }
}

fn input(today: &str, titles: Vec<TitleStatRow>, days: Vec<DayStatRow>, watched: Vec<WatchedRow>) -> SummaryInput {
    SummaryInput { today: today.into(), titles, days, watched }
}

#[test]
fn day_numbers_count_from_1970_and_name_back() {
    assert_eq!(day_number("1970-01-01"), Some(0));
    assert_eq!(day_number("2000-03-01"), Some(11_017));
    assert_eq!(day_number("2024-02-29"), Some(19_782));
    assert_eq!(day_number("2026-10-03"), Some(20_729));
    for number in -1_000..30_000 {
        assert_eq!(day_number(&day_name(number)), Some(number));
    }
    for bad in ["", "2026-13-01", "2026-00-10", "2026-10-32", "2026-1-03", "2026/10/03"] {
        assert_eq!(day_number(bad), None, "{bad:?}");
    }
}

#[test]
fn an_iso_week_runs_monday_to_sunday() {
    for day in ["2026-09-28", "2026-10-03", "2026-10-04"] {
        assert_eq!(day_name(monday_of(day_number(day).unwrap())), "2026-09-28", "{day}");
    }
}

/// Today is Saturday 3 October: the week began on Monday 28 September, the
/// month on the 1st, and a row dated tomorrow counts only in all.
#[test]
fn week_and_month_split_at_their_boundaries_and_the_future_counts_only_in_all() {
    let days = vec![
        day("2026-09-27", "laptop", 1.0),
        day("2026-09-28", "laptop", 2.0),
        day("2026-10-01", "laptop", 4.0),
        day("2026-10-03", "phone", 8.0),
        day("2026-10-03", "laptop", 16.0),
        day("2026-10-04", "laptop", 32.0),
    ];
    let summary = summarize(&input("2026-10-03", vec![], days, vec![]));
    assert_eq!((summary.week_seconds, summary.month_seconds, summary.all_seconds), (30.0, 28.0, 63.0));
    assert_eq!(summary.last30.len(), 30);
    assert_eq!(summary.last30[0].day, "2026-09-04");
    assert_eq!(summary.last30[29], DayBar { day: "2026-10-03".into(), seconds: 24.0 });
    assert_eq!(summary.last30[25], DayBar { day: "2026-09-29".into(), seconds: 0.0 });
}

#[test]
fn the_last_30_days_cross_a_year_boundary() {
    let summary = summarize(&input("2026-01-10", vec![], vec![day("2025-12-31", "laptop", 5.0)], vec![]));
    assert_eq!(summary.last30[0].day, "2025-12-12");
    assert_eq!(summary.last30[19], DayBar { day: "2025-12-31".into(), seconds: 5.0 });
    assert_eq!((summary.week_seconds, summary.month_seconds), (0.0, 0.0));
}

#[test]
fn history_is_newest_first_with_each_titles_total() {
    let titles = vec![
        title("01A", "laptop", 100.0, None, 600.0),
        title("01A", "phone", 50.0, Some(300.0), 120.0),
        title("01B", "laptop", 300.0, None, 60.0),
        title("01C", "laptop", 400.0, None, 0.0),
    ];
    let watched = vec![finished("01A", 200), finished("01OLD", 10), finished("01C", 400)];
    let summary = summarize(&input("2026-10-03", titles, vec![], watched));
    let lines: Vec<(HistoryKind, &str, i64, f64)> = summary
        .history
        .iter()
        .map(|line| (line.kind, line.set_id.as_str(), line.at, line.seconds))
        .collect();
    assert_eq!(
        lines,
        [
            (HistoryKind::Finished, "01C", 400, 0.0),
            (HistoryKind::Started, "01C", 400, 0.0),
            (HistoryKind::Again, "01A", 300, 720.0),
            (HistoryKind::Started, "01B", 300, 60.0),
            (HistoryKind::Finished, "01A", 200, 720.0),
            (HistoryKind::Started, "01A", 50, 720.0),
            (HistoryKind::Finished, "01OLD", 10, 0.0),
        ]
    );
}

#[test]
fn nothing_watched_is_thirty_zero_days_and_no_history() {
    let summary = summarize(&input("2026-10-03", vec![], vec![], vec![]));
    assert_eq!((summary.week_seconds, summary.month_seconds, summary.all_seconds), (0.0, 0.0, 0.0));
    assert!(!summary.all_seconds.is_sign_negative(), "a plain zero, not -0.0");
    assert!(summary.last30.iter().all(|day| day.seconds == 0.0) && summary.last30.len() == 30);
    assert!(summary.history.is_empty());
}

#[test]
fn a_today_that_is_not_a_date_still_sums_all_and_lists_history() {
    let summary = summarize(&input(
        "someday",
        vec![title("01A", "laptop", 1.0, None, 5.0)],
        vec![day("2026-10-03", "laptop", 5.0)],
        vec![],
    ));
    assert_eq!((summary.all_seconds, summary.week_seconds, summary.last30.len()), (5.0, 0.0, 0));
    assert_eq!(summary.history.len(), 1);
}
```

In `crates/mediagram-core/tests/shared_watch_state_fixtures.rs` add the import
```rust
use mediagram_core::state::stats::summary::{DayBar, HistoryEntry, SummaryInput, summarize};
```
and append:
```rust

/// The web compares only the keys a case names — one about the week need
/// not spell out thirty bars — and so does this.
#[derive(Deserialize)]
#[serde(rename_all = "camelCase", deny_unknown_fields)]
struct SummaryExpect {
    week_seconds: Option<f64>,
    month_seconds: Option<f64>,
    all_seconds: Option<f64>,
    last30: Option<Vec<DayBar>>,
    history: Option<Vec<HistoryEntry>>,
}

#[derive(Deserialize)]
struct SummaryCase {
    name: String,
    input: SummaryInput,
    expect: SummaryExpect,
}

#[test]
fn stats_summary_fixtures_match_the_web() {
    let Some(cases) = load::<SummaryCase>("stats-summary.json") else {
        return;
    };
    assert!(!cases.is_empty(), "stats-summary.json holds no cases");
    for case in cases {
        let got = summarize(&case.input);
        let (want, name) = (case.expect, case.name);
        if let Some(seconds) = want.week_seconds {
            assert_eq!(got.week_seconds, seconds, "case: {name} (weekSeconds)");
        }
        if let Some(seconds) = want.month_seconds {
            assert_eq!(got.month_seconds, seconds, "case: {name} (monthSeconds)");
        }
        if let Some(seconds) = want.all_seconds {
            assert_eq!(got.all_seconds, seconds, "case: {name} (allSeconds)");
        }
        if let Some(last30) = want.last30 {
            assert_eq!(got.last30, last30, "case: {name} (last30)");
        }
        if let Some(history) = want.history {
            assert_eq!(got.history, history, "case: {name} (history)");
        }
    }
}
```

- [ ] **Step 2: Run, expect a compile failure**

Run: `cargo test -p mediagram-core --test shared_watch_state_fixtures`
Expected: FAIL — `could not find 'stats' in 'state'`.

- [ ] **Step 3: Implement**

`crates/mediagram-core/src/state/stats.rs` (first version; Task 3.6 replaces it whole):

```rust
//! Viewing stats: how long each profile watched what, and when — recorded
//! by this device's own position writes, synced as rows each device owns,
//! and summed for the stats page. Pinned to the web by the `stats-*.json`
//! fixtures under `web/test/fixtures/watch-state/`.

mod calendar;
pub mod summary;
```

`crates/mediagram-core/src/state/stats/calendar.rs` (whole file; algorithm cross-checked against Python `datetime` for every 37th day from 0001 to 9999):

```rust
//! Dates as day numbers, without a date library: the core gets its "today"
//! from Kotlin as `YYYY-MM-DD` and only ever needs to count days back from
//! it and name them again. Proleptic Gregorian, after Howard Hinnant's
//! `days_from_civil` and `civil_from_days`.

use crate::state::record::is_day;

/// Days since 1970-01-01, or `None` for anything that is not `YYYY-MM-DD`
/// with a month 1–12 and a day 1–31.
pub(super) fn day_number(day: &str) -> Option<i64> {
    if !is_day(day) {
        return None;
    }
    let year: i64 = day[0..4].parse().ok()?;
    let month: i64 = day[5..7].parse().ok()?;
    let date: i64 = day[8..10].parse().ok()?;
    if !(1..=12).contains(&month) || !(1..=31).contains(&date) {
        return None;
    }
    // Years start in March here, so a leap day ends one rather than sits in it.
    let year = if month <= 2 { year - 1 } else { year };
    let era = year.div_euclid(400);
    let year_of_era = year - era * 400;
    let day_of_year = (153 * ((month + 9) % 12) + 2) / 5 + date - 1;
    let day_of_era = year_of_era * 365 + year_of_era / 4 - year_of_era / 100 + day_of_year;
    Some(era * 146_097 + day_of_era - 719_468)
}

/// `YYYY-MM-DD` for a day number — `day_number`'s inverse.
pub(super) fn day_name(number: i64) -> String {
    let shifted = number + 719_468;
    let era = shifted.div_euclid(146_097);
    let day_of_era = shifted - era * 146_097;
    let year_of_era =
        (day_of_era - day_of_era / 1_460 + day_of_era / 36_524 - day_of_era / 146_096) / 365;
    let day_of_year = day_of_era - (365 * year_of_era + year_of_era / 4 - year_of_era / 100);
    let march_based = (5 * day_of_year + 2) / 153;
    let date = day_of_year - (153 * march_based + 2) / 5 + 1;
    let month = if march_based < 10 { march_based + 3 } else { march_based - 9 };
    let year = year_of_era + era * 400 + i64::from(month <= 2);
    format!("{year:04}-{month:02}-{date:02}")
}

/// Monday of the ISO week `number` falls in. 1970-01-01 was a Thursday.
pub(super) fn monday_of(number: i64) -> i64 {
    number - (number + 3).rem_euclid(7)
}
```

`crates/mediagram-core/src/state/stats/summary.rs` (whole file):

```rust
//! The stats page's numbers: watch time this week, this month and in all,
//! the last 30 days, and the history — summed over every device's rows.
//! Pure, and pinned to the web by `stats-summary.json`.

use std::collections::HashMap;

use serde::Deserialize;

use super::calendar::{day_name, day_number, monday_of};
use crate::state::record::{DayStatRow, TitleStatRow};
use crate::state::rows::WatchedRow;

/// One profile's stats rows from every device, its live `watched` marks,
/// and `today` — the reading device's local date, `YYYY-MM-DD`.
#[derive(Debug, Deserialize)]
pub struct SummaryInput {
    pub today: String,
    pub titles: Vec<TitleStatRow>,
    pub days: Vec<DayStatRow>,
    pub watched: Vec<WatchedRow>,
}

#[derive(Debug, Clone, Default, PartialEq, uniffi::Record)]
pub struct StatsSummary {
    /// Monday of today's ISO week through today.
    pub week_seconds: f64,
    /// The first of today's month through today.
    pub month_seconds: f64,
    /// Every day row, one dated after today included.
    pub all_seconds: f64,
    /// 30 days, oldest first, ending today; a day nothing was watched is 0.
    pub last30: Vec<DayBar>,
    /// Newest first.
    pub history: Vec<HistoryEntry>,
}

/// One bar of the last 30 days: every device's seconds on `day`.
#[derive(Debug, Clone, PartialEq, Deserialize, uniffi::Record)]
pub struct DayBar {
    pub day: String,
    pub seconds: f64,
}

/// One history line. `seconds` is the title's total across devices — the
/// same on each of its lines, and 0 for a title finished before stats.
#[derive(Debug, Clone, PartialEq, Deserialize, uniffi::Record)]
#[serde(rename_all = "camelCase")]
pub struct HistoryEntry {
    pub kind: HistoryKind,
    pub set_id: String,
    /// Epoch milliseconds.
    pub at: i64,
    pub seconds: f64,
}

/// First played, watched to the end, or started over after that.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize, uniffi::Enum)]
#[serde(rename_all = "lowercase")]
pub enum HistoryKind {
    Started,
    Finished,
    Again,
}

pub fn summarize(input: &SummaryInput) -> StatsSummary {
    let all_seconds = total(input.days.iter());
    let history = history(input);
    let Some(today) = day_number(&input.today) else {
        // Nothing to count back from: only what needs no calendar.
        return StatsSummary { all_seconds, history, ..StatsSummary::default() };
    };
    let today_name = input.today.as_str();
    let monday = day_name(monday_of(today));
    let month = &today_name[..8];
    let so_far = || input.days.iter().filter(|row| row.day.as_str() <= today_name);
    let week_seconds = total(so_far().filter(|row| row.day >= monday));
    let month_seconds = total(so_far().filter(|row| row.day.starts_with(month)));
    let mut by_day: HashMap<&str, f64> = HashMap::new();
    for row in &input.days {
        *by_day.entry(row.day.as_str()).or_insert(0.0) += row.seconds;
    }
    let last30 = (today - 29..=today)
        .map(day_name)
        .map(|day| DayBar { seconds: by_day.get(day.as_str()).copied().unwrap_or(0.0), day })
        .collect();
    StatsSummary { week_seconds, month_seconds, all_seconds, last30, history }
}

/// Summed from a plain 0: `Sum` for floats starts at -0.0, which would
/// reach Kotlin as a negative zero for a profile with nothing watched.
fn total<'a>(rows: impl Iterator<Item = &'a DayStatRow>) -> f64 {
    rows.fold(0.0, |sum, row| sum + row.seconds)
}

/// One title's rows from every device, folded.
struct Title {
    started: f64,
    again: Option<f64>,
    seconds: f64,
}

/// Per title: started at its earliest `startedAt`, started over at its
/// latest `againAt`, finished at its live `watched` mark — that one also
/// for a title with no stats rows, since a finish from before stats existed
/// is real history.
fn history(input: &SummaryInput) -> Vec<HistoryEntry> {
    let mut titles: HashMap<&str, Title> = HashMap::new();
    for row in &input.titles {
        let title = titles.entry(row.set_id.as_str()).or_insert(Title {
            started: row.started_at,
            again: None,
            seconds: 0.0,
        });
        title.started = title.started.min(row.started_at);
        if let Some(again) = row.again_at {
            title.again = Some(title.again.map_or(again, |held| held.max(again)));
        }
        title.seconds += row.seconds;
    }
    let entry = |kind, set_id: &str, at: i64| HistoryEntry {
        kind,
        set_id: set_id.to_string(),
        at,
        seconds: titles.get(set_id).map_or(0.0, |title| title.seconds),
    };
    let mut lines = Vec::new();
    for (set_id, title) in &titles {
        lines.push(entry(HistoryKind::Started, set_id, title.started as i64));
        if let Some(again) = title.again {
            lines.push(entry(HistoryKind::Again, set_id, again as i64));
        }
    }
    for row in &input.watched {
        lines.push(entry(HistoryKind::Finished, &row.set_id, row.finished_at));
    }
    lines.sort_by(|a, b| {
        b.at.cmp(&a.at)
            .then_with(|| a.set_id.cmp(&b.set_id))
            .then_with(|| rank(a.kind).cmp(&rank(b.kind)))
    });
    lines
}

/// At one moment on one title: finished, then started over, then started.
fn rank(kind: HistoryKind) -> u8 {
    match kind {
        HistoryKind::Finished => 0,
        HistoryKind::Again => 1,
        HistoryKind::Started => 2,
    }
}

#[cfg(test)]
#[path = "summary_tests.rs"]
mod tests;
```

`crates/mediagram-core/src/state/mod.rs` — after `mod schema;` (`:29`) add `pub mod stats;`.

`crates/mediagram-core/src/state/record.rs` — after `mod stats_record;` add `pub(crate) use stats_record::is_day;` (first used by `calendar.rs` now; added earlier it would be an unused import).

`crates/mediagram-core/src/state/rows.rs` — `WatchedRow` (`:23-28`):
```diff
 /// One title a profile watched to the end, and when.
-#[derive(Debug, Clone, PartialEq, uniffi::Record)]
+#[derive(Debug, Clone, PartialEq, serde::Deserialize, uniffi::Record)]
+#[serde(rename_all = "camelCase")]
 pub struct WatchedRow {
```

- [ ] **Step 4: Run, expect PASS**

Run: `cargo test -p mediagram-core --lib state::stats::summary` and `cargo test -p mediagram-core --test shared_watch_state_fixtures`
Expected: `7 passed`; fixtures `6 passed`, no `skipping:` for `stats-summary.json`.

- [ ] **Step 5: Commit**

```bash
git add crates/mediagram-core/src/state/stats.rs crates/mediagram-core/src/state/stats crates/mediagram-core/src/state/mod.rs crates/mediagram-core/src/state/record.rs crates/mediagram-core/src/state/rows.rs crates/mediagram-core/tests/shared_watch_state_fixtures.rs
git commit -m "feat(core): summarize viewing stats into week, month, 30 days and history"
```

## Task 3.5: Export and import — every device's rows, newer-or-missing in

**Files:**
- Create: `crates/mediagram-core/src/state/stats/exchange.rs`, `crates/mediagram-core/src/state/stats/exchange_tests.rs`
- Modify: `crates/mediagram-core/src/state/stats.rs` (`pub(crate) mod exchange;`), `crates/mediagram-core/src/state/exchange.rs` (replace the Task 3.2 empty vectors; one `import` line)

**Interfaces:** Produces `pub(crate) fn stats::exchange::export(conn, profile_id) -> rusqlite::Result<(Vec<TitleStatRow>, Vec<DayStatRow>)>` (every device's rows, `ORDER BY set_id, device` / `day, device`, so an unchanged store exports an unchanged body — `sync.rs:153-172` compares bodies) and `pub(crate) fn stats::exchange::import(conn, profile_id, &[TitleStatRow], &[DayStatRow]) -> rusqlite::Result<u64>` (one upsert per row, `DO UPDATE … WHERE excluded.updated_at > table.updated_at` — the `set_watchlisted` precedent `rows.rs:150-154`; never deletes; this device's own rows too, contract §3). Consumed by `export_record`/`import_merged` and by `Core::stats` (Task 3.7). Gossip is intended: rows are keyed by device, so re-exporting another device's rows cannot double-count.

- [ ] **Step 1: Failing tests** — create `crates/mediagram-core/src/state/stats/exchange_tests.rs`

```rust
use super::*;
use crate::state::StateDb;
use crate::state::exchange::{export_record, import_merged};
use crate::state::merge::{MergedProfile, MergedState};
use crate::state::{profiles, sync};

fn db() -> (tempfile::TempDir, StateDb) {
    let dir = tempfile::tempdir().unwrap();
    let db = StateDb::new(dir.path().to_path_buf());
    (dir, db)
}

fn profile(db: &StateDb) -> String {
    db.with(|conn| profiles::create(conn, "André", false))
        .unwrap()
        .unwrap()
        .id
}

fn title(set_id: &str, device: &str, seconds: f64, updated_at: f64) -> TitleStatRow {
    TitleStatRow {
        set_id: set_id.into(),
        device: device.into(),
        started_at: 1.0,
        last_watched_at: updated_at,
        seconds,
        again_at: None,
        updated_at,
    }
}

fn day(device: &str, seconds: f64, updated_at: f64) -> DayStatRow {
    DayStatRow { day: "2026-10-03".into(), device: device.into(), seconds, updated_at }
}

/// `import_merged` of one viewer carrying only these stats rows.
fn import(db: &StateDb, title_stats: Vec<TitleStatRow>, day_stats: Vec<DayStatRow>) -> u64 {
    let merged = MergedState {
        profiles: vec![MergedProfile {
            name: "andré".into(),
            display_name: "André".into(),
            title_stats,
            day_stats,
            ..Default::default()
        }],
        ..Default::default()
    };
    db.with(|conn| import_merged(conn, &merged)).unwrap()
}

#[test]
fn a_merged_row_is_taken_only_when_newer_or_missing() {
    let (_dir, db) = db();
    let id = profile(&db);
    let rows = |seconds, at| (vec![title("01A", "phone", seconds, at)], vec![day("phone", seconds, at)]);

    let (titles, days) = rows(300.0, 20.0);
    assert_eq!(import(&db, titles, days), 2);
    let (titles, days) = rows(300.0, 20.0);
    assert_eq!(import(&db, titles, days), 0, "the same rows again change nothing");
    let (titles, days) = rows(100.0, 10.0);
    assert_eq!(import(&db, titles, days), 0, "an older copy never wins");
    let (titles, days) = rows(600.0, 30.0);
    assert_eq!(import(&db, titles, days), 2);

    assert_eq!(db.with(|conn| export(conn, &id)).unwrap(), rows(600.0, 30.0));
}

/// Every device's rows go out, not only this one's, and always in the same
/// order, so an unchanged store sends an unchanged document.
#[test]
fn export_carries_every_devices_rows_in_a_fixed_order() {
    let (_dir, db) = db();
    let id = profile(&db);
    import(
        &db,
        vec![title("01B", "phone", 1.0, 5.0), title("01A", "tv", 2.0, 5.0), title("01A", "phone", 3.0, 5.0)],
        vec![day("tv", 4.0, 5.0), day("phone", 5.0, 5.0)],
    );

    let (titles, days) = db.with(|conn| export(conn, &id)).unwrap();
    let keys: Vec<(&str, &str)> = titles.iter().map(|row| (row.set_id.as_str(), row.device.as_str())).collect();
    assert_eq!(keys, [("01A", "phone"), ("01A", "tv"), ("01B", "phone")]);
    assert_eq!(days.iter().map(|row| row.device.as_str()).collect::<Vec<_>>(), ["phone", "tv"]);

    let record = db.with(|conn| export_record(conn, "laptop")).unwrap();
    assert_eq!(record.profiles[0].title_stats, titles);
    assert_eq!(record.profiles[0].day_stats, days);
}

/// A reinstall that kept its device id gets its own minutes back.
#[test]
fn this_devices_own_newer_row_comes_back_too() {
    let (_dir, db) = db();
    let id = profile(&db);
    let own = db.with(sync::device_id).unwrap();
    import(&db, vec![title("01A", &own, 900.0, 20.0)], vec![]);
    assert_eq!(db.with(|conn| export(conn, &id)).unwrap().0[0].seconds, 900.0);
}

/// Every document a device without stats writes stays byte-identical.
#[test]
fn a_store_without_stats_exports_a_document_without_the_keys() {
    let (_dir, db) = db();
    profile(&db);
    let record = db.with(|conn| export_record(conn, "laptop")).unwrap();
    let body = serde_json::to_string(&record).unwrap();
    assert!(!body.contains("titleStats") && !body.contains("dayStats"), "{body}");
}
```

- [ ] **Step 2: Run, expect a compile failure**

Add `pub(crate) mod exchange;` to `state/stats.rs` (between `mod calendar;` and `pub mod summary;`). Run: `cargo test -p mediagram-core --lib state::stats::exchange`
Expected: FAIL — `file not found for module 'exchange'`.

- [ ] **Step 3: Implement** — `crates/mediagram-core/src/state/stats/exchange.rs` (whole file):

```rust
//! Viewing stats on the sync record: every row of a profile out — every
//! device's, not only this one's, so a device that goes away keeps its
//! minutes — and the merged rows back in.
//!
//! Corrective, never wholesale, like the rest of `import_merged`: a row is
//! taken only when it is newer than the local one or missing here, and
//! nothing is ever deleted. Nothing here records watch time.

use rusqlite::{Connection, params};

use crate::state::record::{DayStatRow, TitleStatRow};

/// Both tables' rows for `profile_id`, every device's, in a fixed order so
/// an unchanged store exports an unchanged document.
pub(crate) fn export(
    conn: &Connection,
    profile_id: &str,
) -> rusqlite::Result<(Vec<TitleStatRow>, Vec<DayStatRow>)> {
    let mut title_rows = conn.prepare(
        "SELECT set_id, device, started_at, last_watched_at, seconds, again_at, updated_at
           FROM stats_titles WHERE profile_id = ?1 ORDER BY set_id, device",
    )?;
    let titles = title_rows
        .query_map([profile_id], |row| {
            Ok(TitleStatRow {
                set_id: row.get(0)?,
                device: row.get(1)?,
                started_at: row.get::<_, i64>(2)? as f64,
                last_watched_at: row.get::<_, i64>(3)? as f64,
                seconds: row.get(4)?,
                again_at: row.get::<_, Option<i64>>(5)?.map(|at| at as f64),
                updated_at: row.get::<_, i64>(6)? as f64,
            })
        })?
        .collect::<rusqlite::Result<Vec<_>>>()?;
    let mut day_rows = conn.prepare(
        "SELECT day, device, seconds, updated_at
           FROM stats_days WHERE profile_id = ?1 ORDER BY day, device",
    )?;
    let days = day_rows
        .query_map([profile_id], |row| {
            Ok(DayStatRow {
                day: row.get(0)?,
                device: row.get(1)?,
                seconds: row.get(2)?,
                updated_at: row.get::<_, i64>(3)? as f64,
            })
        })?
        .collect::<rusqlite::Result<Vec<_>>>()?;
    Ok((titles, days))
}

/// Takes in every merged row newer than the local one for its key, or
/// missing here — this device's own rows too: a reinstall that kept its
/// device id gets its minutes back. Returns how many rows changed.
pub(crate) fn import(
    conn: &Connection,
    profile_id: &str,
    titles: &[TitleStatRow],
    days: &[DayStatRow],
) -> rusqlite::Result<u64> {
    let mut changed = 0u64;
    for row in titles {
        changed += conn.execute(
            "INSERT INTO stats_titles(profile_id, set_id, device, started_at, last_watched_at, seconds, again_at, updated_at)
               VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)
               ON CONFLICT(profile_id, set_id, device) DO UPDATE SET
                 started_at = excluded.started_at,
                 last_watched_at = excluded.last_watched_at,
                 seconds = excluded.seconds,
                 again_at = excluded.again_at,
                 updated_at = excluded.updated_at
               WHERE excluded.updated_at > stats_titles.updated_at",
            params![
                profile_id,
                row.set_id,
                row.device,
                row.started_at as i64,
                row.last_watched_at as i64,
                row.seconds,
                row.again_at.map(|at| at as i64),
                row.updated_at as i64
            ],
        )? as u64;
    }
    for row in days {
        changed += conn.execute(
            "INSERT INTO stats_days(profile_id, day, device, seconds, updated_at) VALUES (?1, ?2, ?3, ?4, ?5)
               ON CONFLICT(profile_id, day, device) DO UPDATE SET
                 seconds = excluded.seconds,
                 updated_at = excluded.updated_at
               WHERE excluded.updated_at > stats_days.updated_at",
            params![profile_id, row.day, row.device, row.seconds, row.updated_at as i64],
        )? as u64;
    }
    Ok(changed)
}

#[cfg(test)]
#[path = "exchange_tests.rs"]
mod tests;
```

`crates/mediagram-core/src/state/exchange.rs`:
```diff
 use super::rows;
+use super::stats;
 use super::watched_exchange;
@@ export_record
         let preferences = preferences_exchange::export_preferences(conn, &profile.id)?;
+        let (title_stats, day_stats) = stats::exchange::export(conn, &profile.id)?;
         profiles.push(ProfileState {
@@
             preferences,
-            title_stats: Vec::new(),
-            day_stats: Vec::new(),
+            title_stats,
+            day_stats,
         });
@@ import_merged
         changed +=
             preferences_exchange::import_preferences(conn, &profile_id, &profile.preferences)?;
+        changed +=
+            stats::exchange::import(conn, &profile_id, &profile.title_stats, &profile.day_stats)?;
     }
```

- [ ] **Step 4: Run, expect PASS**

Run: `cargo test -p mediagram-core --lib state::`
Expected: all pass — 4 in `state::stats::exchange`, every existing `exchange_tests`/`sync_tests` case unchanged (no store in them has stats rows, so their documents are byte-identical).

- [ ] **Step 5: Commit**

```bash
git add crates/mediagram-core/src/state/stats.rs crates/mediagram-core/src/state/stats crates/mediagram-core/src/state/exchange.rs
git commit -m "feat(core): sync every device's viewing stats rows, newer rows win"
```

## Task 3.6: Recording on this device's own position writes

**Files:**
- Modify: `crates/mediagram-core/src/state/stats.rs` (replace whole), `crates/mediagram-core/src/state/mod.rs` (the `ticks` field: 195 → 199 lines), `crates/mediagram-core/src/state/rows.rs` (doc line), `crates/mediagram-core/src/api/state.rs` (`set_progress`, `set_watched`), `crates/mediagram-core/tests/api_surface.rs` (5 calls), `crates/mediagram-core/tests/shared_watch_state_fixtures.rs`
- Create: `crates/mediagram-core/src/state/stats_tests.rs`

**Interfaces:**
- `pub const STEP_CAP_SECONDS: f64 = 15.0`; `pub struct Tick { pub at: f64, pub wall_ms: i64 }` (`Deserialize`, camelCase — the step fixture's `{at, wallMs}`); `pub fn step_seconds(prev: Option<&Tick>, at: f64, now_ms: i64) -> f64`; `pub fn again_now(had_progress: bool, watched_live: bool) -> bool` (web `againNow`, pinned by the same fixture).
- `pub(crate) type Ticks = HashMap<(String, String), Tick>` held in `StateDb.ticks: Mutex<Ticks>`. Lifetime verified: `StateDb::new` is called once per `Core` (`api/mod.rs:78`), and Kotlin keeps one `Core` for the app's life, so the map lives exactly as long as the process. A retired/replaced `Core` starts empty, which counts 0 for one write.
- `StateDb::set_progress_counted(&self, profile_id, set_id, at, duration, local_day, now_ms) -> Option<()>` (own rows stamp `updated_at = MAX(now, stored + 1)`, contract §1) and `StateDb::forget_tick(&self, profile_id, set_id)`. Lock order is always `conn` then `ticks`; `forget_tick` takes only `ticks`.
- uniffi (Kotlin sees it after Task 3.8): `Core::set_progress(profile_id, set_id, at, duration, local_day: String)`; `Core::set_watched(…, true)` forgets the tick first.
- **`rows::set_progress` is unchanged and records nothing** (29 callers, none edited: `api/state.rs:119` — switched to the counted write below — plus 4 in `rows_tests.rs`, 4 in `exchange_tests.rs`, 20 in `sync_tests.rs`). The counted write wraps it in one transaction. Import (`exchange.rs:109` `import_progress`) never went through it, so imports cannot record by construction. The 5 `Core::set_progress` calls in `tests/api_surface.rs` gain the day argument.

- [ ] **Step 1: Failing tests** — create `crates/mediagram-core/src/state/stats_tests.rs`

```rust
use super::*;
use crate::state::exchange::import_merged;
use crate::state::merge::{MergedProfile, MergedState};
use crate::state::profiles::{self, now_ms};
use crate::state::record::{DayStatRow, ProgressRow, TitleStatRow};

const DAY: &str = "2026-10-03";

fn db() -> (tempfile::TempDir, StateDb) {
    let dir = tempfile::tempdir().unwrap();
    let db = StateDb::new(dir.path().to_path_buf());
    (dir, db)
}

fn profile(db: &StateDb) -> String {
    db.with(|conn| profiles::create(conn, "André", false))
        .unwrap()
        .unwrap()
        .id
}

/// A position write of `01A`, `secs` seconds of wall time after `t0`.
fn play(db: &StateDb, id: &str, at: f64, t0: i64, secs: i64, day: &str) {
    db.set_progress_counted(id, "01A", at, Some(5400.0), day, t0 + secs * 1000)
        .unwrap();
}

/// This device's `01A` row: seconds, started, last watched, started over.
fn title(db: &StateDb, id: &str) -> (f64, i64, i64, Option<i64>) {
    db.with(|conn| {
        conn.query_row(
            "SELECT seconds, started_at, last_watched_at, again_at FROM stats_titles
               WHERE profile_id = ?1 AND set_id = '01A'",
            [id],
            |row| Ok((row.get(0)?, row.get(1)?, row.get(2)?, row.get(3)?)),
        )
    })
    .unwrap()
}

fn days(db: &StateDb, id: &str) -> Vec<(String, f64)> {
    db.with(|conn| {
        let mut stmt =
            conn.prepare("SELECT day, seconds FROM stats_days WHERE profile_id = ?1 ORDER BY day")?;
        let rows = stmt.query_map([id], |row| Ok((row.get(0)?, row.get(1)?)))?;
        rows.collect()
    })
    .unwrap()
}

#[test]
fn a_step_counts_the_smaller_advance_capped_and_nothing_backwards() {
    let prev = Tick { at: 100.0, wall_ms: 1_000_000 };
    for (at, now_ms, expect, why) in [
        (110.0, 1_010_000, 10.0, "steady playback"),
        (110.0, 1_300_000, 10.0, "a pause counts only the time after resuming"),
        (700.0, 1_010_000, 10.0, "a seek forward counts the wall time it took"),
        (120.0, 1_010_000, 10.0, "2× speed counts wall time"),
        (160.0, 1_060_000, 15.0, "a long gap is capped"),
        (50.0, 1_010_000, 0.0, "a seek back counts nothing"),
        (110.0, 1_000_000, 0.0, "no wall time passed"),
        (110.0, 990_000, 0.0, "the clock went back"),
        (f64::NAN, 1_010_000, 0.0, "a broken position counts nothing"),
    ] {
        assert_eq!(step_seconds(Some(&prev), at, now_ms), expect, "{why}");
    }
    assert_eq!(step_seconds(None, 110.0, 1_010_000), 0.0, "a first write counts nothing");
}

#[test]
fn only_a_finished_title_with_no_position_here_is_started_over() {
    assert!(again_now(false, true));
    assert!(!again_now(true, true) && !again_now(false, false) && !again_now(true, false));
}

#[test]
fn the_first_write_of_a_title_starts_it_and_counts_nothing() {
    let (_dir, db) = db();
    let id = profile(&db);
    let t0 = now_ms();
    play(&db, &id, 600.0, t0, 0, DAY);
    assert_eq!(title(&db, &id), (0.0, t0, t0, None));
    assert!(days(&db, &id).is_empty(), "no step, no day row");
    let device: String = db
        .with(|conn| conn.query_row("SELECT device FROM stats_titles", [], |row| row.get(0)))
        .unwrap();
    assert_eq!(Some(device), db.with(sync::device_id), "rows name the device that wrote them");
}

#[test]
fn steady_playback_adds_wall_time_to_the_title_and_its_day() {
    let (_dir, db) = db();
    let id = profile(&db);
    let t0 = now_ms();
    for (at, secs) in [(0.0, 0), (10.0, 10), (20.0, 20)] {
        play(&db, &id, at, t0, secs, DAY);
    }
    assert_eq!(title(&db, &id), (20.0, t0, t0 + 20_000, None));
    assert_eq!(days(&db, &id), [(DAY.to_string(), 20.0)]);
    let position = db.with(|conn| rows::progress_for(conn, &id)).unwrap();
    assert_eq!(position[0].at, 20.0, "the position itself landed too");
}

#[test]
fn a_step_across_midnight_counts_on_the_day_of_the_write_that_ends_it() {
    let (_dir, db) = db();
    let id = profile(&db);
    let t0 = now_ms();
    play(&db, &id, 0.0, t0, 0, "2026-10-03");
    play(&db, &id, 10.0, t0, 10, "2026-10-04");
    assert_eq!(days(&db, &id), [("2026-10-04".to_string(), 10.0)]);
}

#[test]
fn a_restarted_process_counts_nothing_for_its_first_write() {
    let dir = tempfile::tempdir().unwrap();
    let t0 = now_ms();
    let id = {
        let db = StateDb::new(dir.path().to_path_buf());
        let id = profile(&db);
        play(&db, &id, 0.0, t0, 0, DAY);
        play(&db, &id, 10.0, t0, 10, DAY);
        id
    };
    let db = StateDb::new(dir.path().to_path_buf());
    play(&db, &id, 20.0, t0, 20, DAY);
    assert_eq!(title(&db, &id).0, 10.0, "no tick survives a restart to measure from");
    play(&db, &id, 30.0, t0, 30, DAY);
    assert_eq!(title(&db, &id), (20.0, t0, t0 + 30_000, None), "started once, not again");
    assert_eq!(days(&db, &id), [(DAY.to_string(), 20.0)]);
}

#[test]
fn starting_a_finished_title_over_is_watched_again_once() {
    let (_dir, db) = db();
    let id = profile(&db);
    let t0 = now_ms();
    play(&db, &id, 0.0, t0, 0, DAY);
    play(&db, &id, 10.0, t0, 10, DAY);
    db.with(|conn| rows::set_watched(conn, &id, "01A", true)).unwrap();
    db.forget_tick(&id, "01A");
    play(&db, &id, 20.0, t0, 20, DAY);
    assert_eq!(title(&db, &id).0, 10.0, "a finished title's next play is measured from nothing");
    play(&db, &id, 30.0, t0, 30, DAY);
    let (seconds, started, _, again) = title(&db, &id);
    assert_eq!((seconds, started), (20.0, t0), "started over, not started anew");
    assert_eq!(again, Some(t0 + 20_000), "only the first write after finishing starts it over");
}

/// Finished on another device (or before stats), never played here: its
/// first play here is already a start-over.
#[test]
fn a_title_finished_before_it_played_here_starts_as_watched_again() {
    let (_dir, db) = db();
    let id = profile(&db);
    db.with(|conn| rows::set_watched(conn, &id, "01A", true)).unwrap();
    let t0 = now_ms();
    play(&db, &id, 0.0, t0, 0, DAY);
    assert_eq!(title(&db, &id), (0.0, t0, t0, Some(t0)));
}

#[test]
fn a_title_taken_back_from_finished_is_not_watched_again() {
    let (_dir, db) = db();
    let id = profile(&db);
    db.with(|conn| rows::set_watched(conn, &id, "01A", true)).unwrap();
    db.with(|conn| rows::set_watched(conn, &id, "01A", false)).unwrap();
    play(&db, &id, 0.0, now_ms(), 0, DAY);
    assert_eq!(title(&db, &id).3, None);
}

/// One transaction: when the day row cannot be written, the position and
/// the title row are not either.
#[test]
fn a_position_and_its_watch_time_land_together_or_not_at_all() {
    let (_dir, db) = db();
    let id = profile(&db);
    let t0 = now_ms();
    play(&db, &id, 0.0, t0, 0, DAY);
    db.with(|conn| {
        conn.execute_batch(
            "CREATE TRIGGER refuse_day BEFORE INSERT ON stats_days
             BEGIN SELECT RAISE(ABORT, 'cannot count'); END;",
        )
    })
    .unwrap();

    assert!(db.set_progress_counted(&id, "01A", 10.0, None, DAY, t0 + 10_000).is_none());

    assert_eq!(db.with(|conn| rows::progress_for(conn, &id)).unwrap()[0].at, 0.0);
    assert_eq!(title(&db, &id).0, 0.0);
}

/// Positions merged in from other devices are never this device's viewing:
/// importing one writes no stats row and leaves no tick to measure from.
#[test]
fn importing_a_merge_records_no_watch_time() {
    let (_dir, db) = db();
    let id = profile(&db);
    let t0 = now_ms();
    let position = ProgressRow { set_id: "01A".into(), at: 100.0, duration: None, updated_at: t0 as f64 };
    let merged = MergedState {
        profiles: vec![MergedProfile {
            name: "andré".into(),
            display_name: "André".into(),
            progress: vec![position],
            ..Default::default()
        }],
        ..Default::default()
    };
    db.with(|conn| import_merged(conn, &merged)).unwrap();
    let counted: i64 = db
        .with(|conn| conn.query_row("SELECT COUNT(*) FROM stats_titles", [], |row| row.get(0)))
        .unwrap();
    assert_eq!(counted, 0, "an imported position writes no stats row");

    play(&db, &id, 110.0, t0, 10, DAY);
    assert_eq!(title(&db, &id).0, 0.0, "nor leaves a tick for the next own write");
    assert!(days(&db, &id).is_empty());
}

/// This device's rows came back from a reinstall stamped from ahead of its
/// clock: its next writes still add to them and stamp past them, or the
/// next import would hand the older copy back.
#[test]
fn this_devices_stamps_never_move_backwards() {
    let (_dir, db) = db();
    let id = profile(&db);
    let t0 = now_ms();
    let own = db.with(sync::device_id).unwrap();
    let ahead = (t0 + 1_000_000) as f64;
    let merged = MergedState {
        profiles: vec![MergedProfile {
            name: "andré".into(),
            display_name: "André".into(),
            title_stats: vec![TitleStatRow {
                set_id: "01A".into(),
                device: own.clone(),
                started_at: t0 as f64,
                last_watched_at: ahead,
                seconds: 100.0,
                again_at: None,
                updated_at: ahead,
            }],
            day_stats: vec![DayStatRow { day: DAY.into(), device: own, seconds: 100.0, updated_at: ahead }],
            ..Default::default()
        }],
        ..Default::default()
    };
    db.with(|conn| import_merged(conn, &merged)).unwrap();

    play(&db, &id, 0.0, t0, 0, DAY);
    play(&db, &id, 10.0, t0, 10, DAY);

    let stamps: (f64, f64, i64, i64) = db
        .with(|conn| {
            conn.query_row(
                "SELECT t.seconds, d.seconds, t.updated_at, d.updated_at
                   FROM stats_titles t JOIN stats_days d USING (profile_id, device)",
                [],
                |row| Ok((row.get(0)?, row.get(1)?, row.get(2)?, row.get(3)?)),
            )
        })
        .unwrap();
    assert_eq!(stamps, (110.0, 110.0, t0 + 1_000_002, t0 + 1_000_001));
}
```

In `crates/mediagram-core/tests/shared_watch_state_fixtures.rs` add the import
```rust
use mediagram_core::state::stats::{Tick, again_now, step_seconds};
```
and append (the fixture is `{name, fn, args, expect}`, dispatched by `fn` like the web's runner):
```rust

#[derive(Deserialize)]
struct StepCase {
    name: String,
    #[serde(rename = "fn")]
    function: String,
    args: Vec<serde_json::Value>,
    expect: serde_json::Value,
}

#[test]
fn stats_step_fixtures_match_the_web() {
    let Some(cases) = load::<StepCase>("stats-step.json") else {
        return;
    };
    assert!(!cases.is_empty(), "stats-step.json holds no cases");
    for case in cases {
        let (args, name) = (&case.args, &case.name);
        match case.function.as_str() {
            "stepSeconds" => {
                let prev: Option<Tick> = serde_json::from_value(args[0].clone()).unwrap();
                let (at, now_ms) = (args[1].as_f64().unwrap(), args[2].as_i64().unwrap());
                let counted = step_seconds(prev.as_ref(), at, now_ms);
                assert_eq!(Some(counted), case.expect.as_f64(), "stepSeconds: {name}");
            }
            "againNow" => {
                let again = again_now(args[0].as_bool().unwrap(), args[1].as_bool().unwrap());
                assert_eq!(Some(again), case.expect.as_bool(), "againNow: {name}");
            }
            other => panic!("stats-step.json names an unknown fn {other:?} in {name}"),
        }
    }
}
```

- [ ] **Step 2: Run, expect a compile failure**

Add `#[cfg(test)] #[path = "stats_tests.rs"] mod tests;` at the end of `state/stats.rs`. Run: `cargo test -p mediagram-core --lib state::stats::tests`
Expected: FAIL — `no method named 'set_progress_counted' found for reference '&StateDb'`, `cannot find type 'Tick'`, `cannot find function 'step_seconds'` / `'again_now'`.

- [ ] **Step 3: Implement**

`crates/mediagram-core/src/state/stats.rs` (whole file, final):

```rust
//! Viewing stats: how long each profile watched what, and when — recorded
//! by this device's own position writes, synced as rows each device owns,
//! and summed for the stats page. Pinned to the web by the `stats-*.json`
//! fixtures under `web/test/fixtures/watch-state/`.
//!
//! This file is the recording half: the step rule, the in-memory last tick
//! it measures from, and the title and day rows a write adds to. Rows of
//! other devices arrive only through `exchange`, and a position merged in
//! from another device is never recorded — it was not watched here.

use std::collections::HashMap;

use rusqlite::{Connection, OptionalExtension, params};

use super::{StateDb, rows, sync};

mod calendar;
pub(crate) mod exchange;
pub mod summary;

/// The most one step counts: 1.5 × the player's 10 s save tick. A longer
/// gap between two writes was a pause, a seek or a sleep, not watching.
pub const STEP_CAP_SECONDS: f64 = 15.0;

/// A title's last own position write: where it was, and when.
#[derive(Debug, Clone, Copy, PartialEq, serde::Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Tick {
    pub at: f64,
    pub wall_ms: i64,
}

/// The last tick of every title this process wrote a position for, by
/// (profile, set). Never stored or synced: a restarted process starts
/// empty, so its first write of a title counts nothing rather than the
/// hours the app was closed.
pub(crate) type Ticks = HashMap<(String, String), Tick>;

/// Seconds watched between `prev` and a write at position `at`, wall clock
/// `now_ms`: the smaller of the two advances, capped. A seek forward counts
/// the time it took, 2× speed counts wall time, and a first write, a seek
/// back or a clock that went back count nothing.
pub fn step_seconds(prev: Option<&Tick>, at: f64, now_ms: i64) -> f64 {
    let Some(prev) = prev else { return 0.0 };
    let d_pos = at - prev.at;
    let d_wall = (now_ms - prev.wall_ms) as f64 / 1000.0;
    // Both `>` are false for NaN, so a broken position counts nothing rather
    // than whatever `f64::min` would make of it.
    if d_pos > 0.0 && d_wall > 0.0 {
        d_pos.min(d_wall).min(STEP_CAP_SECONDS)
    } else {
        0.0
    }
}

/// Whether a write starts a finished title over: no position for it here,
/// yet it is marked watched. One underway since its restart has a position
/// again, so it is not started over a second time.
pub fn again_now(had_progress: bool, watched_live: bool) -> bool {
    !had_progress && watched_live
}

impl StateDb {
    /// This device's own position write, and the watch time it adds. The
    /// position, this device's title row and its day row commit together or
    /// not at all, so a total never runs ahead of a write that did not land.
    /// `local_day` is the viewer's date, `YYYY-MM-DD`: a step that spans
    /// midnight counts on the day of the write that ends it. `None` when
    /// nothing could be written, as for every `with`.
    pub(crate) fn set_progress_counted(
        &self,
        profile_id: &str,
        set_id: &str,
        at: f64,
        duration: Option<f64>,
        local_day: &str,
        now_ms: i64,
    ) -> Option<()> {
        let at = at.max(0.0);
        let key = (profile_id.to_string(), set_id.to_string());
        self.with(|conn| {
            let mut ticks = self.ticks.lock().unwrap_or_else(|error| error.into_inner());
            let step = step_seconds(ticks.get(&key), at, now_ms);
            let tx = conn.unchecked_transaction()?;
            // Read before the upsert, which makes the position exist.
            let again = again_now(
                found(&tx, HAS_PROGRESS, profile_id, set_id)?,
                found(&tx, IS_WATCHED, profile_id, set_id)?,
            );
            rows::set_progress(&tx, profile_id, set_id, at, duration)?;
            let device = sync::device_id(&tx)?;
            add_to_title(&tx, profile_id, set_id, &device, step, again, now_ms)?;
            if step > 0.0 {
                add_to_day(&tx, profile_id, local_day, &device, step, now_ms)?;
            }
            tx.commit()?;
            ticks.insert(key, Tick { at, wall_ms: now_ms });
            Ok(())
        })
    }

    /// Forgets a title's last tick: once it is finished, its next play is a
    /// new viewing, measured from nothing.
    pub(crate) fn forget_tick(&self, profile_id: &str, set_id: &str) {
        let mut ticks = self.ticks.lock().unwrap_or_else(|error| error.into_inner());
        ticks.remove(&(profile_id.to_string(), set_id.to_string()));
    }
}

const HAS_PROGRESS: &str = "SELECT 1 FROM progress WHERE profile_id = ?1 AND set_id = ?2";
const IS_WATCHED: &str =
    "SELECT 1 FROM watched WHERE profile_id = ?1 AND set_id = ?2 AND removed_at IS NULL";

fn found(conn: &Connection, sql: &str, profile_id: &str, set_id: &str) -> rusqlite::Result<bool> {
    conn.query_row(sql, params![profile_id, set_id], |_| Ok(()))
        .optional()
        .map(|row| row.is_some())
}

/// Starts the title on this device at its first write here, and adds the
/// step. `again_at` moves only on a start-over and is kept otherwise.
///
/// `updated_at` never moves backwards, here or for the day row: a clock that
/// steps back (or a reinstall's own rows imported with stamps from ahead)
/// must not give this device's newer seconds an older stamp than a copy
/// other devices already hold, or the next import would undo them.
fn add_to_title(
    conn: &Connection,
    profile_id: &str,
    set_id: &str,
    device: &str,
    step: f64,
    again: bool,
    now_ms: i64,
) -> rusqlite::Result<()> {
    conn.execute(
        "INSERT INTO stats_titles(profile_id, set_id, device, started_at, last_watched_at, seconds, again_at, updated_at)
           VALUES (?1, ?2, ?3, ?4, ?4, ?5, ?6, ?4)
           ON CONFLICT(profile_id, set_id, device) DO UPDATE SET
             seconds = seconds + excluded.seconds,
             last_watched_at = excluded.last_watched_at,
             again_at = COALESCE(excluded.again_at, again_at),
             updated_at = MAX(excluded.updated_at, updated_at + 1)",
        params![profile_id, set_id, device, now_ms, step, again.then_some(now_ms)],
    )?;
    Ok(())
}

fn add_to_day(
    conn: &Connection,
    profile_id: &str,
    day: &str,
    device: &str,
    step: f64,
    now_ms: i64,
) -> rusqlite::Result<()> {
    conn.execute(
        "INSERT INTO stats_days(profile_id, day, device, seconds, updated_at) VALUES (?1, ?2, ?3, ?4, ?5)
           ON CONFLICT(profile_id, day, device) DO UPDATE SET
             seconds = seconds + excluded.seconds,
             updated_at = MAX(excluded.updated_at, updated_at + 1)",
        params![profile_id, day, device, step, now_ms],
    )?;
    Ok(())
}

#[cfg(test)]
#[path = "stats_tests.rs"]
mod tests;
```

`crates/mediagram-core/src/state/mod.rs`:
```diff
 pub struct StateDb {
     data_dir: PathBuf,
     conn: Mutex<LocalState>,
+    /// The last own position write per title, for watch time; see `stats`.
+    ticks: Mutex<stats::Ticks>,
 }
@@ StateDb::new
             conn: Mutex::new(LocalState::Unopened),
+            ticks: Mutex::default(),
         }
```

`crates/mediagram-core/src/state/rows.rs` (`:46-47`):
```diff
 /// Sets where a profile is in `set_id`. Clamped to non-negative, like the
-/// web: a negative position has no title to seek to.
+/// web: a negative position has no title to seek to. Records no watch time —
+/// this device's own writes go through `StateDb::set_progress_counted`.
```

`crates/mediagram-core/src/api/state.rs` (`:110-122`, `:133-139`):
```diff
+    /// This device's own position write, and the watch time it adds to the
+    /// viewer's stats. `local_day` is today where the viewer is, `YYYY-MM-DD`
+    /// (Kotlin's `LocalDate.now()`) — the day that watch time counts on.
     pub async fn set_progress(
         self: Arc<Self>,
         profile_id: String,
         set_id: String,
         at: f64,
         duration: Option<f64>,
+        local_day: String,
     ) {
         self.blocking(move |core| {
+            let now = profiles::now_ms();
             core.state_db
-                .with(|conn| rows::set_progress(conn, &profile_id, &set_id, at, duration))
+                .set_progress_counted(&profile_id, &set_id, at, duration, &local_day, now)
         })
         .await;
     }
@@
     pub async fn set_watched(self: Arc<Self>, profile_id: String, set_id: String, finished: bool) {
         self.blocking(move |core| {
+            if finished {
+                // A finished title's next play is a new viewing, measured from nothing.
+                core.state_db.forget_tick(&profile_id, &set_id);
+            }
             core.state_db
                 .with(|conn| rows::set_watched(conn, &profile_id, &set_id, finished))
```

`crates/mediagram-core/tests/api_surface.rs` — the five `.set_progress(…)` calls (`:157, :161, :165, :169, :403`) gain the day:
```bash
sed -i -E 's/^(\s*\.set_progress\(.*)\)$/\1, "2026-10-03".into())/' crates/mediagram-core/tests/api_surface.rs
grep -c '"2026-10-03".into())' crates/mediagram-core/tests/api_surface.rs
```
Expected: `5`.

- [ ] **Step 4: Run, expect PASS**

Run: `cargo test -p mediagram-core --lib state::` ; `cargo test -p mediagram-core --test api_surface` ; `cargo test -p mediagram-core --test shared_watch_state_fixtures`
Expected: lib all pass (12 in `state::stats::tests`); `api_surface` `11 passed`; fixtures `7 passed`, no `skipping:`.

- [ ] **Step 5: Commit**

```bash
git add crates/mediagram-core/src/state crates/mediagram-core/src/api/state.rs crates/mediagram-core/tests/api_surface.rs crates/mediagram-core/tests/shared_watch_state_fixtures.rs
git commit -m "feat(core): count watch time on this device's own position writes"
```

## Task 3.7: `Core::stats`, end to end

**Files:**
- Create: `crates/mediagram-core/src/api/state/stats.rs`, `crates/mediagram-core/tests/viewing_stats_api.rs`
- Modify: `crates/mediagram-core/src/api/state.rs` (`mod stats;` after `mod collections;`, `:18`), `crates/mediagram-core/src/state/sync_tests.rs` (append a module)

**Interfaces:** Produces uniffi `Core::stats(self: Arc<Self>, profile_id: String, today: String) -> StatsSummary` (never throws). Nothing watched gives 30 zero days and no history; a storage failure gives `StatsSummary::default()`, which has an empty `last30`.

- [ ] **Step 1: Failing tests** — create `crates/mediagram-core/tests/viewing_stats_api.rs`

```rust
//! Viewing stats through the surface Kotlin calls: a position write records
//! watch time, finishing a title makes its next play a new viewing, and
//! `stats` reads it all back as one summary. Real clock, so the waits are
//! real too — short, and only ever compared loosely.

use std::sync::Arc;
use std::time::Duration;

use mediagram_core::api::Core;
use mediagram_core::state::stats::summary::HistoryKind;

const TODAY: &str = "2026-10-03";

fn core(dir: &std::path::Path) -> Arc<Core> {
    Core::new(dir.display().to_string(), 1, "test-hash".into(), "test-device".into())
}

/// A position write a little after the last, as the player's save tick makes them.
async fn play(core: &Arc<Core>, profile: &str, at: f64) {
    tokio::time::sleep(Duration::from_millis(20)).await;
    core.clone()
        .set_progress(profile.into(), "01FILM".into(), at, Some(5400.0), TODAY.into())
        .await;
}

#[tokio::test]
async fn watching_finishing_and_starting_over_read_back_as_history() {
    let dir = tempfile::tempdir().unwrap();
    let core = core(dir.path());
    let viewer = core.clone().create_profile("André".into(), false).await.unwrap();

    play(&core, &viewer.id, 0.0).await;
    play(&core, &viewer.id, 10.0).await;
    let watched = core.clone().stats(viewer.id.clone(), TODAY.into()).await;
    assert!(
        watched.all_seconds > 0.0 && watched.all_seconds < 10.0,
        "wall time, not position: {}",
        watched.all_seconds
    );

    core.clone().set_watched(viewer.id.clone(), "01FILM".into(), true).await;
    play(&core, &viewer.id, 20.0).await;
    let after = core.clone().stats(viewer.id.clone(), TODAY.into()).await;

    assert_eq!(after.all_seconds, watched.all_seconds, "finishing forgets the last tick");
    let kinds: Vec<HistoryKind> = after.history.iter().map(|line| line.kind).collect();
    assert_eq!(kinds, [HistoryKind::Again, HistoryKind::Finished, HistoryKind::Started]);
    assert!(
        after
            .history
            .iter()
            .all(|line| line.set_id == "01FILM" && line.seconds == watched.all_seconds)
    );
    assert_eq!((after.last30.len(), after.last30[29].day.as_str()), (30, TODAY));
    assert_eq!(after.week_seconds, after.all_seconds);
}

#[tokio::test]
async fn a_profile_with_nothing_watched_reads_as_thirty_empty_days() {
    let dir = tempfile::tempdir().unwrap();
    let summary = core(dir.path()).stats("nobody".into(), TODAY.into()).await;
    assert_eq!((summary.all_seconds, summary.last30.len()), (0.0, 30));
    assert!(summary.history.is_empty());
    assert!(summary.last30.iter().all(|day| day.seconds == 0.0));
}
```

Append to `crates/mediagram-core/src/state/sync_tests.rs`:

```rust

/// Watch time from two machines adds up once on both, however many rounds
/// pass: each device's rows are its own, and a merge keeps only the newest
/// copy of each.
mod viewing_stats_on_two_machines {
    use super::*;
    use crate::state::stats::exchange::export;

    fn day_total(db: &StateDb, id: &str) -> f64 {
        db.with(|conn| export(conn, id)).unwrap().1.iter().map(|row| row.seconds).sum()
    }

    #[tokio::test]
    async fn minutes_from_both_machines_sum_once_on_both() {
        let (_ldir, laptop) = db();
        let laptop_id = profile(&laptop);
        let (_pdir, phone) = db();
        let phone_id = profile(&phone);
        let channel = FakeChannel::new(Vec::new());
        let t0 = profiles::now_ms();
        for (at, secs) in [(0.0, 0), (10.0, 10), (20.0, 20)] {
            laptop
                .set_progress_counted(&laptop_id, "01A", at, None, "2026-10-03", t0 + secs * 1000)
                .unwrap();
        }
        for (at, secs) in [(20.0, 30), (25.0, 35)] {
            phone
                .set_progress_counted(&phone_id, "01A", at, None, "2026-10-03", t0 + secs * 1000)
                .unwrap();
        }

        for _ in 0..3 {
            once(&laptop, &channel, "laptop", SyncMemo::default().entry("h")).await;
            once(&phone, &channel, "phone", SyncMemo::default().entry("h")).await;
        }

        assert_eq!(day_total(&laptop, &laptop_id), 25.0);
        assert_eq!(day_total(&phone, &phone_id), 25.0);
    }
}
```

- [ ] **Step 2: Run, expect a compile failure**

Run: `cargo test -p mediagram-core --test viewing_stats_api`
Expected: FAIL — `no method named 'stats' found for struct 'Arc<Core>'`.

- [ ] **Step 3: Implement** — `crates/mediagram-core/src/api/state/stats.rs` (whole file), and `mod stats;` after `mod collections;` in `api/state.rs`:

```rust
//! The stats page's one read. Split out to keep `state.rs` under the line
//! limit; every rule lives in `crate::state::stats`, pinned to the web by
//! the shared fixtures.

use std::sync::Arc;

use crate::state::rows;
use crate::state::stats::{exchange, summary};

use super::super::Core;

#[uniffi::export(async_runtime = "tokio")]
impl Core {
    /// `profile_id`'s watch time and history, summed over every device, as
    /// of `today` (`YYYY-MM-DD` where the viewer is). Nothing watched reads
    /// as 30 empty days and no history; a storage failure as an empty
    /// summary — never an error.
    pub async fn stats(self: Arc<Self>, profile_id: String, today: String) -> summary::StatsSummary {
        self.blocking(move |core| {
            core.state_db
                .with(|conn| {
                    let (titles, days) = exchange::export(conn, &profile_id)?;
                    let watched = rows::watched_for(conn, &profile_id)?;
                    let input = summary::SummaryInput { today, titles, days, watched };
                    Ok(summary::summarize(&input))
                })
                .unwrap_or_default()
        })
        .await
    }
}
```

- [ ] **Step 4: Run every Rust gate**

```bash
cargo test -p mediagram-core
cargo test -p mediagram-core --test shared_watch_state_fixtures -- --nocapture 2>&1 | grep -c skipping
cargo clippy -p mediagram-core --all-targets --all-features -- -D warnings
cargo test -p mediagram --test code_standards
```
Expected: all green (`viewing_stats_api` `2 passed`; `viewing_stats_on_two_machines` `1 passed`); the `grep -c skipping` prints `0`; clippy prints no warning; code_standards passes.

- [ ] **Step 5: Commit**

```bash
git add crates/mediagram-core/src/api/state.rs crates/mediagram-core/src/api/state crates/mediagram-core/tests/viewing_stats_api.rs crates/mediagram-core/src/state/sync_tests.rs
git commit -m "feat(core): read a profile's viewing stats summary"
```

## Task 3.8: Kotlin bindings, the fake core, and every call site the new argument breaks

The uniffi `setProgress` gains a fifth, non-default parameter, so every Kotlin caller and overrider of `CoreInterface.setProgress` stops compiling. The lead's own gate `:core:testing:compileDebugKotlin` compiles `core:data` main (`core/testing/build.gradle.kts`: `api(project(":core:data"))`), which holds the production caller. So the call sites are updated here, mechanically. The production one passes the device's local date per contract §1. The Stats page, the repository's `stats()` and every UI piece stay in phase 04.

Sites, all verified by grep (every `override suspend fun setProgress(` whose parameters start with `profileId`, and every `core.setProgress(` call; the repository-level `setProgress(setId, at, duration)` overrides in `WatchSyncTest`, `CatalogViewModelTest`, `ProfileViewModelTest`, `FakeWatchStateRepository`, `ui-tv/FakeWatchState` are a different interface and untouched):
- override: `android/core/testing/src/main/kotlin/testing/FakeCore.kt:494`
- override: `android/core/data/src/test/kotlin/WatchStateRepositoryTest.kt:116`
- override: `android/core/data/src/test/kotlin/WatchStateOwnershipTest.kt:74-79`
- override: `android/feature/setup/src/test/kotlin/SettingsWatchOwnershipTest.kt:40-45` (`WatchCore : FakeCoreHandle by FakeCore(…)`; `FakeCoreHandle : CoreInterface`)
- call (production): `android/core/data/src/main/kotlin/WatchStateRepository.kt:284`
- call: `android/core/data/src/test/kotlin/WatchStateRepositoryTest.kt:29`
- calls (10): `android/core/testing/src/main/kotlin/testing/CoreContract.kt:123, 124, 136, 146, 148, 158, 171, 192, 365, 381`
- `stats` needs no override anywhere but `FakeCore`: every other `CoreInterface` implementer delegates (`object : CoreInterface by FakeCore()` / `by seeded`).

**Files:**
- Modify (generated): `android/core/rust/src/main/kotlin/uniffi/mediagram_core/mediagram_core.kt`
- Modify: the seven Kotlin files above
- Modify: `Cargo.toml`, `Cargo.lock`, `web/package.json`, `android/app/build.gradle.kts` (version)

- [ ] **Step 1: Regenerate** (also rebuilds the gitignored `.so` for all four ABIs — a stale one passes the build and crashes the app at launch with `UnsatisfiedLinkError`)

```bash
ANDROID_NDK_HOME=/home/andre/android-sdk/ndk/28.2.13676358 scripts/generate-android-bindings.sh
B=android/core/rust/src/main/kotlin/uniffi/mediagram_core/mediagram_core.kt
grep -c 'fun `stats`(`profileId`: kotlin.String, `today`: kotlin.String): StatsSummary' $B
grep -c '`duration`: kotlin.Double?, `localDay`: kotlin.String)' $B
grep -n 'data class StatsSummary\|data class DayBar\|data class HistoryEntry\|enum class HistoryKind' $B
```
Expected: `2`, `2` (interface and implementation each), and the four type declarations.

- [ ] **Step 2: Fake core** — `android/core/testing/src/main/kotlin/testing/FakeCore.kt`: add `import uniffi.mediagram_core.StatsSummary` after `import uniffi.mediagram_core.StateSnapshot` (`:24`), and replace `:494-495`

```kotlin
    override suspend fun setProgress(profileId: String, setId: String, at: Double, duration: Double?) =
        watchState.setProgress(profileId, setId, at, duration)
```
with
```kotlin
    override suspend fun setProgress(profileId: String, setId: String, at: Double, duration: Double?, localDay: String) =
        watchState.setProgress(profileId, setId, at, duration)

    /** Records no watch time, so every profile reads as nothing watched yet. */
    override suspend fun stats(profileId: String, today: String): StatsSummary =
        StatsSummary(weekSeconds = 0.0, monthSeconds = 0.0, allSeconds = 0.0, last30 = emptyList(), history = emptyList())
```

- [ ] **Step 3: Call sites**

```bash
sed -i -E 's/core\.setProgress\(([^()]*)\)/core.setProgress(\1, "2026-10-03")/' \
  android/core/testing/src/main/kotlin/testing/CoreContract.kt \
  android/core/data/src/test/kotlin/WatchStateRepositoryTest.kt
grep -c 'core.setProgress(.*, "2026-10-03")' android/core/testing/src/main/kotlin/testing/CoreContract.kt android/core/data/src/test/kotlin/WatchStateRepositoryTest.kt
```
Expected: `CoreContract.kt:10`, `WatchStateRepositoryTest.kt:1` (dry-run on copies: exactly these lines change).

`android/core/data/src/main/kotlin/WatchStateRepository.kt` — add `import java.time.LocalDate` above `import kotlinx.coroutines.CoroutineDispatcher` (`:3`; desugaring is on, `build-logic/…/KotlinAndroid.kt:47`), and at `:284`:
```diff
     ) = writing { core, id ->
-        core.setProgress(id, setId, at, duration)
+        core.setProgress(id, setId, at, duration, LocalDate.now().toString())
     }
```

`android/core/data/src/test/kotlin/WatchStateRepositoryTest.kt:116`:
```diff
-                    override suspend fun setProgress(profileId: String, setId: String, at: Double, duration: Double?) {
+                    override suspend fun setProgress(profileId: String, setId: String, at: Double, duration: Double?, localDay: String) {
```

`android/core/data/src/test/kotlin/WatchStateOwnershipTest.kt:74-79`:
```diff
     override suspend fun setProgress(
         profileId: String,
         setId: String,
         at: Double,
         duration: Double?,
+        localDay: String,
     ) = write()
```

`android/feature/setup/src/test/kotlin/SettingsWatchOwnershipTest.kt:40-45`:
```diff
     override suspend fun setProgress(
         profileId: String,
         setId: String,
         at: Double,
         duration: Double?,
+        localDay: String,
     ) {
         writes += "progress:$profileId:$setId"
     }
```

- [ ] **Step 4: Compile and run the affected Android modules**

```bash
cd android && ./gradlew -q :core:testing:compileDebugKotlin :core:testing:testDebugUnitTest :core:data:testDebugUnitTest :feature:setup:testDebugUnitTest; cd ..
```
Expected: `BUILD SUCCESSFUL` (quiet: no output). `FakeCoreContractTest` runs every `CoreContract` case against `FakeCore`, now with the day argument.

- [ ] **Step 5: Bump (patch) by pattern, check, commit**

```bash
V=$(grep -m1 -oE '^version = "[0-9.]+"' Cargo.toml | grep -oE '[0-9.]+' | awk -F. '{print $1"."$2"."$3+1}')
sed -i -E '0,/^version = "[0-9.]+"/s//version = "'$V'"/' Cargo.toml
sed -i -E '0,/"version": "[0-9.]+"/s//"version": "'$V'"/' web/package.json
sed -i -E 's/versionName = "[0-9.]+"/versionName = "'$V'"/' android/app/build.gradle.kts
cargo metadata -q --format-version 1 >/dev/null
grep -m1 '^version' Cargo.toml; grep -m1 '"version"' web/package.json; grep 'versionName = "' android/app/build.gradle.kts
scripts/check.sh
git add android/core android/feature/setup/src/test/kotlin/SettingsWatchOwnershipTest.kt android/app/build.gradle.kts Cargo.toml Cargo.lock web/package.json
git commit -m "feat(core): viewing stats reach Kotlin, set_progress takes the local day; release $V"
```
Expected: the three greps print `$V`; `check.sh` ends `all checks passed`. `git status` shows no `.so` (gitignored).

## Rollback

One commit per task; revert newest first. Revert 3.6–3.8 together: 3.6 changes the uniffi signature that 3.8's Kotlin follows. Schema v7 is additive (`CREATE TABLE IF NOT EXISTS`). A build from before it opening a v7 store sees `user_version` 7 ≥ its `VERSION` (`mod.rs:125`), migrates nothing and ignores the tables. Its documents simply carry no stats keys, and other devices keep their gossip copies of its rows, so a downgrade loses no minutes. Nothing here deletes or rewrites a pre-existing row.

## Risks

| Risk | L × I | Mitigation |
|---|---|---|
| Kotlin stops compiling on the new `setProgress` argument | H × H if missed | Task 3.8 lists and fixes all 16 sites (4 overrides, 12 calls); gradle gate + `check.sh` |
| Web and core disagree on an edge | M × M → L | Reconciled with phase 01's plan, and run against its fixture JSON (see the note under Review focus); the remaining unpinned edges are under Open questions |
| Stale `.so` packaged after a later Rust change | M × H | Task 3.8 regenerates; phase 04 must rerun `scripts/build-android-core.sh` after any Rust edit before an APK |
| Clock skew: an own-device row imported with a future `updatedAt` outruns local writes | L × M → resolved | `updated_at = MAX(now, stored + 1)` (contract §1, amended); `this_devices_stamps_never_move_backwards` |
| `mod.rs` at 199/200 | L × L | Measured; a next field there needs an extraction first |

## Success criteria

- `cargo test -p mediagram-core` green. 32 new lib tests, `shared_watch_state_fixtures` 7 tests with **no** `skipping:` line, `viewing_stats_api` 2, `api_surface` 11.
- `cargo clippy -p mediagram-core --all-targets --all-features -- -D warnings` clean; `cargo test -p mediagram --test code_standards` green.
- Every review-focus test above exists under its name and passes. Removing `forget_tick` from `Core::set_watched` makes `watching_finishing_and_starting_over_read_back_as_history` fail (mutation-checked).
- A store without stats exports a document without `titleStats`/`dayStats`; `record-parse.json`, `merge.json`, `lists-merge.json` pass unchanged.
- Generated Kotlin has `CoreInterface.stats(profileId, today): StatsSummary`, `setProgress(…, localDay: String)`, `StatsSummary`, `DayBar`, `HistoryEntry`, `HistoryKind`. `./gradlew :core:testing:testDebugUnitTest :core:data:testDebugUnitTest :feature:setup:testDebugUnitTest` and `scripts/check.sh` green.
- All three manifests carry the same patch-bumped version on the last commit.

## For phase 04

- `CoreInterface.stats(profileId, today = LocalDate.now().toString())` is the page's one read. Map `HistoryKind.STARTED/FINISHED/AGAIN` to "Started"/"Finished"/"Watched again".
- The production `setProgress` caller already passes `LocalDate.now().toString()` (`WatchStateRepository.kt:284`). Inject a clock there only if a test needs it.
- `FakeCore.stats` is a zero stub. Make it configurable when the first Android stats test needs it.
- After any further Rust change: `ANDROID_NDK_HOME=… scripts/build-android-core.sh` before packaging an APK.
- Docs (phase 05): system-architecture § sync gains `titleStats`/`dayStats` and core schema v7.

## Open questions

1. `DayBar` names the `last30` element (Rust/Kotlin need a name; the contract leaves it anonymous). It is taken from phase 04's assumed Kotlin names — worth one line in the contract.
2. Not pinned by any fixture, decided here: a `today` that is not a date gives week/month 0, an empty `last30`, and still computes all and history. The web's `summarize` would throw there (`shiftDay` → `toISOString` on an invalid date), which its server never triggers. `HistoryEntry.at` is `i64` (Kotlin `Long`), so a fractional `startedAt` from a hostile document is truncated, where the web keeps the fraction.
3. `stats()` on a storage failure returns the default (empty `last30`), not 30 zero days. Is that fine for the page?
