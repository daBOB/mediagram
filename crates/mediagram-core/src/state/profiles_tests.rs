use super::*;
use crate::state::StateDb;

fn db() -> (tempfile::TempDir, StateDb) {
    let dir = tempfile::tempdir().unwrap();
    let db = StateDb::new(dir.path().to_path_buf());
    (dir, db)
}

#[test]
fn creating_and_listing_round_trips_a_name() {
    let (_dir, db) = db();
    db.with(|conn| create(conn, "André")).unwrap();
    let names: Vec<String> = db.with(list).unwrap().into_iter().map(|p| p.name).collect();
    assert_eq!(names, vec!["André".to_string()]);
}

#[test]
fn a_blank_name_creates_nothing() {
    let (_dir, db) = db();
    assert_eq!(db.with(|conn| create(conn, "   ")).unwrap(), None);
}

#[test]
fn choosing_an_unknown_id_is_reported_false_and_remembers_nothing() {
    let (_dir, db) = db();
    assert!(!db.with(|conn| choose(conn, "nope")).unwrap());
    assert_eq!(db.with(chosen).unwrap(), None);
}

#[test]
fn choosing_a_real_profile_is_remembered() {
    let (_dir, db) = db();
    let id = db.with(|conn| create(conn, "André")).unwrap().unwrap().id;
    db.with(|conn| choose(conn, &id)).unwrap();
    assert_eq!(db.with(chosen).unwrap(), Some(id));
}

#[test]
fn deleting_an_unknown_id_reports_false() {
    let (_dir, db) = db();
    assert!(!db.with(|conn| delete(conn, "nope")).unwrap());
}

#[test]
fn deleting_a_real_profile_removes_it_from_the_list() {
    let (_dir, db) = db();
    let id = db.with(|conn| create(conn, "André")).unwrap().unwrap().id;

    assert!(db.with(|conn| delete(conn, &id)).unwrap());

    assert_eq!(db.with(list).unwrap(), Vec::new());
}

/// `chosen` checks the profile still exists on every read rather than
/// trusting what was last written, so deleting the chosen profile clears it
/// without `delete` having to know it was the one chosen.
#[test]
fn deleting_the_chosen_profile_clears_it() {
    let (_dir, db) = db();
    let id = db.with(|conn| create(conn, "André")).unwrap().unwrap().id;
    db.with(|conn| choose(conn, &id)).unwrap();

    db.with(|conn| delete(conn, &id)).unwrap();

    assert_eq!(db.with(chosen).unwrap(), None);
}

/// A second machine's document mentions a viewer this one has never seen:
/// `profile_named` has to create them rather than drop the sync.
#[test]
fn profile_named_creates_an_unseen_viewer_and_reuses_them_after() {
    let (_dir, db) = db();
    let first = db.with(|conn| profile_named(conn, "andré", Some("André"))).unwrap().unwrap();
    let second = db.with(|conn| profile_named(conn, "ANDRÉ", Some("ANDRÉ"))).unwrap().unwrap();
    assert_eq!(first, second, "the same viewer, spelled differently, is one profile");
}
