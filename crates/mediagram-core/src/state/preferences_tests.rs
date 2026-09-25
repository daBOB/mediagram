use super::*;
use crate::state::StateDb;

fn db() -> (tempfile::TempDir, StateDb) {
    let dir = tempfile::tempdir().unwrap();
    let db = StateDb::new(dir.path().to_path_buf());
    (dir, db)
}

fn a_profile(db: &StateDb) -> String {
    db.with(|conn| crate::state::profiles::create(conn, "André", false)).unwrap().unwrap().id
}

#[test]
fn setting_a_preference_is_read_back() {
    let (_dir, db) = db();
    let profile = a_profile(&db);

    assert!(db.with(|conn| set(conn, &profile, "show:severance", "audio", Some("en"))).unwrap());

    let rows = db.with(|conn| list_for(conn, &profile)).unwrap();
    assert_eq!(
        rows,
        vec![PreferenceRow { scope: "show:severance".into(), name: "audio".into(), value: "en".into() }]
    );
}

#[test]
fn setting_it_again_replaces_rather_than_duplicates() {
    let (_dir, db) = db();
    let profile = a_profile(&db);
    db.with(|conn| set(conn, &profile, "show:severance", "audio", Some("en"))).unwrap();

    db.with(|conn| set(conn, &profile, "show:severance", "audio", Some("de"))).unwrap();

    let rows = db.with(|conn| list_for(conn, &profile)).unwrap();
    assert_eq!(rows.len(), 1);
    assert_eq!(rows[0].value, "de");
}

#[test]
fn an_empty_value_forgets_the_choice() {
    let (_dir, db) = db();
    let profile = a_profile(&db);
    db.with(|conn| set(conn, &profile, "show:severance", "audio", Some("en"))).unwrap();

    assert!(db.with(|conn| set(conn, &profile, "show:severance", "audio", Some("   "))).unwrap());

    assert!(db.with(|conn| list_for(conn, &profile)).unwrap().is_empty());
}

#[test]
fn a_missing_value_forgets_the_choice() {
    let (_dir, db) = db();
    let profile = a_profile(&db);
    db.with(|conn| set(conn, &profile, "show:severance", "audio", Some("en"))).unwrap();

    assert!(db.with(|conn| set(conn, &profile, "show:severance", "audio", None)).unwrap());

    assert!(db.with(|conn| list_for(conn, &profile)).unwrap().is_empty());
}

#[test]
fn a_blank_scope_or_name_is_refused_and_nothing_is_stored() {
    let (_dir, db) = db();
    let profile = a_profile(&db);

    assert!(!db.with(|conn| set(conn, &profile, "   ", "audio", Some("en"))).unwrap());
    assert!(!db.with(|conn| set(conn, &profile, "show:severance", "  ", Some("en"))).unwrap());
    assert!(db.with(|conn| list_for(conn, &profile)).unwrap().is_empty());
}

#[test]
fn every_stored_string_is_capped_rather_than_refused() {
    let (_dir, db) = db();
    let profile = a_profile(&db);
    let long = "x".repeat(500);

    assert!(db.with(|conn| set(conn, &profile, &long, "audio", Some(&long))).unwrap());

    let rows = db.with(|conn| list_for(conn, &profile)).unwrap();
    assert_eq!(rows[0].scope.len(), MAX_PREFERENCE);
    assert_eq!(rows[0].value.len(), MAX_PREFERENCE);
}

/// A profile removed takes its preferences with it — the same
/// `ON DELETE CASCADE` every other per-profile table relies on.
#[test]
fn deleting_a_profile_cascades_to_its_preferences() {
    let (_dir, db) = db();
    let profile = a_profile(&db);
    db.with(|conn| set(conn, &profile, "show:severance", "audio", Some("en"))).unwrap();

    assert!(db.with(|conn| crate::state::profiles::delete(conn, &profile)).unwrap());

    // The profile is gone, so a direct count rather than `list_for`, which
    // reads just as happily for a profile id that never existed at all.
    let left: i64 =
        db.with(|conn| conn.query_row("SELECT COUNT(*) FROM preferences", [], |row| row.get(0))).unwrap();
    assert_eq!(left, 0);
}
