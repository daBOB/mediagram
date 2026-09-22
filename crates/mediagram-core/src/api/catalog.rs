//! What a refreshed catalog holds, and where it lives on disk.
//!
//! Layout under `<data_dir>/catalog/`: version directories (`v-<created_at>`,
//! one per successful refresh), a `current` symlink pointing at the one in
//! use, and two siblings that outlive every version — `artwork/`, see
//! [`artwork_dir`], and `details.db`, holding the descriptions this device
//! fetched for itself, see [`super::details::details_db`]. Anything else
//! fetched belongs beside those two and for their reasons, never inside a
//! version. `refresh.rs` is the only thing that ever writes a
//! version; this module only ever reads one, through `current`, so a refresh
//! landing mid-query cannot be observed as a half-written database — the
//! symlink swap in `refresh.rs` is atomic, and an already-open handle keeps
//! the version it opened.

use std::collections::HashSet;
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

/// Where fetched poster artwork and the TMDB provider-id cache live.
///
/// Two things have to be true of this path at once, and naming only the
/// first is how artwork came to outlive a start-over meant to forget it.
///
/// **Out of the version directory**, so a refresh cannot delete it.
/// `install_staged` removes one wholesale before renaming a fresh download
/// into place, `remove_other_versions` clears every version but the one just
/// published, and a refresh runs on every catalog load. Artwork kept there
/// was counted on a device at 0, then 236, then 0 again across a restart.
/// Neither pass touches a sibling: both remove only entries named `v-…` or
/// `incoming`, and a version is always `v-<pushed_at>`.
///
/// **Inside `catalog/`**, so forgetting the library forgets its artwork too.
/// Signing out deletes this directory whole; artwork held anywhere else
/// would survive it, leaving the next account to set the device up looking
/// at cached payloads naming the previous one's titles — and growing without
/// bound, since nothing else ever removes it.
pub(super) fn artwork_dir(core: &Core) -> PathBuf {
    dir(core).join("artwork")
}

pub(super) fn library_db(dir: &Path) -> PathBuf {
    dir.join(mlib_spec::schema::INDEX_FILE)
}

/// Opens a database read-only: nothing under `<data_dir>/catalog/` is this
/// crate's index to write, and a writable handle could checkpoint the WAL of
/// a version a refresh is about to remove.
pub(super) fn open_ro(path: &Path) -> Result<Connection, CoreError> {
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

/// Looks in the current version's own `posters/` first, then in the
/// artwork directory a fetch writes to — both behind the same key
/// validation, so a second lookup location is never a second way past it.
///
/// A package ships its own chosen artwork inside the version it arrived
/// in, so that copy stays authoritative for the keys it covers: a fetch
/// only ever ran for a title the package had nothing for.
pub(super) fn poster_path(core: &Core, poster_key: String) -> Option<String> {
    if !mlib_spec::package::poster_key_is_valid(&poster_key) {
        return None;
    }
    let name = format!("{poster_key}.jpg");
    let in_version = current_dir(core).join("posters").join(&name);
    if in_version.exists() {
        return Some(in_version.display().to_string());
    }
    let in_artwork = artwork_dir(core).join(&name);
    in_artwork.exists().then(|| in_artwork.display().to_string())
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

/// `*.jpg` entries under `<current>/posters/` and the artwork directory,
/// counted once per key even when a title is held in both — one title held
/// in both places is one poster, not two. An absent directory contributes
/// zero, not a failure — the ordinary state before any artwork is fetched.
fn count_posters(version_dir: &Path, artwork_dir: &Path) -> u64 {
    let mut keys: HashSet<std::ffi::OsString> = HashSet::new();
    for base in [version_dir.join("posters"), artwork_dir.to_path_buf()] {
        let Ok(entries) = std::fs::read_dir(&base) else {
            continue;
        };
        keys.extend(
            entries
                .filter_map(Result::ok)
                .map(|entry| entry.path())
                .filter(|path| path.extension().is_some_and(|ext| ext == "jpg"))
                .filter_map(|path| path.file_stem().map(std::ffi::OsStr::to_os_string)),
        );
    }
    keys.len() as u64
}

/// When the index in a version directory was pushed, from its name.
///
/// `refresh.rs` names every installed version `v-<pushed_at>` and points
/// `current` at it, so the catalogue's age is already written down and
/// needs no second record that could disagree with it. A name that is not
/// one of ours — including `current` itself, read literally rather than
/// through the symlink — reads as unknown rather than as a wrong date.
fn pushed_at_of(name: &str) -> Option<i64> {
    name.strip_prefix("v-")?.parse().ok()
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

    // `current` is the symlink; reading it (not resolving it) turns its
    // target's name, `v-<pushed_at>`, back into the timestamp it was named
    // for.
    let published_at = std::fs::read_link(&dir)
        .ok()
        .and_then(|target| target.file_name().map(|name| name.to_string_lossy().into_owned()))
        .as_deref()
        .and_then(pushed_at_of);

    dto::CatalogFacts {
        origin,
        sets: count_playable(&dir).unwrap_or(0),
        posters: count_posters(&dir, &artwork_dir(core)),
        schema: mlib_spec::schema::SCHEMA_VERSION as u32,
        published_at,
    }
}

#[cfg(test)]
#[path = "catalog_tests.rs"]
mod tests;
