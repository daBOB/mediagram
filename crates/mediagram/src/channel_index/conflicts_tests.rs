use super::*;
use crate::index::set_row::SetRow;
use crate::index::{db, sets};
use crate::test_fakes::channel::{CHAT_ID, FakeChannel};
use crate::test_fakes::upload::sample_caption;

/// One part's caption for `set_id` (alphanumeric, as a caption requires),
/// its title `title`.
fn caption(set_id: &str, idx: u32, title: &str) -> String {
    let mut caption = sample_caption(set_id, 100, 2);
    caption.title = Some(title.to_string());
    caption.part.i = idx;
    mlib_spec::to_text(&caption, "").unwrap()
}

/// A two-part set titled "Local Title", each part recorded under the
/// message id given (or none).
fn local_set(conn: &Connection, set_id: &str, messages: [Option<i32>; 2]) {
    let mut row = sample_caption(set_id, 100, 2);
    row.title = Some("Local Title".to_string());
    sets::insert_set(conn, &SetRow::from_caption(&row, 1_700_000_000)).unwrap();
    for (idx, message) in messages.into_iter().enumerate() {
        conn.execute(
            "INSERT INTO parts(set_id, idx, byte_offset, byte_length, chat_id, message_id, doc_id, sha256, status)
             VALUES (?1, ?2, ?3, 50, ?4, ?5, ?5, 'deadbeef', 'done')",
            rusqlite::params![set_id, idx, idx * 50, CHAT_ID, message],
        )
        .unwrap();
    }
}

fn post(channel: &FakeChannel, caption: String) -> Option<i32> {
    Some(channel.with(|c| c.post(caption, Some(vec![0]), false, true)))
}

fn title(conn: &Connection, set_id: &str) -> Option<String> {
    sets::get_set(conn, set_id).unwrap().unwrap().title
}

#[tokio::test]
async fn a_conflicting_set_is_rewritten_from_its_own_caption() {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    let channel = FakeChannel::new();
    let first = post(&channel, caption("setA", 0, "Channel Title"));
    let second = post(&channel, caption("setA", 1, "Channel Title"));
    local_set(&conn, "setA", [first, second]);

    let summary = resolve(&conn, &channel, &["setA".to_string()])
        .await
        .unwrap();

    assert_eq!(summary.resolved, 1);
    assert_eq!(title(&conn, "setA").as_deref(), Some("Channel Title"));
}

/// An interrupted edit rewrites captions from the first part on, so the
/// first part's caption is the newest even when its message is the newer
/// post of the two.
#[tokio::test]
async fn the_first_part_s_caption_wins_over_a_later_part_s() {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    let channel = FakeChannel::new();
    let second = post(&channel, caption("setA", 1, "Before The Edit"));
    let first = post(&channel, caption("setA", 0, "After The Edit"));
    local_set(&conn, "setA", [first, second]);

    resolve(&conn, &channel, &["setA".to_string()])
        .await
        .unwrap();

    assert_eq!(title(&conn, "setA").as_deref(), Some("After The Edit"));
}

#[tokio::test]
async fn a_set_whose_messages_are_all_gone_is_left_as_it_was() {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    let channel = FakeChannel::new();
    local_set(&conn, "setA", [Some(41), Some(42)]);

    let summary = resolve(&conn, &channel, &["setA".to_string()])
        .await
        .unwrap();

    assert_eq!(summary.resolved, 0);
    assert_eq!(title(&conn, "setA").as_deref(), Some("Local Title"));
}

/// Captions out of reach do not fail the pull, which has already merged:
/// the set is left as it was, for the next pull to try again.
#[tokio::test]
async fn an_unreachable_channel_leaves_the_set_for_the_next_pull() {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    let channel = FakeChannel::new();
    let first = post(&channel, caption("setA", 0, "Channel Title"));
    local_set(&conn, "setA", [first, None]);
    channel.with(|c| c.captions_fail = true);

    let summary = resolve(&conn, &channel, &["setA".to_string()])
        .await
        .unwrap();

    assert_eq!(summary.resolved, 0);
    assert_eq!(title(&conn, "setA").as_deref(), Some("Local Title"));
}

/// Sets from a newer uploader are named by kind with how many there were,
/// so the report says one line per kind rather than one per set.
#[tokio::test]
async fn sets_of_a_kind_this_build_cannot_decode_are_counted_per_kind() {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    let channel = FakeChannel::new();
    let mut conflicts = Vec::new();
    for (set_id, kind) in [("setA", "vr"), ("setB", "vr"), ("setC", "hologram")] {
        let message = post(&channel, caption(set_id, 0, "Channel Title"));
        local_set(&conn, set_id, [message, None]);
        conn.execute(
            "UPDATE sets SET kind = ?1 WHERE set_id = ?2",
            [kind, set_id],
        )
        .unwrap();
        conflicts.push(set_id.to_string());
    }

    let summary = resolve(&conn, &channel, &conflicts).await.unwrap();

    assert_eq!(summary.resolved, 0);
    assert_eq!(
        summary.skipped_kinds,
        [
            SkippedKind {
                kind: "vr".to_string(),
                count: 2
            },
            SkippedKind {
                kind: "hologram".to_string(),
                count: 1
            },
        ]
    );
}

/// Captions in a newer `#mlib` format are counted across every set, so the
/// report can say a reinstall would read them.
#[tokio::test]
async fn captions_in_a_newer_format_are_counted_across_sets() {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    let channel = FakeChannel::new();
    for set_id in ["setA", "setB"] {
        let newer = post(&channel, format!("#mlib v=99\n{{\"set\":\"{set_id}\"}}"));
        local_set(&conn, set_id, [newer, None]);
    }

    let summary = resolve(&conn, &channel, &["setA".to_string(), "setB".to_string()])
        .await
        .unwrap();

    assert_eq!(summary.resolved, 0);
    assert_eq!(summary.newer_captions, 2);
}
