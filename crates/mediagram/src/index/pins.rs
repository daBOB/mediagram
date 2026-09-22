//! Which `#mlib-index` messages are pinned, as far as this index knows.
//!
//! The channel is meant to carry exactly one pinned index snapshot. Keeping
//! that true means remembering which one is current and which ones a push
//! could not clear. `push-index` and `rescan` both keep this record, so it
//! lives with the index rather than in either command.

use anyhow::{Context, Result};
use rusqlite::Connection;

use crate::index::db;

/// The snapshot most recently pinned.
const META_INDEX_MESSAGE_ID: &str = "index_message_id";
/// Index messages still pinned that should not be: the one a push replaced,
/// plus any a previous push failed to unpin or a `rescan` rediscovered.
/// Stored comma-separated, newest first.
const META_STALE_INDEX_ID: &str = "stale_index_message_id";

/// Records the snapshot a push just pinned as the current one.
pub fn record_current(conn: &Connection, message_id: i32) -> Result<()> {
    db::set_meta(conn, META_INDEX_MESSAGE_ID, &message_id.to_string())
        .context("recording new index message id")
}

/// Writes down which ids a push could not prove unpinned.
///
/// Separate from the clearing so the bookkeeping can be tested without a
/// channel: whether a stale id survives a push is the whole question, and the
/// defect this replaced was that a false `Ok` erased it.
///
/// The unresolved ids are written before the replaced id is forgotten, so a
/// write that fails part way can at worst leave an id listed twice — which
/// `pending_unpins` folds — and never lose one.
pub fn record_unpin_outcome(conn: &Connection, unresolved: &[i32]) -> Result<()> {
    if unresolved.is_empty() {
        db::delete_meta(conn, META_STALE_INDEX_ID)?;
    } else {
        db::set_meta(conn, META_STALE_INDEX_ID, &join_ids(unresolved))
            .context("recording the index pins still to clear")?;
    }
    db::delete_meta(conn, META_INDEX_MESSAGE_ID)
}

/// The index messages this push should unpin, newest first.
///
/// Both keys hold comma-separated ids: `index_message_id` the snapshot this
/// push replaces, `stale_index_message_id` anything a previous push could not
/// clear or a `rescan` rediscovered.
pub fn pending_unpins(conn: &Connection) -> Vec<i32> {
    let mut ids: Vec<i32> = Vec::new();
    for key in [META_INDEX_MESSAGE_ID, META_STALE_INDEX_ID] {
        let Ok(Some(raw)) = db::get_meta(conn, key) else {
            continue;
        };
        for piece in raw.split(',').map(str::trim).filter(|p| !p.is_empty()) {
            match piece.parse::<i32>() {
                Ok(id) if !ids.contains(&id) => ids.push(id),
                Ok(_) => {}
                Err(_) => {
                    tracing::warn!(key, value = %piece, "recorded index message id is not valid")
                }
            }
        }
    }
    ids
}

/// Records the index snapshots a `rescan` found in the channel.
///
/// The memory of which snapshot is current lives in `library.db`, and
/// `rescan` exists because that file gets lost — so without this the first
/// push after a rescan pins a new index and leaves the old one pinned beside
/// it. Found by a live run against a real channel, which is what those are for.
pub fn record_index_messages(conn: &Connection, ids: &[i32]) -> Result<()> {
    if ids.is_empty() {
        return Ok(());
    }
    let mut sorted: Vec<i32> = ids.to_vec();
    sorted.sort_unstable_by(|a, b| b.cmp(a));
    sorted.dedup();

    let (newest, older) = sorted.split_first().expect("checked non-empty");
    db::set_meta(conn, META_INDEX_MESSAGE_ID, &newest.to_string())
        .context("recording the current index message id")?;
    if older.is_empty() {
        db::delete_meta(conn, META_STALE_INDEX_ID)?;
    } else {
        db::set_meta(conn, META_STALE_INDEX_ID, &join_ids(older))
            .context("recording stale index message ids")?;
    }
    Ok(())
}

fn join_ids(ids: &[i32]) -> String {
    ids.iter().map(i32::to_string).collect::<Vec<_>>().join(",")
}
