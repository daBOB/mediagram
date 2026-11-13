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
        households: Vec::new(),
    };
    let text = serde_json::to_string(&state).unwrap();
    assert!(text.contains("\"editorsChoice\":[]"));
    assert_eq!(serde_json::from_str::<MergedState>(&text).unwrap(), state);
}

/// A scene mark is one row by its own id: the later edit wins outright, a
/// removal included, whichever order the documents arrive in.
#[test]
fn the_later_edit_to_one_scene_id_wins() {
    use crate::state::merge::merge_states;
    use crate::state::record::{ProfileState, SYNC_FORMAT, SceneRow, SyncRecord};

    let scene = |updated_at: f64, removed: bool| SceneRow {
        id: "s1".into(),
        set_id: "01A".into(),
        at: 61.5,
        updated_at,
        removed,
    };
    let record = |device: &str, row: SceneRow| SyncRecord {
        format: SYNC_FORMAT,
        device: device.into(),
        written_at: 1.0,
        profiles: vec![ProfileState {
            name: "André".into(),
            local_id: None,
            kids: false,
            progress: vec![],
            watched: vec![],
            unwatched: vec![],
            watchlist: vec![],
            collections: vec![],
            preferences: vec![],
            scenes: vec![row],
            title_stats: vec![],
            day_stats: vec![],
            roles: Default::default(),
            names: Default::default(),
        }],
        kids: vec![],
        editors_choice: vec![],
        households: vec![],
    };
    let scenes_of = |records: &[SyncRecord]| merge_states(records).profiles.remove(0).scenes;

    let removed_later = [record("a", scene(1000.0, false)), record("b", scene(2000.0, true))];
    assert_eq!(scenes_of(&removed_later), [scene(2000.0, true)]);
    let marked_later = [record("b", scene(2000.0, true)), record("a", scene(3000.0, false))];
    assert_eq!(scenes_of(&marked_later), [scene(3000.0, false)]);
}
