//! Dropping local sets another machine removed from the channel.
//!
//! A `remove` deletes the set's messages and its rows on the machine that ran
//! it, and that machine's next push leaves the set out of the channel's
//! index. Every other uploader still holds the rows, and the merge before
//! its own publish would keep them: to the merge they look like sets this
//! machine uploaded and has not published yet. Republished, they list a
//! title nobody can play beside the one that replaced it. The channel tells
//! the two apart: a set uploaded here still has its messages.

use std::collections::HashSet;
use std::path::Path;

use anyhow::{Context, Result};
use rusqlite::Connection;

use super::{Candidate, attach, candidates};
use crate::index::lifecycle;

/// The local-only, complete sets, so a caller can ask the channel which of
/// them are gone *before* dropping any, in its own runtime.
pub fn local_candidates(local: &Connection, channel_path: &Path) -> Result<Vec<Candidate>> {
    attach(local, channel_path)?;
    let found = candidates::local_only(local);
    let _ = local.execute_batch("DETACH DATABASE channel");
    found
}

/// Deletes `gone`'s rows, parts and assets with them, in one transaction;
/// returns the ids dropped, sorted.
pub fn drop_removed(local: &Connection, gone: &HashSet<String>) -> Result<Vec<String>> {
    let mut ids: Vec<String> = gone.iter().cloned().collect();
    ids.sort();
    if ids.is_empty() {
        return Ok(ids);
    }
    local
        .execute_batch("BEGIN IMMEDIATE")
        .context("starting to drop sets removed elsewhere")?;
    let outcome = ids
        .iter()
        .try_for_each(|id| lifecycle::delete_rows(local, id))
        .and_then(|()| {
            local
                .execute_batch("COMMIT")
                .context("committing the dropped sets")
        });
    if let Err(err) = outcome {
        let _ = local.execute_batch("ROLLBACK");
        return Err(err);
    }
    Ok(ids)
}
