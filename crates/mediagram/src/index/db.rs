//! Opens `library.db`, applies the mlib-spec schema and exposes the small
//! key-value `meta` table used to remember state that doesn't belong in
//! `sets`/`parts` (e.g. the source file path of a still-pending set).

use std::path::Path;

use anyhow::{Context, Result};
use rusqlite::Connection;

/// Opens (creating if needed) `<data_dir>/library.db`, enables WAL mode and
/// foreign keys, runs every migration, and records the schema version.
pub fn open(data_dir: &Path) -> Result<Connection> {
    std::fs::create_dir_all(data_dir)
        .with_context(|| format!("creating data dir {}", data_dir.display()))?;
    let path = data_dir.join("library.db");
    let conn =
        Connection::open(&path).with_context(|| format!("opening database {}", path.display()))?;

    conn.pragma_update(None, "journal_mode", "WAL")
        .context("enabling WAL journal mode")?;
    conn.pragma_update(None, "foreign_keys", true)
        .context("enabling foreign key enforcement")?;

    migrate(&conn)?;

    Ok(conn)
}

/// Applies every migration group above the database's recorded version, in
/// one transaction, then records the version reached.
///
/// Gating by version rather than replaying every statement is what allows a
/// migration to add a column: SQLite has no `ADD COLUMN IF NOT EXISTS`, so a
/// replayed list fails the second time it runs. The version is only advanced
/// after the statements commit, so an interrupted upgrade is retried rather
/// than skipped.
fn migrate(conn: &Connection) -> Result<()> {
    // The meta table lives in the first group, so a database that predates it
    // reports version 0 and gets everything.
    let current: i64 = conn
        .query_row(
            "SELECT value FROM meta WHERE key = 'schema_version'",
            [],
            |row| row.get::<_, String>(0),
        )
        .ok()
        .and_then(|v| v.parse().ok())
        .unwrap_or(0);

    if current >= mlib_spec::schema::SCHEMA_VERSION {
        return Ok(());
    }

    conn.execute_batch("BEGIN")
        .context("starting the migration transaction")?;
    for (index, group) in mlib_spec::schema::GROUPS.iter().enumerate() {
        let version = index as i64 + 1;
        if version <= current {
            continue;
        }
        for statement in group.iter() {
            if let Err(err) = conn.execute(statement, []) {
                let _ = conn.execute_batch("ROLLBACK");
                return Err(err).with_context(|| format!("migrating to v{version}: {statement}"));
            }
        }
    }
    if let Err(err) = set_meta(
        conn,
        "schema_version",
        &mlib_spec::schema::SCHEMA_VERSION.to_string(),
    ) {
        let _ = conn.execute_batch("ROLLBACK");
        return Err(err).context("recording schema version");
    }
    conn.execute_batch("COMMIT")
        .context("committing the migration")?;
    Ok(())
}

/// Upserts a key in the `meta` table.
pub fn set_meta(conn: &Connection, key: &str, value: &str) -> Result<()> {
    conn.execute(
        "INSERT INTO meta(key, value) VALUES (?1, ?2)
         ON CONFLICT(key) DO UPDATE SET value = excluded.value",
        (key, value),
    )
    .with_context(|| format!("writing meta key {key}"))?;
    Ok(())
}

/// Reads a key from the `meta` table, if present.
pub fn get_meta(conn: &Connection, key: &str) -> Result<Option<String>> {
    conn.query_row("SELECT value FROM meta WHERE key = ?1", [key], |row| {
        row.get(0)
    })
    .optional_context(key)
}

/// Deletes a key from the `meta` table; a no-op if it was never set.
pub fn delete_meta(conn: &Connection, key: &str) -> Result<()> {
    conn.execute("DELETE FROM meta WHERE key = ?1", [key])
        .with_context(|| format!("deleting meta key {key}"))?;
    Ok(())
}

/// Small helper to turn rusqlite's `QueryReturnedNoRows` into `None` while
/// still surfacing real errors with context.
trait OptionalContext<T> {
    fn optional_context(self, key: &str) -> Result<Option<T>>;
}

impl<T> OptionalContext<T> for rusqlite::Result<T> {
    fn optional_context(self, key: &str) -> Result<Option<T>> {
        match self {
            Ok(v) => Ok(Some(v)),
            Err(rusqlite::Error::QueryReturnedNoRows) => Ok(None),
            Err(e) => Err(e).with_context(|| format!("reading meta key {key}")),
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn opens_creates_schema_and_records_version() {
        let dir = tempfile::tempdir().unwrap();
        let conn = open(dir.path()).unwrap();
        assert!(dir.path().join("library.db").exists());
        let version = get_meta(&conn, "schema_version").unwrap();
        assert_eq!(version, Some(mlib_spec::schema::SCHEMA_VERSION.to_string()));
    }

    #[test]
    fn meta_roundtrip_and_delete() {
        let dir = tempfile::tempdir().unwrap();
        let conn = open(dir.path()).unwrap();
        assert_eq!(get_meta(&conn, "source:x").unwrap(), None);
        set_meta(&conn, "source:x", "/tmp/a.mkv").unwrap();
        assert_eq!(
            get_meta(&conn, "source:x").unwrap(),
            Some("/tmp/a.mkv".to_string())
        );
        set_meta(&conn, "source:x", "/tmp/b.mkv").unwrap();
        assert_eq!(
            get_meta(&conn, "source:x").unwrap(),
            Some("/tmp/b.mkv".to_string())
        );
        delete_meta(&conn, "source:x").unwrap();
        assert_eq!(get_meta(&conn, "source:x").unwrap(), None);
    }

    #[test]
    fn reopen_is_idempotent() {
        let dir = tempfile::tempdir().unwrap();
        open(dir.path()).unwrap();
        // Migrations run again on an existing database without error.
        open(dir.path()).unwrap();
    }
}
