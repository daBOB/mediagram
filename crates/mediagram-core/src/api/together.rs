//! Watch-together session messages: one unpinned text message per session in
//! the library channel, edited in place (`docs/mlib-spec.md` §7b). Only I/O
//! lives here. The rules of a session are in Kotlin
//! (`android/core/model/.../together/`), ported from the web
//! (`web/public/lib/together/`) and held to its fixtures.

use std::sync::PoisonError;

use grammers_client::message::InputMessage;
use grammers_mtsender::InvocationError;
use grammers_tl_types as tl;

use super::account::revoked::checked_for;
use super::account::session;
use super::channel::library;
use super::{Core, CoreError};
use crate::updates::TOGETHER_PREFIX;

/// One history read. Search is never used: it misses fresh messages and
/// reads the marker as the hashtag `#mlib`.
const RECENT: usize = 100;

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct TogetherMessage {
    pub message_id: i32,
    pub text: String,
}

#[derive(Debug, Clone, PartialEq, uniffi::Enum)]
pub enum TogetherEdit {
    Ok,
    /// The message is gone: the session is over.
    Gone,
    /// Telegram asked for a pause longer than grammers sleeps through on its own.
    Wait {
        seconds: u32,
    },
}

/// What an edit's answer means to a session, as the web's adapter reads it.
/// `channel_error` is no use here: it reads every 400 as "no longer
/// visible", and an edit that changes nothing is a 400 too.
pub(crate) fn edit_outcome(
    result: Result<(), InvocationError>,
) -> Result<TogetherEdit, InvocationError> {
    match result {
        Ok(()) => Ok(TogetherEdit::Ok),
        Err(InvocationError::Rpc(rpc)) if rpc.name == "MESSAGE_NOT_MODIFIED" => {
            Ok(TogetherEdit::Ok)
        }
        Err(InvocationError::Rpc(rpc)) if rpc.name == "MESSAGE_ID_INVALID" => {
            Ok(TogetherEdit::Gone)
        }
        Err(InvocationError::Rpc(rpc)) if rpc.code == 420 => Ok(TogetherEdit::Wait {
            seconds: rpc.value.unwrap_or(30),
        }),
        Err(other) => Err(other),
    }
}

/// A message's text, or `None` once it is deleted: a channel answers a
/// deleted id with an empty message, whose text grammers reads as `""`.
fn live_text(raw: &tl::enums::Message) -> Option<String> {
    match raw {
        tl::enums::Message::Message(message) => Some(message.message.clone()),
        _ => None,
    }
}

#[uniffi::export(async_runtime = "tokio")]
impl Core {
    /// Session messages among the newest channel messages, newest first.
    pub async fn together_recent(&self, handle: String) -> Result<Vec<TogetherMessage>, CoreError> {
        let (_, peer) = library::peer_of(self, &handle)?;
        let (client, owner) = session::connection(self).await;
        let mut messages = client.iter_messages(peer).limit(RECENT);
        let mut found = Vec::new();
        loop {
            let next = checked_for(self, &owner, messages.next().await, |err| {
                CoreError::network("could not read the watch-together sessions")(err)
            })
            .await?;
            let Some(message) = next else { break };
            // Only the channel's own posts, as the index is read.
            if message.post() && message.text().starts_with(TOGETHER_PREFIX) {
                found.push(TogetherMessage {
                    message_id: message.id(),
                    text: message.text().to_owned(),
                });
            }
        }
        Ok(found)
    }

    /// A session message's current text, or `None` once it is deleted.
    pub async fn together_read(
        &self,
        handle: String,
        message_id: i32,
    ) -> Result<Option<String>, CoreError> {
        let (_, peer) = library::peer_of(self, &handle)?;
        let (client, owner) = session::connection(self).await;
        let read = client.get_messages_by_id(peer, &[message_id]).await;
        let messages = checked_for(self, &owner, read, |err| {
            CoreError::network("could not read the watch-together session")(err)
        })
        .await?;
        Ok(messages
            .into_iter()
            .next()
            .flatten()
            .and_then(|message| live_text(&message.raw)))
    }

    /// Sends a new session message, never pinned, and answers its id.
    pub async fn together_send(&self, handle: String, text: String) -> Result<i32, CoreError> {
        let (_, peer) = library::peer_of(self, &handle)?;
        let (client, owner) = session::connection(self).await;
        let message = InputMessage::new().text(text).silent(true);
        let sent = client
            .send_message(peer, message)
            .await
            .map(|sent| sent.id());
        checked_for(self, &owner, sent, |err| {
            CoreError::network("the watch-together session could not be started")(err)
        })
        .await
    }

    pub async fn together_edit(
        &self,
        handle: String,
        message_id: i32,
        text: String,
    ) -> Result<TogetherEdit, CoreError> {
        let (_, peer) = library::peer_of(self, &handle)?;
        let (client, owner) = session::connection(self).await;
        let edit = client
            .edit_message(peer, message_id, InputMessage::new().text(text))
            .await;
        checked_for(self, &owner, edit_outcome(edit), |err| {
            CoreError::network("the watch-together session could not be updated")(err)
        })
        .await
    }

    pub async fn together_delete(&self, handle: String, message_id: i32) -> Result<(), CoreError> {
        let (_, peer) = library::peer_of(self, &handle)?;
        let (client, owner) = session::connection(self).await;
        let deleted = client
            .delete_messages(peer, &[message_id])
            .await
            .map(|_| ());
        checked_for(self, &owner, deleted, |err| {
            CoreError::network("the watch-together session could not be ended")(err)
        })
        .await
    }
}

#[uniffi::export]
impl Core {
    /// The newest watch-together message the listener heard. Read it after a `LibraryEvent::Together`.
    pub fn together_heard(&self) -> Option<TogetherMessage> {
        self.together_heard
            .lock()
            .unwrap_or_else(PoisonError::into_inner)
            .clone()
    }
}

#[cfg(test)]
#[path = "together_tests.rs"]
mod tests;
