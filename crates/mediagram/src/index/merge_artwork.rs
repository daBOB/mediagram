//! Filling `artwork` from the attached channel snapshot.
//!
//! Like `credits` and `franchises`, never half-written: one key is one image,
//! replaced whole by [`crate::index::artwork::put`], never patched a byte at
//! a time. So a key already present in `main` never has anything to fill —
//! only a whole key missing from `main` is copied. New in v10, so
//! `shared_columns` tolerates a channel snapshot with no `artwork` table yet,
//! the same way it tolerates a missing column.

use anyhow::{Context, Result};
use rusqlite::Connection;

use crate::index::merge_columns::shared_columns;

/// Returns how many artwork rows were added.
pub(super) fn merge(conn: &Connection) -> Result<usize> {
    let cols = shared_columns(conn, "artwork")?;
    if cols.is_empty() {
        return Ok(0);
    }
    let col_list = cols.join(", ");
    conn.execute(
        &format!(
            "INSERT INTO main.artwork ({col_list})
             SELECT {col_list} FROM channel.artwork ch
             WHERE NOT EXISTS (SELECT 1 FROM main.artwork m WHERE m.key = ch.key)"
        ),
        [],
    )
    .context("inserting artwork this index lacks")
}
