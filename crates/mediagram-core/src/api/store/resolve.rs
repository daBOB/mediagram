//! Resolving a poster key to the file it names, on disk or in the index's
//! `artwork` table — `poster_path`'s own per-key form, and `resolve_cached`,
//! the batched, memoised form `editorial` enriches a whole listing with.
//! Split out of `store.rs` to keep that file under the line limit.

use std::collections::{HashMap, HashSet};
use std::path::Path;
use std::sync::atomic::{AtomicU64, Ordering};

use rusqlite::Connection;

use super::{Core, artwork_dir, current_dir, open};

/// The two on-disk locations a poster key's file may already sit in: the
/// current version's own `posters/`, then the artwork directory a fetch or a
/// table write leaves it in. Shared by [`poster_path`] and [`resolve_with`],
/// which both check exactly these two spots before deciding whether a third
/// — the index's `artwork` table — is worth asking.
fn on_disk(version_dir: &Path, artwork_dir: &Path, key: &str) -> Option<String> {
    // ponytail: every artwork file is named `.jpg` regardless of its real
    // mime, matching the convention TMDB posters already used here; an image
    // loader that sniffs by content (as Coil does) still renders a PNG or
    // WebP fine under that name. Give the table's own `mime` its own
    // extension if a loader ever needs one.
    let name = format!("{key}.jpg");
    let in_version = version_dir.join("posters").join(&name);
    if in_version.exists() {
        return Some(in_version.display().to_string());
    }
    let in_artwork = artwork_dir.join(&name);
    in_artwork
        .exists()
        .then(|| in_artwork.display().to_string())
}

/// Looks in the current version's own `posters/` first, then in the artwork
/// directory a fetch writes to, then in the index's own `artwork` table —
/// all three behind the same key validation, so a second or third lookup
/// location is never a second way past it.
///
/// A package ships its own chosen artwork inside the version it arrived in,
/// so that copy stays authoritative for the keys it covers: a fetch or a
/// table lookup only ever runs for a title the package had nothing for.
///
/// A table hit is written into the artwork directory once, the same file a
/// fetch would have left there, so every later lookup for that key is a
/// plain file check like the two above — the table itself is only ever
/// consulted on a miss, and only ever opens a connection then: this is
/// `fetch_portrait`'s own check before it downloads anything, called once
/// per portrait fetch rather than once per listing, and must stay cheap for
/// the ordinary case where the file is already on disk.
pub(in crate::api) fn poster_path(core: &Core, poster_key: String) -> Option<String> {
    if !mlib_spec::package::poster_key_is_valid(&poster_key) {
        return None;
    }
    let version_dir = current_dir(core);
    let artwork = artwork_dir(core);
    if let Some(path) = on_disk(&version_dir, &artwork, &poster_key) {
        return Some(path);
    }
    let dest = artwork.join(format!("{poster_key}.jpg"));
    write_from_table(core, &poster_key, &dest).then(|| dest.display().to_string())
}

/// Writes `poster_key`'s bytes from the index's `artwork` table to `dest`,
/// if the index holds any. `false` on any failure — a missing table, a
/// missing key, or a write error — all of which leave this lookup with
/// nothing, exactly as if the table did not exist.
fn write_from_table(core: &Core, poster_key: &str, dest: &Path) -> bool {
    let Ok(conn) = open(core) else { return false };
    write_from_conn(&conn, poster_key, dest)
}

/// A number no two calls in this process ever share, for [`write_from_conn`]'s
/// own temporary file name — nothing else needs it to mean anything beyond that.
static WRITE_SEQ: AtomicU64 = AtomicU64::new(0);

/// `write_from_table`'s write, given a connection already open — a listing's
/// own, so materialising many keys costs one connection, not one per key.
///
/// Written beside `dest` under a name unique to this call, then renamed into
/// place: `media_set` and a listing can now both resolve the same key at
/// once, each against its own connection, so two writers must never share
/// one temporary file, and a reader's [`on_disk`] check must never see a
/// file half-written at the name it is about to open.
fn write_from_conn(conn: &Connection, poster_key: &str, dest: &Path) -> bool {
    let Ok(Some((_mime, bytes))) = crate::artwork::get(conn, poster_key) else {
        return false;
    };
    let Some(parent) = dest.parent() else {
        return false;
    };
    if std::fs::create_dir_all(parent).is_err() {
        return false;
    }
    let unique = WRITE_SEQ.fetch_add(1, Ordering::Relaxed);
    let tmp = parent.join(format!(".{poster_key}.{}.{unique}.tmp", std::process::id()));
    let done = std::fs::write(&tmp, bytes).is_ok() && std::fs::rename(&tmp, dest).is_ok();
    if !done {
        let _ = std::fs::remove_file(&tmp);
    }
    done
}

/// [`poster_path`]'s two-stat check plus, on a miss, the same table write —
/// except `conn` and `artwork_keys` (every key the index's `artwork` table
/// actually holds, read once — see `crate::artwork::keys`) are supplied
/// rather than discovered per key, so a listing enriching many keys pays for
/// one connection and one "which keys does the table hold" query, not one of
/// each per key, and never queries a key the table plainly does not have.
///
/// Every kind of key this resolves — poster, backdrop, season poster,
/// portrait — materialises from the table on a miss the same way; none of
/// them is disk-only.
pub(in crate::api) fn resolve_with(
    version_dir: &Path,
    artwork_dir: &Path,
    conn: &Connection,
    key: &str,
    artwork_keys: &HashSet<String>,
) -> Option<String> {
    if !mlib_spec::package::poster_key_is_valid(key) {
        return None;
    }
    if let Some(path) = on_disk(version_dir, artwork_dir, key) {
        return Some(path);
    }
    if !artwork_keys.contains(key) {
        return None;
    }
    let dest = artwork_dir.join(format!("{key}.jpg"));
    write_from_conn(conn, key, &dest).then(|| dest.display().to_string())
}

/// [`resolve_with`], remembering the answer per key in `cache` — many rows in
/// one listing name the same show's poster or the same season's poster, so
/// resolving each key once turns a few thousand rows into at most a few
/// hundred distinct keys' worth of stats, and a table read only for a key
/// [`resolve_with`] actually had to materialise.
pub(in crate::api) fn resolve_cached(
    cache: &mut HashMap<String, Option<String>>,
    version_dir: &Path,
    artwork_dir: &Path,
    conn: &Connection,
    key: &str,
    artwork_keys: &HashSet<String>,
) -> Option<String> {
    if let Some(hit) = cache.get(key) {
        return hit.clone();
    }
    let resolved = resolve_with(version_dir, artwork_dir, conn, key, artwork_keys);
    cache.insert(key.to_string(), resolved.clone());
    resolved
}

#[cfg(test)]
#[path = "resolve_tests.rs"]
mod tests;
