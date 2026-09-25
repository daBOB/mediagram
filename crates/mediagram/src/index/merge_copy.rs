//! Copying rows from the attached channel snapshot into the local index,
//! over the columns both sides have.

use anyhow::{Context, Result};
use rusqlite::Connection;

use crate::index::merge_columns::shared_columns;

/// Copies every `channel.{table}` row for `set_id` into `main.{table}`.
pub(super) fn insert_row(
    conn: &Connection,
    table: &str,
    cols: &[String],
    set_id: &str,
) -> Result<()> {
    let col_list = cols.join(", ");
    let sql = format!(
        "INSERT INTO main.{table} ({col_list}) SELECT {col_list} FROM channel.{table} WHERE set_id = ?1"
    );
    conn.execute(&sql, [set_id])
        .with_context(|| format!("copying {table} for {set_id} from the channel"))?;
    Ok(())
}

/// For every set now in `main` — just added or already shared — adds any
/// `(kind, lang)` combination the channel has that the local index lacks.
pub(super) fn fill_missing_assets(conn: &Connection) -> Result<()> {
    let cols = shared_columns(conn, "assets")?;
    let col_list = cols.join(", ");
    let sql = format!(
        "INSERT INTO main.assets ({col_list})
         SELECT {col_list} FROM channel.assets ch
         WHERE EXISTS (SELECT 1 FROM main.sets s WHERE s.set_id = ch.set_id)
           AND NOT EXISTS (
             SELECT 1 FROM main.assets m
             WHERE m.set_id = ch.set_id AND m.kind = ch.kind AND m.lang = ch.lang)"
    );
    conn.execute(&sql, [])
        .context("filling in missing assets from the channel")?;
    Ok(())
}
