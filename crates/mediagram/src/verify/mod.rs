//! Set verification: metadata-only by default, or a full re-download and
//! hash comparison with `--full`. [`report`] is the pure decision layer,
//! [`download_hash`] is the only piece that talks to Telegram, and this
//! module reads/writes the local index rows `commands::verify` needs
//! (kept here rather than in `index::parts`, which only tracks the upload
//! side of a part, not verification).

use anyhow::{Context, Result, bail};
use rusqlite::Connection;

use crate::index::sets;

pub mod download_hash;
pub mod render;
pub mod report;
pub mod session;

/// One `parts` row, trimmed to what verification needs. `chat_id` is kept
/// because a message id only identifies a message together with its chat:
/// parts recorded in another chat must be reported as such, never as
/// missing.
pub struct LocalPart {
    pub idx: u32,
    pub byte_length: u64,
    pub chat_id: Option<i64>,
    pub message_id: Option<i64>,
    pub doc_id: Option<i64>,
    pub sha256: Option<String>,
    pub status: String,
    pub verified_at: Option<i64>,
}

/// Resolves the CLI's `set_id`/`--all` choice into concrete set ids, oldest
/// first. Callers validate up front that exactly one of the two is set.
pub fn resolve_set_ids(conn: &Connection, set_id: Option<&str>, all: bool) -> Result<Vec<String>> {
    if let Some(id) = set_id {
        return match sets::get_set(conn, id)? {
            Some(_) => Ok(vec![id.to_string()]),
            None => bail!("no set with id {id} in the local index"),
        };
    }
    debug_assert!(all, "caller must require set_id or --all");
    let mut stmt = conn.prepare("SELECT set_id FROM sets ORDER BY created_at")?;
    let ids = stmt
        .query_map([], |row| row.get::<_, String>(0))?
        .collect::<rusqlite::Result<Vec<_>>>()
        .context("listing sets for --all")?;
    Ok(ids)
}

/// Every part row of a set, in idx order, regardless of upload status.
pub fn load_parts(conn: &Connection, set_id: &str) -> Result<Vec<LocalPart>> {
    let mut stmt = conn.prepare(
        "SELECT idx, byte_length, chat_id, message_id, doc_id, sha256, status, verified_at
         FROM parts WHERE set_id = ?1 ORDER BY idx",
    )?;
    stmt.query_map([set_id], |row| {
        let byte_length: i64 = row.get(1)?;
        Ok(LocalPart {
            idx: row.get(0)?,
            // Lengths are written as i64 by the upload and rescan paths; a
            // negative value means a corrupt or hostile index row, so clamp
            // to 0 and let the invariant check report the set.
            byte_length: u64::try_from(byte_length).unwrap_or(0),
            chat_id: row.get(2)?,
            message_id: row.get(3)?,
            doc_id: row.get(4)?,
            sha256: row.get(5)?,
            status: row.get(6)?,
            verified_at: row.get(7)?,
        })
    })?
    .collect::<rusqlite::Result<Vec<_>>>()
    .with_context(|| format!("loading parts for set {set_id}"))
}

/// Records a successful `--full` hash match. `verified_at` is the result of
/// the last verification, not a high-water mark: [`clear_verified`] wipes it
/// again as soon as a part fails, so a non-null value always means "this
/// part matched its recorded hash at that time and has not failed since".
pub fn mark_verified(conn: &Connection, set_id: &str, idx: u32, now: i64) -> Result<()> {
    set_verified_at(conn, set_id, idx, Some(now))
}

/// Drops a stale `verified_at` after a part fails, so neither the printed
/// row nor the index snapshot pushed to the channel can present an old
/// success next to a current failure.
pub fn clear_verified(conn: &Connection, set_id: &str, idx: u32) -> Result<()> {
    set_verified_at(conn, set_id, idx, None)
}

fn set_verified_at(conn: &Connection, set_id: &str, idx: u32, at: Option<i64>) -> Result<()> {
    conn.execute(
        "UPDATE parts SET verified_at = ?1 WHERE set_id = ?2 AND idx = ?3",
        rusqlite::params![at, set_id, idx],
    )
    .with_context(|| format!("recording verified_at for set {set_id} part {idx}"))?;
    Ok(())
}

/// `--since`: a part already verified at or after `since` is left alone, so
/// an interrupted `--full` sweep can be resumed without re-downloading the
/// parts it already proved.
pub fn verified_since(part: &LocalPart, since: Option<i64>) -> bool {
    match (since, part.verified_at) {
        (Some(since), Some(at)) => at >= since,
        _ => false,
    }
}
