use serde_json::{Value, json};

use super::*;
use crate::state::record::MAX_STAMP;

fn document(changes: Value) -> String {
    let mut body = json!({ "format": 1, "device": "phone", "writtenAt": 5 });
    body.as_object_mut()
        .unwrap()
        .extend(changes.as_object().unwrap().clone());
    body.to_string()
}

fn profile(fields: Value) -> ProfileState {
    let text = document(json!({ "profiles": [fields] }));
    let mut record = parse_record(&text).expect("the document itself is sound");
    record.profiles.remove(0)
}

#[test]
fn a_sound_document_reads_back_as_written() {
    let text = document(json!({
        "device": " phone ",
        "profiles": [{ "name": "André", "localId": "p1",
            "progress": [{ "setId": "01A", "at": 42.5, "duration": 900, "updatedAt": 10 }],
            "watched": [{ "setId": "01B", "updatedAt": 11 }],
            "unwatched": [{ "setId": "01C", "updatedAt": 12, "lastFinishedAt": 9 }] }],
        "kids": [{ "setId": "01K", "updatedAt": 3, "age": 6 }],
        "editorsChoice": [{ "setId": "01E", "updatedAt": 4 }]
    }));
    let record = parse_record(&text).unwrap();
    assert_eq!(
        (record.format, record.device.as_str(), record.written_at),
        (1, "phone", 5.0)
    );
    let profile = &record.profiles[0];
    assert_eq!(
        (profile.name.as_str(), profile.local_id.as_deref()),
        ("André", Some("p1"))
    );
    assert_eq!(
        profile.progress,
        [ProgressRow {
            set_id: "01A".into(),
            at: 42.5,
            duration: Some(900.0),
            updated_at: 10.0,
        }]
    );
    assert_eq!(
        profile.watched,
        [WatchedRow {
            set_id: "01B".into(),
            updated_at: 11.0,
        }]
    );
    assert_eq!(
        profile.unwatched,
        [UnwatchedRow {
            set_id: "01C".into(),
            updated_at: 12.0,
            last_finished_at: 9.0,
        }]
    );
    assert_eq!(record.kids[0].age, Some(6));
    assert_eq!(record.editors_choice[0].set_id, "01E");
}

#[test]
fn text_that_is_not_a_json_object_is_no_document() {
    for text in ["", "not json", "[]", "null", "\"format\""] {
        assert_eq!(parse_record(text), None, "{text:?}");
    }
}

/// A document from a newer build is ignored rather than half-read into the
/// database this machine trusts.
#[test]
fn only_a_whole_format_this_build_knows_is_read() {
    for format in [
        json!(0),
        json!(1.5),
        json!(SYNC_FORMAT + 1),
        json!("x"),
        json!(null),
    ] {
        assert_eq!(
            parse_record(&document(json!({ "format": format }))),
            None,
            "{format}"
        );
    }
    assert!(parse_record(&document(json!({ "format": "1" }))).is_some());
    let mut missing: Value = serde_json::from_str(&document(json!({}))).unwrap();
    missing.as_object_mut().unwrap().remove("format");
    assert_eq!(parse_record(&missing.to_string()), None);
}

#[test]
fn a_document_must_name_its_device() {
    for device in [json!(null), json!(""), json!("   "), json!(7)] {
        assert_eq!(
            parse_record(&document(json!({ "device": device }))),
            None,
            "{device}"
        );
    }
}

/// `Number(writtenAt) || 0`: what is not a number is 0, and a finite number
/// passes through, negative or not.
#[test]
fn written_at_falls_back_to_zero_only_when_it_is_not_a_number() {
    let written_at = |value: Value| {
        parse_record(&document(json!({ "writtenAt": value })))
            .unwrap()
            .written_at
    };
    assert_eq!(written_at(json!("soon")), 0.0);
    assert_eq!(written_at(json!([1])), 0.0);
    assert_eq!(written_at(json!(-3)), -3.0);
    assert_eq!(written_at(json!("12")), 12.0);
}

/// One unreadable row costs that row, never the profile or the rest of its
/// history.
#[test]
fn a_bad_row_is_dropped_and_its_neighbours_kept() {
    let good = json!({ "setId": "01A", "at": 0, "updatedAt": 10 });
    let parsed = profile(json!({ "name": "André", "progress": [
        { "at": 1, "updatedAt": 10 },
        { "setId": "01B", "at": -1, "updatedAt": 10 },
        { "setId": "01C", "at": "later", "updatedAt": 10 },
        { "setId": "01D", "at": 1, "updatedAt": 0 },
        { "setId": "01E", "at": 1, "updatedAt": MAX_STAMP * 2.0 },
        "01F",
        good
    ] }));
    let kept: Vec<&str> = parsed.progress.iter().map(|r| r.set_id.as_str()).collect();
    assert_eq!(
        kept,
        ["01A"],
        "a position at 0 is the start of a film, not nothing"
    );
}

#[test]
fn a_runtime_that_is_not_positive_is_no_runtime() {
    for duration in [json!(0), json!(-5), json!("long"), json!(null)] {
        let parsed = profile(json!({ "name": "André",
            "progress": [{ "setId": "01A", "at": 1, "duration": duration, "updatedAt": 10 }] }));
        assert_eq!(parsed.progress[0].duration, None, "{duration}");
    }
}

#[test]
fn a_mark_needs_a_title_and_a_stamp() {
    let parsed = profile(json!({ "name": "André", "watched": [
        { "setId": "01A", "updatedAt": MAX_STAMP },
        { "setId": "01B", "updatedAt": -1 },
        { "setId": " ", "updatedAt": 5 },
        { "updatedAt": 5 }
    ] }));
    let kept: Vec<&str> = parsed.watched.iter().map(|r| r.set_id.as_str()).collect();
    assert_eq!(kept, ["01A"]);
}

/// A removal's finish past the bound would tombstone every later position
/// of that title, so both of its times must be stamps.
#[test]
fn a_removal_needs_both_of_its_times() {
    let parsed = profile(json!({ "name": "André", "unwatched": [
        { "setId": "01A", "updatedAt": 5 },
        { "setId": "01B", "updatedAt": 5, "lastFinishedAt": MAX_STAMP * 2.0 },
        { "setId": "01C", "updatedAt": 0, "lastFinishedAt": 4 },
        { "setId": "01D", "updatedAt": 5, "lastFinishedAt": 4 }
    ] }));
    let kept: Vec<&str> = parsed.unwatched.iter().map(|r| r.set_id.as_str()).collect();
    assert_eq!(kept, ["01D"]);
}

/// A restricting flag must not be switched on by a value that merely looks
/// truthy.
#[test]
fn only_a_literal_true_makes_a_kids_profile() {
    assert!(profile(json!({ "name": "Mia", "kids": true })).kids);
    for kids in [json!("true"), json!(1), json!({}), json!(null)] {
        assert!(
            !profile(json!({ "name": "Mia", "kids": kids })).kids,
            "{kids}"
        );
    }
}

#[test]
fn a_profile_without_a_name_is_dropped_and_the_others_kept() {
    let text = document(json!({ "profiles": [
        { "progress": [] }, { "name": "  " }, "André", { "name": " Bea " }
    ] }));
    let names: Vec<String> = parse_record(&text)
        .unwrap()
        .profiles
        .into_iter()
        .map(|p| p.name)
        .collect();
    assert_eq!(names, ["Bea"]);
}

/// An older document lacks the newer keys; a wrong-typed key says nothing.
#[test]
fn missing_or_wrong_typed_lists_read_as_empty() {
    let parsed =
        profile(json!({ "name": "André", "watched": "01A", "watchlist": { "setId": "01A" } }));
    assert!(parsed.progress.is_empty() && parsed.watched.is_empty() && parsed.watchlist.is_empty());
    assert!(parsed.preferences.is_empty() && parsed.title_stats.is_empty());
    assert_eq!(parsed.local_id, None);
    let record = parse_record(&document(json!({ "kids": "01A" }))).unwrap();
    assert!(
        record.profiles.is_empty() && record.kids.is_empty() && record.editors_choice.is_empty()
    );
}
