use super::*;
use crate::state::StateDb;
use crate::state::profiles;

fn profile(db: &StateDb) -> String {
    db.with(|conn| profiles::create(conn, "André", false))
        .unwrap()
        .unwrap()
        .id
}

#[test]
fn setting_progress_round_trips() {
    let dir = tempfile::tempdir().unwrap();
    let db = StateDb::new(dir.path().to_path_buf());
    let id = profile(&db);
    db.with(|conn| set_progress(conn, &id, "01A", 742.0, Some(1204.0)))
        .unwrap();
    let rows = db.with(|conn| progress_for(conn, &id)).unwrap();
    assert_eq!(rows.len(), 1);
    assert_eq!(rows[0].set_id, "01A");
    assert_eq!(rows[0].at, 742.0);
}

/// Finishing a title must clear the resume position — otherwise the
/// Continue shelf and "watched it all" would disagree with each other.
#[test]
fn finishing_a_title_clears_its_position() {
    let dir = tempfile::tempdir().unwrap();
    let db = StateDb::new(dir.path().to_path_buf());
    let id = profile(&db);
    db.with(|conn| set_progress(conn, &id, "01A", 742.0, None))
        .unwrap();

    db.with(|conn| set_watched(conn, &id, "01A", true)).unwrap();

    assert_eq!(db.with(|conn| progress_for(conn, &id)).unwrap(), Vec::new());
    assert_eq!(db.with(|conn| watched_for(conn, &id)).unwrap().len(), 1);
}

/// Taking a mark back leaves any position exactly where it was: `set_watched`
/// pairs with `clear_progress` only on the way to `true`, never on the way
/// back, and a resume point left in place must survive the un-mark.
#[test]
fn taking_a_mark_back_never_touches_a_position() {
    let dir = tempfile::tempdir().unwrap();
    let db = StateDb::new(dir.path().to_path_buf());
    let id = profile(&db);
    // After the mark, not before: `set_watched(true)` already clears the
    // position, so a position set only before it would leave this vacuous.
    db.with(|conn| set_watched(conn, &id, "01A", true)).unwrap();
    db.with(|conn| set_progress(conn, &id, "01A", 300.0, None))
        .unwrap();
    db.with(|conn| set_watched(conn, &id, "01A", false))
        .unwrap();

    assert_eq!(db.with(|conn| watched_for(conn, &id)).unwrap(), Vec::new());
    let progress = db.with(|conn| progress_for(conn, &id)).unwrap();
    assert_eq!(progress.len(), 1);
    assert_eq!(progress[0].at, 300.0);
}

/// A re-mark after a removal is visible again — the tombstone's `removed_at`
/// must clear, not just sit beside a bumped `finished_at`.
#[test]
fn a_remark_after_a_removal_is_watched_again() {
    let dir = tempfile::tempdir().unwrap();
    let db = StateDb::new(dir.path().to_path_buf());
    let id = profile(&db);
    db.with(|conn| set_watched(conn, &id, "01A", true)).unwrap();
    db.with(|conn| set_watched(conn, &id, "01A", false))
        .unwrap();
    db.with(|conn| set_watched(conn, &id, "01A", true)).unwrap();

    assert_eq!(
        db.with(|conn| watched_for(conn, &id))
            .unwrap()
            .iter()
            .map(|row| row.set_id.as_str())
            .collect::<Vec<_>>(),
        vec!["01A"]
    );
}

#[test]
fn adding_to_the_watchlist_twice_is_not_two_rows() {
    let dir = tempfile::tempdir().unwrap();
    let db = StateDb::new(dir.path().to_path_buf());
    let id = profile(&db);
    db.with(|conn| set_watchlisted(conn, &id, "01A", true))
        .unwrap();
    db.with(|conn| set_watchlisted(conn, &id, "01A", true))
        .unwrap();
    assert_eq!(
        db.with(|conn| watchlist_for(conn, &id)).unwrap(),
        vec!["01A".to_string()]
    );
}

/// Kids has no profile: two viewers on one player must see the same mark.
#[test]
fn kids_is_not_scoped_to_a_profile() {
    let dir = tempfile::tempdir().unwrap();
    let db = StateDb::new(dir.path().to_path_buf());
    db.with(|conn| set_kids(conn, "01A", true)).unwrap();
    assert_eq!(db.with(kids).unwrap(), vec!["01A".to_string()]);
}
