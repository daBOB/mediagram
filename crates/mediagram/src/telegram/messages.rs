//! Batched retrieval of channel messages by id.

use std::collections::HashMap;

use anyhow::{Context, Result};
use grammers_client::Client;
use grammers_client::message::Message;
use grammers_session::types::PeerRef;
use grammers_tl_types as tl;

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
            if let Some(message) = message
                && !is_deleted(&message.raw)
            {
                map.insert(*id, message);
            }
        }
    }
    Ok(map)
}

/// A deleted channel message comes back as `messageEmpty`, not as a missing
/// entry: grammers files it under the channel it was asked about, so without
/// this a removed set reads as still in the channel and a merge revives it.
fn is_deleted(raw: &tl::enums::Message) -> bool {
    matches!(raw, tl::enums::Message::Empty(_))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn an_empty_message_is_deleted_and_a_real_one_is_not() {
        let empty = tl::enums::Message::Empty(tl::types::MessageEmpty {
            id: 17136,
            peer_id: None,
        });
        assert!(is_deleted(&empty));

        let service = tl::enums::Message::Service(tl::types::MessageService {
            out: false,
            mentioned: false,
            media_unread: false,
            reactions_are_possible: false,
            silent: false,
            post: true,
            legacy: false,
            id: 1,
            from_id: None,
            peer_id: tl::enums::Peer::Channel(tl::types::PeerChannel { channel_id: 1 }),
            saved_peer_id: None,
            reply_to: None,
            date: 0,
            action: tl::enums::MessageAction::Empty,
            reactions: None,
            ttl_period: None,
        });
        assert!(!is_deleted(&service));
    }
}
