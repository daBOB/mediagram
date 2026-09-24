//! Keeping the Continue shelf the same on every device.
//!
//! Matches `web/src/state/sync.ts`: local state remains authoritative, and
//! sync failures never prevent playback. [`StateChannel`] separates the
//! merge and retry decisions from the Telegram adapter so tests can exercise
//! complete rounds without a live account.

use std::collections::HashMap;

mod device;
mod error;
pub use device::device_id;
use error::SyncError;

use super::StateDb;
use super::exchange;
use super::merge::merge_states;
use super::record::{SyncRecord, parse_record};

/// One device's document, as it sits on the channel.
#[derive(Debug, Clone, PartialEq)]
pub struct ChannelDocument {
    pub message_id: i32,
    /// Whose it is, from the caption.
    pub device: String,
    /// The JSON body.
    pub text: String,
}

/// Where the documents live.
///
/// Deliberately small: list them, and put mine. A device only ever writes
/// its own message — Telegram has no compare-and-swap, so two devices
/// editing one shared message would clobber each other with no way to
/// notice.
pub trait StateChannel {
    type Error: std::fmt::Display;
    async fn list(&self) -> Result<Vec<ChannelDocument>, Self::Error>;
    /// Sends, or edits `message_id` when this device has written before.
    async fn put(&self, body: String, message_id: Option<i32>) -> Result<i32, Self::Error>;
}

#[derive(Debug, Clone, Default, PartialEq, uniffi::Record)]
pub struct SyncOutcome {
    /// Rows this device took in.
    pub pulled: u64,
    /// Whether a document was actually sent.
    pub pushed: bool,
    /// What went wrong, if anything. Never a panic, never a `Result`.
    pub failed: Option<String>,
}

/// What a round remembers between calls, keyed by library handle so that
/// switching the chosen library never reuses another channel's message id
/// or "nothing changed" memory — see `api::state_sync` on why a handle
/// switch must not touch either.
#[derive(Default)]
pub struct SyncMemo {
    by_handle: HashMap<String, Memo>,
}

impl SyncMemo {
    pub fn entry(&mut self, handle: &str) -> &mut Memo {
        self.by_handle.entry(handle.to_string()).or_default()
    }
}

/// This device's own message on one channel, once it is known, and the
/// last body sent there so an unchanged one is not sent again.
#[derive(Default, Clone)]
pub struct Memo {
    mine: Option<i32>,
    last_sent: Option<String>,
}

/// Runs a complete round under the same lock used by the production adapter.
/// A timer and a channel event must not both send this device's first document.
pub async fn serialized<C: StateChannel>(
    memo: &tokio::sync::Mutex<SyncMemo>,
    handle: &str,
    state_db: &StateDb,
    channel: &C,
    device: &str,
) -> SyncOutcome {
    let mut memo = memo.lock().await;
    once(state_db, channel, device, memo.entry(handle)).await
}

/// One round: read everyone's, merge, take in what is newer, write back.
///
/// Pull before push so what this device sends already reflects what it
/// just learnt. A failed import rolls back; a failed send keeps the imported
/// rows and reports their count so callers can refresh the local library.
pub async fn once<C: StateChannel>(
    state_db: &StateDb,
    channel: &C,
    device: &str,
    memo: &mut Memo,
) -> SyncOutcome {
    let mut outcome = SyncOutcome::default();
    if let Err(failed) = round(state_db, channel, device, memo, &mut outcome).await {
        outcome.failed = Some(failed.to_string());
    }
    outcome
}

async fn round<C: StateChannel>(
    state_db: &StateDb,
    channel: &C,
    device: &str,
    memo: &mut Memo,
    outcome: &mut SyncOutcome,
) -> Result<(), SyncError<C::Error>> {
    let documents = channel.list().await.map_err(SyncError::Channel)?;
    outcome.pulled =
        merge_and_import_documents(state_db, &documents, device, memo).ok_or(SyncError::Import)?;
    outcome.pushed = publish_state_if_changed(state_db, channel, device, memo).await?;
    Ok(())
}

/// Merges what the channel holds into this device.
///
/// **This device's own document is part of the merge** — built fresh from
/// `state.db` rather than trusted from what the channel handed back, so
/// nothing here depends on this device having ever sent one. Without it
/// the merge would answer only what the others knew, and `import_merged`
/// is corrective rather than wholesale precisely so that this cannot erase
/// anything — but including it is what makes the answer complete rather
/// than merely safe.
fn merge_and_import_documents(
    state_db: &StateDb,
    documents: &[ChannelDocument],
    device: &str,
    memo: &mut Memo,
) -> Option<u64> {
    state_db.with(|conn| {
        let mut records: Vec<SyncRecord> = vec![exchange::export_record(conn, device)?];
        for document in documents {
            // A device's own message is recognised here rather than
            // filtered out beforehand, so its id is learnt even on a
            // round where nothing needs sending.
            if document.device == device {
                memo.mine = Some(document.message_id);
            } else if let Some(record) = parse_record(&document.text) {
                records.push(record);
            }
        }
        exchange::import_merged(conn, &merge_states(&records))
    })
}

/// Writes this device's document, unless it would be the same one again.
async fn publish_state_if_changed<C: StateChannel>(
    state_db: &StateDb,
    channel: &C,
    device: &str,
    memo: &mut Memo,
) -> Result<bool, SyncError<C::Error>> {
    let record = state_db
        .with(|conn| exchange::export_record(conn, device))
        .ok_or(SyncError::Read)?;
    let body = serde_json::to_string(&record)?;
    // `writtenAt` changes on every export and nothing reads it during a
    // merge, so it is left out of the comparison: including it would make
    // every document different from the last and send one on every tick
    // for ever.
    let mut comparable_record = record.clone();
    comparable_record.written_at = 0.0;
    let comparable = serde_json::to_string(&comparable_record)?;
    if memo.last_sent.as_deref() == Some(comparable.as_str()) {
        return Ok(false);
    }

    let sent = channel
        .put(body, memo.mine)
        .await
        .map_err(SyncError::Channel)?;
    memo.mine = Some(sent);
    memo.last_sent = Some(comparable);
    Ok(true)
}

#[cfg(test)]
#[path = "sync_tests.rs"]
mod tests;
