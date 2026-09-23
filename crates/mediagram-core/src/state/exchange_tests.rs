use super::*;
use crate::state::StateDb;
use crate::state::merge::MergedProfile;
use crate::state::record::{ProgressRow, WatchedRow};

fn db() -> (tempfile::TempDir, StateDb) {
    let dir = tempfile::tempdir().unwrap();
    let db = StateDb::new(dir.path().to_path_buf());
    (dir, db)
}

fn profile(db: &StateDb) -> String {
    db.with(|conn| profiles::create(conn, "André")).unwrap().unwrap().id
}

/// A device that never heard about `02B` must not lose the position it does
/// know about: importing a merge that only mentions `02B` may not touch
/// `01A`.
#[test]
fn import_never_deletes_a_row_the_merge_did_not_mention() {
    let (_dir, db) = db();
    let id = profile(&db);
    db.with(|conn| rows::set_progress(conn, &id, "01A", 100.0, None)).unwrap();

    let merged = MergedState {
        profiles: vec![MergedProfile {
            name: "andré".into(),
            display_name: "André".into(),
            progress: vec![ProgressRow { set_id: "02B".into(), at: 5.0, duration: None, updated_at: 1.0 }],
            watched: vec![],
            ..Default::default()
        }],
        ..Default::default()
    };
    db.with(|conn| import_merged(conn, &merged)).unwrap();

    let positions = db.with(|conn| rows::progress_for(conn, &id)).unwrap();
    assert!(positions.iter().any(|row| row.set_id == "01A"), "an untouched row must survive an import");
}

/// The tombstone rule: a completion deletes a position no newer than it,
/// even one the completion's own row never mentions by timestamp.
#[test]
fn a_completion_deletes_a_position_no_newer_than_it() {
    let (_dir, db) = db();
    let id = profile(&db);
    db.with(|conn| rows::set_progress(conn, &id, "01A", 100.0, None)).unwrap();

    let merged = MergedState {
        profiles: vec![MergedProfile {
            name: "andré".into(),
            display_name: "André".into(),
            progress: vec![],
            watched: vec![WatchedRow { set_id: "01A".into(), updated_at: 9_999_999_999_999.0 }],
            ..Default::default()
        }],
        ..Default::default()
    };
    db.with(|conn| import_merged(conn, &merged)).unwrap();

    assert_eq!(db.with(|conn| rows::progress_for(conn, &id)).unwrap(), Vec::new());
    assert_eq!(db.with(|conn| rows::watched_for(conn, &id)).unwrap().len(), 1);
}

/// A viewer named in the merge but never seen on this device gets a local
/// profile, spelled the way the merge's `displayName` says — never the
/// normalised identity.
#[test]
fn an_unknown_viewer_is_created_from_the_display_name() {
    let (_dir, db) = db();
    let merged = MergedState {
        profiles: vec![MergedProfile {
            name: "robin".into(),
            display_name: "Robin".into(),
            progress: vec![ProgressRow { set_id: "01A".into(), at: 5.0, duration: None, updated_at: 1.0 }],
            watched: vec![],
            ..Default::default()
        }],
        ..Default::default()
    };
    db.with(|conn| import_merged(conn, &merged)).unwrap();

    let names: Vec<String> = db.with(profiles::list).unwrap().into_iter().map(|p| p.name).collect();
    assert_eq!(names, vec!["Robin".to_string()]);
}

/// `state.db` sits beside `catalog/`, never inside it, so a refresh that
/// replaces the catalog wholesale must leave it alone.
#[test]
fn a_catalog_refresh_does_not_touch_state_db() {
    let (dir, db) = db();
    let id = profile(&db);
    db.with(|conn| rows::set_progress(conn, &id, "01A", 100.0, None)).unwrap();

    let catalog_root = dir.path().join("catalog");
    let incoming = catalog_root.join("incoming");
    std::fs::create_dir_all(&incoming).unwrap();
    crate::versions::install_staged(&catalog_root, &incoming, "v-1").unwrap();

    let positions = db.with(|conn| rows::progress_for(conn, &id)).unwrap();
    assert_eq!(positions.len(), 1, "a catalog refresh must not touch state.db");
}
