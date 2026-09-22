//! Descriptions this device fetched for itself, kept beside the index rather
//! than in it.
//!
//! The index arrives from the channel already written, and this crate opens
//! it read-only on purpose — see `crate::api::store`. A description the phone
//! fetches therefore has nowhere to go inside it and needs a store of its
//! own: the same `shows` table, built from the same migrations, in a database
//! [`details_db`] puts where a refresh cannot reach it.
//!
//! `title_info` is the other half, and the only reader: the index answers
//! first, and what this device fetched fills the gaps.

use std::path::PathBuf;

use mediagram_tmdb::details::TitleDetailsRow;
use mlib_spec::schema;
use rusqlite::{Connection, OpenFlags};

use crate::dto::TitleInfo;
use crate::shows::{TitleDetails, key_parts, read};

use crate::api::{Core, CoreError, store};

/// Where descriptions this device fetched are kept. Two things must both
/// hold, exactly as for `crate::api::store::artwork_dir`:
///
/// - **outside every version directory**, so a refresh — which removes
///   every `v-…` and `incoming` entry but the version it installs — keeps it;
/// - **inside `catalog/`**, so signing out, which deletes that directory
///   whole, takes it too, and the next account never reads the previous
///   one's synopses.
pub fn details_db(core: &Core) -> PathBuf {
    store::dir(core).join("details.db")
}

/// Opens the sidecar for writing, creating the file and its schema on first
/// use.
///
/// The schema is the shared one, never a copy: a column added to
/// `mlib_spec`'s migrations reaches this database and the index alike, or
/// the two silently disagree about what a row holds.
///
/// Only the statements above what this file has already recorded are
/// applied. SQLite has no `ADD COLUMN IF NOT EXISTS`, so replaying the whole
/// list over an existing sidecar fails on the first `ALTER TABLE` — which
/// would leave a device that upgraded unable to open its own store at all.
///
/// The directory is whichever one [`details_db`] chose, so a test can move
/// the sidecar and still drive the real thing over it.
pub fn open_or_create(core: &Core) -> Result<Connection, CoreError> {
    let path = details_db(core);
    if let Some(parent) = path.parent() {
        std::fs::create_dir_all(parent).map_err(CoreError::io(PREPARING))?;
    }
    let conn =
        Connection::open(&path).map_err(CoreError::io("opening the description store"))?;

    let at: i64 = conn.pragma_query_value(None, "user_version", |row| row.get(0)).unwrap_or(0);
    if at < schema::SCHEMA_VERSION {
        migrate_from(&conn, at)?;
    }
    Ok(conn)
}

/// Applies every statement past the one the file records, then advances the
/// recorded version — both in one transaction.
///
/// The transaction is the whole point, and the version must move inside it.
/// A group can be several statements: v6 adds two columns. Killed between
/// them without one, the file keeps the first column and still records v5,
/// so every later open replays that `ALTER TABLE` and fails on "duplicate
/// column name" for good — descriptions silently stop being recorded on that
/// device until it is set up again. Rolled back instead, the file is exactly
/// what it was and the next open retries it. `PRAGMA user_version` is
/// transactional, and `index/db.rs` guards the index the same way.
fn migrate_from(conn: &Connection, at: i64) -> Result<(), CoreError> {
    let applied = schema::migrations_up_to(at).len();
    conn.execute_batch("BEGIN").map_err(CoreError::io(PREPARING))?;
    for statement in schema::migrations_up_to(schema::SCHEMA_VERSION).into_iter().skip(applied) {
        if conn.execute(statement, []).is_err() {
            let _ = conn.execute_batch("ROLLBACK");
            return Err(CoreError::Io(PREPARING.into()));
        }
    }
    if conn.pragma_update(None, "user_version", schema::SCHEMA_VERSION).is_err() {
        let _ = conn.execute_batch("ROLLBACK");
        return Err(CoreError::Io(PREPARING.into()));
    }
    conn.execute_batch("COMMIT").map_err(CoreError::io(PREPARING))
}

const PREPARING: &str = "preparing the description store";

/// Records what a fetch learned about one title; see [`crate::shows::upsert`].
pub fn upsert(conn: &Connection, row: &TitleDetailsRow) -> Result<(), CoreError> {
    crate::shows::upsert(conn, row).map_err(CoreError::io("recording a description"))
}

/// What is known about a title: the index's own row first, whatever this
/// device fetched after.
///
/// The publisher's row wins. It was written in the library's language by
/// whoever curated it, and a phone that fetched its own copy of the same
/// title has no better claim on it; a title the index says nothing about is
/// what a fetch is for.
///
/// Neither store holding it is not an error — a course has no provider
/// entry, and a library assembled without a key has no rows at all.
pub(in crate::api) fn title_info(core: &Core, poster_key: String) -> Option<TitleInfo> {
    // One gate, both stores, before either is opened. `poster_path` settles
    // the same question the same way for the two places artwork can sit: a
    // second lookup location must never become a second way past the check.
    key_parts(&poster_key)?;
    in_index(core, &poster_key).or_else(|| fetched(core, &poster_key)).map(Into::into)
}

/// The row the downloaded index carries, if it carries one.
fn in_index(core: &Core, poster_key: &str) -> Option<TitleDetails> {
    let conn = store::open(core).ok()?;
    read(&conn, poster_key).ok().flatten()
}

/// The row a fetch on this device left, if there has been one.
///
/// Absent is the ordinary case — every library nobody has fetched for — so a
/// missing file is nothing rather than an error. Opened read-only, and only
/// once the file is known to be there, because a lookup that created the
/// store would leave one behind on every device that merely opened a title.
fn fetched(core: &Core, poster_key: &str) -> Option<TitleDetails> {
    let path = details_db(core);
    if !path.exists() {
        return None;
    }
    let conn = Connection::open_with_flags(path, OpenFlags::SQLITE_OPEN_READ_ONLY).ok()?;
    read(&conn, poster_key).ok().flatten()
}

#[cfg(test)]
#[path = "details_tests.rs"]
mod tests;
