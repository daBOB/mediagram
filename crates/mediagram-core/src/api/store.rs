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
use crate::dto::{self, SetSummary};
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

pub(super) fn list_sets(core: &Core) -> Result<Vec<SetSummary>, CoreError> {
    let path = library_db(&current_dir(core));
    if !path.exists() {
        return Ok(Vec::new());
    }
    let conn = open_ro(&path)?;
    let sets = queries::list_playable(&conn).map_err(CoreError::io("reading the catalog"))?;
    let ratings = crate::shows::certifications(&conn).map_err(CoreError::io("reading age ratings"))?;
    let subtitles =
        crate::catalog_assets::subtitle_languages(&conn).map_err(CoreError::io("reading subtitle languages"))?;
    let summarized = crate::catalog_assets::summaries(&conn).map_err(CoreError::io("reading which sets have a summary"))?;

    // The index's own genres first; where its row has none, whatever this
    // device fetched fills in. Coarser than `enrich::details::title_info`,
    // which takes an index row whole, so a genre shelf may show fetched
    // genres beside an index overview. A deliberate difference from the web
    // player, which has no device-side sidecar: a title this device fetched
    // but the index says nothing about still files onto its genre shelves.
    let mut genres = crate::shows::genres(&conn).map_err(CoreError::io("reading genres"))?;
    if let Some(fetched) = super::enrich::details::open_fetched_ro(core) {
        // Tolerant, unlike the index read above: this store is only ever a
        // fallback, so a sidecar that cannot be read — a partial file a
        // rolled-back migration left behind, say — must not take the whole
        // catalog down over genres it was never depended on for.
        match crate::shows::genres(&fetched) {
            Ok(more) => {
                for (key, list) in more {
                    genres.entry(key).or_insert(list);
                }
            }
            Err(err) => tracing::warn!(error = %err, "fetched genres could not be read"),
        }
    }

    Ok(sets
        .iter()
        .map(|set| {
            let mut summary = dto::summary_from(set);
            summary.fsk = summary.poster_key.as_ref().and_then(|key| ratings.get(key).cloned());
            summary.genres = summary.poster_key.as_ref().and_then(|key| genres.get(key).cloned()).unwrap_or_default();
            summary.subtitles = subtitles.get(&set.set_id).cloned().unwrap_or_default();
            summary.has_summary = summarized.contains(&set.set_id);
            summary
        })
        .collect())
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
