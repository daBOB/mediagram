//! Which index messages a push should unpin.
//!
//! The channel is meant to carry exactly one pinned `#mlib-index` message.
//! Keeping that true means remembering which one is current — and that memory
//! lives in `library.db`, which `rescan` exists precisely because people lose.
//!
//! Found by the live gate: after `rm library.db && rescan`, the next
//! `push-index` pinned a new snapshot and left the previous one pinned too,
//! because the id it would have unpinned went with the database.

use mediagram::commands::push_index::{pending_unpins, record_index_messages};
use mediagram::index::db;

fn open() -> rusqlite::Connection {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    // The directory has to outlive the connection.
    std::mem::forget(dir);
    conn
}

#[test]
fn a_fresh_index_has_nothing_to_unpin() {
    let conn = open();

    assert!(pending_unpins(&conn).is_empty());
}

#[test]
fn rescan_records_the_newest_snapshot_as_current() {
    let conn = open();

    record_index_messages(&conn, &[40, 61, 55]).unwrap();

    // The newest is the one a later push replaces, so it is what gets
    // unpinned next time; the older two are stale and unpinned as well.
    assert_eq!(pending_unpins(&conn), vec![61, 55, 40]);
}

#[test]
fn every_snapshot_a_rescan_saw_is_eventually_unpinned() {
    // The live failure had two pinned at once. A reader picking the wrong one
    // gets a stale library, so both have to be cleared, not just the newest.
    let conn = open();

    record_index_messages(&conn, &[61, 63]).unwrap();

    let pending = pending_unpins(&conn);
    assert!(pending.contains(&61));
    assert!(pending.contains(&63));
}

#[test]
fn recording_nothing_leaves_the_bookkeeping_alone() {
    let conn = open();
    record_index_messages(&conn, &[61]).unwrap();

    record_index_messages(&conn, &[]).unwrap();

    assert_eq!(pending_unpins(&conn), vec![61]);
}

#[test]
fn a_repeat_rescan_does_not_accumulate_duplicates() {
    let conn = open();

    record_index_messages(&conn, &[40, 61]).unwrap();
    record_index_messages(&conn, &[40, 61]).unwrap();

    assert_eq!(pending_unpins(&conn), vec![61, 40]);
}
