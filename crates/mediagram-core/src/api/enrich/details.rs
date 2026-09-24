//! Where this device keeps the descriptions it fetched for itself, and the
//! one lookup that reads them together with the index's own: the index
//! answers first, and what this device fetched fills the gaps. The store
//! itself is [`crate::shows::sidecar`].

use std::path::PathBuf;

use mediagram_tmdb::details::TitleDetailsRow;
use rusqlite::{Connection, OpenFlags};

use crate::dto::TitleInfo;
use crate::shows::{read, sidecar, title_of};

use crate::api::{Core, CoreError, store};

/// Where descriptions this device fetched are kept: like
/// `store::artwork_dir`, outside every version so a refresh keeps them, and
/// inside `catalog/` so signing out takes them too.
pub fn details_db(core: &Core) -> PathBuf {
    store::dir(core).join("details.db")
}

/// Opens this device's sidecar for writing; see [`sidecar::open_or_create`].
pub fn open_or_create(core: &Core) -> Result<Connection, CoreError> {
    sidecar::open_or_create(&details_db(core))
}

/// Records what a fetch learned about one title; see [`crate::shows::upsert`].
pub fn upsert(conn: &Connection, row: &TitleDetailsRow) -> Result<(), CoreError> {
    crate::shows::upsert(conn, row).map_err(CoreError::io("recording a description"))
}

/// What is known about a title: the index's own row first — the publisher
/// curated it, and a phone's own fetch has no better claim — then whatever
/// this device fetched. Neither holding it is ordinary, not an error.
pub(in crate::api) fn title_info(core: &Core, poster_key: String) -> Option<TitleInfo> {
    // One gate, both stores, before either is opened. `poster_path` settles
    // the same question the same way for the two places artwork can sit: a
    // second lookup location must never become a second way past the check.
    title_of(&poster_key)?;
    in_index(core, &poster_key).or_else(|| fetched(core, &poster_key)).map(Into::into)
}

/// The row the downloaded index carries, if it carries one.
fn in_index(core: &Core, poster_key: &str) -> Option<TitleDetailsRow> {
    let conn = match store::open(core) {
        Ok(conn) => conn,
        // No catalog installed yet: nothing to describe, and nothing wrong.
        Err(CoreError::NotFound(_)) => return None,
        Err(err) => {
            tracing::warn!(error = %err, "the index could not be opened for a description");
            return None;
        }
    };
    read_logged(&conn, poster_key, "the index")
}

/// A stored description, or `None` — logging a store that could not be read,
/// so a corrupt one is not mistaken for a title nobody described.
fn read_logged(conn: &Connection, poster_key: &str, store: &str) -> Option<TitleDetailsRow> {
    read(conn, poster_key).unwrap_or_else(|err| {
        tracing::warn!(error = %err, store, "a stored description could not be read");
        None
    })
}

/// The row a fetch on this device left, if there has been one. Opened
/// read-only and only once the file exists, so a lookup never creates it.
fn fetched(core: &Core, poster_key: &str) -> Option<TitleDetailsRow> {
    read_logged(&open_fetched_ro(core)?, poster_key, "the description store")
}

/// This device's fetched-description sidecar, opened read-only — or `None`
/// when there is none. Shared with `store::list_sets`, which merges this
/// store's genres into the index's own the same way a single lookup here
/// prefers the index and falls back to this file.
pub(in crate::api) fn open_fetched_ro(core: &Core) -> Option<Connection> {
    let path = details_db(core);
    if !path.exists() {
        return None;
    }
    match Connection::open_with_flags(path, OpenFlags::SQLITE_OPEN_READ_ONLY) {
        Ok(conn) => Some(conn),
        Err(err) => {
            tracing::warn!(error = %err, "the description store could not be opened");
            None
        }
    }
}

#[cfg(test)]
#[path = "details_tests.rs"]
mod tests;
