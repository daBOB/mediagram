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
use serde::{Deserialize, Serialize};

use crate::catalog as queries;
use crate::dto::{self, SetSummary};

use super::{Core, CoreError};

pub(super) const CURRENT: &str = "current";
pub(super) const IDENTITY_FILE: &str = "identity.json";
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

/// The five fields the cipher authenticates — what "already held" means.
/// Recorded only after a successful decrypt, never from `sha256`: that field
/// is not authenticated, and a reader that treats it as identity can have an
/// update suppressed by whoever last wrote the pointer.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub(super) struct Identity {
    pub format: u32,
    pub created_at: i64,
    pub key_id: String,
    pub schema: i64,
    pub spec: u32,
}

pub(super) fn identity_of(pointer: &mlib_spec::package::LatestPointer) -> Identity {
    Identity {
        format: pointer.format,
        created_at: pointer.created_at,
        key_id: pointer.key_id.clone(),
        schema: pointer.schema,
        spec: pointer.spec,
    }
}

/// The identity recorded when the version at `dir` was last decrypted, or
/// `None` if `dir` holds nothing — a first run, or a corrupt record, look
/// identical from here, and both mean "nothing held yet".
pub(super) fn read_identity(dir: &Path) -> Option<Identity> {
    let text = std::fs::read_to_string(dir.join(IDENTITY_FILE)).ok()?;
    serde_json::from_str(&text).ok()
}

pub(super) fn write_identity(dir: &Path, identity: &Identity) -> Result<(), CoreError> {
    let text = serde_json::to_string(identity)
        .map_err(|_| CoreError::Io("recording the package identity".into()))?;
    std::fs::write(dir.join(IDENTITY_FILE), text)
        .map_err(|_| CoreError::Io("recording the package identity".into()))
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

#[cfg(test)]
mod tests {
    use super::*;

    fn identity() -> Identity {
        Identity {
            format: 1,
            created_at: 1_781_568_000,
            key_id: "9f2c41ab".into(),
            schema: 6,
            spec: 4,
        }
    }

    #[test]
    fn a_written_identity_reads_back_equal() {
        let dir = tempfile::tempdir().unwrap();
        write_identity(dir.path(), &identity()).unwrap();
        assert_eq!(read_identity(dir.path()), Some(identity()));
    }

    #[test]
    fn no_identity_file_reads_back_as_nothing_held() {
        let dir = tempfile::tempdir().unwrap();
        assert_eq!(read_identity(dir.path()), None);
    }
}
