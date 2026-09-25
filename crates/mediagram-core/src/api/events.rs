//! Waiting for the library channel to change.
//!
//! Converts raw grammers updates for [`crate::updates`], which classifies
//! and debounces changes using the web's shared fixtures.
//!
//! Events are hints; `catch_up` does not replay offline channel changes.
//! Callers run an ordinary sync round whenever they start listening.

use grammers_session::types::PeerId;
use grammers_tl_types as tl;

use crate::updates::{ChannelUpdate, LibraryEvent, UpdateKind};

use super::channel::library;
use super::{Core, CoreError};

mod listener;
mod open;
pub(super) use listener::Listener;

/// Holds only the listener lock while waiting, so idle listening never stalls playback.
pub(super) async fn next(
    core: &Core,
    handle: &str,
    own_device: &str,
) -> Result<LibraryEvent, CoreError> {
    listener::next(core, channel_of(core, handle)?, own_device).await
}

/// The bare channel id behind a library handle, which is how updates name it.
fn channel_of(core: &Core, handle: &str) -> Result<i64, CoreError> {
    let entry = library::lookup(core, handle)?;
    PeerId::from_bot_api_dialog_id(entry.chat)
        .and_then(PeerId::bare_id)
        .ok_or_else(|| CoreError::NotFound("this device no longer has that library stored".into()))
}

/// A raw update in the shape [`classify`] reads, or `None` for the kinds it
/// never acts on.
fn channel_update(update: &tl::enums::Update) -> Option<ChannelUpdate> {
    use tl::enums::Update as U;
    let (kind, message) = match update {
        U::NewChannelMessage(u) => (UpdateKind::New, &u.message),
        U::EditChannelMessage(u) => (UpdateKind::Edit, &u.message),
        U::PinnedChannelMessages(u) => {
            return Some(ChannelUpdate {
                kind: UpdateKind::Pinned,
                channel: u.channel_id,
                caption: None,
                service: false,
                pinned: u.pinned,
            });
        }
        _ => return None,
    };
    let (peer, caption, service) = match message {
        tl::enums::Message::Message(m) => (&m.peer_id, Some(m.message.clone()), false),
        tl::enums::Message::Service(m) => (&m.peer_id, None, true),
        tl::enums::Message::Empty(_) => return None,
    };
    let tl::enums::Peer::Channel(peer) = peer else {
        return None;
    };
    Some(ChannelUpdate {
        kind,
        channel: peer.channel_id,
        caption,
        service,
        pinned: false,
    })
}

#[uniffi::export(async_runtime = "tokio")]
impl Core {
    /// Waits until the library `handle` names changes in a way worth a
    /// round: another device's watch state (`State`) or a newly published
    /// index (`Index`). Only a hint — run the ordinary sync or refresh on it,
    /// and run one when you start listening, since nothing missed while not
    /// listening is replayed. `own_device` is this device's watch-state id,
    /// so its own writes are not reported back. Cancelling the call stops
    /// the wait; an error means listening stopped — back off and call again.
    pub async fn next_library_event(
        &self,
        handle: String,
        own_device: String,
    ) -> Result<LibraryEvent, CoreError> {
        next(self, &handle, &own_device).await
    }
}

#[cfg(test)]
#[path = "events_tests.rs"]
mod tests;
