use super::*;
use crate::index::db;
use crate::test_fakes::channel::FakeChannel;

/// Two pinned index snapshots, recorded as the ones a publish replaces.
fn two_replaced_pins(channel: &FakeChannel, conn: &Connection) -> (i32, i32) {
    let older = channel.with(|c| c.publish(vec![1], 1_700_000_000));
    let newer = channel.with(|c| c.publish(vec![2], 1_700_000_100));
    pins::record_index_messages(conn, &[older, newer]).unwrap();
    (older, newer)
}

#[tokio::test]
async fn every_replaced_pin_that_clears_is_forgotten() {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    let channel = FakeChannel::new();
    two_replaced_pins(&channel, &conn);

    unpin_previous(&channel, &conn).await.unwrap();

    assert!(channel.with(|c| c.pinned_indexes()).is_empty());
    assert!(pins::pending_unpins(&conn).unwrap().is_empty());
}

/// Best-effort about the channel: a pin it will not clear is kept on the
/// list for the next publish, and the others are still cleared.
#[tokio::test]
async fn a_refused_unpin_is_kept_for_the_next_publish() {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    let channel = FakeChannel::new();
    let (older, _) = two_replaced_pins(&channel, &conn);
    channel.with(|c| c.unpin_fails.insert(older));

    unpin_previous(&channel, &conn).await.unwrap();

    assert_eq!(pins::pending_unpins(&conn).unwrap(), [older]);
    assert_eq!(channel.with(|c| c.pinned_indexes()), [older]);
}

/// Telegram has answered an unpin with success and left the message
/// pinned. The pin flag is read back, the unpin tried once more, and the id
/// kept rather than dropped on the strength of a success that was not one.
#[tokio::test]
async fn an_unpin_that_changes_nothing_is_tried_twice_then_kept() {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    let channel = FakeChannel::new();
    let older = channel.with(|c| c.publish(vec![1], 1_700_000_000));
    pins::record_index_messages(&conn, &[older]).unwrap();
    channel.with(|c| c.unpin_lies.insert(older));

    unpin_previous(&channel, &conn).await.unwrap();

    assert_eq!(channel.with(|c| c.unpins), 2);
    assert_eq!(pins::pending_unpins(&conn).unwrap(), [older]);
}

/// A message that no longer exists, or is no longer pinned, has no pin left
/// to clear: it is dropped after one request, with nothing to read back.
#[tokio::test]
async fn a_message_already_gone_or_unpinned_counts_as_cleared() {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    let channel = FakeChannel::new();
    let unpinned = channel.with(|c| c.post("#mlib-index".to_string(), None, false, true));
    let deleted = unpinned + 1;
    pins::record_index_messages(&conn, &[unpinned, deleted]).unwrap();

    unpin_previous(&channel, &conn).await.unwrap();

    assert_eq!(channel.with(|c| c.unpins), 2);
    assert!(pins::pending_unpins(&conn).unwrap().is_empty());
}
