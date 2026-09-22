//! Waiting for the library channel to change.
//!
//! The grammers half of push updates. What counts as a change, and how often
//! one is let through, is decided in [`crate::updates`] and pinned by the
//! web's fixtures; this file only turns raw updates into that shape and
//! waits.
//!
//! Measured before any of this was written
//! (`plans/260922-2222-telegram-push-updates/reports/`): every change in the
//! channel reaches the other sessions of the account within milliseconds,
//! and `catch_up` replays nothing that was missed while offline. So an event
//! is a hint to run the ordinary round, never the data itself, and the
//! caller runs one round whenever it starts listening.

use std::collections::VecDeque;
use std::time::Duration;

use grammers_client::client::{UpdateStream, UpdatesConfiguration};
use grammers_mtsender::InvocationError;
use grammers_session::types::PeerId;
use grammers_tl_types as tl;
use tokio::time::Instant;

use crate::updates::{ChannelUpdate, Debouncer, LibraryEvent, UpdateKind, classify};

use super::account::session;
use super::channel::library;
use super::{Core, CoreError};

/// A burst — an upload's part, index, pin and unpin — folds into one event.
const WINDOW_MS: u64 = 5_000;

/// Updates held while nobody is waiting. They are hints, so dropping the
/// excess when the app is slow to ask loses nothing a round won't find.
const QUEUE_LIMIT: usize = 100;

/// One connection's update stream, and what it has seen but not yet said.
pub(super) struct Listener {
    stream: UpdateStream,
    debouncer: Debouncer,
    ready: VecDeque<LibraryEvent>,
    started: Instant,
}

/// Waits for the next change worth a round in the library `handle` names.
///
/// Holds only the listener's own lock while it waits — never the state lock
/// every read takes — so a wait of hours does not stall playback.
pub(super) async fn next(core: &Core, handle: &str, own_device: &str) -> Result<LibraryEvent, CoreError> {
    let channel = channel_of(core, handle)?;
    let mut slot = core.events.lock().await;
    if slot.is_none() {
        *slot = Some(open(core).await?);
    }
    let listener = slot.as_mut().expect("just set");
    match listener.wait(channel, own_device).await {
        Ok(event) => Ok(event),
        Err(err) => {
            // The sender pool behind the stream has quit — today only when
            // the `Core` itself is going. Cleared so a later call starts over
            // on whatever connection is live then. Any other error keeps the
            // listener: grammers re-sends its pending request on the next call.
            if matches!(err, InvocationError::Dropped) {
                *slot = None;
            }
            Err(CoreError::Network("stopped listening for library changes".into()))
        }
    }
}

async fn open(core: &Core) -> Result<Listener, CoreError> {
    // Subscribe first, while a failure or a cancelled call costs nothing:
    // the receiver below is handed out once per connection, and taking it
    // before a round trip that can fail would lose it for the app's life.
    session::subscribe(core).await?;
    let (client, updates) = session::updates_receiver(core).await;
    let updates = updates
        .ok_or_else(|| CoreError::Network("this connection's updates are already being read".into()))?;
    // `catch_up` here means "start from the state `subscribe` just stored",
    // not "replay what was missed" — nothing was, it is seconds old. From
    // that base grammers' first `getDifference` is re-sent if cancelled, and
    // a dropped connection is recovered from rather than left silent.
    let configuration = UpdatesConfiguration {
        catch_up: true,
        update_queue_limit: Some(QUEUE_LIMIT),
    };
    let stream = client
        .stream_updates(updates, configuration)
        .await
        .map_err(|_| CoreError::Network("could not start listening for library changes".into()))?;
    Ok(Listener {
        stream,
        debouncer: Debouncer::new(WINDOW_MS),
        ready: VecDeque::new(),
        started: Instant::now(),
    })
}

impl Listener {
    fn now_ms(&self) -> u64 {
        u64::try_from(self.started.elapsed().as_millis()).unwrap_or(u64::MAX)
    }

    async fn wait(&mut self, channel: i64, own_device: &str) -> Result<LibraryEvent, InvocationError> {
        loop {
            if let Some(event) = self.ready.pop_front() {
                return Ok(event);
            }
            let now = self.now_ms();
            self.ready.extend(self.debouncer.take(now));
            if !self.ready.is_empty() {
                continue;
            }
            let raw = match self.debouncer.next_due() {
                // A window is open: wait for more, but no longer than its end.
                Some(due) => {
                    let deadline = self.started + Duration::from_millis(due);
                    match tokio::time::timeout_at(deadline, self.stream.next_raw()).await {
                        Ok(raw) => raw?,
                        Err(_) => continue,
                    }
                }
                None => self.stream.next_raw().await?,
            };
            let event = channel_update(&raw.0).and_then(|update| classify(&update, channel, own_device));
            if let Some(event) = event {
                let now = self.now_ms();
                self.debouncer.offer(event, now);
            }
        }
    }
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
