use super::*;
use crate::state::StateDb;
use crate::state::merge::{MergedProfile, MergedState};
use crate::state::{exchange, preferences, profiles};

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

fn pref(scope: &str, name: &str, value: &str, updated_at: f64) -> SyncPreference {
    SyncPreference {
        scope: scope.into(),
        name: name.into(),
        value: value.into(),
        updated_at,
    }
}

fn merged(rows: Vec<SyncPreference>) -> MergedState {
    MergedState {
        profiles: vec![MergedProfile {
            name: "andré".into(),
            display_name: "André".into(),
            preferences: rows,
            ..Default::default()
        }],
        ..Default::default()
    }
}

fn stored(db: &StateDb, id: &str) -> Vec<String> {
    let mut rows: Vec<String> = db
        .with(|conn| preferences::list_for(conn, id))
        .unwrap()
        .into_iter()
        .map(|row| format!("{}/{}={}", row.scope, row.name, row.value))
        .collect();
    rows.sort();
    rows
}

fn import(db: &StateDb, rows: Vec<SyncPreference>) -> u64 {
    db.with(|conn| exchange::import_merged(conn, &merged(rows)))
        .unwrap()
}

#[test]
fn export_carries_the_synced_names_and_leaves_per_device_ones_at_home() {
    let (_dir, db) = db();
    let id = profile(&db);
    db.with(|conn| {
        preferences::set(conn, &id, "profile", "subtitle", Some("en"))?;
        preferences::set(conn, &id, "show:x", "cue-size", Some("125"))?;
        preferences::set(conn, &id, "show:x", "audio", Some("eng"))
    })
    .unwrap();
    let sent = db.with(|conn| export_preferences(conn, &id)).unwrap();
    let mut names: Vec<_> = sent
        .iter()
        .map(|r| format!("{}/{}", r.scope, r.name))
        .collect();
    names.sort();
    assert_eq!(names, ["profile/subtitle", "show:x/cue-size"]);
}

#[test]
fn import_takes_a_newer_row_and_ignores_an_older_or_identical_one() {
    let (_dir, db) = db();
    let id = profile(&db);
    assert_eq!(
        import(&db, vec![pref("profile", "subtitle", "en", 1000.0)]),
        1
    );
    assert_eq!(
        import(&db, vec![pref("profile", "subtitle", "en", 1000.0)]),
        0
    );
    assert_eq!(
        import(&db, vec![pref("profile", "subtitle", "de", 500.0)]),
        0
    );
    assert_eq!(stored(&db, &id), ["profile/subtitle=en"]);
    assert_eq!(
        import(&db, vec![pref("profile", "subtitle", "de", 2000.0)]),
        1
    );
    assert_eq!(stored(&db, &id), ["profile/subtitle=de"]);
}

#[test]
fn an_equal_time_row_with_another_value_is_the_tie_break_winner_and_applies() {
    let (_dir, db) = db();
    let id = profile(&db);
    import(&db, vec![pref("profile", "subtitle", "en", 1000.0)]);
    assert_eq!(
        import(&db, vec![pref("profile", "subtitle", "de", 1000.0)]),
        1
    );
    assert_eq!(stored(&db, &id), ["profile/subtitle=de"]);
}

#[test]
fn import_ignores_a_name_that_does_not_travel() {
    let (_dir, db) = db();
    let id = profile(&db);
    import(&db, vec![pref("s", "audio", "eng", 1000.0)]);
    assert!(stored(&db, &id).is_empty());
}
