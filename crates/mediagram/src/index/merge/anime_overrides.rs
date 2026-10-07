//! Merging `anime_overrides` from the attached channel snapshot.
//!
//! Unlike `shows`, an override is not merely filled in where missing: the
//! same title can be re-decided on either uploading machine, so the newer
//! `set_at` wins outright, `anime` included — `NULL` (back to automatic) is
//! a value like any other here, not something to skip. New in v11, so
//! `shared_columns` tolerates a channel snapshot with no `anime_overrides`
//! table yet, the same way it tolerates a missing column.

use anyhow::{Context, Result};
use rusqlite::Connection;

use super::columns::shared_columns;

/// Returns how many override rows were inserted or updated.
pub(super) fn merge(conn: &Connection) -> Result<usize> {
    let cols = shared_columns(conn, "anime_overrides")?;
    if cols.is_empty() {
        return Ok(0);
    }
    conn.execute(
        "INSERT INTO main.anime_overrides(source, kind, id, anime, set_at)
         SELECT source, kind, id, anime, set_at FROM channel.anime_overrides WHERE true
         ON CONFLICT(source, kind, id) DO UPDATE
            SET anime = excluded.anime, set_at = excluded.set_at
          WHERE excluded.set_at > anime_overrides.set_at",
        [],
    )
    .context("merging anime overrides")
}
