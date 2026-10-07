//! Which index messages a push should unpin.
//!
//! The channel is meant to carry exactly one pinned `#mlib-index` message.
//! Keeping that true means remembering which one is current — and that memory
//! lives in `library.db`, which `rescan` exists precisely because people lose.
//!
//! The case pinned here: after `rm library.db && rescan`, the next
//! `push-index` must unpin the previous snapshot rather than leave it pinned
//! beside the new one, although the id it would have unpinned went with the
//! database.

use mediagram::index::db;
use mediagram::index::pins::{
    pending_unpins, pulled, record_index_messages, record_pulled, record_unpin_outcome,
};

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

    assert!(pending_unpins(&conn).unwrap().is_empty());
}

#[test]
fn rescan_records_the_newest_snapshot_as_current() {
    let conn = open();

    record_index_messages(&conn, &[40, 61, 55]).unwrap();

    // The newest is the one a later push replaces, so it is what gets
    // unpinned next time; the older two are stale and unpinned as well.
    assert_eq!(pending_unpins(&conn).unwrap(), vec![61, 55, 40]);
}

#[test]
fn every_snapshot_a_rescan_saw_is_eventually_unpinned() {
    // The live failure had two pinned at once. A reader picking the wrong one
    // gets a stale library, so both have to be cleared, not just the newest.
    let conn = open();

    record_index_messages(&conn, &[61, 63]).unwrap();

    let pending = pending_unpins(&conn).unwrap();
    assert!(pending.contains(&61));
    assert!(pending.contains(&63));
}

#[test]
fn recording_nothing_leaves_the_bookkeeping_alone() {
    let conn = open();
    record_index_messages(&conn, &[61]).unwrap();

    record_index_messages(&conn, &[]).unwrap();

    assert_eq!(pending_unpins(&conn).unwrap(), vec![61]);
}

#[test]
fn a_repeat_rescan_does_not_accumulate_duplicates() {
    let conn = open();

    record_index_messages(&conn, &[40, 61]).unwrap();
    record_index_messages(&conn, &[40, 61]).unwrap();

    assert_eq!(pending_unpins(&conn).unwrap(), vec![61, 40]);
}

/// The defect this guards: a push whose unpin returned `Ok` without taking
/// effect erased the id from both keys, so no later push could find it. Only
/// a `rescan`, which reads the pins off the channel, could — and a push that
/// cannot clean up after itself is a channel with two pinned indexes in it.
#[test]
fn an_id_that_could_not_be_proved_unpinned_survives_the_push() {
    let conn = open();
    record_index_messages(&conn, &[1558]).unwrap();

    // What a push does when the channel still reports 1558 as pinned.
    record_unpin_outcome(&conn, &[1558]).unwrap();

    assert_eq!(
        pending_unpins(&conn).unwrap(),
        vec![1558],
        "the next push has to try again"
    );
}

#[test]
fn an_id_proved_unpinned_is_forgotten() {
    let conn = open();
    record_index_messages(&conn, &[1558]).unwrap();

    record_unpin_outcome(&conn, &[]).unwrap();

    assert!(pending_unpins(&conn).unwrap().is_empty());
}

/// A push clears what it proved and keeps what it did not, in one pass.
#[test]
fn a_push_keeps_only_what_it_could_not_clear() {
    let conn = open();
    record_index_messages(&conn, &[1561, 1558, 1539]).unwrap();

    record_unpin_outcome(&conn, &[1558]).unwrap();

    assert_eq!(pending_unpins(&conn).unwrap(), vec![1558]);
}

/// Which channel index the local index last took in decides whether a
/// publish has anything to pull; losing track of it may only ever cost a
/// pull, never skip one.
#[test]
fn the_pulled_channel_index_is_remembered_and_forgotten() {
    let conn = open();
    assert_eq!(pulled(&conn).unwrap(), None);

    record_pulled(&conn, Some(1561)).unwrap();
    assert_eq!(pulled(&conn).unwrap(), Some(1561));

    record_pulled(&conn, None).unwrap();
    assert_eq!(pulled(&conn).unwrap(), None);
}
