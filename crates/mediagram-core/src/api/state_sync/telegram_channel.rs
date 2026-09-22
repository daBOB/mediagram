//! Watch-state documents, on the same channel as everything else.
//!
//! The only part of syncing that speaks MTProto — everything worth getting
//! right (when to send, whether anything changed, what to do with what
//! comes back) is on the other side of `StateChannel` and is tested against
//! a fake in `state::sync_tests`. A port of
//! `web/src/telegram/state-channel.ts`, including the one hazard measured
//! there directly rather than assumed.
//!
//! **A first send whose pin is refused takes the document back and fails
//! the round.** Unpinned, discovery is the pin list, so the document is
//! invisible — not even this device would find it again — and the next
//! round would send another beside it. Pins are flood-limited hard
//! (`FLOOD_WAIT_633`, measured on the web player), so a refusal costs this
//! round, never a stray document for the life of the install.

use std::io::Cursor;

use grammers_client::Client;
use grammers_client::media::{Document, Uploaded};
use grammers_client::message::InputMessage;
use grammers_mtsender::InvocationError;
use grammers_session::types::PeerRef;
use grammers_tl_types::enums::MessagesFilter;

use crate::api::account::revoked::{checked, unless_revoked};
use crate::api::channel::index::channel_error;
use crate::api::{Core, CoreError};
use crate::state::channel::{device_from_caption, state_caption};
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
    peer: PeerRef,
}

impl<'a> TelegramStateChannel<'a> {
    pub(super) fn new(core: &'a Core, client: Client, peer: PeerRef) -> Self {
        TelegramStateChannel { core, client, peer }
    }

    /// Downloaded one chunk at a time rather than in parallel: there are as
    /// many of these as there are devices, and a burst of downloads on
    /// startup is a good way to meet a flood wait for no benefit. `None`
    /// for a document over the cap or one that is not valid UTF-8 — either
    /// way, not a real state document, so the round skips it rather than
    /// failing over it.
    async fn download_capped(&self, document: &Document) -> Result<Option<String>, CoreError> {
        let mut bytes = Vec::new();
        let mut chunks = self.client.iter_download(document);
        while let Some(chunk) = checked(
            self.core,
            chunks.next().await,
            |err| CoreError::network("a state document could not be downloaded")(err),
        )
        .await?
        {
            bytes.extend_from_slice(&chunk);
            if bytes.len() > MAX_STATE_DOC_BYTES {
                return Ok(None);
            }
        }
        Ok(String::from_utf8(bytes).ok())
    }

    /// Sends a first document and pins it, silently — `pin_message` never
    /// notifies. Later writes edit this message in place, so the pin stays
    /// for the life of the install.
    async fn send_and_pin(&self, caption: String, uploaded: Uploaded) -> Result<i32, CoreError> {
        let sent = checked(
            self.core,
            self.client.send_message(self.peer, InputMessage::new().text(caption).document(uploaded)).await,
            |err| CoreError::network("the state document could not be sent")(err),
        )
        .await?;
        let id = sent.id();

        if let Err(err) = checked(
            self.core,
            self.client.pin_message(self.peer, id).await,
            |err| CoreError::network("the state document's pin was refused")(err),
        )
        .await
        {
            let _ = self.client.delete_messages(self.peer, &[id]).await;
            return Err(err);
        }
        Ok(id)
    }
}

impl StateChannel for TelegramStateChannel<'_> {
    type Error = CoreError;

    async fn list(&self) -> Result<Vec<ChannelDocument>, CoreError> {
        let mut pinned =
            self.client.search_messages(self.peer).filter(MessagesFilter::InputMessagesFilterPinned).limit(MOST);
        let mut documents = Vec::new();
        while let Some(message) = checked(self.core, pinned.next().await, channel_error).await? {
            let Some(device) = device_from_caption(message.text()) else { continue };
            let Some((document, _id)) = message_document(&message) else { continue };
            if let Some(text) = self.download_capped(&document).await? {
                documents.push(ChannelDocument { message_id: message.id(), device, text });
            }
        }
        Ok(documents)
    }

    async fn put(&self, body: String, message_id: Option<i32>) -> Result<i32, CoreError> {
        let caption = state_caption(device_of(&body).as_deref().unwrap_or(""));
        let mut cursor = Cursor::new(body.as_bytes());
        let uploaded = self
            .client
            .upload_stream(&mut cursor, body.len(), STATE_DOCUMENT_NAME.to_string())
            .await
            .map_err(CoreError::network("the state document could not be uploaded"))?;

        if let Some(id) = message_id {
            // Edited, never re-sent: a device writes one message for ever.
            let edit = InputMessage::new().text(caption.clone()).document(uploaded.clone());
            match self.client.edit_message(self.peer, id, edit).await {
                Ok(()) => return Ok(id),
                // The message this device remembered as its own is gone —
                // deleted by hand, say. Falls through to a fresh send
                // rather than failing the round over it.
                Err(err) if is_message_id_invalid(&err) => {}
                Err(err) => {
                    let fallback = CoreError::network("the state document could not be edited")(&err);
                    return Err(unless_revoked(self.core, &err, fallback).await);
                }
            }
        }

        self.send_and_pin(caption, uploaded).await
    }
}

fn is_message_id_invalid(err: &InvocationError) -> bool {
    matches!(err, InvocationError::Rpc(rpc) if rpc.name == "MESSAGE_ID_INVALID")
}

/// The device a body names, for the caption — mirrors the web adapter's
/// `JSON.parse(body).device`. `put` is only ever called with a body
/// `state::sync::give` just serialised, so this should never actually miss;
/// an empty device is the harmless fallback if it somehow did.
fn device_of(body: &str) -> Option<String> {
    let value: serde_json::Value = serde_json::from_str(body).ok()?;
    value.get("device")?.as_str().map(str::to_string)
}
