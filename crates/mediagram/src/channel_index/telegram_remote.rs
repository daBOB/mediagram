//! The channel, over a connected Telegram client: the one submodule of
//! `channel_index` that touches Telegram.

use std::collections::HashMap;

use anyhow::{Context, Result};
use grammers_client::Client;
use grammers_client::message::InputMessage;
use grammers_session::types::PeerRef;

use super::message_gone::message_is_gone;
use super::remote::{Candidate, ChannelRemote, Unpin};
use crate::telegram::client::Tg;
use crate::telegram::messages::fetch_messages;
use crate::telegram::retry::{with_flood_wait_only, with_retry};
use mediagram_core::transport::document::message_document;

/// How many pins, and how many marker-search hits, to read: the same bounds
/// the players read with (`mediagram-core`, `api/channel/search.rs`), so the
/// uploader never chooses among candidates a player would not see.
const MAX_PINNED: usize = 100;
const MAX_MARKED: usize = 50;

/// Holds its own handle on the connection, as `TelegramTransport` does, so
/// an upload session can keep both beside the connection they share.
pub struct TelegramRemote {
    client: Client,
    channel: PeerRef,
    chat_id: i64,
    max_attempts: u32,
}

impl TelegramRemote {
    pub fn new(tg: &Tg, max_attempts: u32) -> Self {
        TelegramRemote {
            client: tg.client.clone(),
            channel: tg.channel,
            chat_id: tg.chat_id(),
            max_attempts,
        }
    }
}

impl ChannelRemote for TelegramRemote {
    async fn candidates(&self) -> Result<Vec<Candidate>> {
        let candidate = |message: &grammers_client::message::Message| Candidate {
            id: message.id(),
            caption: message.text().to_string(),
            own_post: message.post(),
        };
        let mut found = Vec::new();
        let mut pinned = self
            .client
            .search_messages(self.channel)
            .filter(grammers_tl_types::enums::MessagesFilter::InputMessagesFilterPinned)
            .limit(MAX_PINNED);
        while let Some(message) = pinned.next().await? {
            found.push(candidate(&message));
        }
        // Allowed to fail, as it is for the players: the pins are the
        // cheaper half, and a channel that will not search still has them.
        let mut marked = self
            .client
            .search_messages(self.channel)
            .query(mlib_spec::index_caption::PREFIX)
            .limit(MAX_MARKED);
        while let Ok(Some(message)) = marked.next().await {
            if !found.iter().any(|c: &Candidate| c.id == message.id()) {
                found.push(candidate(&message));
            }
        }
        Ok(found)
    }

    async fn download(&self, id: i32) -> Result<Option<Vec<u8>>> {
        let found = fetch_messages(&self.client, self.channel, &[id], self.max_attempts).await?;
        let Some((document, _)) = found.get(&id).and_then(message_document) else {
            return Ok(None);
        };
        let mut bytes = Vec::new();
        let mut chunks = self.client.iter_download(&document);
        while let Some(chunk) = chunks.next().await? {
            bytes.extend_from_slice(&chunk);
        }
        Ok(Some(bytes))
    }

    async fn captions(&self, ids: &[i32]) -> Result<HashMap<i32, String>> {
        let found = fetch_messages(&self.client, self.channel, ids, self.max_attempts).await?;
        Ok(found
            .into_iter()
            .map(|(id, m)| (id, m.text().to_string()))
            .collect())
    }

    fn chat_id(&self) -> i64 {
        self.chat_id
    }

    async fn send_document(
        &self,
        bytes: &[u8],
        name: &str,
        mime: &str,
        caption: &str,
    ) -> Result<i32> {
        let mut cursor = std::io::Cursor::new(bytes);
        let uploaded = self
            .client
            .upload_stream(&mut cursor, bytes.len(), name.to_string())
            .await
            .with_context(|| format!("uploading {name}"))?;
        let (client, channel) = (self.client.clone(), self.channel);
        let (mime, caption) = (mime.to_string(), caption.to_string());
        // Not idempotent: a lost response after a committed send would
        // duplicate the message, so only FLOOD_WAIT is retried.
        let message = with_flood_wait_only(self.max_attempts, move || {
            let (client, uploaded, mime, caption) = (
                client.clone(),
                uploaded.clone(),
                mime.clone(),
                caption.clone(),
            );
            async move {
                let input = InputMessage::new()
                    .mime_type(&mime)
                    .text(caption)
                    .document(uploaded);
                client.send_message(channel, input).await
            }
        })
        .await?;
        Ok(message.id())
    }

    async fn delete_message(&self, id: i32) -> Result<()> {
        let (client, channel) = (self.client.clone(), self.channel);
        // Deleting a missing id reports zero affected, not an error, so a
        // retry after a lost response is harmless.
        with_retry(self.max_attempts, move || {
            let client = client.clone();
            async move { client.delete_messages(channel, &[id]).await }
        })
        .await
        .map(|_| ())
    }

    async fn pin(&self, id: i32) -> Result<()> {
        let (client, channel) = (self.client.clone(), self.channel);
        with_retry(self.max_attempts, move || {
            let client = client.clone();
            async move { client.pin_message(channel, id).await }
        })
        .await
    }

    async fn unpin(&self, id: i32) -> Result<Unpin> {
        let (client, channel) = (self.client.clone(), self.channel);
        let sent = with_retry(self.max_attempts, move || {
            let client = client.clone();
            async move { client.unpin_message(channel, id).await }
        })
        .await;
        match sent {
            Ok(()) => Ok(Unpin::Sent),
            Err(err) if message_is_gone(&err) => Ok(Unpin::Gone),
            Err(err) => Err(err),
        }
    }

    /// Asked of the message itself rather than of the pinned-message search,
    /// which is an index and can lag behind what the message carries.
    async fn is_pinned(&self, id: i32) -> Result<bool> {
        let found = fetch_messages(&self.client, self.channel, &[id], self.max_attempts)
            .await
            .context("reading back the pinned flag")?;
        // A deleted message is absent, and has no pin left to clear.
        Ok(found.get(&id).is_some_and(|m| m.pinned()))
    }
}
