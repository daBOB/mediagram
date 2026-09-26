//! Filling `credits` and `franchises` from the attached channel snapshot.
//!
//! Unlike `shows`, neither table is ever half-written: a title's credits are
//! replaced together by one `mediagram_core::credits::upsert` call, and a
//! franchise's row is one `INSERT ... ON CONFLICT`. So a title (or
//! franchise) already present in `main` never has anything to fill — only a
//! whole one missing from `main` is copied.
//!
//! Both are new in v9, so `shared_columns` tolerates a channel snapshot that
//! has neither table yet, the same way it tolerates a missing column: reading
//! a nonexistent table's columns is empty, not an error.

use anyhow::{Context, Result};
use rusqlite::Connection;

use crate::index::merge_columns::shared_columns;

/// Returns `(credits_rows_added, franchises_added)`.
pub(super) fn merge(conn: &Connection) -> Result<(usize, usize)> {
    Ok((
        merge_missing_credits(conn)?,
        merge_missing_franchises(conn)?,
    ))
}

fn merge_missing_credits(conn: &Connection) -> Result<usize> {
    let cols = shared_columns(conn, "credits")?;
    if cols.is_empty() {
        return Ok(0);
    }
    let col_list = cols.join(", ");
    conn.execute(
        &format!(
            "INSERT INTO main.credits ({col_list})
             SELECT {col_list} FROM channel.credits ch
             WHERE NOT EXISTS (
                SELECT 1 FROM main.credits m
                WHERE m.source = ch.source AND m.kind = ch.kind AND m.id = ch.id)"
        ),
        [],
    )
    .context("inserting credits this index lacks")
}

fn merge_missing_franchises(conn: &Connection) -> Result<usize> {
    let cols = shared_columns(conn, "franchises")?;
    if cols.is_empty() {
        return Ok(0);
    }
    let col_list = cols.join(", ");
    conn.execute(
        &format!(
            "INSERT INTO main.franchises ({col_list})
             SELECT {col_list} FROM channel.franchises ch
             WHERE NOT EXISTS (
                SELECT 1 FROM main.franchises m
                WHERE m.source = ch.source AND m.id = ch.id)"
        ),
        [],
    )
    .context("inserting franchises this index lacks")
}
