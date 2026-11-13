use super::*;
use crate::state::StateDb;

fn db() -> (tempfile::TempDir, StateDb) {
    let dir = tempfile::tempdir().unwrap();
    let db = StateDb::new(dir.path().to_path_buf());
    (dir, db)
}

fn a_profile(db: &StateDb) -> String {
    db.with(|conn| crate::state::profiles::create(conn, "André", false))
        .unwrap()
        .unwrap()
        .id
}

#[test]
fn marking_twice_on_one_title_keeps_both_newest_first() {
    let (_dir, db) = db();
    let me = a_profile(&db);
    let first = db.with(|c| mark(c, &me, "01A", 61.5)).unwrap().unwrap();
    // Same millisecond is fine: the rowid decides.
    let second = db.with(|c| mark(c, &me, "01A", 3600.0)).unwrap().unwrap();

    assert_ne!(first, second);
    assert_eq!(first.len(), 16);
    let ids: Vec<String> = db.with(|c| live_for(c, &me)).unwrap().into_iter().map(|m| m.id).collect();
    assert_eq!(ids, [second, first]);
}

#[test]
fn removing_keeps_a_tombstone() {
    let (_dir, db) = db();
    let me = a_profile(&db);
    let id = db.with(|c| mark(c, &me, "01A", 10.0)).unwrap().unwrap();

    assert!(db.with(|c| remove(c, &me, &id)).unwrap());
    assert!(!db.with(|c| remove(c, &me, &id)).unwrap());
    assert!(db.with(|c| live_for(c, &me)).unwrap().is_empty());
    let kept: i64 = db
        .with(|c| c.query_row("SELECT count(*) FROM scenes WHERE removed_at IS NOT NULL", [], |r| r.get(0)))
        .unwrap();
    assert_eq!(kept, 1);
}

#[test]
fn a_bad_second_or_a_missing_profile_marks_nothing() {
    let (_dir, db) = db();
    let me = a_profile(&db);
    assert_eq!(db.with(|c| mark(c, &me, "01A", -1.0)).unwrap(), None);
    assert_eq!(db.with(|c| mark(c, &me, "01A", f64::NAN)).unwrap(), None);
    assert_eq!(db.with(|c| mark(c, "nobody", "01A", 1.0)).unwrap(), None);
}
