//! A device's own `shows` table: descriptions it fetched for itself, kept in
//! a database of their own because the index they describe is read-only here.
//!
//! Built from the same migrations as the index and versioned the same way —
//! `meta.schema_version` — so the two stores cannot drift in what a row
//! holds or in how either records where it is.

use std::path::Path;

use mlib_spec::schema;
use rusqlite::{Connection, OptionalExtension};

use crate::error::CoreError;

const PREPARING: &str = "preparing the description store";

/// Opens the sidecar at `path` for writing, creating the file and its schema
/// on first use and applying only the migrations it has not had: SQLite has
/// no `ADD COLUMN IF NOT EXISTS`, so replaying the whole list over an
/// existing file fails on the first `ALTER TABLE`.
pub fn open_or_create(path: &Path) -> Result<Connection, CoreError> {
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent).map_err(CoreError::io(PREPARING))?;
    }
    let conn = Connection::open(path).map_err(CoreError::io("opening the description store"))?;
    let recorded = recorded_version(&conn)?;
    if recorded != Recorded::Meta(schema::SCHEMA_VERSION) {
        migrate_from(&conn, recorded.version())?;
    }
    Ok(conn)
}

/// Where a file says it is.
#[derive(Debug, PartialEq)]
enum Recorded {
    /// In `meta.schema_version`, as the index records it.
    Meta(i64),
    /// In `PRAGMA user_version`, as sidecars written before the two agreed
    /// recorded it — 0 for a new file. Read once as the starting point; the
    /// next migration moves it into `meta`.
    UserVersion(i64),
}

impl Recorded {
    fn version(&self) -> i64 {
        match self {
            Recorded::Meta(at) | Recorded::UserVersion(at) => *at,
        }
    }
}

/// A read that fails is a real fault, never a new file: replaying every
/// migration over a populated store would only fail later, less usefully.
fn recorded_version(conn: &Connection) -> Result<Recorded, CoreError> {
    let has_meta: bool = conn
        .query_row(
            "SELECT EXISTS(SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'meta')",
            [],
            |row| row.get(0),
        )
        .map_err(CoreError::io(PREPARING))?;
    if has_meta {
        let in_meta: Option<String> = conn
            .query_row("SELECT value FROM meta WHERE key = 'schema_version'", [], |row| row.get(0))
            .optional()
            .map_err(CoreError::io(PREPARING))?;
        if let Some(value) = in_meta {
            return value.parse().map(Recorded::Meta).map_err(CoreError::io(PREPARING));
        }
    }
    conn.pragma_query_value(None, "user_version", |row| row.get(0))
        .map(Recorded::UserVersion)
        .map_err(CoreError::io(PREPARING))
}

/// Applies every statement past `at`, then records the new version in `meta`
/// — both in one transaction.
///
/// A group can be several statements: v6 adds two columns. Killed between
/// them outside a transaction, the file would keep the first column while
/// recording the version before it, and every later open would fail on
/// "duplicate column name". Rolled back instead, the next open retries it.
fn migrate_from(conn: &Connection, at: i64) -> Result<(), CoreError> {
    let applied = schema::migrations_up_to(at).len();
    conn.execute_batch("BEGIN").map_err(CoreError::io(PREPARING))?;
    let pending = schema::migrations_up_to(schema::SCHEMA_VERSION).into_iter().skip(applied);
    for statement in pending {
        if let Err(err) = conn.execute(statement, []) {
            let _ = conn.execute_batch("ROLLBACK");
            tracing::warn!(statement, "a description store migration failed");
            return Err(CoreError::io(PREPARING)(err));
        }
    }
    let recorded = conn.execute(
        "INSERT INTO meta(key, value) VALUES ('schema_version', ?1)
         ON CONFLICT(key) DO UPDATE SET value = excluded.value",
        [schema::SCHEMA_VERSION.to_string()],
    );
    if let Err(err) = recorded {
        let _ = conn.execute_batch("ROLLBACK");
        return Err(CoreError::io(PREPARING)(err));
    }
    conn.execute_batch("COMMIT").map_err(CoreError::io(PREPARING))
}

#[cfg(test)]
#[path = "sidecar_tests.rs"]
mod tests;
