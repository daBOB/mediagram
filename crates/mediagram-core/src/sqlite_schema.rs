//! What an index's own schema holds. It depends on the uploader that wrote
//! the snapshot: a table a later schema added is simply absent from an older
//! one, and every reader of such a table answers empty rather than failing.

use rusqlite::Connection;

/// Whether `conn` holds a table named `name` — a table, not an index or a
/// view that happens to share the name.
pub(crate) fn table_exists(conn: &Connection, name: &str) -> rusqlite::Result<bool> {
    conn.query_row(
        "SELECT EXISTS(SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?1)",
        [name],
        |row| row.get(0),
    )
}

#[cfg(test)]
#[path = "sqlite_schema_tests.rs"]
mod tests;
