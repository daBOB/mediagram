//! Runs the web's own watch-state fixtures against this crate's parsing and
//! merge.
//!
//! `record.rs` and `merge.rs` are ports of `web/src/state/sync-record.ts`
//! and `web/src/state/merge.ts`; the fixtures under
//! `web/test/fixtures/watch-state/` are what pins the two together. A case
//! that only passes after a change here does not belong in the fixture — the
//! web is authoritative, and this file exists to agree with it, not to
//! redefine it.
//!
//! Next up and resume-point are Kotlin-only ports (05/06); this crate has no
//! play-order or shelf logic to hold `next-up.json`/`resume-point.json`
//! against.

use std::path::{Path, PathBuf};

use mediagram_core::state::merge::{MergedState, merge_states};
use mediagram_core::state::record::{SyncRecord, parse_record};
use serde::Deserialize;
use serde::de::DeserializeOwned;

const FIXTURES: &str = "../../web/test/fixtures/watch-state";

fn fixture_path(file: &str) -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR"))
        .join(FIXTURES)
        .join(file)
}

/// Loads one fixture file, or `None` if the web checkout is not present —
/// the same accommodation `shared_playable_sql.rs` makes: the web is a
/// separate deliverable, and its absence is not a failure of this crate.
fn load<T: DeserializeOwned>(file: &str) -> Option<Vec<T>> {
    let path = fixture_path(file);
    let Ok(text) = std::fs::read_to_string(&path) else {
        eprintln!("skipping: {} is not present", path.display());
        return None;
    };
    Some(serde_json::from_str(&text).unwrap_or_else(|err| panic!("{}: {err}", path.display())))
}

#[derive(Deserialize)]
struct RecordParseCase {
    name: String,
    input: String,
    expect: Option<SyncRecord>,
}

#[test]
fn record_parse_fixtures_match_the_web() {
    let Some(cases) = load::<RecordParseCase>("record-parse.json") else {
        return;
    };
    assert!(!cases.is_empty(), "record-parse.json holds no cases");
    for case in cases {
        assert_eq!(
            parse_record(&case.input),
            case.expect,
            "case: {}",
            case.name
        );
    }
}

#[derive(Deserialize)]
struct MergeCase {
    name: String,
    records: Vec<SyncRecord>,
    expect: MergedState,
}

/// Profiles and rows compared as sets, not by the order a `HashMap` iterates
/// them in, the same accommodation the web's own fixture runner makes.
///
/// Sorting the list-carrying fields too is a no-op for `merge.json`, whose
/// cases predate them and leave every one empty — safe to fold in here
/// rather than keep a second, near-identical function for `lists-merge.json`.
fn canonical(mut state: MergedState) -> MergedState {
    state.kids.sort_by(|a, b| a.set_id.cmp(&b.set_id));
    for profile in &mut state.profiles {
        profile.progress.sort_by(|a, b| a.set_id.cmp(&b.set_id));
        profile.watched.sort_by(|a, b| a.set_id.cmp(&b.set_id));
        profile.unwatched.sort_by(|a, b| a.set_id.cmp(&b.set_id));
        profile.watchlist.sort_by(|a, b| a.set_id.cmp(&b.set_id));
        profile.collections.sort_by(|a, b| a.id.cmp(&b.id));
    }
    state.profiles.sort_by(|a, b| a.name.cmp(&b.name));
    state
}

fn run_merge_fixture(file: &str) {
    let Some(cases) = load::<MergeCase>(file) else {
        return;
    };
    assert!(!cases.is_empty(), "{file} holds no cases");
    for case in cases {
        let expect = canonical(case.expect);

        let forward = canonical(merge_states(&case.records));
        assert_eq!(forward, expect, "case: {} (forward)", case.name);

        // Order-independent, spelling included: devices see each other's
        // documents in whatever order Telegram hands them over.
        let mut reversed = case.records.clone();
        reversed.reverse();
        let backward = canonical(merge_states(&reversed));
        assert_eq!(backward, expect, "case: {} (reversed)", case.name);
    }
}

#[test]
fn merge_fixtures_match_the_web_in_both_orders() {
    run_merge_fixture("merge.json");
}

/// Watchlist, Kids and collections: added, removed, re-added, tied, and a
/// tombstone against a live row — `merge.json`'s cases predate all three.
#[test]
fn lists_merge_fixtures_match_the_web_in_both_orders() {
    run_merge_fixture("lists-merge.json");
}
