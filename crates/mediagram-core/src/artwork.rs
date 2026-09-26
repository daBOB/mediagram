//! Reading the `artwork` table: custom poster and backdrop bytes the
//! uploader stores directly in `library.db`, rather than fetched from TMDB.
//!
//! Read-only here — only the uploader ever writes a row — so a device with a
//! v9 or older snapshot simply finds no such table and reads nothing, the
//! same way it already tolerates a missing column.

use rusqlite::{Connection, OptionalExtension, params};

/// The image stored under `key`, as `(mime, bytes)`, or `None` when there is
/// none — including when this snapshot predates the `artwork` table.
pub fn get(conn: &Connection, key: &str) -> rusqlite::Result<Option<(String, Vec<u8>)>> {
    let has_table: bool = conn.query_row(
        "SELECT EXISTS(SELECT 1 FROM sqlite_schema WHERE name = 'artwork')",
        [],
        |row| row.get(0),
    )?;
    if !has_table {
        return Ok(None);
    }
    conn.query_row(
        "SELECT mime, bytes FROM artwork WHERE key = ?1",
        params![key],
        |row| Ok((row.get(0)?, row.get(1)?)),
    )
    .optional()
}

#[cfg(test)]
mod tests {
    use super::*;

    fn open_with_artwork() -> Connection {
        let conn = Connection::open_in_memory().unwrap();
        conn.execute_batch(
            "CREATE TABLE artwork(key TEXT PRIMARY KEY, mime TEXT NOT NULL, bytes BLOB NOT NULL);
             INSERT INTO artwork(key, mime, bytes) VALUES ('title-terra-x', 'image/jpeg', X'01');",
        )
        .unwrap();
        conn
    }

    #[test]
    fn reads_a_stored_image() {
        let conn = open_with_artwork();
        let (mime, bytes) = get(&conn, "title-terra-x").unwrap().unwrap();
        assert_eq!(mime, "image/jpeg");
        assert_eq!(bytes, vec![1]);
    }

    #[test]
    fn a_missing_key_is_none() {
        let conn = open_with_artwork();
        assert!(get(&conn, "title-nowhere").unwrap().is_none());
    }

    #[test]
    fn a_snapshot_with_no_artwork_table_is_none_rather_than_an_error() {
        let conn = Connection::open_in_memory().unwrap();
        assert_eq!(get(&conn, "title-terra-x").unwrap(), None);
    }
}
