//! Posters for the package, resolved from the TMDB responses already cached
//! on disk.
//!
//! `add` fetches `/movie/{id}` or `/tv/{id}` when it resolves a title, and
//! the cache stores the whole payload, `poster_path` included. Reading it
//! back costs nothing and needs no API key. A title whose payload is missing
//! from the cache is fetched if a key is configured, and skipped otherwise:
//! a missing poster is a cosmetic loss, never a failed export.

use mlib_spec::Kind;

use crate::metadata::tmdb_client::TmdbApi;
use crate::metadata::tmdb_types::DetailsResponse;

/// TMDB's image CDN. `w342` is the smallest width that still looks right on a
/// television shelf, and keeps a 300-title package near eight megabytes.
const IMAGE_BASE: &str = "https://image.tmdb.org/t/p/w342";

/// One poster to fetch: the key it will be stored under, and TMDB's path.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct PosterRef {
    pub key: String,
    pub path: String,
}

/// The CDN URL for a TMDB poster path.
pub fn poster_url(poster_path: &str) -> String {
    format!("{IMAGE_BASE}{poster_path}")
}

/// Looks up the poster path for each title, skipping any that cannot be
/// resolved. Requests the same endpoint and query string `resolve` used, so
/// the on-disk cache hits instead of the network.
pub async fn resolve_posters(api: &impl TmdbApi, titles: &[(Kind, u64)]) -> Vec<PosterRef> {
    let mut found: Vec<PosterRef> = Vec::new();
    for (kind, id) in titles {
        let key = poster_key(*kind, *id);
        if found.iter().any(|p| p.key == key) {
            continue;
        }
        if let Some(path) = poster_path_for(api, *kind, *id).await {
            found.push(PosterRef { key, path });
        }
    }
    found
}

async fn poster_path_for(api: &impl TmdbApi, kind: Kind, id: u64) -> Option<String> {
    let path = match kind {
        Kind::Movie => format!("/movie/{id}"),
        Kind::Ep => format!("/tv/{id}"),
    };
    // Must match what `resolve::fetch_details` sends, or the cache key
    // (a hash of path plus sorted query) misses and this hits the network.
    let query = [("append_to_response", "external_ids".to_string())];
    let value = match api.get_json(&path, &query).await {
        Ok(value) => value,
        Err(err) => {
            tracing::warn!(%path, error = %err, "no poster for this title");
            return None;
        }
    };
    let details: DetailsResponse = serde_json::from_value(value).ok()?;
    details.poster_path.filter(|p| is_image_path(p))
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
    match kind {
        Kind::Movie => format!("tmdb-movie-{id}"),
        Kind::Ep => format!("tmdb-tv-{id}"),
    }
}
