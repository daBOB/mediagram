//! Writing a planned set into the index: the step between knowing what a
//! file is and sending its bytes, shared by every kind of set.

use std::path::Path;

use anyhow::{Context, Result};
use mlib_spec::{Caption, PartRange};
use rusqlite::Connection;

use crate::index::sets::SetRow;
use crate::index::{db, parts, sets};

/// Where a set's bytes come from.
pub struct Source<'a> {
    /// The file the parts are cut from.
    pub path: &'a Path,
    /// Whether `path` is a faststart remux this run wrote, rather than the
    /// file the person named. Only a remux is ever deleted after the upload.
    pub remux: bool,
}

/// Records a set in one transaction: its row, one pending part per range,
/// where its bytes come from, and whatever `also` adds (a lesson's sidecars).
/// Either all of it lands or none does, so a set is never half planned.
///
/// The caption is checked against the budget first: a caption that cannot be
/// sent is a set that cannot be finished, and must fail with nothing written.
pub fn record_planned(
    conn: &mut Connection,
    caption: &Caption,
    ranges: &[PartRange],
    source: Source<'_>,
    also: impl FnOnce(&Connection) -> Result<()>,
) -> Result<()> {
    mlib_spec::check_budget(caption).context(
        "caption exceeds Telegram's budget; shorten the variant, the title or the language lists",
    )?;
    let row = SetRow::from_caption(caption, crate::clock::now_unix())?;
    let source_value = source
        .path
        .canonicalize()
        .unwrap_or_else(|_| source.path.to_path_buf())
        .to_string_lossy()
        .into_owned();

    let tx = conn.transaction().context("starting index transaction")?;
    sets::insert_set(&tx, &row)?;
    parts::insert_parts(&tx, &row.set_id, ranges)?;
    db::set_meta(&tx, &db::source_key(&row.set_id), &source_value)?;
    if source.remux {
        db::set_meta(&tx, &db::tmp_key(&row.set_id), &source_value)?;
    }
    also(&tx)?;
    tx.commit().context("committing index transaction")
}
