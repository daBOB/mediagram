//! Rebuilds `sets`/`parts` rows from channel captions: the disaster-recovery
//! path when `library.db` is lost but the channel still holds every part.
//!
//! [`apply_seen`] is pure over a slice of [`Seen`] messages (no Telegram
//! calls), so it is unit-testable without a live connection;
//! `commands::rescan` supplies the messages by paging through history.

use std::collections::BTreeSet;
use std::time::{SystemTime, UNIX_EPOCH};

use anyhow::Result;
use mlib_spec::caption::Caption;
use rusqlite::{Connection, OptionalExtension, params};

use crate::index::set_row::SetRow;
use crate::index::sets;
use crate::upload::transport::Seen;

/// Counts produced by one [`apply_seen`] pass, printed by `mediagram rescan`.
#[derive(Debug, Default, Clone, Copy, PartialEq, Eq)]
pub struct RescanSummary {
    /// Distinct sets whose captions were parsed in this pass.
    pub sets_seen: usize,
    /// Distinct (set, part index) pairs recorded in this pass.
    pub parts_seen: usize,
    pub sets_complete: usize,
    pub sets_incomplete: usize,
    /// Times a part index was seen again under a different message id.
    pub duplicates_skipped: usize,
}

/// Folds every mlib-captioned message in `seen` into `sets`/`parts`, then
/// recomputes `complete`/`pending` status for every set touched. Safe to
/// call repeatedly with overlapping or identical input.
pub fn apply_seen(conn: &Connection, chat_id: i64, seen: &[Seen]) -> Result<RescanSummary> {
    let now = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .unwrap_or_default()
        .as_secs() as i64;

    let mut touched_sets = BTreeSet::new();
    let mut parts_seen = 0usize;
    let mut duplicates_skipped = 0usize;

    for msg in seen {
        // `is_mlib` only matches the `#mlib v=` part marker, so the
        // `#mlib-index` snapshot document (a different marker entirely) and
        // any plain-text message are both skipped here.
        if !mlib_spec::caption_codec::is_mlib(&msg.caption) {
            continue;
        }
        let Ok(caption) = mlib_spec::parse(&msg.caption) else {
            continue;
        };
        // A part with no document media has nothing to index.
        let Some(doc_id) = msg.doc_id else {
            continue;
        };

        upsert_set(conn, &caption, now)?;
        touched_sets.insert(caption.set.clone());

        if upsert_part(conn, chat_id, msg.message_id, doc_id, &caption)? {
            duplicates_skipped += 1;
        } else {
            parts_seen += 1;
        }
    }

    let mut sets_complete = 0usize;
    let mut sets_incomplete = 0usize;
    for set_id in &touched_sets {
        if parts_complete(conn, set_id)? {
            let hashes = crate::index::parts::done_hashes(conn, set_id)?;
            let hash = mlib_spec::set_hash::set_hash(&hashes);
            sets::set_hash_and_complete(conn, set_id, &hash)?;
            sets_complete += 1;
        } else {
            sets::set_status(conn, set_id, "pending")?;
            sets_incomplete += 1;
        }
    }

    Ok(RescanSummary {
        sets_seen: touched_sets.len(),
        parts_seen,
        sets_complete,
        sets_incomplete,
        duplicates_skipped,
    })
}

/// Inserts the set row the first time its id is seen. Every part's caption
/// mirrors the same set-level fields, so whichever caption arrives first
/// during the scan is as good as any other; later ones are left untouched.
fn upsert_set(conn: &Connection, caption: &Caption, created_at: i64) -> Result<()> {
    if sets::get_set(conn, &caption.set)?.is_some() {
        return Ok(());
    }
    let row = SetRow::from_caption(caption, created_at)?;
    sets::insert_set(conn, &row)
}

/// One existing `parts` row's state, enough to detect a duplicate caption.
struct ExistingPart {
    status: String,
    message_id: Option<i64>,
}

fn existing_part(conn: &Connection, set_id: &str, idx: u32) -> Result<Option<ExistingPart>> {
    conn.query_row(
        "SELECT status, message_id FROM parts WHERE set_id = ?1 AND idx = ?2",
        params![set_id, idx],
        |row| {
            Ok(ExistingPart {
                status: row.get(0)?,
                message_id: row.get(1)?,
            })
        },
    )
    .optional()
    .map_err(Into::into)
}

/// Records `caption`'s part as done, keyed by `(set_id, idx)`. Returns `true`
/// for a duplicate observation of an already-`done` part under a different
/// message id, only overwriting it when the new id is higher (ids only
/// increase, so the higher one is the more recent, still-live copy).
fn upsert_part(
    conn: &Connection,
    chat_id: i64,
    message_id: i64,
    doc_id: i64,
    caption: &Caption,
) -> Result<bool> {
    let existing = existing_part(conn, &caption.set, caption.part.i)?;
    let is_duplicate = match &existing {
        Some(existing) => existing.status == "done" && existing.message_id != Some(message_id),
        None => false,
    };
    if is_duplicate {
        let existing_message_id = existing.and_then(|e| e.message_id).unwrap_or(i64::MIN);
        if message_id > existing_message_id {
            write_part_done(conn, chat_id, message_id, doc_id, caption)?;
        }
        return Ok(true);
    }
    write_part_done(conn, chat_id, message_id, doc_id, caption)?;
    Ok(false)
}

fn write_part_done(
    conn: &Connection,
    chat_id: i64,
    message_id: i64,
    doc_id: i64,
    caption: &Caption,
) -> Result<()> {
    conn.execute(
        "INSERT INTO parts(set_id, idx, byte_offset, byte_length, chat_id, message_id, doc_id, sha256, status)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, 'done')
         ON CONFLICT(set_id, idx) DO UPDATE SET
            byte_offset = excluded.byte_offset,
            byte_length = excluded.byte_length,
            chat_id = excluded.chat_id,
            message_id = excluded.message_id,
            doc_id = excluded.doc_id,
            sha256 = excluded.sha256,
            status = 'done'",
        params![
            caption.set,
            caption.part.i,
            caption.part.off as i64,
            caption.part.len as i64,
            chat_id,
            message_id,
            doc_id,
            caption.part.sha256,
        ],
    )?;
    Ok(())
}

/// The playable invariant computed directly from `parts` rather than via
/// `mlib_spec::schema::PLAYABLE_SQL`, which presupposes `status = 'complete'`
/// already — exactly the value rescan is trying to determine.
fn parts_complete(conn: &Connection, set_id: &str) -> Result<bool> {
    let set = sets::get_set(conn, set_id)?
        .ok_or_else(|| anyhow::anyhow!("set {set_id} vanished during rescan"))?;
    let (done_count, done_total): (i64, i64) = conn.query_row(
        "SELECT COUNT(*), COALESCE(SUM(byte_length), 0) FROM parts
         WHERE set_id = ?1 AND status = 'done'",
        [set_id],
        |row| Ok((row.get(0)?, row.get(1)?)),
    )?;
    Ok(done_count as u32 == set.part_count && done_total as u64 == set.total)
}
