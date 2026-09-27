//! The channel, over a connected Telegram client: the one submodule of
//! `channel_index` that touches Telegram.

use std::collections::HashMap;
use std::path::Path;

use anyhow::{Context, Result};
use grammers_client::message::InputMessage;

use super::remote::{Candidate, ChannelRemote, Unpin};
use crate::telegram::client::Tg;
use crate::telegram::retry::{with_flood_wait_only, with_retry};
use crate::verify::download_hash::fetch_messages;
use mediagram_core::transport::document::message_document;

const INDEX_MIME_TYPE: &str = "application/vnd.sqlite3";

/// How many pins, and how many marker-search hits, to read: the same bounds
/// the players read with (`mediagram-core`, `api/channel/search.rs`), so the
/// uploader never chooses among candidates a player would not see.
const MAX_PINNED: usize = 100;
const MAX_MARKED: usize = 50;

/// Errors that mean there is no pin left to clear: `MESSAGE_ID_INVALID` is a
/// message that no longer exists, `MESSAGE_NOT_MODIFIED` one already in the
/// state asked for.
const NOTHING_TO_UNPIN: &[&str] = &["MESSAGE_ID_INVALID", "MESSAGE_NOT_MODIFIED"];

pub struct TelegramRemote<'t> {
    tg: &'t Tg,
    max_attempts: u32,
}

impl<'t> TelegramRemote<'t> {
    pub fn new(tg: &'t Tg, max_attempts: u32) -> Self {
        TelegramRemote { tg, max_attempts }
    }
}

impl ChannelRemote for TelegramRemote<'_> {
    async fn candidates(&self) -> Result<Vec<Candidate>> {
        let candidate = |message: &grammers_client::message::Message| Candidate {
            id: message.id(),
            caption: message.text().to_string(),
            own_post: message.post(),
        };
        let mut found = Vec::new();
        let mut pinned = self
            .tg
            .client
            .search_messages(self.tg.channel)
            .filter(grammers_tl_types::enums::MessagesFilter::InputMessagesFilterPinned)
            .limit(MAX_PINNED);
        while let Some(message) = pinned.next().await? {
            found.push(candidate(&message));
        }
        // Allowed to fail, as it is for the players: the pins are the
        // cheaper half, and a channel that will not search still has them.
        let mut marked = self
            .tg
            .client
            .search_messages(self.tg.channel)
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
        let found =
            fetch_messages(&self.tg.client, self.tg.channel, &[id], self.max_attempts).await?;
        let Some((document, _)) = found.get(&id).and_then(message_document) else {
            return Ok(None);
        };
        let mut bytes = Vec::new();
        let mut chunks = self.tg.client.iter_download(&document);
        while let Some(chunk) = chunks.next().await? {
            bytes.extend_from_slice(&chunk);
        }
        Ok(Some(bytes))
    }

    async fn captions(&self, ids: &[i32]) -> Result<HashMap<i32, String>> {
        let found =
            fetch_messages(&self.tg.client, self.tg.channel, ids, self.max_attempts).await?;
        Ok(found
            .into_iter()
            .map(|(id, m)| (id, m.text().to_string()))
            .collect())
    }

    fn chat_id(&self) -> i64 {
        self.tg.chat_id()
    }

    async fn send_index(&self, path: &Path, caption: &str) -> Result<i32> {
        let size = tokio::fs::metadata(path)
            .await
            .with_context(|| format!("stat {}", path.display()))?
            .len() as usize;
        let mut file = tokio::fs::File::open(path)
            .await
            .with_context(|| format!("opening {}", path.display()))?;
        // An explicit name lets the document be called `library.db`
        // whatever the snapshot's name on disk.
        let uploaded = self
            .tg
            .client
            .upload_stream(&mut file, size, mlib_spec::schema::INDEX_FILE.to_string())
            .await
            .with_context(|| format!("uploading {}", path.display()))?;
        let (client, channel) = (self.tg.client.clone(), self.tg.channel);
        let caption = caption.to_string();
        // Not idempotent: a lost response after a committed send would
        // duplicate the index message, so only FLOOD_WAIT is retried.
        let message = with_flood_wait_only(self.max_attempts, move || {
            let (client, uploaded, caption) = (client.clone(), uploaded.clone(), caption.clone());
            async move {
                let input = InputMessage::new()
                    .mime_type(INDEX_MIME_TYPE)
                    .text(caption)
                    .document(uploaded);
                client.send_message(channel, input).await
            }
        })
        .await?;
        Ok(message.id())
    }

    async fn pin(&self, id: i32) -> Result<()> {
        let (client, channel) = (self.tg.client.clone(), self.tg.channel);
        with_retry(self.max_attempts, move || {
            let client = client.clone();
            async move { client.pin_message(channel, id).await }
        })
        .await
    }

    async fn unpin(&self, id: i32) -> Result<Unpin> {
        let (client, channel) = (self.tg.client.clone(), self.tg.channel);
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
        let found = fetch_messages(&self.tg.client, self.tg.channel, &[id], self.max_attempts)
            .await
            .context("reading back the pinned flag")?;
        // A deleted message is absent, and has no pin left to clear.
        Ok(found.get(&id).is_some_and(|m| m.pinned()))
    }
}

/// Whether an error means the message is gone rather than that the call
/// failed. Matched on the error name, not the 400 class it arrives in: that
/// class also carries `PEER_ID_INVALID`, `CHANNEL_INVALID` and
/// `CHAT_WRITE_FORBIDDEN`, and reading those as "gone" would drop the id and
/// leave a pinned index only a `rescan` could find.
pub fn message_is_gone(err: &anyhow::Error) -> bool {
    matches!(
        err.downcast_ref::<grammers_mtsender::InvocationError>(),
        Some(grammers_mtsender::InvocationError::Rpc(rpc))
            if rpc.code == 400 && NOTHING_TO_UNPIN.contains(&rpc.name.as_str())
    )
}
