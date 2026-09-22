//! Which index messages a push should unpin.
//!
//! The channel is meant to carry exactly one pinned `#mlib-index` message.
//! Keeping that true means remembering which one is current — and that memory
//! lives in `library.db`, which `rescan` exists precisely because people lose.
//!
//! Found by the live gate: after `rm library.db && rescan`, the next
//! `push-index` pinned a new snapshot and left the previous one pinned too,
//! because the id it would have unpinned went with the database.

use mediagram::commands::push_index::{
    pending_unpins, record_index_messages, record_unpin_outcome,
};
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
        pending_unpins(&conn),
        vec![1558],
        "the next push has to try again"
    );
}

#[test]
fn an_id_proved_unpinned_is_forgotten() {
    let conn = open();
    record_index_messages(&conn, &[1558]).unwrap();

    record_unpin_outcome(&conn, &[]).unwrap();

    assert!(pending_unpins(&conn).is_empty());
}

/// A push clears what it proved and keeps what it did not, in one pass.
#[test]
fn a_push_keeps_only_what_it_could_not_clear() {
    let conn = open();
    record_index_messages(&conn, &[1561, 1558, 1539]).unwrap();

    record_unpin_outcome(&conn, &[1558]).unwrap();

    assert_eq!(pending_unpins(&conn), vec![1558]);
}

mod nothing_left_to_unpin {
    use grammers_mtsender::InvocationError;
    use mediagram::commands::push_index::message_is_gone;

    fn rpc(code: i32, message: &str) -> anyhow::Error {
        anyhow::Error::new(InvocationError::Rpc(
            grammers_tl_types::types::RpcError {
                error_code: code,
                error_message: message.to_string(),
            }
            .into(),
        ))
    }

    #[test]
    fn a_message_that_no_longer_exists_is_gone() {
        assert!(message_is_gone(&rpc(400, "MESSAGE_ID_INVALID")));
        assert!(message_is_gone(&rpc(400, "MESSAGE_NOT_MODIFIED")));
    }

    /// These arrive in the same 400 class and say nothing about the message.
    /// Reading them as "gone" would drop the id and strand the pin.
    #[test]
    fn a_channel_level_refusal_is_not_the_message_being_gone() {
        assert!(!message_is_gone(&rpc(400, "PEER_ID_INVALID")));
        assert!(!message_is_gone(&rpc(400, "CHANNEL_INVALID")));
        assert!(!message_is_gone(&rpc(400, "CHAT_WRITE_FORBIDDEN")));
    }

    #[test]
    fn a_server_error_or_a_flood_wait_is_never_gone() {
        assert!(!message_is_gone(&rpc(500, "MESSAGE_ID_INVALID")));
        assert!(!message_is_gone(&rpc(420, "FLOOD_WAIT_31")));
    }
}
