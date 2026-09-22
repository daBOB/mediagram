//! Rebuilds `sets`/`parts` rows from channel captions: the disaster-recovery
//! path when `library.db` is lost but the channel still holds every part.
//!
//! [`apply_seen`] is pure over a slice of [`Seen`] messages (no Telegram
//! calls), so it is unit-testable without a live connection;
//! `commands::rescan` supplies the messages by paging through history.

use std::collections::BTreeSet;

use anyhow::Result;
use mlib_spec::caption::Caption;
use rusqlite::Connection;

use crate::index::rescan_parts::upsert_part;
use crate::index::set_row::SetRow;
use crate::index::sets;
use crate::index::status::SetStatus;

/// One channel message as seen by a history scan: what a rescan rebuilds the
/// index from, and what an upload matches against a pending part to adopt it
/// instead of sending it twice.
pub struct Seen {
    pub message_id: i64,
    pub doc_id: Option<i64>,
    pub caption: String,
}

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
    /// Captions carrying the mlib marker that failed to parse (e.g. a newer spec version).
    pub unparsed: usize,
    /// Index snapshots found in the channel. More than one means more than
    /// one is pinned, which the next push resolves.
    pub index_messages: usize,
}

/// Folds every mlib-captioned message in `seen` into `sets`/`parts`, then
/// recomputes `complete`/`pending` status for every set touched. Safe to
/// call repeatedly with overlapping or identical input.
pub fn apply_seen(conn: &Connection, chat_id: i64, seen: &[Seen]) -> Result<RescanSummary> {
    let now = crate::clock::now_unix();

    let mut touched_sets = BTreeSet::new();
    let mut parts_seen = 0usize;
    let mut duplicates_skipped = 0usize;

    let mut unparsed = 0usize;

    for msg in seen {
        // `is_mlib` only matches the `#mlib v=` part marker, so the
        // `#mlib-index` snapshot document (a different marker entirely) and
        // any plain-text message are both skipped here.
        if !mlib_spec::caption_codec::is_mlib(&msg.caption) {
            continue;
        }
        let caption = match mlib_spec::parse(&msg.caption) {
            Ok(caption) => caption,
            Err(err) => {
                tracing::warn!(message_id = msg.message_id, error = %err, "skipping unparsable mlib caption");
                unparsed += 1;
                continue;
            }
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
            sets::set_status(conn, set_id, SetStatus::Pending)?;
            sets_incomplete += 1;
        }
    }

    Ok(RescanSummary {
        sets_seen: touched_sets.len(),
        parts_seen,
        sets_complete,
        sets_incomplete,
        duplicates_skipped,
        unparsed,
        // Counted by the caller, which is the only place that sees the index
        // snapshots: this function is given part captions only.
        index_messages: 0,
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
