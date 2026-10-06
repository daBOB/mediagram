use serde_json::{Value, json};

use super::*;

fn row(changes: Value) -> Option<SyncPreference> {
    let mut raw = json!({ "scope": "show:x", "name": "subtitle", "value": "de", "updatedAt": 10 });
    raw.as_object_mut()
        .unwrap()
        .extend(changes.as_object().unwrap().clone());
    preference_row(&raw)
}

#[test]
fn a_sound_row_reads_back_trimmed() {
    assert_eq!(
        row(json!({ "scope": " show:x ", "value": " de " })),
        Some(SyncPreference {
            scope: "show:x".into(),
            name: "subtitle".into(),
            value: "de".into(),
            updated_at: 10.0,
        })
    );
}

/// Audio, speed and framing are about the room and stay on the device that
/// chose them, so a peer's document cannot set them here.
#[test]
fn only_the_four_subtitle_names_travel() {
    for name in SYNCED_NAMES {
        assert!(row(json!({ "name": name })).is_some(), "{name}");
    }
    for name in ["audio", "speed", "framing", "Subtitle", ""] {
        assert_eq!(row(json!({ "name": name })), None, "{name}");
    }
}

/// Past 2^53 a local write's `stored + 1` stamp no longer advances, and the
/// peer would win every tie after it.
#[test]
fn a_stamp_must_be_whole_milliseconds_a_js_peer_can_hold() {
    assert!(row(json!({ "updatedAt": 1 })).is_some());
    assert!(row(json!({ "updatedAt": MAX_STAMP })).is_some());
    assert!(row(json!({ "updatedAt": "12" })).is_some());
    for at in [
        json!(0),
        json!(-1),
        json!(10.5),
        json!(MAX_STAMP * 2.0),
        json!("soon"),
    ] {
        assert_eq!(row(json!({ "updatedAt": at })), None, "{at}");
    }
}

#[test]
fn a_scope_or_value_with_nothing_in_it_is_no_preference() {
    for blank in [json!(""), json!("  "), json!(null), json!(6)] {
        assert_eq!(row(json!({ "scope": blank })), None, "scope {blank}");
        assert_eq!(row(json!({ "value": blank })), None, "value {blank}");
    }
    assert_eq!(preference_row(&json!("subtitle")), None);
}

/// Capped by characters, not bytes, so a long value of multi-byte text is
/// cut on a character boundary rather than refused or split mid-character.
#[test]
fn a_strangers_long_scope_and_value_are_capped_at_two_hundred_characters() {
    let long = "é".repeat(250);
    let read = row(json!({ "scope": long, "value": long })).unwrap();
    assert_eq!(read.scope, "é".repeat(200));
    assert_eq!(read.value, "é".repeat(200));
}

#[test]
fn a_written_preference_reads_back_through_the_wire_shape() {
    let written = SyncPreference {
        scope: "key:abc".into(),
        name: "cue-offset".into(),
        value: "-0.5".into(),
        updated_at: 1_700_000_000_000.0,
    };
    let wire = serde_json::to_value(&written).unwrap();
    assert_eq!(wire["updatedAt"], json!(1_700_000_000_000.0));
    assert_eq!(preference_row(&wire), Some(written));
}
