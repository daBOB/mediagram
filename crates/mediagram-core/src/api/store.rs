//! Reading the installed catalog for Kotlin: where this device keeps it, and
//! what the current version holds. How versions are laid out and installed
//! is [`crate::versions`]; this only ever reads one, through `current`.
//!
//! Two siblings of the versions outlive every one of them: `artwork/`, see
//! [`artwork_dir`], and `details.db`, holding the descriptions this device
//! fetched for itself, see [`super::enrich::details::details_db`].

use std::collections::HashSet;
use std::path::{Path, PathBuf};

use rusqlite::Connection;

use crate::catalog as queries;
use crate::dto;
use crate::versions::identity::read_identity;
use crate::versions::{self, count_playable, library_db, open_ro, pushed_at_of};

use super::{Core, CoreError};

pub(super) fn dir(core: &Core) -> PathBuf {
    core.data_dir.join("catalog")
}

pub(super) fn current_dir(core: &Core) -> PathBuf {
    versions::current(&dir(core))
}

/// Where fetched poster artwork and the TMDB provider-id cache live. Two
/// things must both hold:
///
/// - **outside every version directory**, so a refresh — which removes
///   every `v-…` and `incoming` entry but the version it installs, and runs
///   on every catalog load — keeps it;
/// - **inside `catalog/`**, so signing out, which deletes that directory
///   whole, takes it too: the next account never sees cached payloads
///   naming the previous one's titles, and nothing grows without bound.
pub(super) fn artwork_dir(core: &Core) -> PathBuf {
    dir(core).join("artwork")
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

mod editorial;
pub(super) use editorial::list_sets;

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
    in_artwork
        .exists()
        .then(|| in_artwork.display().to_string())
}

pub(super) fn total_size(core: &Core, set_id: String) -> Result<u64, CoreError> {
    let conn = open(core)?;
    queries::playable_set(&conn, &set_id)
        .map_err(CoreError::io("reading the catalog"))?
        .map(|set| set.total)
        .ok_or_else(|| CoreError::NotFound("set not found".into()))
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

/// What the installed catalog is, for the System screen. Every count is
/// collapsed to zero on any failure — opening the database, reading the
/// posters directory — because this is read to draw a screen, and a screen
/// that cannot draw is worse than one that says a library is empty.
pub(super) fn facts(core: &Core) -> dto::CatalogFacts {
    let dir = current_dir(core);
    // Only a refresh from a package writes an identity record; a catalog
    // without one came from the channel. Nothing installed is neither.
    let origin = match read_identity(&dir) {
        Ok(Some(_)) => "package",
        _ if !dir.exists() => "",
        Ok(None) => "channel",
        Err(err) => {
            tracing::warn!(error = %err, "the installed catalog's origin could not be read");
            ""
        }
    }
    .to_string();

    // `current` is the symlink; reading it (not resolving it) turns its
    // target's name, `v-<pushed_at>`, back into the timestamp it was named
    // for.
    let published_at = std::fs::read_link(&dir)
        .ok()
        .and_then(|target| {
            target
                .file_name()
                .map(|name| name.to_string_lossy().into_owned())
        })
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
#[path = "store_tests.rs"]
mod tests;
