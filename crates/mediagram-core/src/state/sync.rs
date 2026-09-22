//! Keeping the Continue shelf the same on every device.
//!
//! A port of `web/src/state/sync.ts`. The local database stays the source
//! of truth for the device it is on — this is an addition to it, never a
//! replacement, and everything here can fail: a player whose sync fails is
//! a player that works exactly as it did before any of this existed.
//!
//! The channel sits behind [`StateChannel`] rather than being reached for
//! directly. The decisions worth getting right — when to send, whether
//! anything changed, what to do with what comes back — are here and are
//! tested against a fake in `sync_tests`. Only the grammers calls are left
//! to `api::state_sync`'s adapter, which cannot be exercised without a live
//! account.

use std::collections::HashMap;

use rusqlite::{Connection, OptionalExtension, params};

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

const DEVICE_KEY: &str = "device_id";

/// This install's own id in the sync channel — a random string, made once
/// and kept in `state.db` for its life. Never the hostname: two installs on
/// identically named machines must not collide, and this id sits in plain
/// sight in every caption this device writes.
pub fn device_id(conn: &Connection) -> rusqlite::Result<String> {
    if let Some(id) = read_device_id(conn)? {
        return Ok(id);
    }
    let made = mint_device_id();
    conn.execute(
        "INSERT INTO state_meta(key, value) VALUES (?1, ?2) ON CONFLICT(key) DO NOTHING",
        params![DEVICE_KEY, made],
    )?;
    // Read back rather than trusting `made`: this proves the row exists
    // rather than assuming the insert landed ahead of whatever else may
    // have raced it to `ON CONFLICT DO NOTHING`.
    Ok(read_device_id(conn)?.unwrap_or(made))
}

fn read_device_id(conn: &Connection) -> rusqlite::Result<Option<String>> {
    conn.query_row("SELECT value FROM state_meta WHERE key = ?1", [DEVICE_KEY], |row| row.get(0)).optional()
}

/// 128 bits from the OS — the same shape `api::channel::library`'s handle
/// uses: opaque, unguessable, and unrelated to any hostname or serial
/// number that could otherwise leak into a caption on a shared channel.
fn mint_device_id() -> String {
    let mut bytes = [0u8; 16];
    getrandom::fill(&mut bytes).expect("the OS random source is available");
    hex::encode(bytes)
}

/// One round: read everyone's, merge, take in what is newer, write back.
///
/// Pull before push so what this device sends already reflects what it
/// just learnt. Never throws: a channel that cannot be reached, or a send
/// that is refused, costs a `failed` line and leaves `state.db` exactly as
/// it was.
pub async fn once<C: StateChannel>(state_db: &StateDb, channel: &C, device: &str, memo: &mut Memo) -> SyncOutcome {
    match round(state_db, channel, device, memo).await {
        Ok(outcome) => outcome,
        Err(failed) => SyncOutcome { pulled: 0, pushed: false, failed: Some(failed) },
    }
}

async fn round<C: StateChannel>(
    state_db: &StateDb,
    channel: &C,
    device: &str,
    memo: &mut Memo,
) -> Result<SyncOutcome, String> {
    let documents = channel.list().await.map_err(|err| err.to_string())?;
    let pulled = take(state_db, &documents, device, memo);
    let pushed = give(state_db, channel, device, memo).await?;
    Ok(SyncOutcome { pulled, pushed, failed: None })
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
fn take(state_db: &StateDb, documents: &[ChannelDocument], device: &str, memo: &mut Memo) -> u64 {
    state_db
        .with(|conn| {
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
        .unwrap_or(0)
}

/// Writes this device's document, unless it would be the same one again.
async fn give<C: StateChannel>(
    state_db: &StateDb,
    channel: &C,
    device: &str,
    memo: &mut Memo,
) -> Result<bool, String> {
    let record = state_db
        .with(|conn| exchange::export_record(conn, device))
        .ok_or_else(|| "the local state could not be read".to_string())?;
    let body = serde_json::to_string(&record).map_err(|err| err.to_string())?;
    // `writtenAt` changes on every export and nothing reads it during a
    // merge, so it is left out of the comparison: including it would make
    // every document different from the last and send one on every tick
    // for ever.
    let mut comparable_record = record.clone();
    comparable_record.written_at = 0.0;
    let comparable = serde_json::to_string(&comparable_record).map_err(|err| err.to_string())?;
    if memo.last_sent.as_deref() == Some(comparable.as_str()) {
        return Ok(false);
    }

    let sent = channel.put(body, memo.mine).await.map_err(|err| err.to_string())?;
    memo.mine = Some(sent);
    memo.last_sent = Some(comparable);
    Ok(true)
}

#[cfg(test)]
#[path = "sync_tests.rs"]
mod tests;
