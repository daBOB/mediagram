//! Posters for the package, resolved from the TMDB responses already cached
//! on disk.
//!
//! `add` fetches `/movie/{id}` or `/tv/{id}` when it resolves a title, and
//! the cache stores the whole payload, `poster_path` included. Reading it
//! back costs nothing and needs no API key. A title whose payload is missing
//! from the cache is fetched if a key is configured, and skipped otherwise:
//! a missing poster is a cosmetic loss, never a failed export.

use std::os::unix::fs::PermissionsExt;
use std::path::Path;
use std::time::Duration;

use anyhow::{Context, Result, bail};
use mlib_spec::Kind;

use crate::tmdb_client::TmdbApi;

/// TMDB's image CDN. `w342` is the smallest width that still looks right on a
/// television shelf, and keeps a 300-title package near eight megabytes.
const IMAGE_BASE: &str = "https://image.tmdb.org/t/p/w342";

/// One poster to fetch: the key it will be stored under, and TMDB's path.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct PosterRef {
    pub key: String,
    pub path: String,
}

/// A poster that will not arrive promptly is not worth stalling a run for,
/// and a body that keeps coming is not worth buffering.
const POSTER_TIMEOUT: Duration = Duration::from_secs(20);
const POSTER_MAX_BYTES: u64 = 4 * 1024 * 1024;

/// The CDN URL for a TMDB poster path.
pub fn poster_url(poster_path: &str) -> String {
    format!("{IMAGE_BASE}{poster_path}")
}

/// Looks up the poster path for each title, skipping any that cannot be
/// resolved. Requests the same endpoint and query string `resolve` used, so
/// the on-disk cache hits instead of the network.
///
/// A series also yields one poster per season that has its own artwork,
/// keyed `<show key>-s<n>`, read from the same payload as the show's.
pub async fn resolve_posters(api: &impl TmdbApi, titles: &[(Kind, u64)]) -> Vec<PosterRef> {
    let mut found: Vec<PosterRef> = Vec::new();
    for (kind, id) in titles {
        let key = poster_key(*kind, *id);
        if found.iter().any(|p| p.key == key) {
            continue;
        }
        found.extend(posters_for(api, *kind, *id, &key).await);
    }
    found
}

async fn posters_for(api: &impl TmdbApi, kind: Kind, id: u64, key: &str) -> Vec<PosterRef> {
    // A course has no provider id, so it never reaches this lookup and simply
    // has no poster from TMDB.
    let details = match crate::details::details(api, kind, id).await {
        Ok(details) => details,
        Err(err) => {
            tracing::warn!(id, error = %err, "no poster for this title");
            return Vec::new();
        }
    };
    let show = details
        .poster_path
        .filter(|p| is_image_path(p))
        .map(|path| PosterRef { key: key.to_owned(), path });
    let seasons = details.seasons.into_iter().filter_map(|season| {
        let path = season.poster_path.filter(|p| is_image_path(p))?;
        let key = mlib_spec::package::season_poster_key(key, season.season_number);
        Some(PosterRef { key, path })
    });
    show.into_iter().chain(seasons).collect()
}

/// A poster path is remote data that becomes part of a URL and a file name.
/// TMDB's own shape is `/<name>.<ext>`, so anything else is refused rather
/// than passed along.
fn is_image_path(path: &str) -> bool {
    let Some(rest) = path.strip_prefix('/') else {
        return false;
    };
    !rest.is_empty()
        && !rest.contains('/')
        && !rest.contains("..")
        && rest
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || c == '.' || c == '_' || c == '-')
}

fn poster_key(kind: Kind, id: u64) -> String {
    format!("tmdb-{}-{id}", kind_key(kind))
}

/// Downloads each poster into `dir` as `<key>.jpg`, returning the keys that
/// landed, in the order they were asked for.
///
/// A poster already on disk is left alone: the command that fills a local
/// directory is run again every time the library grows, and refetching what
/// is already held would make the common case the slow one. Delete the
/// directory to fetch it all again.
///
/// A poster that will not download is skipped rather than fatal. The catalog
/// is the product; the artwork is a convenience, and one unreachable image
/// must never cost a run that has already done real work.
pub async fn download_into(
    http: &reqwest::Client,
    refs: &[PosterRef],
    dir: &Path,
) -> Result<Vec<String>> {
    if refs.is_empty() {
        return Ok(Vec::new());
    }
    std::fs::create_dir_all(dir).with_context(|| format!("creating {}", dir.display()))?;
    restrict_dir(dir)?;

    let mut written = Vec::new();
    for poster in refs {
        // The key becomes a file name, a manifest path and a tar member
        // name, so it is checked here rather than trusted from upstream.
        if !mlib_spec::package::poster_key_is_valid(&poster.key) {
            tracing::warn!(key = %poster.key, "poster key rejected");
            continue;
        }
        let dest = dir.join(format!("{}.jpg", poster.key));
        if dest.exists() {
            written.push(poster.key.clone());
            continue;
        }
        match download(http, &poster_url(&poster.path), &dest).await {
            Ok(()) => written.push(poster.key.clone()),
            Err(err) => {
                tracing::warn!(key = %poster.key, error = %err, "poster skipped");
            }
        }
    }
    Ok(written)
}

/// How many of `refs` are already on disk in `dir`, so a caller can say what
/// it actually did rather than reporting every poster as freshly fetched.
pub fn already_held(refs: &[PosterRef], dir: &Path) -> usize {
    refs.iter()
        .filter(|p| dir.join(format!("{}.jpg", p.key)).exists())
        .count()
}

async fn download(http: &reqwest::Client, url: &str, dest: &Path) -> Result<()> {
    let response = http
        .get(url)
        .timeout(POSTER_TIMEOUT)
        .send()
        .await
        .context("requesting poster")?;
    let response = response
        .error_for_status()
        .context("poster request failed")?;
    if let Some(len) = response.content_length()
        && len > POSTER_MAX_BYTES
    {
        bail!("poster is {len} bytes, over the {POSTER_MAX_BYTES} byte limit");
    }
    let bytes = response.bytes().await.context("reading poster body")?;
    if bytes.len() as u64 > POSTER_MAX_BYTES {
        bail!("poster body exceeded the {POSTER_MAX_BYTES} byte limit");
    }
    std::fs::write(dest, &bytes).with_context(|| format!("writing {}", dest.display()))?;
    restrict(dest)
}

/// Artwork is written for one account's library and nobody else's.
fn restrict(path: &Path) -> Result<()> {
    std::fs::set_permissions(path, std::fs::Permissions::from_mode(0o600))
        .with_context(|| format!("restricting {}", path.display()))
}

/// The same, for a directory, which needs the execute bit to be enterable.
fn restrict_dir(path: &Path) -> Result<()> {
    std::fs::set_permissions(path, std::fs::Permissions::from_mode(0o700))
        .with_context(|| format!("restricting {}", path.display()))
}

/// How a kind is spelled in the key, matching the poster keys exactly: TMDB
/// numbers films and series independently, so 550 is two different titles.
pub fn kind_key(kind: Kind) -> &'static str {
    match kind {
        Kind::Movie => "movie",
        // A course has no provider entry; it never reaches this table.
        Kind::Ep | Kind::Tut | Kind::Doc => "tv",
    }
}
