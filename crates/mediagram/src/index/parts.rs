//! `parts` table: one row per byte-range part of a set, tracking upload
//! progress and the identifiers needed to find the message again.

use anyhow::{Context, Result};
use mlib_spec::part_plan::PartRange;
use rusqlite::{Connection, params};

/// One row of the `parts` table.
#[derive(Debug, Clone, PartialEq)]
pub struct PartRow {
    pub set_id: String,
    pub idx: u32,
    pub byte_offset: u64,
    pub byte_length: u64,
    pub chat_id: Option<i64>,
    pub message_id: Option<i64>,
    pub doc_id: Option<i64>,
    pub sha256: Option<String>,
    pub status: String,
    pub verified_at: Option<i64>,
}

fn from_row(row: &rusqlite::Row<'_>) -> rusqlite::Result<PartRow> {
    // sqlite integers are signed 64-bit; byte offsets/lengths are stored as
    // `i64` and widened back to `u64` here (parts never approach 2^63 bytes).
    let byte_offset: i64 = row.get("byte_offset")?;
    let byte_length: i64 = row.get("byte_length")?;
    Ok(PartRow {
        set_id: row.get("set_id")?,
        idx: row.get("idx")?,
        byte_offset: byte_offset as u64,
        byte_length: byte_length as u64,
        chat_id: row.get("chat_id")?,
        message_id: row.get("message_id")?,
        doc_id: row.get("doc_id")?,
        sha256: row.get("sha256")?,
        status: row.get("status")?,
        verified_at: row.get("verified_at")?,
    })
}

/// Inserts one `pending` row per planned part.
pub fn insert_parts(conn: &Connection, set_id: &str, parts: &[PartRange]) -> Result<()> {
    let mut stmt = conn.prepare(
        "INSERT INTO parts(set_id, idx, byte_offset, byte_length, status)
         VALUES (?1, ?2, ?3, ?4, 'pending')",
    )?;
    for part in parts {
        stmt.execute(params![set_id, part.idx, part.off as i64, part.len as i64])?;
    }
    Ok(())
}

/// Every `pending` part of a set, in upload order.
/// Every column `from_row` reads, named once so two queries cannot drift.
const PART_COLUMNS: &str = "set_id, idx, byte_offset, byte_length, chat_id, message_id, doc_id,
     sha256, status, verified_at";

pub fn pending_parts(conn: &Connection, set_id: &str) -> Result<Vec<PartRow>> {
    let mut stmt = conn.prepare(&format!(
        "SELECT {PART_COLUMNS} FROM parts WHERE set_id = ?1 AND status = 'pending' ORDER BY idx"
    ))?;
    let rows = stmt
        .query_map([set_id], from_row)?
        .collect::<rusqlite::Result<Vec<_>>>()?;
    Ok(rows)
}

/// Records a part as uploaded (or adopted from an existing channel message).
#[allow(clippy::too_many_arguments)]
pub fn mark_done(
    conn: &Connection,
    set_id: &str,
    idx: u32,
    chat_id: i64,
    message_id: i64,
    doc_id: i64,
    sha256: &str,
) -> Result<()> {
    conn.execute(
        "UPDATE parts SET chat_id = ?1, message_id = ?2, doc_id = ?3, sha256 = ?4, status = 'done'
         WHERE set_id = ?5 AND idx = ?6",
        params![chat_id, message_id, doc_id, sha256, set_id, idx],
    )?;
    Ok(())
}

/// Every part of a set, in idx order, whatever its status.
///
/// Used by `edit`, which rewrites one caption per uploaded part and needs to
/// see them all to know which have a message.
pub fn all_parts(conn: &Connection, set_id: &str) -> Result<Vec<PartRow>> {
    let mut stmt = conn
        .prepare(&format!(
            "SELECT {PART_COLUMNS} FROM parts WHERE set_id = ?1 ORDER BY idx"
        ))
        .context("preparing the part query")?;
    let rows = stmt
        .query_map([set_id], from_row)
        .with_context(|| format!("listing parts of {set_id}"))?;
    rows.collect::<rusqlite::Result<Vec<_>>>()
        .context("reading a part row")
}

/// Sha256 hex of every `done` part, in idx order; the input to `set_hash`.
/// Bytes of a set already in the channel, for reporting a resumed upload
/// against the whole of it rather than against what this run has done.
pub fn done_bytes(conn: &Connection, set_id: &str) -> Result<u64> {
    let total: i64 = conn
        .query_row(
            "SELECT COALESCE(SUM(byte_length), 0) FROM parts
              WHERE set_id = ?1 AND status = 'done'",
            params![set_id],
            |row| row.get(0),
        )
        .context("summing the parts already sent")?;
    Ok(total.max(0) as u64)
}

pub fn done_hashes(conn: &Connection, set_id: &str) -> Result<Vec<String>> {
    let mut stmt = conn
        .prepare("SELECT sha256 FROM parts WHERE set_id = ?1 AND status = 'done' ORDER BY idx")?;
    let rows = stmt
        .query_map([set_id], |row| row.get::<_, Option<String>>(0))?
        .collect::<rusqlite::Result<Vec<_>>>()?;
    // A `done` row always has a hash; anything else is a mark_done bug, not
    // recoverable data, so it's fine to drop nulls rather than error here.
    Ok(rows.into_iter().flatten().collect())
}

// Covered by `tests/index_state.rs`: insert_parts/pending_parts/mark_done/
// done_hashes round trip, including the two-part ordering guarantee.
