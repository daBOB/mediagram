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
