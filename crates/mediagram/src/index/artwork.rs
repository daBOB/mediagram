//! Custom poster and backdrop art the uploader supplies directly.
//!
//! Keyed the way TMDB art already is (`tmdb-…`/`tmdb-…-bg`), which overrides
//! the provider's own art where both exist, or — for a title with no
//! provider id — by its slug (`title-…`/`title-…-bg`,
//! [`mlib_spec::package::title_art_key`]). The bytes live in the index
//! itself and ride the ordinary index push, so no separate upload step is
//! needed to publish them.

use std::path::{Path, PathBuf};

use anyhow::{Context, Result, bail};
use rusqlite::{Connection, OptionalExtension, params};

/// Largest single image. Generous for a poster or a backdrop, small enough
/// that a handful of custom titles cannot push the published package toward
/// its 64 MB ceiling before anyone notices.
pub const MAX_ARTWORK_BYTES: usize = 1024 * 1024;

/// Extensions accepted from disk, in the order looked for by
/// [`adopt_folder`].
const IMAGE_EXTENSIONS: &[&str] = &["jpg", "jpeg", "png", "webp"];

/// Stores one image under `key`, replacing whatever it already held.
///
/// # Errors
/// Refuses a key this build's artwork rules do not recognise, or an image
/// over [`MAX_ARTWORK_BYTES`].
pub fn put(conn: &Connection, key: &str, mime: &str, bytes: &[u8]) -> Result<()> {
    if !mlib_spec::package::poster_key_is_valid(key) {
        bail!("`{key}` is not a valid artwork key");
    }
    if bytes.len() > MAX_ARTWORK_BYTES {
        bail!(
            "{key} is {} bytes, over the {MAX_ARTWORK_BYTES}-byte limit; resize the image",
            bytes.len()
        );
    }
    conn.execute(
        "INSERT INTO artwork(key, mime, bytes) VALUES (?1, ?2, ?3)
         ON CONFLICT(key) DO UPDATE SET mime = excluded.mime, bytes = excluded.bytes",
        params![key, mime, bytes],
    )
    .with_context(|| format!("storing artwork for {key}"))?;
    Ok(())
}

/// Reads a file from disk and stores it under `key`, its mime guessed from
/// the file's extension.
///
/// # Errors
/// Refuses a file whose extension is not `.jpg`, `.jpeg`, `.png` or `.webp`,
/// one that cannot be read, or one over [`MAX_ARTWORK_BYTES`].
pub fn put_file(conn: &Connection, key: &str, path: &Path) -> Result<()> {
    let mime = mime_from_ext(path)?;
    let bytes = std::fs::read(path).with_context(|| format!("reading {}", path.display()))?;
    put(conn, key, mime, &bytes)
}

fn mime_from_ext(path: &Path) -> Result<&'static str> {
    let ext = path
        .extension()
        .and_then(|e| e.to_str())
        .map(str::to_ascii_lowercase)
        .unwrap_or_default();
    match ext.as_str() {
        "jpg" | "jpeg" => Ok("image/jpeg"),
        "png" => Ok("image/png"),
        "webp" => Ok("image/webp"),
        _ => bail!(
            "{}: unrecognised image extension; use .jpg, .jpeg, .png or .webp",
            path.display()
        ),
    }
}

/// The image stored under `key`, as `(mime, bytes)`.
pub fn get(conn: &Connection, key: &str) -> Result<Option<(String, Vec<u8>)>> {
    conn.query_row(
        "SELECT mime, bytes FROM artwork WHERE key = ?1",
        [key],
        |row| Ok((row.get(0)?, row.get(1)?)),
    )
    .optional()
    .with_context(|| format!("reading artwork for {key}"))
}

/// Removes one image. `true` when a row was actually there.
pub fn clear(conn: &Connection, key: &str) -> Result<bool> {
    let removed = conn
        .execute("DELETE FROM artwork WHERE key = ?1", [key])
        .with_context(|| format!("clearing artwork for {key}"))?;
    Ok(removed > 0)
}

/// Picks up `poster.*`/`backdrop.*` at `dir`'s root, if present, storing
/// them under `key` and its backdrop key. Returns how many images were
/// stored — `0`, ordinarily, since most folders carry neither.
///
/// # Errors
/// Propagates a read or size-limit failure from whichever file is found;
/// a folder with neither file is not an error.
pub fn adopt_folder(conn: &Connection, dir: &Path, key: &str) -> Result<usize> {
    let mut stored = 0;
    if let Some(path) = find_named(dir, "poster") {
        put_file(conn, key, &path)?;
        stored += 1;
    }
    if let Some(path) = find_named(dir, "backdrop") {
        put_file(conn, &mlib_spec::package::backdrop_key(key), &path)?;
        stored += 1;
    }
    Ok(stored)
}

/// `<stem>.<ext>` at `dir`'s root, case-insensitively on both — a folder is
/// usually curated by hand, and `Poster.JPG` is as much "the poster" as
/// `poster.jpg` is.
fn find_named(dir: &Path, stem: &str) -> Option<PathBuf> {
    std::fs::read_dir(dir).ok()?.filter_map(Result::ok).find_map(|entry| {
        let path = entry.path();
        let name = path.file_stem()?.to_str()?;
        let ext = path.extension()?.to_str()?.to_ascii_lowercase();
        (name.eq_ignore_ascii_case(stem) && IMAGE_EXTENSIONS.contains(&ext.as_str()) && path.is_file())
            .then_some(path)
    })
}

#[cfg(test)]
#[path = "artwork_tests.rs"]
mod tests;
