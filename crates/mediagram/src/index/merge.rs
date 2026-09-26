//! Merging a channel's index snapshot into the local one.
//!
//! Two machines can publish to one channel, and a push replaces the
//! channel's index wholesale — so a machine that has fallen behind another
//! cannot publish without first bringing in what it lacks. This does that:
//! `ATTACH` the downloaded snapshot onto the local connection, in `main` and
//! `channel`, and copy across what the local index is missing.
//!
//! | Table | Rule |
//! |---|---|
//! | `sets` | Channel-only, `complete`, and confirmed live (see [`merge_from`]). |
//! | `parts` | Every row of a set just added. |
//! | `assets` | Missing `(set_id, kind, lang)` rows, added or shared alike. |
//! | `shows` | Missing keys inserted; shared keys get their `NULL`s filled. |
//! | `credits`, `franchises` | Missing keys inserted whole; never partially filled. |
//! | `meta` | Never touched — machine-local state, not library content. |
//!
//! A shared set whose metadata differs between the two is not decided here:
//! see [`crate::index::merge_conflicts`], which re-reads it from captions.

use std::collections::HashSet;
use std::path::Path;

use anyhow::{Context, Result};
use rusqlite::Connection;

use crate::index::merge_candidates;
use crate::index::merge_columns::shared_columns;
use crate::index::merge_copy::{fill_missing_assets, insert_row};
use crate::index::merge_credits;
use crate::index::merge_diff::conflicting_sets;
use crate::index::merge_shows;

/// Re-exported here: `merge_from`'s callers (outside `index`) build its
/// `keep_if_live` closure from this, so it belongs on this module's face
/// rather than requiring a second `use` of the private `merge_candidates`.
pub use crate::index::merge_candidates::Candidate;

/// What one merge did.
#[derive(Debug, Default, Clone)]
pub struct MergeReport {
    pub sets_added: Vec<String>,
    /// Channel-only sets left mid-upload elsewhere; nothing to add yet.
    pub sets_skipped_pending: Vec<String>,
    /// Channel-only, complete sets whose messages no longer exist — removed
    /// since the channel's index was pushed.
    pub sets_skipped_removed: Vec<String>,
    /// Shared sets whose metadata differs, left untouched here.
    pub conflicts: Vec<String>,
    pub shows_added: usize,
    pub shows_filled: usize,
    pub credits_added: usize,
    pub franchises_added: usize,
}

/// Merges `channel_path`'s index into `local`, one transaction, via `ATTACH`.
///
/// `keep_if_live` is handed every channel-only complete candidate at once
/// and answers which of them still exist in the channel — a closure so a
/// live check (a batched Telegram lookup) and a test's fixed answer are the
/// same shape. It runs before the write transaction opens, so the network
/// round trip it may make does not hold a lock on `local`.
pub fn merge_from(
    local: &Connection,
    channel_path: &Path,
    keep_if_live: impl FnOnce(&[Candidate]) -> Result<HashSet<String>>,
) -> Result<MergeReport> {
    attach(local, channel_path)?;
    let outcome = (|| -> Result<MergeReport> {
        let (candidates, pending) = merge_candidates::discover(local)?;
        let keep = keep_if_live(&candidates)?;
        run_transaction(local, &candidates, &keep, &pending)
    })();
    // Best-effort: the merge already committed or rolled back by the time
    // this runs, so a failure here only leaves the attachment in place for
    // whoever opens `local` next, not a corrupt index.
    if let Err(err) = local.execute_batch("DETACH DATABASE channel") {
        tracing::warn!(error = %err, "failed to detach the channel database after merging");
    }
    outcome
}

/// The channel-only, complete sets a merge would add, so a caller can check
/// them against the channel *before* merging — asynchronously, in its own
/// runtime — and hand [`merge_from`] the answer.
pub fn channel_candidates(local: &Connection, channel_path: &Path) -> Result<Vec<Candidate>> {
    attach(local, channel_path)?;
    let found = merge_candidates::discover(local).map(|(candidates, _)| candidates);
    let _ = local.execute_batch("DETACH DATABASE channel");
    found
}

fn attach(conn: &Connection, channel_path: &Path) -> Result<()> {
    let path = channel_path
        .to_str()
        .context("channel index path is not valid UTF-8")?;
    conn.execute("ATTACH DATABASE ?1 AS channel", [path])
        .context("attaching the channel index")?;
    Ok(())
}

fn run_transaction(
    conn: &Connection,
    candidates: &[Candidate],
    keep: &HashSet<String>,
    pending: &[String],
) -> Result<MergeReport> {
    conn.execute_batch("BEGIN IMMEDIATE")
        .context("starting the merge transaction")?;
    match copy_kept(conn, candidates, keep) {
        Ok(mut report) => {
            report.sets_skipped_pending = pending.to_vec();
            report.sets_skipped_removed = candidates
                .iter()
                .map(|c| &c.set_id)
                .filter(|id| !keep.contains(*id))
                .cloned()
                .collect();
            if let Err(err) = conn.execute_batch("COMMIT") {
                let _ = conn.execute_batch("ROLLBACK");
                return Err(err).context("committing the merge");
            }
            Ok(report)
        }
        Err(err) => {
            let _ = conn.execute_batch("ROLLBACK");
            Err(err)
        }
    }
}

fn copy_kept(
    conn: &Connection,
    candidates: &[Candidate],
    keep: &HashSet<String>,
) -> Result<MergeReport> {
    let sets_cols = shared_columns(conn, "sets")?;
    let parts_cols = shared_columns(conn, "parts")?;
    let mut sets_added = Vec::new();
    for candidate in candidates {
        if !keep.contains(&candidate.set_id) {
            continue;
        }
        insert_row(conn, "sets", &sets_cols, &candidate.set_id)?;
        insert_row(conn, "parts", &parts_cols, &candidate.set_id)?;
        sets_added.push(candidate.set_id.clone());
    }

    let conflicts = conflicting_sets(conn)?;
    fill_missing_assets(conn)?;
    let (shows_added, shows_filled) = merge_shows::merge(conn)?;
    let (credits_added, franchises_added) = merge_credits::merge(conn)?;

    Ok(MergeReport {
        sets_added,
        conflicts,
        shows_added,
        shows_filled,
        credits_added,
        franchises_added,
        ..MergeReport::default()
    })
}

#[cfg(test)]
#[path = "merge_tests.rs"]
mod tests;
