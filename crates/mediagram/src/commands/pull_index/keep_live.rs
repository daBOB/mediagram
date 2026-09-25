//! Which channel-only sets still exist in the channel: one batched Telegram
//! lookup of their part messages, made before merging.
//!
//! Awaited here, in the command's own runtime, rather than from inside
//! `merge::merge_from`'s synchronous closure: blocking a runtime thread on a
//! future that needs that runtime's I/O only works while another thread is
//! free to drive it, and hangs where none is.

use std::collections::HashSet;

use anyhow::Result;

use crate::config::Config;
use crate::index::merge::Candidate;
use crate::telegram::client::Tg;
use crate::verify::download_hash::fetch_messages;

/// The candidates whose every part message is still in the channel. A set
/// with any part gone was removed after the channel's index was pushed. Fails
/// closed: a set whose parts lack messages, or name another chat, is not
/// taken as present.
pub(super) async fn live_sets(
    tg: &Tg,
    cfg: &Config,
    candidates: &[Candidate],
) -> Result<HashSet<String>> {
    if candidates.is_empty() {
        return Ok(HashSet::new());
    }
    let ids: Vec<i32> = candidates
        .iter()
        .flat_map(|c| {
            c.messages
                .iter()
                .filter_map(|(_, mid)| i32::try_from(*mid).ok())
        })
        .collect();
    let found = fetch_messages(&tg.client, tg.channel, &ids, cfg.max_attempts).await?;
    let chat = tg.chat_id();
    Ok(candidates
        .iter()
        .filter(|c| {
            !c.messages.is_empty()
                && i64::try_from(c.messages.len()).is_ok_and(|n| n == c.part_count)
                && c.messages.iter().all(|(chat_id, mid)| {
                    *chat_id == chat && i32::try_from(*mid).is_ok_and(|id| found.contains_key(&id))
                })
        })
        .map(|c| c.set_id.clone())
        .collect())
}
