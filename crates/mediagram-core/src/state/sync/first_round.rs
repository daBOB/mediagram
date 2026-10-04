//! Whether this device has taken in a sync round yet.
//!
//! Until it has, it has not heard who the household already is, so a first
//! profile waits (`ProfileManager::create_first`). Set by the import itself,
//! in its transaction, so a round whose import rolled back — or whose
//! channel could not be listed — never counts. Local only, in `state_meta`:
//! never exported, never synced.

use rusqlite::{Connection, OptionalExtension};

const KEY: &str = "first_round_imported";

/// Records that a round's import is committing. Writing it again is a no-op.
pub(crate) fn mark_round_imported(conn: &Connection) -> rusqlite::Result<()> {
    conn.execute(
        "INSERT INTO state_meta(key, value) VALUES (?1, '1') ON CONFLICT(key) DO NOTHING",
        [KEY],
    )?;
    Ok(())
}

pub(crate) fn round_imported(conn: &Connection) -> rusqlite::Result<bool> {
    conn.query_row("SELECT 1 FROM state_meta WHERE key = ?1", [KEY], |_| Ok(()))
        .optional()
        .map(|found| found.is_some())
}
