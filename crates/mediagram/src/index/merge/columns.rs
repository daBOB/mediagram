//! Which columns a merge may copy: only the ones both the local index and
//! the attached channel snapshot have. The channel's index was written by
//! whichever machine last pushed, which may still be on an older schema (see
//! `mlib_spec::schema::OLDEST_READABLE_SCHEMA`), so a column this build added
//! since — `shows.popularity`, say — may simply not exist over there.

use std::collections::HashSet;

use anyhow::{Context, Result};
use rusqlite::{Connection, params};

/// `table`'s columns present in both `main` (the local index) and `channel`
/// (the attached snapshot), in `main`'s column order.
pub(super) fn shared_columns(conn: &Connection, table: &str) -> Result<Vec<String>> {
    let local = table_columns(conn, table, "main")?;
    let channel: HashSet<String> = table_columns(conn, table, "channel")?.into_iter().collect();
    Ok(local.into_iter().filter(|c| channel.contains(c)).collect())
}

fn table_columns(conn: &Connection, table: &str, schema: &str) -> Result<Vec<String>> {
    let mut stmt = conn
        .prepare("SELECT name FROM pragma_table_info(?1, ?2)")
        .context("preparing the column list query")?;
    stmt.query_map(params![table, schema], |row| row.get::<_, String>(0))
        .with_context(|| format!("listing columns of {schema}.{table}"))?
        .collect::<rusqlite::Result<Vec<_>>>()
        .with_context(|| format!("reading a column of {schema}.{table}"))
}
