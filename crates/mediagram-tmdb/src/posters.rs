//! Posters for the package, resolved from the TMDB responses already cached
//! on disk.
//!
//! `add` fetches `/movie/{id}` or `/tv/{id}` when it resolves a title, and
//! the cache stores the whole payload, `poster_path` included. Reading it
//! back costs nothing and needs no API key. A title whose payload is missing
//! from the cache is fetched if a key is configured, and skipped otherwise:
//! a missing poster is a cosmetic loss, never a failed export.

use mlib_spec::Kind;
use std::time::Duration;

use crate::tmdb_client::TmdbApi;

/// TMDB's image CDN. `w342` is the smallest width that still looks right on a
/// television shelf, and keeps a 300-title package near eight megabytes.
const IMAGE_BASE: &str = "https://image.tmdb.org/t/p/w342";

/// The width a cast or crew portrait is fetched at — a face on a credits row
/// needs far fewer pixels than a poster does.
pub const PORTRAIT_WIDTH: u32 = 185;

/// One image to fetch: the key it will be stored under, TMDB's path, and —
/// for a backdrop only — the width it was resolved at.
///
/// A poster is always fetched at [`IMAGE_BASE`]'s width, so it carries no
/// width of its own; a backdrop's varies by caller (the desktop web player's
/// hero wants far more pixels than a phone screen), so [`resolve_backdrops`]
/// stamps the width it was asked for onto every ref it returns.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct PosterRef {
    pub key: String,
    pub path: String,
    pub backdrop_width: Option<u32>,
}

impl PosterRef {
    /// The CDN URL, at the width the key's kind of image is shown at.
    #[must_use]
    pub fn url(&self) -> String {
        match self.backdrop_width {
            Some(width) => format!("https://image.tmdb.org/t/p/w{width}{}", self.path),
            None => poster_url(&self.path),
        }
    }
}

/// A poster that will not arrive promptly is not worth stalling a run for,
/// and a body that keeps coming is not worth buffering.
pub(crate) const POSTER_TIMEOUT: Duration = Duration::from_secs(20);
pub(crate) const POSTER_MAX_BYTES: u64 = 4 * 1024 * 1024;

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

/// Looks up each title's backdrop, keyed `<title key>-bg`, from the same
/// cached payload as its poster, at `width` — the desktop web player asks
/// for its widest (`w1280`); the phone asks narrower, by its own screen
/// class (see `crates/mediagram-core/src/api/enrich`).
///
/// Separate from [`resolve_posters`] on purpose: the export package and the
/// phone's on-device fetch both build on that one, and neither wants
/// backdrops at package-build time — the package has a size cap, and a
/// phone's width is only known once Kotlin asks.
pub async fn resolve_backdrops(
    api: &impl TmdbApi,
    titles: &[(Kind, u64)],
    width: u32,
) -> Vec<PosterRef> {
    let mut found: Vec<PosterRef> = Vec::new();
    for (kind, id) in titles {
        let key = mlib_spec::package::backdrop_key(&poster_key(*kind, *id));
        if found.iter().any(|p| p.key == key) {
            continue;
        }
        let details = match crate::details::details(api, *kind, *id).await {
            Ok(details) => details,
            Err(err) => {
                tracing::warn!(id, error = %err, "no backdrop for this title");
                continue;
            }
        };
        if let Some(path) = details.backdrop_path.filter(|p| is_image_path(p)) {
            found.push(PosterRef {
                key,
                path,
                backdrop_width: Some(width),
            });
        }
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
        .map(|path| PosterRef {
            key: key.to_owned(),
            path,
            backdrop_width: None,
        });
    let seasons = details.seasons.into_iter().filter_map(|season| {
        let path = season.poster_path.filter(|p| is_image_path(p))?;
        let key = mlib_spec::package::season_poster_key(key, season.season_number);
        Some(PosterRef {
            key,
            path,
            backdrop_width: None,
        })
    });
    show.into_iter().chain(seasons).collect()
}

/// A poster path is remote data that becomes part of a URL and a file name.
/// TMDB's own shape is `/<name>.<ext>`, so anything else is refused rather
/// than passed along.
///
/// Public because `mediagram_core::credits::portraits` checks a `profile`
/// path read out of a possibly-foreign channel snapshot the same way, before
/// it ever reaches a URL.
pub fn is_image_path(path: &str) -> bool {
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

/// The key a title's poster is stored under: `tmdb-movie-<id>` or `tmdb-tv-<id>`.
pub fn poster_key(kind: Kind, id: u64) -> String {
    format!("tmdb-{}-{id}", kind_key(kind))
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
