//! Opens `library.db`, applies the mlib-spec schema and exposes the small
//! key-value `meta` table used to remember state that doesn't belong in
//! `sets`/`parts` (e.g. the source file path of a still-pending set).

use std::path::{Path, PathBuf};

use anyhow::{Context, Result};
use rusqlite::{Connection, OptionalExtension};

/// Where the index lives in a data directory.
pub fn index_path(data_dir: &Path) -> PathBuf {
    data_dir.join(mlib_spec::schema::INDEX_FILE)
}

/// The index's path, or an error saying there is nothing to `purpose` when
/// no upload has created one yet — a plainer answer than SQLite's "unable to
/// open database file", and one that never creates an empty index as a side
/// effect of looking.
pub fn require_index(data_dir: &Path, purpose: &str) -> Result<PathBuf> {
    let path = index_path(data_dir);
    if !path.exists() {
        anyhow::bail!(
            "no {} in {}; nothing to {purpose}",
            mlib_spec::schema::INDEX_FILE,
            data_dir.display()
        );
    }
    Ok(path)
}

/// Opens (creating if needed) `<data_dir>/library.db`, enables WAL mode and
/// foreign keys, runs every migration, and records the schema version.
pub fn open(data_dir: &Path) -> Result<Connection> {
    crate::paths::ensure_private_dir(data_dir)?;
    let path = index_path(data_dir);
    let conn = super::sqlite_init::open(&path)
        .with_context(|| format!("opening database {}", path.display()))?;

    conn.pragma_update(None, "journal_mode", "WAL")
        .context("enabling WAL journal mode")?;
    conn.pragma_update(None, "foreign_keys", true)
        .context("enabling foreign key enforcement")?;

    super::migrations::apply(&conn)?;

    Ok(conn)
}

/// Opens an existing `<data_dir>/library.db` for reading only.
///
/// The player never writes, and a read-write connection would: SQLite
/// checkpoints the WAL back into the main file when the last such connection
/// closes, which rewrites an index the uploader owns. Read-only also means a
/// serving process can never migrate a database a newer uploader wrote.
///
/// No migration runs here, so an index older than this build is reported
/// rather than upgraded behind the uploader's back. `purpose` finishes the
/// sentence "nothing to ..." when there is no index at all.
pub fn open_read_only(data_dir: &Path, purpose: &str) -> Result<Connection> {
    open_at_least(
        &require_index(data_dir, purpose)?,
        mlib_spec::schema::SCHEMA_VERSION,
    )
}

/// A snapshot someone else wrote — the channel's index a player installed —
/// opened for reading. Accepted down to
/// [`mlib_spec::schema::OLDEST_READABLE_SCHEMA`]: it cannot be migrated here,
/// and the machine that wrote it may not be upgraded yet. A reader of one must
/// treat anything newer than that floor as possibly absent.
pub fn open_snapshot(path: &Path) -> Result<Connection> {
    open_at_least(path, mlib_spec::schema::OLDEST_READABLE_SCHEMA)
}

fn open_at_least(path: &Path, oldest: i64) -> Result<Connection> {
    super::sqlite_init::configure();
    let conn = Connection::open_with_flags(
        path,
        rusqlite::OpenFlags::SQLITE_OPEN_READ_ONLY | rusqlite::OpenFlags::SQLITE_OPEN_NO_MUTEX,
    )
    .with_context(|| format!("opening {} read-only", path.display()))?;
    let version = super::migrations::version(&conn)?;
    if version < oldest {
        anyhow::bail!(
            "{} is at schema v{version}, this build expects v{oldest}; run any writing command once to migrate it",
            path.display(),
        );
    }
    Ok(conn)
}

/// `meta` key holding the file a pending set is uploaded from, for `resume`.
pub fn source_key(set_id: &str) -> String {
    format!("source:{set_id}")
}

/// `meta` key holding a faststart remux written for a set, so the upload
/// deletes that file — and never the person's original — when it is done.
pub fn tmp_key(set_id: &str) -> String {
    format!("tmp:{set_id}")
}

/// Every per-set `meta` key, for forgetting a set entirely.
pub fn set_keys(set_id: &str) -> [String; 2] {
    [source_key(set_id), tmp_key(set_id)]
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
    .optional()
    .with_context(|| format!("reading meta key {key}"))
}

/// Deletes a key from the `meta` table; a no-op if it was never set.
pub fn delete_meta(conn: &Connection, key: &str) -> Result<()> {
    conn.execute("DELETE FROM meta WHERE key = ?1", [key])
        .with_context(|| format!("deleting meta key {key}"))?;
    Ok(())
}

#[cfg(test)]
#[path = "db_tests.rs"]
mod tests;
