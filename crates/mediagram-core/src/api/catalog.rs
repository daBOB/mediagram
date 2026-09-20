//! What a refreshed catalog holds, and where it lives on disk.
//!
//! Layout under `<data_dir>/catalog/`: version directories (`v-<created_at>`,
//! one per successful refresh) and a `current` symlink pointing at the one in
//! use. `refresh.rs` is the only thing that ever writes here; this module
//! only ever reads, through `current`, so a refresh landing mid-query cannot
//! be observed as a half-written database — the symlink swap in `refresh.rs`
//! is atomic, and an already-open handle keeps the version it opened.

use std::path::{Path, PathBuf};

use rusqlite::{Connection, OpenFlags};

use crate::catalog as queries;
use crate::dto::{self, SetSummary};

use super::identity;
use super::{Core, CoreError};

pub(super) use identity::{identity_of, read_identity, write_identity};

pub(super) const CURRENT: &str = "current";
pub(super) const MANIFEST_FILE: &str = "manifest.json";

pub(super) fn dir(core: &Core) -> PathBuf {
    core.data_dir.join("catalog")
}

pub(super) fn current_dir(core: &Core) -> PathBuf {
    dir(core).join(CURRENT)
}

fn library_db(dir: &Path) -> PathBuf {
    dir.join("library.db")
}

/// Opens a database read-only: nothing under `<data_dir>/catalog/` is this
/// crate's index to write, and a writable handle could checkpoint the WAL of
/// a version a refresh is about to remove.
fn open_ro(path: &Path) -> Result<Connection, CoreError> {
    Connection::open_with_flags(path, OpenFlags::SQLITE_OPEN_READ_ONLY)
        .map_err(|_| CoreError::Io("opening the catalog".into()))
}

/// Opens the current catalog, or reports it as not-yet-loaded rather than
/// creating an empty database — `Connection::open` would happily do the
/// latter for a path that does not exist, and even `open_with_flags` with
/// `SQLITE_OPEN_READ_ONLY` reports a missing file as an unhelpful "unable to
/// open database file" rather than the plain "nothing refreshed yet" this
/// is.
pub(super) fn open(core: &Core) -> Result<Connection, CoreError> {
    let path = library_db(&current_dir(core));
    if !path.exists() {
        return Err(CoreError::NotFound("no catalog is loaded yet".into()));
    }
    open_ro(&path)
}

pub(super) fn list_sets(core: &Core) -> Result<Vec<SetSummary>, CoreError> {
    let path = library_db(&current_dir(core));
    if !path.exists() {
        return Ok(Vec::new());
    }
    let conn = open_ro(&path)?;
    let sets = queries::list_playable(&conn).map_err(|_| CoreError::Io("reading the catalog".into()))?;
    Ok(sets.iter().map(dto::summary_from).collect())
}

pub(super) fn poster_path(core: &Core, poster_key: String) -> Option<String> {
    if !mlib_spec::package::poster_key_is_valid(&poster_key) {
        return None;
    }
    let path = current_dir(core)
        .join("posters")
        .join(format!("{poster_key}.jpg"));
    path.exists().then(|| path.display().to_string())
}

pub(super) fn total_size(core: &Core, set_id: String) -> Result<u64, CoreError> {
    let conn = open(core)?;
    queries::playable_set(&conn, &set_id)
        .map_err(|_| CoreError::Io("reading the catalog".into()))?
        .map(|set| set.total)
        .ok_or_else(|| CoreError::NotFound("set not found".into()))
}

/// Counts what a version holds, for `refresh_catalog`'s return value.
pub(super) fn count_playable(dir: &Path) -> Result<u64, CoreError> {
    let conn = open_ro(&library_db(dir))?;
    let sets =
        queries::list_playable(&conn).map_err(|_| CoreError::Io("reading the catalog".into()))?;
    Ok(sets.len() as u64)
}

/// `*.jpg` entries under `<current>/posters/`. An absent directory is zero
/// posters, not a failure — the ordinary state before any artwork is fetched.
fn count_posters(dir: &Path) -> u64 {
    std::fs::read_dir(dir.join("posters"))
        .map(|entries| {
            entries
                .filter_map(Result::ok)
                .filter(|entry| entry.path().extension().is_some_and(|ext| ext == "jpg"))
                .count() as u64
        })
        .unwrap_or(0)
}

/// What the installed catalog is, for the System screen. Every count is
/// collapsed to zero on any failure — opening the database, reading the
/// posters directory — because this is read to draw a screen, and a screen
/// that cannot draw is worse than one that says a library is empty.
pub(super) fn facts(core: &Core) -> dto::CatalogFacts {
    let dir = current_dir(core);
    let origin = match read_identity(&dir) {
        Ok(Some(_)) => "package",
        _ => "channel",
    }
    .to_string();

    dto::CatalogFacts {
        origin,
        sets: count_playable(&dir).unwrap_or(0),
        posters: count_posters(&dir),
        schema: mlib_spec::schema::SCHEMA_VERSION as u32,
    }
}
