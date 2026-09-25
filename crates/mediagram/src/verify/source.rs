//! Message retrieval and chunk streams used by verification orchestration.

use std::collections::HashMap;

use anyhow::Result;
use grammers_client::client::DownloadIter;
use grammers_client::media::Document;
use mediagram_core::transport::document::message_document;

use super::download_hash::{ChunkSource, fetch_messages};
use super::{other_chat, report::ObservedMessage};
use crate::index::parts::PartRow;
use crate::index::status::PartStatus;
use crate::telegram::client::Tg;

pub(super) enum RemoteMessage<D> {
    NoDocument,
    Document {
        document: D,
        id: i64,
        size: Option<u64>,
    },
}

pub(super) trait VerificationSource {
    type Document;
    type Chunks: ChunkSource;

    async fn messages(&self, ids: &[i32]) -> Result<HashMap<i32, RemoteMessage<Self::Document>>>;
    fn download(&self, document: &Self::Document) -> Self::Chunks;
}

pub(super) struct TelegramSource<'a> {
    pub tg: &'a Tg,
    pub max_attempts: u32,
}

impl VerificationSource for TelegramSource<'_> {
    type Document = Document;
    type Chunks = DownloadIter;

    async fn messages(&self, ids: &[i32]) -> Result<HashMap<i32, RemoteMessage<Document>>> {
        let messages =
            fetch_messages(&self.tg.client, self.tg.channel, ids, self.max_attempts).await?;
        Ok(messages
            .into_iter()
            .map(|(id, message)| {
                let remote = match message_document(&message) {
                    Some((document, id)) => RemoteMessage::Document {
                        size: document.size().map(|size| size as u64),
                        document,
                        id,
                    },
                    None => RemoteMessage::NoDocument,
                };
                (id, remote)
            })
            .collect())
    }

    fn download(&self, document: &Document) -> DownloadIter {
        self.tg.client.iter_download(document)
    }
}

/// What Telegram shows for this part, plus the document itself when there is
/// one to hash, so `--full` never has to look the message up a second time.
pub(super) fn observe<'a, D>(
    part: &PartRow,
    chat_id: i64,
    messages: &'a HashMap<i32, RemoteMessage<D>>,
) -> (ObservedMessage, Option<&'a D>) {
    if part.status != PartStatus::Done {
        return (ObservedMessage::NotUploaded, None);
    }
    if let Some(recorded) = other_chat(part, chat_id) {
        let observed = ObservedMessage::OtherChat {
            recorded,
            current: chat_id,
        };
        return (observed, None);
    }
    let Some(id) = part.message_id.and_then(|id| i32::try_from(id).ok()) else {
        return (ObservedMessage::NotUploaded, None);
    };
    let Some(message) = messages.get(&id) else {
        return (ObservedMessage::MessageMissing, None);
    };
    match message {
        RemoteMessage::Document { document, id, size } => (
            ObservedMessage::Document {
                doc_id: *id,
                size: *size,
            },
            Some(document),
        ),
        RemoteMessage::NoDocument => (ObservedMessage::NoDocument, None),
    }
}
