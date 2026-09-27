//! Reading the `artwork` table: custom poster and backdrop bytes the
//! uploader stores directly in `library.db`, rather than fetched from TMDB.
//!
//! Read-only here — only the uploader ever writes a row — so a device with a
//! v9 or older snapshot simply finds no such table and reads nothing, the
//! same way it already tolerates a missing column.

use std::collections::HashSet;

use rusqlite::{Connection, OptionalExtension, params};

/// Whether this snapshot's `library.db` has ever recorded a custom image —
/// `false` for any snapshot older than the table, or one the uploader has
/// never written to. [`keys`] answers the more useful "which keys", read the
/// same way; this stays for [`get`]'s own single-key form, which has no set
/// of keys to check a key against.
pub fn table_exists(conn: &Connection) -> rusqlite::Result<bool> {
    conn.query_row(
        "SELECT EXISTS(SELECT 1 FROM sqlite_schema WHERE name = 'artwork')",
        [],
        |row| row.get(0),
    )
}

/// Every key this snapshot's `artwork` table holds bytes for, read once so a
/// listing can ask "is there art for this key" against an in-memory set
/// rather than a query per key — the same shape the web player's own
/// `artworkKeys` reads once per catalog (`web/src/catalog/artwork-routes.ts`).
/// Empty, not an error, for a snapshot with no such table.
pub fn keys(conn: &Connection) -> rusqlite::Result<HashSet<String>> {
    if !table_exists(conn)? {
        return Ok(HashSet::new());
    }
    let mut stmt = conn.prepare("SELECT key FROM artwork")?;
    let rows = stmt.query_map([], |row| row.get(0))?;
    rows.collect()
}

/// The image stored under `key`, as `(mime, bytes)`, or `None` when there is
/// none — including when this snapshot predates the `artwork` table.
///
/// For a single key asked about once, not a batch: a caller that already
/// read [`keys`] and knows `key` is in it should read the row directly
/// rather than through here, which re-asks whether the table exists at all
/// on every call — the cost this function accepts so a caller with no set of
/// keys in hand (a single portrait lookup) never has to read the whole table
/// first just to ask about one key.
pub fn get(conn: &Connection, key: &str) -> rusqlite::Result<Option<(String, Vec<u8>)>> {
    #[cfg(test)]
    GET_CALLS.with(|calls| calls.set(calls.get() + 1));
    if !table_exists(conn)? {
        return Ok(None);
    }
    conn.query_row(
        "SELECT mime, bytes FROM artwork WHERE key = ?1",
        params![key],
        |row| Ok((row.get(0)?, row.get(1)?)),
    )
    .optional()
}

// A count of `get` calls, for a test proving a listing queries a shared key
// once rather than once per row that names it — compiled only for tests, so
// it costs the release binary nothing.
#[cfg(test)]
thread_local! {
    static GET_CALLS: std::cell::Cell<usize> = const { std::cell::Cell::new(0) };
}

#[cfg(test)]
pub fn get_calls() -> usize {
    GET_CALLS.with(std::cell::Cell::get)
}

#[cfg(test)]
pub fn reset_get_calls() {
    GET_CALLS.with(|calls| calls.set(0));
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

    #[test]
    fn keys_reads_every_row_at_once() {
        let conn = open_with_artwork();
        conn.execute("INSERT INTO artwork(key, mime, bytes) VALUES ('tmdb-movie-550', 'image/jpeg', X'02')", [])
            .unwrap();
        let held = keys(&conn).unwrap();
        assert_eq!(held, HashSet::from(["title-terra-x".to_string(), "tmdb-movie-550".to_string()]));
    }

    #[test]
    fn keys_is_empty_without_an_artwork_table() {
        let conn = Connection::open_in_memory().unwrap();
        assert_eq!(keys(&conn).unwrap(), HashSet::new());
    }
}
