use super::*;
use crate::test_fakes::channel::{CHAT_ID, FakeChannel};

/// A channel-only set whose `part_count` parts were each posted as a message.
fn posted_set(channel: &FakeChannel, set_id: &str, part_count: i64) -> Candidate {
    let messages = (0..part_count)
        .map(|i| {
            let id = channel.with(|c| c.post(format!("{set_id} part {i}"), None, false, true));
            (CHAT_ID, i64::from(id))
        })
        .collect();
    Candidate {
        set_id: set_id.to_string(),
        messages,
        part_count,
    }
}

fn delete(channel: &FakeChannel, id: i64) {
    channel.with(|c| c.messages.retain(|m| i64::from(m.id) != id));
}

fn live(found: HashSet<String>) -> Vec<String> {
    let mut found: Vec<String> = found.into_iter().collect();
    found.sort();
    found
}

#[tokio::test]
async fn sets_whose_every_part_is_still_posted_are_live() {
    let channel = FakeChannel::new();
    let candidates = [posted_set(&channel, "a", 2), posted_set(&channel, "b", 1)];

    let found = live_sets(&channel, &candidates).await.unwrap();

    assert_eq!(live(found), ["a", "b"]);
}

/// One deleted part is enough: the set was removed after the channel's index
/// was pushed, and adding it would list a title nobody can play.
#[tokio::test]
async fn a_set_with_any_part_deleted_is_not_live() {
    let channel = FakeChannel::new();
    let removed = posted_set(&channel, "removed", 3);
    delete(&channel, removed.messages[1].1);
    let kept = posted_set(&channel, "kept", 1);

    let found = live_sets(&channel, &[removed, kept]).await.unwrap();

    assert_eq!(live(found), ["kept"]);
}

/// Message ids are per chat: the same id in another chat says nothing about
/// whether this set's part is still in this one.
#[tokio::test]
async fn a_part_recorded_in_another_chat_is_not_taken_as_present() {
    let channel = FakeChannel::new();
    let mut elsewhere = posted_set(&channel, "elsewhere", 1);
    elsewhere.messages[0].0 = CHAT_ID - 1;

    let found = live_sets(&channel, &[elsewhere]).await.unwrap();

    assert!(found.is_empty());
}

/// Fails closed on what the index cannot vouch for: a part with no message,
/// a set with no messages at all, an id no message could have.
#[tokio::test]
async fn a_set_whose_parts_are_not_all_accounted_for_is_not_live() {
    let channel = FakeChannel::new();
    let mut short = posted_set(&channel, "short", 2);
    short.messages.pop();
    let empty = Candidate {
        set_id: "empty".to_string(),
        messages: Vec::new(),
        part_count: 0,
    };
    let mut out_of_range = posted_set(&channel, "out-of-range", 1);
    out_of_range.messages[0].1 = i64::MAX;

    let found = live_sets(&channel, &[short, empty, out_of_range])
        .await
        .unwrap();

    assert!(found.is_empty());
}

/// With nothing to ask about, the channel is not asked: a merge with no
/// channel-only sets makes no round trip.
#[tokio::test]
async fn no_candidates_needs_no_lookup() {
    let channel = FakeChannel::new();
    channel.with(|c| c.captions_fail = true);

    let found = live_sets(&channel, &[]).await.unwrap();

    assert!(found.is_empty());
}

/// A lookup that fails is an error, not "nothing is live": the latter would
/// skip every channel-only set as removed.
#[tokio::test]
async fn a_failed_lookup_is_an_error() {
    let channel = FakeChannel::new();
    let candidate = posted_set(&channel, "a", 1);
    channel.with(|c| c.captions_fail = true);

    assert!(live_sets(&channel, &[candidate]).await.is_err());
}
