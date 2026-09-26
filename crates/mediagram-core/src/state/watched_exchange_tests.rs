use super::*;
use crate::state::StateDb;
use crate::state::rows;

fn db_with_profile() -> (tempfile::TempDir, StateDb, String) {
    let dir = tempfile::tempdir().unwrap();
    let db = StateDb::new(dir.path().to_path_buf());
    let id = db
        .with(|conn| crate::state::profiles::create(conn, "André", false))
        .unwrap()
        .unwrap()
        .id;
    (dir, db, id)
}

#[test]
fn a_live_mark_and_its_removal_export_under_different_keys() {
    let (_dir, db, id) = db_with_profile();
    db.with(|conn| rows::set_watched(conn, &id, "01A", true))
        .unwrap();
    db.with(|conn| rows::set_watched(conn, &id, "01B", true))
        .unwrap();
    db.with(|conn| rows::set_watched(conn, &id, "01B", false))
        .unwrap();

    let (watched, unwatched) = db.with(|conn| export_watched(conn, &id)).unwrap();
    assert_eq!(
        watched.iter().map(|r| r.set_id.as_str()).collect::<Vec<_>>(),
        vec!["01A"]
    );
    assert_eq!(unwatched.len(), 1);
    assert_eq!(unwatched[0].set_id, "01B");
    assert!(unwatched[0].last_finished_at > 0.0);
    assert!(unwatched[0].updated_at > unwatched[0].last_finished_at);
}

/// A removal older than what this device already holds live must not
/// overwrite it — the merge already decided the live mark was newer.
#[test]
fn an_older_removal_does_not_overwrite_a_newer_live_mark() {
    let (_dir, db, id) = db_with_profile();
    db.with(|conn| rows::set_watched(conn, &id, "01A", true))
        .unwrap();
    let finished_at = db
        .with(|conn| rows::watched_for(conn, &id))
        .unwrap()[0]
        .finished_at;

    db.with(|conn| {
        import_unwatched(
            conn,
            &id,
            &[UnwatchedRow {
                set_id: "01A".into(),
                updated_at: (finished_at - 100) as f64,
                last_finished_at: (finished_at - 200) as f64,
            }],
        )
    })
    .unwrap();

    assert_eq!(
        db.with(|conn| rows::watched_for(conn, &id))
            .unwrap()
            .iter()
            .map(|r| r.set_id.as_str())
            .collect::<Vec<_>>(),
        vec!["01A"]
    );
}

/// A removal newer than what this device holds live replaces it outright.
#[test]
fn a_newer_removal_replaces_a_live_mark() {
    let (_dir, db, id) = db_with_profile();
    db.with(|conn| rows::set_watched(conn, &id, "01A", true))
        .unwrap();
    let finished_at = db
        .with(|conn| rows::watched_for(conn, &id))
        .unwrap()[0]
        .finished_at;

    db.with(|conn| {
        import_unwatched(
            conn,
            &id,
            &[UnwatchedRow {
                set_id: "01A".into(),
                updated_at: (finished_at + 1000) as f64,
                last_finished_at: finished_at as f64,
            }],
        )
    })
    .unwrap();

    assert_eq!(db.with(|conn| rows::watched_for(conn, &id)).unwrap(), Vec::new());
}

/// An unwatched row for a title this device has never heard of still
/// materialises a tombstone, the same way an unmet viewer still gets a
/// local profile.
#[test]
fn an_unwatched_row_for_an_unknown_title_still_creates_a_tombstone() {
    let (_dir, db, id) = db_with_profile();
    let changed = db
        .with(|conn| {
            import_unwatched(
                conn,
                &id,
                &[UnwatchedRow {
                    set_id: "01A".into(),
                    updated_at: 2000.0,
                    last_finished_at: 1000.0,
                }],
            )
        })
        .unwrap();
    assert_eq!(changed, 1);
    assert_eq!(db.with(|conn| rows::watched_for(conn, &id)).unwrap(), Vec::new());
}

/// A removal exactly as new as a local live mark still applies:
/// `merge::watched::reconcile` gives that tie to the removal, and import
/// must agree with it rather than favouring whichever fact happened to be
/// standing already (R2).
#[test]
fn a_removal_tied_exactly_with_a_local_live_mark_still_applies() {
    let (_dir, db, id) = db_with_profile();
    db.with(|conn| rows::set_watched(conn, &id, "01A", true))
        .unwrap();
    let finished_at = db
        .with(|conn| rows::watched_for(conn, &id))
        .unwrap()[0]
        .finished_at;

    db.with(|conn| {
        import_unwatched(
            conn,
            &id,
            &[UnwatchedRow {
                set_id: "01A".into(),
                updated_at: finished_at as f64,
                last_finished_at: finished_at as f64,
            }],
        )
    })
    .unwrap();

    assert_eq!(db.with(|conn| rows::watched_for(conn, &id)).unwrap(), Vec::new());
}

/// Re-marking watched always beats a future-dated removal, even one this
/// device only knows about through an import carrying another device's
/// (ahead-running) clock (R1).
#[test]
fn remarking_watched_always_beats_a_future_dated_removal() {
    let (_dir, db, id) = db_with_profile();
    let far_future = (crate::state::profiles::now_ms() + 100_000) as f64;

    db.with(|conn| {
        import_unwatched(
            conn,
            &id,
            &[UnwatchedRow {
                set_id: "01A".into(),
                updated_at: far_future,
                last_finished_at: far_future - 1000.0,
            }],
        )
    })
    .unwrap();
    assert_eq!(db.with(|conn| rows::watched_for(conn, &id)).unwrap(), Vec::new());

    db.with(|conn| rows::set_watched(conn, &id, "01A", true))
        .unwrap();
    let (watched, _) = db.with(|conn| export_watched(conn, &id)).unwrap();
    assert_eq!(watched.len(), 1);
    assert!(watched[0].updated_at > far_future);
}
