use super::*;
use crate::test_fakes::channel::{FakeChannel, index_in, set_ids, snapshot_of};
use crate::test_fakes::upload::sample_caption;

/// Files in `dir` whose name starts with `prefix`.
fn files_named(dir: &Path, prefix: &str) -> usize {
    std::fs::read_dir(dir)
        .unwrap()
        .filter(|e| {
            e.as_ref()
                .unwrap()
                .file_name()
                .to_string_lossy()
                .starts_with(prefix)
        })
        .count()
}

/// Another machine's publish of `sets`, as the candidate a pull starts from.
async fn published(channel: &FakeChannel, sets: &[&str]) -> Candidate {
    let snapshot = snapshot_of(channel, sets);
    channel.with(|c| c.publish(snapshot, 1_700_000_000));
    current(channel).await.unwrap().expect("a channel index")
}

#[tokio::test]
async fn an_empty_channel_has_no_channel_index() {
    let channel = FakeChannel::new();
    channel.with(|c| c.post("a pinned announcement".to_string(), None, true, true));

    assert!(current(&channel).await.unwrap().is_none());
}

/// The newest snapshot the channel itself posted, pinned or not — what every
/// player picks — and never one a member slipped in, however new.
#[tokio::test]
async fn the_newest_own_snapshot_is_the_channel_index() {
    let channel = FakeChannel::new();
    let render = mlib_spec::index_caption::render;
    channel.with(|c| c.post(render(1_700_000_000, 1), Some(vec![1]), true, true));
    let newest = channel.with(|c| c.post(render(1_700_000_100, 2), Some(vec![2]), false, true));
    channel.with(|c| c.post(render(1_700_000_200, 3), Some(vec![3]), true, false));

    let chosen = current(&channel).await.unwrap().expect("a channel index");

    assert_eq!(chosen.id, newest);
}

#[tokio::test]
async fn a_channel_index_of_a_newer_schema_is_refused_naming_both_versions() {
    let channel = FakeChannel::new();
    let newer = mlib_spec::schema::SCHEMA_VERSION + 1;
    channel.with(|c| c.publish_with_schema(vec![1], 1_700_000_000, newer));

    let err = current(&channel).await.unwrap_err().to_string();

    assert!(err.contains(&format!("schema v{newer}")), "{err}");
    assert!(
        err.contains(&format!("(v{})", mlib_spec::schema::SCHEMA_VERSION)),
        "{err}"
    );
}

/// No channel index is recorded as none pulled, so the next publish does
/// not skip a pull on the strength of an id the channel no longer shows. A
/// dry run records nothing either way.
#[tokio::test]
async fn no_channel_index_is_recorded_as_nothing_pulled_except_in_a_dry_run() {
    let dir = tempfile::tempdir().unwrap();
    let channel = FakeChannel::new();
    pins::record_pulled(&db::open(dir.path()).unwrap(), Some(7)).unwrap();

    pull_from(&channel, dir.path(), None, true).await.unwrap();
    assert_eq!(
        pins::pulled(&db::open(dir.path()).unwrap()).unwrap(),
        Some(7)
    );

    pull_from(&channel, dir.path(), None, false).await.unwrap();
    assert_eq!(pins::pulled(&db::open(dir.path()).unwrap()).unwrap(), None);
}

/// A chosen message carrying no document has nothing to merge, the same as
/// no message at all.
#[tokio::test]
async fn a_channel_index_without_a_document_is_nothing_to_merge() {
    let dir = tempfile::tempdir().unwrap();
    let channel = FakeChannel::new();
    let local = index_in(dir.path(), &channel, &["local"]);
    pins::record_pulled(&local, Some(7)).unwrap();
    let render = mlib_spec::index_caption::render(1_700_000_000, 0);
    channel.with(|c| c.post(render, None, true, true));
    let chosen = current(&channel).await.unwrap();

    pull_from(&channel, dir.path(), chosen.as_ref(), false)
        .await
        .unwrap();

    assert_eq!(set_ids(&local), ["local"]);
    assert_eq!(pins::pulled(&local).unwrap(), None);
}

/// A pull backs the local index up before writing, merges, records what it
/// pulled, and leaves no downloaded copy behind.
#[tokio::test]
async fn a_pull_backs_up_merges_and_records_what_it_pulled() {
    let dir = tempfile::tempdir().unwrap();
    let channel = FakeChannel::new();
    let local = index_in(dir.path(), &channel, &["local"]);
    let chosen = published(&channel, &["remote"]).await;

    pull_from(&channel, dir.path(), Some(&chosen), false)
        .await
        .unwrap();

    assert_eq!(set_ids(&local), ["local", "remote"]);
    assert_eq!(pins::pulled(&local).unwrap(), Some(chosen.id));
    assert_eq!(files_named(dir.path(), "library.before-channel-merge-"), 1);
    assert_eq!(files_named(dir.path(), "library.channel."), 0);
}

/// A dry run merges into a throwaway copy: the local index, the record of
/// what was pulled and the data directory are all as they were.
#[tokio::test]
async fn a_dry_run_writes_nothing() {
    let dir = tempfile::tempdir().unwrap();
    let channel = FakeChannel::new();
    let local = index_in(dir.path(), &channel, &["local"]);
    let chosen = published(&channel, &["remote"]).await;

    pull_from(&channel, dir.path(), Some(&chosen), true)
        .await
        .unwrap();

    assert_eq!(set_ids(&local), ["local"]);
    assert_eq!(pins::pulled(&local).unwrap(), None);
    assert_eq!(files_named(dir.path(), "library.before-channel-merge-"), 0);
    assert_eq!(files_named(dir.path(), "library.channel."), 0);
    assert_eq!(files_named(dir.path(), "library.pull-index-dry-run."), 0);
}

/// `setS` in both indexes under different titles, its one part posted with
/// the caption given: a conflict the merge leaves for the captions to settle.
async fn conflicting(channel: &FakeChannel, dir: &Path, part_caption: String) -> Candidate {
    let local = index_in(dir, channel, &["setS"]);
    let message: i32 = local
        .query_row(
            "SELECT message_id FROM parts WHERE set_id = 'setS'",
            [],
            |r| r.get(0),
        )
        .unwrap();
    channel.with(|c| {
        let part = c.messages.iter_mut().find(|m| m.id == message).unwrap();
        part.caption = part_caption;
    });
    let other = tempfile::tempdir().unwrap();
    let theirs = index_in(other.path(), channel, &["setS"]);
    theirs
        .execute(
            "UPDATE sets SET title = 'Their Title' WHERE set_id = 'setS'",
            [],
        )
        .unwrap();
    let path = other.path().join("snapshot.db");
    snapshot::snapshot_to(&theirs, &path).unwrap();
    channel.with(|c| c.publish(std::fs::read(path).unwrap(), 1_700_000_000));
    current(channel).await.unwrap().expect("a channel index")
}

/// Settled from the set's own caption, the conflict is gone and the pull is
/// recorded like any other.
#[tokio::test]
async fn a_conflict_settled_from_its_caption_records_the_pull() {
    let dir = tempfile::tempdir().unwrap();
    let channel = FakeChannel::new();
    let mut caption = sample_caption("setS", 50, 1);
    caption.title = Some("Caption Title".to_string());
    let text = mlib_spec::to_text(&caption, "").unwrap();
    let chosen = conflicting(&channel, dir.path(), text).await;

    pull_from(&channel, dir.path(), Some(&chosen), false)
        .await
        .unwrap();

    let local = db::open(dir.path()).unwrap();
    let title: String = local
        .query_row("SELECT title FROM sets WHERE set_id = 'setS'", [], |r| {
            r.get(0)
        })
        .unwrap();
    assert_eq!(title, "Caption Title");
    assert_eq!(pins::pulled(&local).unwrap(), Some(chosen.id));
}

/// A publish that finds the recorded id still current skips its pull, so a
/// conflict left unsettled must leave the pull unrecorded for the next
/// publish to retry.
#[tokio::test]
async fn a_conflict_left_unsettled_leaves_the_pull_unrecorded() {
    let dir = tempfile::tempdir().unwrap();
    let channel = FakeChannel::new();
    let chosen = conflicting(&channel, dir.path(), "not a caption".to_string()).await;

    pull_from(&channel, dir.path(), Some(&chosen), false)
        .await
        .unwrap();

    assert_eq!(pins::pulled(&db::open(dir.path()).unwrap()).unwrap(), None);
}

/// A channel index an older uploader published has no subtitle tables;
/// merging it leaves this index's subtitle rows in place and records the
/// publish that restores them to the channel.
#[tokio::test]
async fn a_channel_index_without_subtitle_tables_records_a_publish_owed() {
    let dir = tempfile::tempdir().unwrap();
    let channel = FakeChannel::new();
    let local = index_in(dir.path(), &channel, &["local"]);
    local
        .execute(
            "INSERT INTO subtitle_files(set_id, chat_id, message_id, bytes, sha256, uploaded_at)
             VALUES ('local', 777, 4001, 1000, 'aa', 1700000000)",
            [],
        )
        .unwrap();
    let other = tempfile::tempdir().unwrap();
    let path = other.path().join("older.db");
    std::fs::write(&path, snapshot_of(&channel, &["remote"])).unwrap();
    let older = crate::index::sqlite_init::open(&path).unwrap();
    older
        .execute_batch("DROP TABLE subtitle_tracks; DROP TABLE subtitle_files;")
        .unwrap();
    drop(older);
    channel.with(|c| c.publish(std::fs::read(&path).unwrap(), 1_700_000_000));
    let chosen = current(&channel).await.unwrap();

    pull_from(&channel, dir.path(), chosen.as_ref(), false)
        .await
        .unwrap();

    assert!(pins::publish_owed(&local).unwrap().is_some());
    assert_eq!(set_ids(&local), ["local", "remote"]);
}
