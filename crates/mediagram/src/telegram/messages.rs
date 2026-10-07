//! Batched retrieval of channel messages by id.

use std::collections::HashMap;

use anyhow::{Context, Result};
use grammers_client::Client;
use grammers_client::message::Message;
use grammers_session::types::PeerRef;

use super::retry::with_retry;

/// Telegram returns at most 100 messages per `get_messages_by_id` call.
const MESSAGE_BATCH: usize = 100;

/// Fetches every message id in `ids` (batched to Telegram's 100-per-call
/// limit) and returns those that still exist, keyed by id. A plain read, so
/// ordinary retry applies.
pub async fn fetch_messages(
    client: &Client,
    channel: PeerRef,
    ids: &[i32],
    max_attempts: u32,
) -> Result<HashMap<i32, Message>> {
    let mut map = HashMap::with_capacity(ids.len());
    for chunk in ids.chunks(MESSAGE_BATCH) {
        let chunk_vec = chunk.to_vec();
        let results = with_retry(max_attempts, || {
            let chunk_vec = chunk_vec.clone();
            async move { client.get_messages_by_id(channel, &chunk_vec).await }
        })
        .await
        .context("fetching part messages by id")?;
        for (id, message) in chunk.iter().zip(results) {
            if let Some(message) = message {
                map.insert(*id, message);
            }
        }
    }
    Ok(map)
}
