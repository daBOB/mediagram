//! Merging `categories` from the attached channel snapshot.
//!
//! Like `anime_overrides`, a category is not merely filled in where
//! missing: the same unit can be re-categorised, or cleared, on either
//! uploading machine, so the newer `set_at` wins outright, a `NULL` clear
//! included. New in v12, so `shared_columns` tolerates a channel snapshot
//! with no `categories` table yet, the same way it tolerates a missing
//! column.

use anyhow::{Context, Result};
use rusqlite::Connection;

use crate::index::merge_columns::shared_columns;

/// Returns how many category rows were inserted or updated.
pub(super) fn merge(conn: &Connection) -> Result<usize> {
    let cols = shared_columns(conn, "categories")?;
    if cols.is_empty() {
        return Ok(0);
    }
    conn.execute(
        "INSERT INTO main.categories(department, item_key, category, set_at)
         SELECT department, item_key, category, set_at FROM channel.categories WHERE true
         ON CONFLICT(department, item_key) DO UPDATE
            SET category = excluded.category, set_at = excluded.set_at
          WHERE excluded.set_at > categories.set_at",
        [],
    )
    .context("merging categories")
}
