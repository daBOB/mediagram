//! Which channel-only sets still exist in the channel, and which local-only
//! ones are gone from it: one batched Telegram lookup of their part messages
//! each, made before merging.
//!
//! Awaited here, in the command's own runtime, rather than from inside
//! `merge::merge_from`'s synchronous closure: blocking a runtime thread on a
//! future that needs that runtime's I/O only works while another thread is
//! free to drive it, and hangs where none is.

use std::collections::HashSet;

use anyhow::Result;

use super::remote::ChannelRemote;
use crate::index::merge::Candidate;

/// The candidates whose every part message is still in the channel. A set
/// with any part gone was removed after the channel's index was pushed. Fails
/// closed: a set whose parts lack messages, or name another chat, is not
/// taken as present.
pub(super) async fn live_sets(
    remote: &impl ChannelRemote,
    candidates: &[Candidate],
) -> Result<HashSet<String>> {
    if candidates.is_empty() {
        return Ok(HashSet::new());
    }
    let found = remote.captions(&message_ids(candidates)).await?;
    let chat = remote.chat_id();
    Ok(candidates
        .iter()
        .filter(|c| {
            accounted_for(c)
                && c.messages.iter().all(|(chat_id, mid)| {
                    *chat_id == chat && i32::try_from(*mid).is_ok_and(|id| found.contains_key(&id))
                })
        })
        .map(|c| c.set_id.clone())
        .collect())
}

/// The local-only candidates none of whose part messages is still in the
/// channel: removed on another machine. Fails open, the reverse of
/// [`live_sets`], because the answer deletes rows: a set with any part still
/// posted, a part without a message, or a part in another chat is kept.
pub(super) async fn gone_sets(
    remote: &impl ChannelRemote,
    candidates: &[Candidate],
) -> Result<HashSet<String>> {
    if candidates.is_empty() {
        return Ok(HashSet::new());
    }
    let found = remote.captions(&message_ids(candidates)).await?;
    let chat = remote.chat_id();
    Ok(candidates
        .iter()
        .filter(|c| {
            accounted_for(c)
                && c.messages.iter().all(|(chat_id, mid)| {
                    *chat_id == chat && i32::try_from(*mid).is_ok_and(|id| !found.contains_key(&id))
                })
        })
        .map(|c| c.set_id.clone())
        .collect())
}

fn message_ids(candidates: &[Candidate]) -> Vec<i32> {
    candidates
        .iter()
        .flat_map(|c| {
            c.messages
                .iter()
                .filter_map(|(_, mid)| i32::try_from(*mid).ok())
        })
        .collect()
}

/// Every part the set claims has a message recorded.
fn accounted_for(c: &Candidate) -> bool {
    !c.messages.is_empty() && i64::try_from(c.messages.len()).is_ok_and(|n| n == c.part_count)
}

#[cfg(test)]
#[path = "live_tests.rs"]
mod tests;
