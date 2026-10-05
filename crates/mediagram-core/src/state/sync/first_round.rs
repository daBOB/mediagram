//! Whether this device has taken in a sync round of the library it follows.
//!
//! Until it has, it has not heard who that household already is, so a first
//! profile waits (`ProfileManager::create_first`). Kept per library: choosing
//! another one is joining another household, whose names the last library's
//! rounds never brought. Two marks, both local only in `state_meta` — never
//! exported, never synced:
//!
//! - the library a round was last imported for, written once that import has
//!   committed, so a rolled-back import or a channel that could not be listed
//!   never counts;
//! - the library this device follows, written when one is installed
//!   (`refresh_library`, the step every first choice and every switch takes).
//!
//! A device installed before the second mark existed has none: it follows the
//! library it has always synced with, so any imported round counts for it.

use rusqlite::{Connection, OptionalExtension, params};

const IMPORTED_KEY: &str = "first_round_imported";
const FOLLOWED_KEY: &str = "followed_library";

/// Records that a round of `handle` was taken in.
pub(crate) fn mark_round_imported(conn: &Connection, handle: &str) -> rusqlite::Result<()> {
    write(conn, IMPORTED_KEY, handle)
}

/// Records that this device now follows `handle`.
pub(crate) fn follow_library(conn: &Connection, handle: &str) -> rusqlite::Result<()> {
    write(conn, FOLLOWED_KEY, handle)
}

/// A round of the library this device follows has been taken in.
pub(crate) fn round_imported(conn: &Connection) -> rusqlite::Result<bool> {
    Ok(match (read(conn, IMPORTED_KEY)?, read(conn, FOLLOWED_KEY)?) {
        (Some(imported), Some(followed)) => imported == followed,
        (Some(_), None) => true,
        (None, _) => false,
    })
}

fn write(conn: &Connection, key: &str, value: &str) -> rusqlite::Result<()> {
    conn.execute(
        "INSERT INTO state_meta(key, value) VALUES (?1, ?2)
           ON CONFLICT(key) DO UPDATE SET value = excluded.value",
        params![key, value],
    )?;
    Ok(())
}

fn read(conn: &Connection, key: &str) -> rusqlite::Result<Option<String>> {
    conn.query_row("SELECT value FROM state_meta WHERE key = ?1", [key], |row| row.get(0))
        .optional()
}
