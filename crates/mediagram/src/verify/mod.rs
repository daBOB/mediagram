//! Set verification: metadata-only by default, or a full re-download and
//! hash comparison with `--full`. [`report`] is the pure decision layer,
//! `source` and [`download_hash`] handle Telegram IO, and this
//! module reads/writes the local index rows `commands::verify` needs
//! (kept here rather than in `index::parts`, which only tracks the upload
//! side of a part, not verification).

use anyhow::{Context, Result, bail};
use rusqlite::Connection;

use crate::index::parts::PartRow;
use crate::index::sets;

pub mod download_hash;
pub mod render;
pub mod report;
pub mod session;
mod source;

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

/// Clears a part's old `verified_at` when today's verdict failed.
///
/// A part that fails today must not keep advertising an old success: the row
/// is what `push-index` snapshots to the channel for other clients, and the
/// printed report reads the verdict's copy.
pub fn forget_stale_success(
    conn: &Connection,
    set_id: &str,
    part: &PartRow,
    verdict: &mut report::PartVerdict,
) -> Result<()> {
    if verdict.failed() && part.verified_at.is_some() {
        clear_verified(conn, set_id, part.idx)?;
        verdict.verified_at = None;
    }
    Ok(())
}

/// The chat a part was recorded in, when that is not the one being
/// verified. A part with no recorded `chat_id` predates that column being
/// written and is taken to live in the configured channel.
pub fn other_chat(part: &PartRow, chat_id: i64) -> Option<i64> {
    part.chat_id.filter(|&recorded| recorded != chat_id)
}

/// `--since`: a part already verified at or after `since` is left alone, so
/// an interrupted `--full` sweep can be resumed without re-downloading the
/// parts it already proved.
pub fn verified_since(part: &PartRow, since: Option<i64>) -> bool {
    match (since, part.verified_at) {
        (Some(since), Some(at)) => at >= since,
        _ => false,
    }
}
