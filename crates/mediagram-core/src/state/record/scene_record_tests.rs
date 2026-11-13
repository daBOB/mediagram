use serde_json::json;

use super::*;

#[test]
fn a_scene_row_reads_the_json_the_web_player_writes() {
    let live = json!({ "id": "a1", "setId": "S", "at": 61.5, "updatedAt": 1700000000000_i64 });
    assert_eq!(
        scene_row(&live),
        Some(SceneRow {
            id: "a1".into(),
            set_id: "S".into(),
            at: 61.5,
            updated_at: 1.7e12,
            removed: false
        })
    );
    let mut gone = live.clone();
    gone["removed"] = json!(true);
    assert!(scene_row(&gone).unwrap().removed);
}

#[test]
fn a_bad_scene_row_is_dropped_alone() {
    let ok = json!({ "id": "a1", "setId": "S", "at": 1, "updatedAt": 5 });
    for (key, bad) in [
        ("at", json!(null)),
        ("at", json!(-1)),
        ("id", json!(5)),
        ("id", json!("a-b")),
        ("id", json!("x".repeat(65))),
        ("setId", json!("")),
        ("updatedAt", json!(0)),
    ] {
        let mut row = ok.clone();
        row[key] = bad;
        assert_eq!(scene_row(&row), None, "{key}");
    }
    let mut missing = ok.clone();
    missing.as_object_mut().unwrap().remove("at");
    assert_eq!(scene_row(&missing), None);
    assert!(scene_row(&ok).is_some());
}

#[test]
fn a_live_row_writes_no_removed_key() {
    let row = scene_row(&json!({ "id": "a", "setId": "S", "at": 0, "updatedAt": 5 })).unwrap();
    assert!(!serde_json::to_string(&row).unwrap().contains("removed"));
}
