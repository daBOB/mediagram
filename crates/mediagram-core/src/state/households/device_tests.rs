use super::*;
use crate::state::{StateDb, profiles, sync};

fn db() -> (tempfile::TempDir, StateDb) {
    let dir = tempfile::tempdir().unwrap();
    let db = StateDb::new(dir.path().to_path_buf());
    (dir, db)
}

fn sql(db: &StateDb, statements: &str) {
    db.with(|conn| conn.execute_batch(statements)).unwrap();
}

const TWO_HOUSEHOLDS: &str = "INSERT INTO households(id, name) VALUES ('h2', 'Oma');
     INSERT INTO profiles(id, name, created_at) VALUES ('a', 'a', 1);
     INSERT INTO profiles(id, name, created_at, household_id) VALUES ('b', 'b', 2, 'h2');";

fn remembered(db: &StateDb) -> Option<String> {
    db.with(stored).unwrap()
}

#[test]
fn a_device_that_chose_before_joins_its_profiles_household() {
    let (_dir, db) = db();
    sql(&db, TWO_HOUSEHOLDS);
    db.with(|conn| profiles::choose(conn, "b")).unwrap();

    assert_eq!(db.with(joined).unwrap(), Some("h2".to_string()));
    assert_eq!(remembered(&db), Some("h2".to_string()), "the answer is remembered");

    db.with(|conn| profiles::choose(conn, "a")).unwrap();
    assert_eq!(db.with(joined).unwrap(), Some("h2".to_string()), "a later choice does not move it");
}

#[test]
fn a_new_device_joins_the_only_household_once_heard() {
    let (_dir, db) = db();
    sql(&db, "INSERT INTO profiles(id, name, created_at) VALUES ('a', 'a', 1);");

    assert_eq!(db.with(joined).unwrap(), None, "nothing heard yet");
    assert_eq!(remembered(&db), None);
    assert!(db.with(profiles_here).unwrap().is_empty());

    db.with(|conn| sync::mark_round_imported(conn, "library")).unwrap();

    assert_eq!(db.with(joined).unwrap(), Some("home".to_string()));
    assert_eq!(db.with(profiles_here).unwrap().len(), 1);
}

#[test]
fn a_new_device_with_two_households_joins_none() {
    let (_dir, db) = db();
    sql(&db, TWO_HOUSEHOLDS);
    db.with(|conn| sync::mark_round_imported(conn, "library")).unwrap();

    assert_eq!(db.with(joined).unwrap(), None);
    assert_eq!(remembered(&db), None);
    assert!(db.with(profiles_here).unwrap().is_empty());
    assert_eq!(db.with(chosen_here).unwrap(), None);
}

#[test]
fn profiles_lists_only_the_joined_household() {
    let (_dir, db) = db();
    sql(&db, TWO_HOUSEHOLDS);
    sql(&db, "INSERT INTO state_meta(key, value) VALUES ('joined_household', 'h2')");

    let ids: Vec<String> = db.with(profiles_here).unwrap().into_iter().map(|p| p.id).collect();

    assert_eq!(ids, ["b"]);
}

#[test]
fn the_chosen_profile_outside_the_joined_household_is_nobody() {
    let (_dir, db) = db();
    sql(&db, TWO_HOUSEHOLDS);
    sql(&db, "INSERT INTO state_meta(key, value) VALUES ('joined_household', 'h2')");

    db.with(|conn| profiles::choose(conn, "a")).unwrap();
    assert_eq!(db.with(chosen_here).unwrap(), None);

    db.with(|conn| profiles::choose(conn, "b")).unwrap();
    assert_eq!(db.with(chosen_here).unwrap(), Some("b".to_string()));
}

#[test]
fn a_device_whose_household_was_removed_follows_its_chosen_profile() {
    let (_dir, db) = db();
    sql(&db, TWO_HOUSEHOLDS);
    sql(&db, "INSERT INTO state_meta(key, value) VALUES ('joined_household', 'h2')");
    db.with(|conn| profiles::choose(conn, "a")).unwrap();
    sql(&db, "UPDATE households SET removed = 1 WHERE id = 'h2'");

    assert_eq!(db.with(joined).unwrap(), Some("home".to_string()));
    assert_eq!(remembered(&db), Some("home".to_string()));

    sql(&db, "CREATE TRIGGER no_more_writes BEFORE UPDATE ON state_meta BEGIN SELECT RAISE(ABORT, 'rewritten'); END");
    assert_eq!(db.with(joined).unwrap(), Some("home".to_string()), "a second call writes nothing");
}
