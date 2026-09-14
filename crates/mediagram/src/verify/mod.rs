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
pub mod report;

/// One `parts` row, trimmed to what verification needs (no upload-side
/// bookkeeping such as `chat_id`).
pub struct LocalPart {
    pub idx: u32,
    pub byte_length: u64,
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
        "SELECT idx, byte_length, message_id, doc_id, sha256, status, verified_at
         FROM parts WHERE set_id = ?1 ORDER BY idx",
    )?;
    stmt.query_map([set_id], |row| {
        let byte_length: i64 = row.get(1)?;
        Ok(LocalPart {
            idx: row.get(0)?,
            byte_length: byte_length as u64,
            message_id: row.get(2)?,
            doc_id: row.get(3)?,
            sha256: row.get(4)?,
            status: row.get(5)?,
            verified_at: row.get(6)?,
        })
    })?
    .collect::<rusqlite::Result<Vec<_>>>()
    .with_context(|| format!("loading parts for set {set_id}"))
}

/// Records a successful `--full` hash match.
pub fn mark_verified(conn: &Connection, set_id: &str, idx: u32, now: i64) -> Result<()> {
    conn.execute(
        "UPDATE parts SET verified_at = ?1 WHERE set_id = ?2 AND idx = ?3",
        rusqlite::params![now, set_id, idx],
    )
    .with_context(|| format!("recording verified_at for set {set_id} part {idx}"))?;
    Ok(())
}
