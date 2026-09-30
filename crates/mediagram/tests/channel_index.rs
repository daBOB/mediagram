//! Pulling and publishing the channel index, against an in-memory channel.
//!
//! Every publish pulls first, so what another machine published is merged,
//! never dropped; these tests hold that to account through the one
//! interface every command uses, including the ways a real channel has
//! misbehaved: a publish landing mid-publish, a refused unpin, an unpin that
//! reports success and changes nothing.

mod support;

use mediagram::channel_index::{ChannelIndex, Mode};
use mediagram::clock::now_unix;
use mediagram::index::db;
use support::channel::{FakeChannel, index_in, set_ids, sets_in, snapshot_of};
use tempfile::TempDir;

/// This machine: a data directory whose local index holds `sets`.
fn this_machine(channel: &FakeChannel, sets: &[&str]) -> TempDir {
    let dir = tempfile::tempdir().unwrap();
    drop(index_in(dir.path(), channel, sets));
    dir
}

fn local_sets(dir: &TempDir) -> Vec<String> {
    set_ids(&db::open(dir.path()).unwrap())
}

fn pinned(channel: &FakeChannel) -> Vec<i32> {
    channel.with(|c| c.pinned_indexes())
}

fn downloads(channel: &FakeChannel) -> usize {
    channel.with(|c| c.downloads)
}

#[tokio::test]
async fn a_first_publish_to_an_empty_channel_pins_the_local_index() {
    let channel = FakeChannel::new();
    let dir = this_machine(&channel, &["OURS"]);

    let id = ChannelIndex::new(&channel, dir.path())
        .publish(Mode::AfterPull)
        .await
        .unwrap();

    assert_eq!(pinned(&channel), [id]);
    assert_eq!(sets_in(&channel.document(id)), ["OURS"]);
    assert_eq!(downloads(&channel), 0);
}

#[tokio::test]
async fn another_machines_publish_is_pulled_before_ours_is_sent() {
    let channel = FakeChannel::new();
    let theirs = snapshot_of(&channel, &["THEIRS"]);
    channel.with(|c| c.publish(theirs, now_unix() - 100));
    let dir = this_machine(&channel, &["OURS"]);

    let id = ChannelIndex::new(&channel, dir.path())
        .publish(Mode::AfterPull)
        .await
        .unwrap();

    assert_eq!(sets_in(&channel.document(id)), ["OURS", "THEIRS"]);
    assert_eq!(local_sets(&dir), ["OURS", "THEIRS"]);
}

/// The second look for the channel index is the one just before sending.
#[tokio::test]
async fn a_publish_landing_mid_publish_is_pulled_again_not_refused() {
    let channel = FakeChannel::new();
    let dir = this_machine(&channel, &["OURS"]);
    let late = snapshot_of(&channel, &["LATE"]);
    channel.on_look(2, move |c| {
        c.publish(late, now_unix() - 10);
    });

    let id = ChannelIndex::new(&channel, dir.path())
        .publish(Mode::AfterPull)
        .await
        .unwrap();

    assert_eq!(sets_in(&channel.document(id)), ["LATE", "OURS"]);
}

#[tokio::test]
async fn a_channel_that_keeps_changing_fails_saying_so() {
    let channel = FakeChannel::new();
    let dir = this_machine(&channel, &["OURS"]);
    for call in 2..=4 {
        let late = snapshot_of(&channel, &[&format!("LATE{call}")]);
        channel.on_look(call, move |c| {
            c.publish(late, now_unix() - 10 + i64::try_from(call).unwrap());
        });
    }

    let err = ChannelIndex::new(&channel, dir.path())
        .publish(Mode::AfterPull)
        .await
        .unwrap_err();

    assert!(format!("{err:#}").contains("kept changing"), "{err:#}");
    assert_eq!(channel.with(|c| c.sends), 0);
}

#[tokio::test]
async fn force_publishes_without_pulling() {
    let channel = FakeChannel::new();
    let theirs = snapshot_of(&channel, &["THEIRS"]);
    channel.with(|c| c.publish(theirs, now_unix() - 100));
    let dir = this_machine(&channel, &["OURS"]);

    let id = ChannelIndex::new(&channel, dir.path())
        .publish(Mode::Force)
        .await
        .unwrap();

    assert_eq!(sets_in(&channel.document(id)), ["OURS"]);
    assert_eq!(local_sets(&dir), ["OURS"]);
    assert_eq!(downloads(&channel), 0);
}

#[tokio::test]
async fn a_publish_with_nothing_new_in_the_channel_downloads_nothing() {
    let channel = FakeChannel::new();
    let theirs = snapshot_of(&channel, &["THEIRS"]);
    channel.with(|c| c.publish(theirs, now_unix() - 100));
    let dir = this_machine(&channel, &["OURS"]);
    let index = ChannelIndex::new(&channel, dir.path());

    index.publish(Mode::AfterPull).await.unwrap();
    assert_eq!(downloads(&channel), 1);
    index.publish(Mode::AfterPull).await.unwrap();

    assert_eq!(downloads(&channel), 1);
}

#[tokio::test]
async fn a_refused_unpin_is_tried_again_by_the_next_publish() {
    let channel = FakeChannel::new();
    let dir = this_machine(&channel, &["OURS"]);
    let index = ChannelIndex::new(&channel, dir.path());

    let first = index.publish(Mode::AfterPull).await.unwrap();
    channel.with(|c| c.unpin_fails.insert(first));
    let second = index.publish(Mode::AfterPull).await.unwrap();
    assert_eq!(pinned(&channel), [first, second]);

    channel.with(|c| c.unpin_fails.clear());
    let third = index.publish(Mode::AfterPull).await.unwrap();

    assert_eq!(pinned(&channel), [third]);
}

/// Telegram has answered an unpin with success and left the message pinned;
/// only reading the message back tells the two apart.
#[tokio::test]
async fn an_unpin_that_changes_nothing_is_tried_again_by_the_next_publish() {
    let channel = FakeChannel::new();
    let dir = this_machine(&channel, &["OURS"]);
    let index = ChannelIndex::new(&channel, dir.path());

    let first = index.publish(Mode::AfterPull).await.unwrap();
    channel.with(|c| c.unpin_lies.insert(first));
    let second = index.publish(Mode::AfterPull).await.unwrap();
    assert_eq!(pinned(&channel), [first, second]);

    channel.with(|c| c.unpin_lies.clear());
    let third = index.publish(Mode::AfterPull).await.unwrap();

    assert_eq!(pinned(&channel), [third]);
}

#[tokio::test]
async fn a_members_post_is_never_the_channel_index() {
    let channel = FakeChannel::new();
    let theirs = snapshot_of(&channel, &["THEIRS"]);
    let members = snapshot_of(&channel, &["MEMBERS"]);
    channel.with(|c| {
        c.publish(theirs, now_unix() - 100);
        let caption = mlib_spec::index_caption::render(now_unix() - 10, 0);
        c.post(caption, Some(members), true, false);
    });
    let dir = this_machine(&channel, &["OURS"]);

    ChannelIndex::new(&channel, dir.path())
        .pull(false)
        .await
        .unwrap();

    assert_eq!(local_sets(&dir), ["OURS", "THEIRS"]);
}

#[tokio::test]
async fn a_snapshot_dated_in_the_future_is_never_the_channel_index() {
    let channel = FakeChannel::new();
    let theirs = snapshot_of(&channel, &["THEIRS"]);
    let future = snapshot_of(&channel, &["FUTURE"]);
    channel.with(|c| {
        c.publish(theirs, now_unix() - 100);
        c.publish(future, now_unix() + 400 * 24 * 60 * 60);
    });
    let dir = this_machine(&channel, &["OURS"]);

    ChannelIndex::new(&channel, dir.path())
        .pull(false)
        .await
        .unwrap();

    assert_eq!(local_sets(&dir), ["OURS", "THEIRS"]);
}

/// A publish killed between sending and pinning leaves its snapshot
/// unpinned. Every player still finds it by its marker, so a publish from
/// here has to pull it too, or it would drop what that one added.
#[tokio::test]
async fn a_snapshot_left_unpinned_is_still_pulled() {
    let channel = FakeChannel::new();
    let theirs = snapshot_of(&channel, &["THEIRS"]);
    let unpinned = snapshot_of(&channel, &["THEIRS", "UNPINNED"]);
    channel.with(|c| {
        c.publish(theirs, now_unix() - 100);
        let caption = mlib_spec::index_caption::render(now_unix() - 10, 0);
        c.post(caption, Some(unpinned), false, true);
    });
    let dir = this_machine(&channel, &["OURS"]);

    let id = ChannelIndex::new(&channel, dir.path())
        .publish(Mode::AfterPull)
        .await
        .unwrap();

    assert_eq!(
        sets_in(&channel.document(id)),
        ["OURS", "THEIRS", "UNPINNED"]
    );
}

/// A dry run writes nothing, and in particular does not count as having
/// pulled: the publish after it still pulls.
#[tokio::test]
async fn a_dry_run_changes_nothing_and_the_next_publish_still_pulls() {
    let channel = FakeChannel::new();
    let theirs = snapshot_of(&channel, &["THEIRS"]);
    channel.with(|c| c.publish(theirs, now_unix() - 100));
    let dir = this_machine(&channel, &["OURS"]);
    let index = ChannelIndex::new(&channel, dir.path());

    index.pull(true).await.unwrap();
    assert_eq!(local_sets(&dir), ["OURS"]);

    let id = index.publish(Mode::AfterPull).await.unwrap();
    assert_eq!(downloads(&channel), 2);
    assert_eq!(sets_in(&channel.document(id)), ["OURS", "THEIRS"]);
}

/// A pull that fails part way must not count as having pulled, or the next
/// publish would skip the pull and drop what the channel holds.
#[tokio::test]
async fn a_failed_pull_is_not_taken_for_a_pull_by_the_next_publish() {
    let channel = FakeChannel::new();
    let theirs = snapshot_of(&channel, &["THEIRS"]);
    channel.with(|c| {
        c.publish(theirs, now_unix() - 100);
        c.captions_fail = true;
    });
    let dir = this_machine(&channel, &["OURS"]);
    let index = ChannelIndex::new(&channel, dir.path());

    index.publish(Mode::AfterPull).await.unwrap_err();
    channel.with(|c| c.captions_fail = false);
    let id = index.publish(Mode::AfterPull).await.unwrap();

    assert_eq!(sets_in(&channel.document(id)), ["OURS", "THEIRS"]);
}

/// Two background uploads finishing together publish one after the other:
/// the second finds the first's publish current, pulls nothing, and clears
/// its pin — rather than both pinning from the same starting point.
#[tokio::test]
async fn two_publishes_on_one_machine_take_turns() {
    let channel = FakeChannel::new();
    let dir = this_machine(&channel, &["OURS"]);
    let (one, two) = (
        ChannelIndex::new(&channel, dir.path()),
        ChannelIndex::new(&channel, dir.path()),
    );

    let (first, second) = tokio::join!(one.publish(Mode::AfterPull), two.publish(Mode::AfterPull));

    let last = first.unwrap().max(second.unwrap());
    assert_eq!(pinned(&channel), [last]);
    assert_eq!(downloads(&channel), 0);
}

/// A channel whose index caption names a schema newer than this build
/// understands is refused rather than merged into the local index, naming
/// both versions so an operator knows to reinstall.
mod newer_schema_guard {
    use super::*;

    fn newer_than_this_build() -> i64 {
        mlib_spec::schema::SCHEMA_VERSION + 1
    }

    #[tokio::test]
    async fn a_pull_refuses_a_newer_schema() {
        let channel = FakeChannel::new();
        let theirs = snapshot_of(&channel, &["THEIRS"]);
        let schema = newer_than_this_build();
        channel.with(|c| c.publish_with_schema(theirs, now_unix() - 100, schema));
        let dir = this_machine(&channel, &["OURS"]);

        let err = ChannelIndex::new(&channel, dir.path())
            .pull(false)
            .await
            .unwrap_err();

        let msg = format!("{err:#}");
        assert!(msg.contains(&schema.to_string()), "{msg}");
        assert!(
            msg.contains(&mlib_spec::schema::SCHEMA_VERSION.to_string()),
            "{msg}"
        );
        assert_eq!(local_sets(&dir), ["OURS"]);
    }

    #[tokio::test]
    async fn an_after_pull_publish_refuses_a_newer_schema() {
        let channel = FakeChannel::new();
        let theirs = snapshot_of(&channel, &["THEIRS"]);
        channel.with(|c| c.publish_with_schema(theirs, now_unix() - 100, newer_than_this_build()));
        let dir = this_machine(&channel, &["OURS"]);

        ChannelIndex::new(&channel, dir.path())
            .publish(Mode::AfterPull)
            .await
            .unwrap_err();

        assert_eq!(channel.with(|c| c.sends), 0);
    }

    /// `--force` is meant to overwrite a channel without pulling first, but
    /// it must still refuse one it cannot read — a stale uploader forcing
    /// over a newer schema would erase every set the newer one added.
    #[tokio::test]
    async fn a_force_publish_still_refuses_a_newer_schema() {
        let channel = FakeChannel::new();
        let theirs = snapshot_of(&channel, &["THEIRS"]);
        channel.with(|c| c.publish_with_schema(theirs, now_unix() - 100, newer_than_this_build()));
        let dir = this_machine(&channel, &["OURS"]);

        ChannelIndex::new(&channel, dir.path())
            .publish(Mode::Force)
            .await
            .unwrap_err();

        assert_eq!(channel.with(|c| c.sends), 0);
    }

    /// A caption at exactly this build's schema is not "newer" and pulls
    /// normally — the guard is `>`, not `>=`.
    #[tokio::test]
    async fn a_pull_accepts_its_own_schema_version() {
        let channel = FakeChannel::new();
        let theirs = snapshot_of(&channel, &["THEIRS"]);
        channel.with(|c| {
            c.publish_with_schema(theirs, now_unix() - 100, mlib_spec::schema::SCHEMA_VERSION)
        });
        let dir = this_machine(&channel, &["OURS"]);

        ChannelIndex::new(&channel, dir.path())
            .pull(false)
            .await
            .unwrap();

        assert_eq!(local_sets(&dir), ["OURS", "THEIRS"]);
    }
}
