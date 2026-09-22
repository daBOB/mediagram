//! Carrying out a removal.
//!
//! Messages first, index rows second. The order is deliberate: a run that
//! dies between the two leaves rows pointing at messages that are gone, which
//! `verify` reports plainly and re-running `remove` finishes. The other order
//! would leave messages nothing refers to — invisible, unreclaimable, and
//! resurrected by the next `rescan`.

use anyhow::{Context, Result};
use grammers_client::Client;
use grammers_session::types::PeerRef;
use rusqlite::Connection;

use crate::remove::plan::Removal;
use crate::telegram::retry::with_retry;

/// Telegram takes at most 100 message ids per delete call.
const DELETE_BATCH: usize = 100;

/// Deletes a set's messages from the channel. Returns how many Telegram
/// reported as affected.
pub async fn delete_messages(
    client: &Client,
    channel: PeerRef,
    removal: &Removal,
    max_attempts: u32,
) -> Result<usize> {
    let ids: Vec<i32> = removal
        .message_ids
        .iter()
        .map(|id| i32::try_from(*id))
        .collect::<Result<Vec<_>, _>>()
        .with_context(|| format!("a message id of {} is out of range", removal.set_id))?;

    let mut affected = 0;
    for batch in ids.chunks(DELETE_BATCH) {
        let batch = batch.to_vec();
        affected += with_retry(max_attempts, || {
            let client = client.clone();
            let batch = batch.clone();
            async move { client.delete_messages(channel, &batch).await }
        })
        .await
        .with_context(|| format!("deleting the messages of {}", removal.set_id))?;
    }
    Ok(affected)
}

/// Removes a set's rows. `parts` and `assets` cascade from `sets`.
///
/// Runs after the messages are gone, so the index never claims to hold
/// something the channel no longer has.
pub fn delete_rows(conn: &Connection, set_id: &str) -> Result<()> {
    conn.execute("PRAGMA foreign_keys = ON", [])
        .context("enabling foreign keys")?;
    conn.execute("DELETE FROM sets WHERE set_id = ?1", [set_id])
        .with_context(|| format!("deleting the index rows of {set_id}"))?;
    // The source path is remembered for `resume`; with the set gone it is
    // just a stale pointer.
    for key in crate::index::db::set_keys(set_id) {
        crate::index::db::delete_meta(conn, &key)?;
    }
    Ok(())
}
