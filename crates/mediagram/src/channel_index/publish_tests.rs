use super::*;
use crate::test_fakes::channel::{FakeChannel, index_in, sets_in};

/// Snapshots a publish left behind in `dir`.
fn snapshots_left(dir: &Path) -> usize {
    std::fs::read_dir(dir)
        .unwrap()
        .filter(|e| {
            e.as_ref()
                .unwrap()
                .file_name()
                .to_string_lossy()
                .starts_with("library.push.")
        })
        .count()
}

/// What a publish sends is a snapshot of the local index under an index
/// caption, pinned, and remembered as both the pin to clear next time and
/// the channel index this machine already holds.
#[tokio::test]
async fn a_publish_sends_pins_and_records_a_snapshot_of_the_local_index() {
    let dir = tempfile::tempdir().unwrap();
    let channel = FakeChannel::new();
    let conn = index_in(dir.path(), &channel, &["a", "b"]);

    let id = publish(&channel, dir.path(), Mode::AfterPull)
        .await
        .unwrap();

    assert_eq!(sets_in(&channel.document(id)), ["a", "b"]);
    let caption = channel.with(|c| {
        c.messages
            .iter()
            .find(|m| m.id == id)
            .unwrap()
            .caption
            .clone()
    });
    assert_eq!(
        mlib_spec::index_caption::schema(&caption),
        Some(mlib_spec::schema::SCHEMA_VERSION)
    );
    assert_eq!(channel.with(|c| c.pinned_indexes()), [id]);
    assert_eq!(pins::pulled(&conn).unwrap(), Some(id));
    assert_eq!(pins::pending_unpins(&conn).unwrap(), [id]);
    assert_eq!(snapshots_left(dir.path()), 0);
}

/// The next publish clears the pin this one replaced.
#[tokio::test]
async fn a_second_publish_unpins_the_first() {
    let dir = tempfile::tempdir().unwrap();
    let channel = FakeChannel::new();
    index_in(dir.path(), &channel, &["a"]);

    let first = publish(&channel, dir.path(), Mode::AfterPull)
        .await
        .unwrap();
    let second = publish(&channel, dir.path(), Mode::AfterPull)
        .await
        .unwrap();

    assert_ne!(first, second);
    assert_eq!(channel.with(|c| c.pinned_indexes()), [second]);
}

#[tokio::test]
async fn a_publish_settles_the_publish_owed() {
    let dir = tempfile::tempdir().unwrap();
    let channel = FakeChannel::new();
    let conn = index_in(dir.path(), &channel, &["a"]);
    pins::owe_publish(&conn).unwrap();

    publish(&channel, dir.path(), Mode::Force).await.unwrap();

    assert_eq!(pins::publish_owed(&conn).unwrap(), None);
}

/// A send the channel refuses changes none of the bookkeeping: the debt is
/// still owed, nothing new is to be unpinned, and the snapshot is removed.
#[tokio::test]
async fn a_refused_send_records_nothing_and_leaves_no_snapshot() {
    let dir = tempfile::tempdir().unwrap();
    let channel = FakeChannel::new();
    let conn = index_in(dir.path(), &channel, &["a"]);
    pins::owe_publish(&conn).unwrap();
    channel.with(|c| c.send_fails = true);

    let err = publish(&channel, dir.path(), Mode::AfterPull)
        .await
        .unwrap_err();

    assert!(
        format!("{err:#}").contains("sending the index document"),
        "{err:#}"
    );
    assert!(pins::publish_owed(&conn).unwrap().is_some());
    assert!(pins::pending_unpins(&conn).unwrap().is_empty());
    assert_eq!(snapshots_left(dir.path()), 0);
}
