//! A set's life in the local index between planning and completion: the
//! file it is uploaded from, the file the person named (what the subtitles
//! are read from), and — when `add` remuxed it — the temporary copy to
//! delete once it is up.
//!
//! All three live in `meta` under per-set keys that only this module spells, so
//! planning, uploading, resuming and removing a set cannot disagree about
//! them. Completing a set clears the source and the remux (not the original, which
//! the subtitles are read from next) in the same transaction that marks it
//! complete — and records the publish it is now owed — so a set is either
//! pending with a source to resume from, or complete with nothing left
//! behind and a publish owed until one settles it. `rescan`, which completes
//! sets it finds whole in the channel, forgets them the same way.

use std::path::{Path, PathBuf};

use anyhow::{Context, Result, ensure};
use rusqlite::Connection;

use crate::index::{db, pins, sets};

/// What completing a set leaves for the caller to do.
#[derive(Debug, PartialEq, Eq)]
pub struct Completed {
    /// The remux `add` wrote for this set, no longer recorded anywhere; the
    /// caller deletes the file. Never the person's own file: only a remux is
    /// ever recorded as temporary.
    pub temp: Option<PathBuf>,
}

/// Records where a planned set uploads from; `temporary` when that is a
/// remux `add` wrote, to be deleted once the set is up. Called inside the
/// transaction that records the set.
pub fn record_source(
    conn: &Connection,
    set_id: &str,
    source: &Path,
    temporary: bool,
) -> Result<()> {
    let value = source.to_string_lossy();
    db::set_meta(conn, &source_key(set_id), &value)?;
    if temporary {
        db::set_meta(conn, &temp_key(set_id), &value)?;
    }
    Ok(())
}

/// Records the file the person named, which a remux replaces as the
/// upload's source. Completion deletes the remux and forgets the source, but
/// the subtitles are read from this one afterwards.
pub fn record_original(conn: &Connection, set_id: &str, original: &Path) -> Result<()> {
    db::set_meta(conn, &original_key(set_id), &original.to_string_lossy())
}

/// The file the person named, for a set planned since it was recorded.
pub fn original_of(conn: &Connection, set_id: &str) -> Result<Option<PathBuf>> {
    Ok(db::get_meta(conn, &original_key(set_id))?.map(PathBuf::from))
}

/// Where a pending set uploads from, if the index knows.
pub fn source_of(conn: &Connection, set_id: &str) -> Result<Option<PathBuf>> {
    Ok(db::get_meta(conn, &source_key(set_id))?.map(PathBuf::from))
}

/// Marks the set complete under `hash`, forgets its source and temporary
/// copy, and owes a publish, in one transaction: nothing can leave a
/// complete set still recording a source, a pending one without it, or a
/// completed upload that nothing will publish.
pub fn complete(conn: &Connection, set_id: &str, hash: &str) -> Result<Completed> {
    let tx = conn
        .unchecked_transaction()
        .context("starting the transaction that completes a set")?;
    sets::set_hash_and_complete(&tx, set_id, hash)?;
    // A set removed while it was uploading matches nothing: it must not come
    // back as "added", nor have its source deleted as if it had.
    ensure!(tx.changes() == 1, "set {set_id} is no longer in the index");
    pins::owe_publish(&tx)?;
    let temp = db::get_meta(&tx, &temp_key(set_id))?.map(PathBuf::from);
    // The original stays: the subtitles are still to be read from it, and
    // until they are, another upload must not delete it.
    forget_upload(&tx, set_id)?;
    tx.commit()
        .with_context(|| format!("committing the completion of set {set_id}"))?;
    Ok(Completed { temp })
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
    forget(conn, set_id)
}

/// Forgets a set's source, original and temporary copy, for a set that is
/// gone.
pub fn forget(conn: &Connection, set_id: &str) -> Result<()> {
    forget_upload(conn, set_id)?;
    forget_original(conn, set_id)
}

/// Forgets the original once its subtitles have been read (or given up on).
/// While it is recorded, the file the person named is still in use.
pub fn forget_original(conn: &Connection, set_id: &str) -> Result<()> {
    db::delete_meta(conn, &original_key(set_id))
}

fn forget_upload(conn: &Connection, set_id: &str) -> Result<()> {
    db::delete_meta(conn, &source_key(set_id))?;
    db::delete_meta(conn, &temp_key(set_id))
}

fn source_key(set_id: &str) -> String {
    format!("source:{set_id}")
}

fn original_key(set_id: &str) -> String {
    format!("orig:{set_id}")
}

fn temp_key(set_id: &str) -> String {
    format!("tmp:{set_id}")
}
