//! Textless posters for the poster game: a title readable in the artwork
//! gives the answer away before the last step, so TMDB's poster without
//! lettering is used when it has one.

use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::sync::{Arc, LazyLock, Mutex};

use mediagram_game::PosterLookup;
use mediagram_tmdb::poster_files::download_into;
use mediagram_tmdb::posters::{PosterRef, is_image_path, kind_key};
use mediagram_tmdb::tmdb_client::{TmdbApi, TmdbClient};

use crate::shows::title_of;

const WIDTH: u32 = 500;

/// The cache under the data folder, shared by a night hosted here and the
/// posters handed to a games app.
pub const CACHE_DIR: &str = "game-posters";

/// A lookup from a game title's key to its textless poster, cached under
/// `cache_dir`. `None` when the HTTP client cannot be built; the games then
/// show the normal posters.
pub fn poster_lookup(tmdb_key: String, cache_dir: PathBuf, poster_keys: HashMap<String, String>) -> Option<PosterLookup> {
    let http = crate::http::client()
        .inspect_err(|err| tracing::warn!(error = %err, "textless posters unavailable"))
        .ok()?;
    let api = Arc::new(TmdbClient::new(http.clone(), tmdb_key));
    let cache_dir = Arc::new(cache_dir);
    let fetch = one_at_a_time(Arc::new(move |poster_key: String| {
        let (api, http, dir) = (api.clone(), http.clone(), cache_dir.clone());
        Box::pin(async move {
            let fetch = textless_poster(api.as_ref(), &http, &dir, &poster_key);
            tokio::time::timeout(LOOKUP_TIMEOUT, fetch).await.ok().flatten()
        })
    }));
    let keys = Arc::new(poster_keys);
    // Only the catalog's poster keys reach the locks, so their map stays as small as the catalog
    // however many unknown keys a caller sends.
    Some(Arc::new(move |title_key: String| {
        let (keys, fetch) = (keys.clone(), fetch.clone());
        Box::pin(async move { fetch(keys.get(&title_key)?.clone()).await })
    }))
}

/// Longest one lookup may take, TMDB's answer and the download together. A request that
/// hangs (the Wi-Fi gone mid-call) would otherwise hold its poster's lock for good.
const LOOKUP_TIMEOUT: std::time::Duration = std::time::Duration::from_secs(20);

/// Runs `inner` for one poster key at a time: the key names the cached file,
/// which two sets of one film share. The prefetch and the image route usually
/// ask for the same poster together; unserialised they would both download it
/// into one temporary file, and a truncated image could be renamed into the
/// cache for good. The second caller waits, then finds the first one's file
/// (or miss marker) in the cache. The locks are the process's, not the
/// lookup's: a games app's requests each build a lookup of their own over the
/// same cache.
fn one_at_a_time(inner: PosterLookup) -> PosterLookup {
    static LOCKS: LazyLock<Mutex<HashMap<String, Arc<tokio::sync::Mutex<()>>>>> = LazyLock::new(Mutex::default);
    Arc::new(move |key: String| {
        let inner = inner.clone();
        Box::pin(async move {
            let lock = LOCKS.lock().unwrap_or_else(|e| e.into_inner()).entry(key.clone()).or_default().clone();
            let _held = lock.lock().await;
            inner(key).await
        })
    })
}

/// The cached textless poster for `poster_key`, fetching it on a first ask.
///
/// A title TMDB has none for gets a `.none` marker, so it is asked about
/// once; a request that fails leaves no marker, so the next ask retries.
pub async fn textless_poster(
    api: &impl TmdbApi,
    http: &reqwest::Client,
    cache_dir: &Path,
    poster_key: &str,
) -> Option<PathBuf> {
    let (kind, id) = title_of(poster_key)?;
    let file = cache_dir.join(format!("{poster_key}.jpg"));
    let marker = cache_dir.join(format!("{poster_key}.none"));
    if file.exists() {
        return Some(file);
    }
    if marker.exists() {
        return None;
    }
    let images = api
        .get_json(&format!("/{}/{id}/images", kind_key(kind)), &[("include_image_language", "null".into())])
        .await
        .inspect_err(|err| tracing::warn!(error = %format_args!("{err:#}"), "textless poster lookup failed"))
        .ok()?;
    let Some(path) = first_textless(&images) else {
        std::fs::create_dir_all(cache_dir).ok()?;
        std::fs::write(marker, b"").ok()?;
        return None;
    };
    let wanted = PosterRef { key: poster_key.to_string(), path, width: Some(WIDTH) };
    download_into(http, &[wanted], cache_dir).await.ok()?;
    file.exists().then_some(file)
}

/// The first poster TMDB lists with no language, i.e. without lettering.
fn first_textless(images: &serde_json::Value) -> Option<String> {
    images["posters"]
        .as_array()?
        .iter()
        .filter(|p| p["iso_639_1"].is_null())
        .filter_map(|p| p["file_path"].as_str())
        .find(|path| is_image_path(path))
        .map(str::to_string)
}

#[cfg(test)]
#[path = "game_posters_tests.rs"]
mod tests;
