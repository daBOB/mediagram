//! A set's life in the index between planning and completion: its source,
//! the remux to delete once it is up, and completing it in one transaction.

use std::path::Path;

use mediagram::index::lifecycle::{self, Completed};
use mediagram::index::set_row::SetRow;
use mediagram::index::status::SetStatus;
use mediagram::index::{db, pins, sets};

mod support;
use support::upload::sample_caption;

const SET: &str = "01J0000000000000000000LIF1";

fn planned(temporary: bool) -> (tempfile::TempDir, rusqlite::Connection) {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    sets::insert_set(&conn, &SetRow::from_caption(&sample_caption(SET, 10, 1), 1)).unwrap();
    let source = Path::new("/media/film.faststart.mp4");
    lifecycle::record_source(&conn, SET, source, temporary).unwrap();
    (dir, conn)
}

#[test]
fn a_pending_set_knows_where_it_uploads_from() {
    let (_dir, conn) = planned(false);
    assert_eq!(
        lifecycle::source_of(&conn, SET).unwrap().as_deref(),
        Some(Path::new("/media/film.faststart.mp4"))
    );
}

/// Complete, with nothing left behind: the source forgotten, and the remux
/// handed back to be deleted.
#[test]
fn completing_a_set_forgets_its_source_and_hands_back_its_remux() {
    let (_dir, conn) = planned(true);

    let completed = lifecycle::complete(&conn, SET, &"a".repeat(64)).unwrap();

    assert_eq!(
        completed,
        Completed {
            temp: Some("/media/film.faststart.mp4".into())
        }
    );
    assert_eq!(lifecycle::source_of(&conn, SET).unwrap(), None);
    assert_eq!(
        sets::get_set(&conn, SET).unwrap().unwrap().status,
        SetStatus::Complete
    );
}

/// A person's own file is never recorded as temporary, so it is never
/// handed back for deletion.
#[test]
fn a_set_uploaded_from_the_persons_own_file_hands_back_nothing() {
    let (_dir, conn) = planned(false);
    assert_eq!(
        lifecycle::complete(&conn, SET, &"a".repeat(64)).unwrap(),
        Completed { temp: None }
    );
}

/// One transaction: if the source cannot be forgotten, the set is not
/// complete either, and a resume still finds where it uploads from.
#[test]
fn a_completion_that_cannot_commit_leaves_the_set_pending_with_its_source() {
    let (_dir, conn) = planned(false);
    conn.execute_batch(
        "CREATE TRIGGER refuse_source_cleanup BEFORE DELETE ON meta
         WHEN OLD.key LIKE 'source:%' BEGIN SELECT RAISE(ABORT, 'cleanup failed'); END;",
    )
    .unwrap();

    lifecycle::complete(&conn, SET, &"a".repeat(64)).unwrap_err();

    assert_eq!(
        sets::get_set(&conn, SET).unwrap().unwrap().status,
        SetStatus::Pending
    );
    assert!(lifecycle::source_of(&conn, SET).unwrap().is_some());
}

#[test]
fn forgetting_a_set_forgets_its_source_and_remux() {
    let (_dir, conn) = planned(true);
    lifecycle::forget(&conn, SET).unwrap();
    assert_eq!(lifecycle::source_of(&conn, SET).unwrap(), None);
    assert_eq!(
        lifecycle::complete(&conn, SET, &"a".repeat(64)).unwrap(),
        Completed { temp: None }
    );
}

/// Existing indexes resume through these exact spellings; there is no
/// migration, so they may never change.
#[test]
fn the_keys_an_existing_index_carries_still_read() {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    sets::insert_set(&conn, &SetRow::from_caption(&sample_caption(SET, 10, 1), 1)).unwrap();
    db::set_meta(&conn, &format!("source:{SET}"), "/a.mkv").unwrap();
    db::set_meta(&conn, &format!("tmp:{SET}"), "/a.mkv").unwrap();

    assert_eq!(
        lifecycle::source_of(&conn, SET).unwrap().as_deref(),
        Some(Path::new("/a.mkv"))
    );
    let completed = lifecycle::complete(&conn, SET, &"a".repeat(64)).unwrap();
    assert_eq!(completed.temp.as_deref(), Some(Path::new("/a.mkv")));
}

/// A completed upload is owed a publish in the same transaction that
/// completes it, so no crash can leave it complete and forgotten.
#[test]
fn completing_a_set_owes_a_publish() {
    let (_dir, conn) = planned(false);
    assert_eq!(pins::publish_owed(&conn).unwrap(), None);
    lifecycle::complete(&conn, SET, &"a".repeat(64)).unwrap();
    assert!(pins::publish_owed(&conn).unwrap().is_some());
}

/// A set removed while it uploaded is not "added" and owes nothing — and
/// the caller, told so, deletes no source as if it had been.
#[test]
fn a_set_no_longer_in_the_index_cannot_be_completed() {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();

    let err = lifecycle::complete(&conn, SET, &"a".repeat(64)).unwrap_err();

    assert!(
        format!("{err:#}").contains("no longer in the index"),
        "{err:#}"
    );
    assert_eq!(pins::publish_owed(&conn).unwrap(), None);
}
