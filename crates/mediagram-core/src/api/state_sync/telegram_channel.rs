//! Watch-state documents on the library channel. Pinned messages are scanned
//! serially and downloads are capped before their JSON reaches the merge layer.
//! First-send cleanup and edit recovery live in `publish`.

use std::io::Cursor;

use grammers_client::Client;
use grammers_client::client::{DownloadIter, SearchIter};
use grammers_client::media::{Document, Uploaded};
use grammers_client::message::{InputMessage, Message};
use grammers_mtsender::{InvocationError, SenderPoolFatHandle};
use grammers_session::types::PeerRef;
use grammers_tl_types::enums::MessagesFilter;

use super::publish::{self, DocumentWriter};
use crate::api::account::revoked::checked_for;
use crate::api::channel::index::channel_error;
use crate::api::{Core, CoreError};
use crate::state::channel::device_from_caption;
use crate::state::sync::{ChannelDocument, StateChannel};
use crate::transport::document::message_document;

/// How many pins to read. One state document per device; not close.
const MOST: usize = 100;
/// A hard ceiling on one state document. The real thing is kilobytes; this
/// only stops a hostile or malformed pin from being read into memory
/// wholesale before anything looks at it.
const MAX_STATE_DOC_BYTES: usize = 1024 * 1024;
const STATE_DOCUMENT_NAME: &str = "watch-state.json";

pub(super) struct TelegramStateChannel<'a> {
    core: &'a Core,
    client: Client,
    owner: SenderPoolFatHandle,
    peer: PeerRef,
}

impl<'a> TelegramStateChannel<'a> {
    pub(super) fn new(
        core: &'a Core,
        client: Client,
        owner: SenderPoolFatHandle,
        peer: PeerRef,
    ) -> Self {
        TelegramStateChannel {
            core,
            client,
            owner,
            peer,
        }
    }
}

impl StateChannel for TelegramStateChannel<'_> {
    type Error = CoreError;

    async fn list(&self) -> Result<Vec<ChannelDocument>, CoreError> {
        let pinned = self
            .client
            .search_messages(self.peer)
            .filter(MessagesFilter::InputMessagesFilterPinned)
            .limit(MOST);
        list_pinned(self.core, &self.owner, pinned, |document| {
            self.client.iter_download(document)
        })
        .await
    }

    async fn put(&self, body: String, message_id: Option<i32>) -> Result<i32, CoreError> {
        publish::put(self.core, &self.owner, self, body, message_id).await
    }
}

impl DocumentWriter for TelegramStateChannel<'_> {
    type Upload = Uploaded;

    async fn upload(&self, body: &str) -> Result<Uploaded, std::io::Error> {
        let mut cursor = Cursor::new(body.as_bytes());
        self.client
            .upload_stream(&mut cursor, body.len(), STATE_DOCUMENT_NAME.to_string())
            .await
    }

    async fn edit(
        &self,
        id: i32,
        caption: String,
        uploaded: Uploaded,
    ) -> Result<(), InvocationError> {
        self.client
            .edit_message(
                self.peer,
                id,
                InputMessage::new().text(caption).document(uploaded),
            )
            .await
    }

    async fn send(&self, caption: String, uploaded: Uploaded) -> Result<i32, InvocationError> {
        self.client
            .send_message(
                self.peer,
                InputMessage::new().text(caption).document(uploaded),
            )
            .await
            .map(|message| message.id())
    }

    async fn pin(&self, id: i32) -> Result<(), InvocationError> {
        self.client.pin_message(self.peer, id).await
    }

    async fn delete(&self, id: i32) -> Result<(), InvocationError> {
        self.client
            .delete_messages(self.peer, &[id])
            .await
            .map(|_| ())
    }
}

/// Raw streaming IO shared by grammers' message and document iterators.
trait Responses {
    type Item;
    async fn next_response(&mut self) -> Result<Option<Self::Item>, InvocationError>;
}

impl Responses for SearchIter {
    type Item = Message;
    async fn next_response(&mut self) -> Result<Option<Message>, InvocationError> {
        self.next().await
    }
}

impl Responses for DownloadIter {
    type Item = Vec<u8>;
    async fn next_response(&mut self) -> Result<Option<Vec<u8>>, InvocationError> {
        self.next().await
    }
}

async fn list_pinned<C: Responses<Item = Vec<u8>>>(
    core: &Core,
    owner: &SenderPoolFatHandle,
    mut pinned: impl Responses<Item = Message>,
    download: impl Fn(&Document) -> C,
) -> Result<Vec<ChannelDocument>, CoreError> {
    let mut documents = Vec::new();
    while let Some(message) =
        checked_for(core, owner, pinned.next_response().await, channel_error).await?
    {
        let Some(device) = device_from_caption(message.text()) else {
            continue;
        };
        let Some((document, _id)) = message_document(&message) else {
            continue;
        };
        if let Some(text) = download_capped(core, owner, download(&document)).await? {
            documents.push(ChannelDocument {
                message_id: message.id(),
                device,
                text,
            });
        }
    }
    Ok(documents)
}

/// Oversized or non-UTF-8 documents are skipped; transport failures fail the round.
async fn download_capped(
    core: &Core,
    owner: &SenderPoolFatHandle,
    mut chunks: impl Responses<Item = Vec<u8>>,
) -> Result<Option<String>, CoreError> {
    let mut bytes = Vec::new();
    while let Some(chunk) = checked_for(core, owner, chunks.next_response().await, |err| {
        CoreError::network("a state document could not be downloaded")(err)
    })
    .await?
    {
        bytes.extend_from_slice(&chunk);
        if bytes.len() > MAX_STATE_DOC_BYTES {
            return Ok(None);
        }
    }
    Ok(String::from_utf8(bytes).ok())
}

#[cfg(test)]
#[path = "telegram_channel_tests.rs"]
mod tests;
