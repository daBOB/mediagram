//! Kotlin's entry points for watch-state sync: this device's id, and one
//! round.
//!
//! Orchestration only. What a round decides — when to send, whether
//! anything changed, what to do with what comes back — lives in
//! `crate::state::sync`, pinned to the web by the same fixtures as the rest
//! of `crate::state` and tested against a fake channel there. What speaks
//! MTProto lives in [`telegram_channel`], kept under `crate::api` rather
//! than under `crate::state` so it can reach the revoked-login handling
//! every other Telegram call here goes through.

mod publish;
mod telegram_channel;

use std::sync::Arc;

use grammers_session::types::PeerRef;

use super::account::session;
use super::channel::library;
use super::{Core, CoreError};
use crate::state::sync::{self, SyncOutcome};
use telegram_channel::TelegramStateChannel;

#[uniffi::export(async_runtime = "tokio")]
impl Core {
    /// This install's own id in the sync channel — a random string made
    /// once and kept in `state.db`, never the hostname. Kotlin passes it on
    /// to `next_library_event` as `own_device`, so this device's own writes
    /// never come back to it as a change worth a round.
    pub async fn state_device_id(self: Arc<Self>) -> String {
        self.blocking(|core| core.state_db.with(sync::device_id).unwrap_or_default())
            .await
    }

    /// One round of watch-state sync against the library `handle` names:
    /// lists the pinned state documents there, merges in what is newer,
    /// and pushes this device's own document if anything changed.
    ///
    /// Never throws — a channel that cannot be reached, a login Telegram
    /// has revoked, or a refused send all come back as `failed`. Successful
    /// imports stay committed and are counted even if sending fails, so the
    /// caller can reload the local state. At most one round runs at a time on
    /// this `Core`: a second call made while one is in flight waits for it,
    /// so a first send is never issued twice.
    pub async fn sync_state(self: Arc<Self>, handle: String) -> SyncOutcome {
        let device = Arc::clone(&self).state_device_id().await;

        match peer_for(&self, &handle) {
            Ok(peer) => {
                let (client, owner) = session::connection(&self).await;
                let channel = TelegramStateChannel::new(&self, client, owner, peer);
                sync::serialized(&self.sync_memo, &handle, &self.state_db, &channel, &device).await
            }
            Err(err) => SyncOutcome {
                pulled: 0,
                pushed: false,
                failed: Some(err.to_string()),
            },
        }
    }
}

/// The channel `handle`'s library lives in — the same one its index does,
/// found the same way `next_library_event` finds it.
fn peer_for(core: &Core, handle: &str) -> Result<PeerRef, CoreError> {
    let entry = library::lookup(core, handle)?;
    entry
        .peer()
        .ok_or_else(|| CoreError::NotFound("this device no longer has that library stored".into()))
}
