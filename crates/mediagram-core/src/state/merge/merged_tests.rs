//! The merged answer's JSON is what the shared fixtures' `expect` is read
//! into and compared against, so its key names and omissions are the web's.

use serde_json::json;

use super::{MergedProfile, MergedState};
use crate::state::record::{AdminClaim, DayStatRow, ListRow, ProfileRoles};

fn viewer() -> MergedProfile {
    MergedProfile {
        name: "andré".into(),
        display_name: "André".into(),
        ..Default::default()
    }
}

#[test]
fn an_ordinary_viewer_writes_camel_case_keys_and_omits_what_it_lacks() {
    let written = serde_json::to_value(viewer()).unwrap();
    assert_eq!(
        written,
        json!({ "name": "andré", "displayName": "André", "progress": [], "watched": [],
                "unwatched": [], "watchlist": [], "collections": [], "preferences": [] })
    );
}

/// `kids`, the stats and the role keys appear only when there is something
/// to say, the role keys beside `name` rather than nested.
#[test]
fn kids_stats_and_roles_are_written_only_when_set() {
    let profile = MergedProfile {
        kids: true,
        day_stats: vec![DayStatRow {
            day: "2026-10-03".into(),
            device: "phone".into(),
            seconds: 60.0,
            updated_at: 1.0,
        }],
        roles: ProfileRoles {
            admin: Some(AdminClaim { claimed_at: 5.0 }),
            parent: Some("bea".into()),
            ..Default::default()
        },
        ..viewer()
    };
    let written = serde_json::to_value(profile).unwrap();
    assert_eq!(written["kids"], json!(true));
    assert_eq!(written["dayStats"][0]["day"], json!("2026-10-03"));
    assert!(written.get("titleStats").is_none());
    assert_eq!(written["admin"], json!({ "claimedAt": 5.0 }));
    assert_eq!(written["parent"], json!("bea"));
    assert!(written.get("roles").is_none() && written.get("pin").is_none());
}

/// A fixture's `expect` names only what it tests; everything else reads as
/// empty rather than failing the case.
#[test]
fn an_expectation_naming_only_some_keys_reads_the_rest_as_empty() {
    let read: MergedState = serde_json::from_value(json!({
        "profiles": [{ "name": "andré", "displayName": "André", "kidsAge": { "age": 6, "updatedAt": 2 } }],
        "editorsChoice": [{ "setId": "01A", "updatedAt": 3 }]
    }))
    .unwrap();
    let profile = &read.profiles[0];
    assert_eq!(profile.name, "andré");
    assert!(!profile.kids && profile.progress.is_empty() && profile.title_stats.is_empty());
    assert_eq!(profile.roles.kids_age.as_ref().map(|k| k.age), Some(6));
    assert!(read.kids.is_empty());
    assert_eq!(
        read.editors_choice,
        [ListRow {
            set_id: "01A".into(),
            updated_at: 3.0,
            removed: false,
            age: None,
        }]
    );
}

#[test]
fn a_merged_state_survives_a_round_trip() {
    let state = MergedState {
        profiles: vec![MergedProfile {
            kids: true,
            ..viewer()
        }],
        kids: vec![ListRow {
            set_id: "01A".into(),
            updated_at: 1.0,
            removed: false,
            age: Some(6),
        }],
        editors_choice: Vec::new(),
    };
    let text = serde_json::to_string(&state).unwrap();
    assert!(text.contains("\"editorsChoice\":[]"));
    assert_eq!(serde_json::from_str::<MergedState>(&text).unwrap(), state);
}
