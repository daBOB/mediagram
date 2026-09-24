use super::*;

#[test]
fn retiring_an_unopened_store_prevents_any_later_io() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("unopened");
    let db = StateDb::new(path.clone());
    db.retire();
    db.retire();
    assert!(matches!(*db.conn.lock().unwrap(), LocalState::Retired));
    assert!(
        db.with(|_| -> rusqlite::Result<()> { panic!("retired operation ran") })
            .is_none()
    );
    assert!(!path.exists());
}

#[test]
fn retiring_an_open_store_closes_it_without_removing_persisted_profiles() {
    let dir = tempfile::tempdir().unwrap();
    let db = StateDb::new(dir.path().to_path_buf());
    let profile = db
        .with(|conn| profiles::create(conn, "Kept"))
        .flatten()
        .unwrap();
    db.retire();
    assert!(matches!(*db.conn.lock().unwrap(), LocalState::Retired));
    assert!(db.with(profiles::list).is_none());
    let replacement = StateDb::new(dir.path().to_path_buf());
    assert_eq!(replacement.with(profiles::list).unwrap(), vec![profile]);
}

#[test]
fn retirement_closes_a_connection_even_after_an_action_poisoned_its_mutex() {
    let dir = tempfile::tempdir().unwrap();
    let db = StateDb::new(dir.path().to_path_buf());
    let failed = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        db.with(|_| -> rusqlite::Result<()> { panic!("interrupted database action") });
    }));
    assert!(failed.is_err());
    db.retire();
    assert!(db.conn.is_poisoned());
    assert!(matches!(
        *db.conn.lock().unwrap_or_else(|error| error.into_inner()),
        LocalState::Retired
    ));
    std::fs::remove_dir_all(dir.path()).unwrap();
    assert!(db.with(profiles::list).is_none());
    assert!(!dir.path().exists());
}
