use super::*;
use crate::state::merge::{MergedProfile, MergedState};
use crate::state::{StateDb, exchange, profiles, scenes};

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

fn row(updated_at: f64, removed: bool) -> SceneRow {
    SceneRow { id: "s1".into(), set_id: "01A".into(), at: 61.5, updated_at, removed }
}

fn merged(rows: Vec<SceneRow>) -> MergedState {
    MergedState {
        profiles: vec![MergedProfile {
            name: "andré".into(),
            display_name: "André".into(),
            scenes: rows,
            ..Default::default()
        }],
        ..Default::default()
    }
}

#[test]
fn scenes_round_trip_through_export_and_import() {
    let (_dir, db) = db();
    let me = profile(&db);
    let id = db.with(|c| scenes::mark(c, &me, "01A", 61.5)).unwrap().unwrap();
    let removed = db.with(|c| scenes::mark(c, &me, "01A", 9.0)).unwrap().unwrap();
    db.with(|c| scenes::remove(c, &me, &removed)).unwrap();

    let sent = db.with(|c| export_scenes(c, &me)).unwrap();
    assert_eq!(sent.len(), 2);
    assert!(sent.iter().any(|r| r.id == id && !r.removed && r.at == 61.5));
    assert!(sent.iter().any(|r| r.id == removed && r.removed));

    // A fresh device taking the same rows in ends up with the same live mark.
    let (_dir2, other) = self::db();
    let you = profile(&other);
    other.with(|c| import_scenes(c, &you, &sent)).unwrap();
    let live = other.with(|c| scenes::live_for(c, &you)).unwrap();
    assert_eq!(live.len(), 1);
    assert_eq!((live[0].id.as_str(), live[0].at), (id.as_str(), 61.5));
}

#[test]
fn a_scene_marked_elsewhere_arrives_through_import_merged() {
    let (_dir, db) = db();
    let me = profile(&db);
    db.with(|c| exchange::import_merged(c, &merged(vec![row(1000.0, false)]))).unwrap();
    let live = db.with(|c| scenes::live_for(c, &me)).unwrap();
    assert_eq!((live[0].id.as_str(), live[0].set_id.as_str(), live[0].marked_at), ("s1", "01A", 1000));
}

#[test]
fn a_later_removal_from_another_device_wins() {
    let (_dir, db) = db();
    let me = profile(&db);
    db.with(|c| import_scenes(c, &me, &[row(1000.0, false)])).unwrap();
    assert_eq!(db.with(|c| import_scenes(c, &me, &[row(2000.0, true)])).unwrap(), 1);

    assert!(db.with(|c| scenes::live_for(c, &me)).unwrap().is_empty());
    assert_eq!(db.with(|c| export_scenes(c, &me)).unwrap(), [row(2000.0, true)]);
    // The older live row no longer revives it, and the same removal is no change.
    assert_eq!(db.with(|c| import_scenes(c, &me, &[row(1000.0, false), row(2000.0, true)])).unwrap(), 0);
}

#[test]
fn an_id_another_profile_holds_is_not_moved() {
    let (_dir, db) = db();
    let me = profile(&db);
    let other = db
        .with(|c| profiles::create(c, "Bea", false))
        .unwrap()
        .unwrap()
        .id;
    db.with(|c| import_scenes(c, &me, &[row(1000.0, false)])).unwrap();
    assert_eq!(db.with(|c| import_scenes(c, &other, &[row(9000.0, true)])).unwrap(), 0);
    assert_eq!(db.with(|c| scenes::live_for(c, &me)).unwrap().len(), 1);
}
