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
//! The achievement rules run here too, against `achievements.json`.
//!
//! Next up and resume-point are Kotlin-only ports (05/06); this crate has no
//! play-order or shelf logic to hold `next-up.json`/`resume-point.json`
//! against.

use std::path::{Path, PathBuf};

use mediagram_core::state::merge::{MergedProfile, MergedState, merge_states};
use mediagram_core::state::record::{SyncRecord, parse_record};
use mediagram_core::state::stats::achievements::{AchievementInput, Achievements, achievements};
use mediagram_core::state::stats::summary::{DayBar, HistoryEntry, SummaryInput, summarize};
use mediagram_core::state::stats::{Tick, again_now, step_seconds};
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
    state.editors_choice.sort_by(|a, b| a.set_id.cmp(&b.set_id));
    for profile in &mut state.profiles {
        profile.progress.sort_by(|a, b| a.set_id.cmp(&b.set_id));
        profile.watched.sort_by(|a, b| a.set_id.cmp(&b.set_id));
        profile.unwatched.sort_by(|a, b| a.set_id.cmp(&b.set_id));
        profile.watchlist.sort_by(|a, b| a.set_id.cmp(&b.set_id));
        profile.collections.sort_by(|a, b| a.id.cmp(&b.id));
        profile
            .preferences
            .sort_by(|a, b| (&a.scope, &a.name).cmp(&(&b.scope, &b.name)));
        profile
            .title_stats
            .sort_by(|a, b| (&a.set_id, &a.device).cmp(&(&b.set_id, &b.device)));
        profile
            .day_stats
            .sort_by(|a, b| (&a.day, &a.device).cmp(&(&b.day, &b.device)));
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
        assert_eq!(
            stats_only(merge_states(&records)),
            expect,
            "case: {} (forward)",
            case.name
        );
        records.reverse();
        assert_eq!(
            stats_only(merge_states(&records)),
            expect,
            "case: {} (reversed)",
            case.name
        );
    }
}

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

#[derive(Deserialize)]
struct AchievementCase {
    name: String,
    input: AchievementInput,
    expect: Achievements,
}

/// Every rule, the kids subset, the day boundaries and the order of both
/// lists, against the web's own answers.
#[test]
fn achievement_fixtures_match_the_web() {
    let Some(cases) = load::<AchievementCase>("achievements.json") else {
        return;
    };
    assert!(!cases.is_empty(), "achievements.json holds no cases");
    for case in cases {
        assert_eq!(
            achievements(&case.input),
            case.expect,
            "case: {}",
            case.name
        );
    }
}
