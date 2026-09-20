//! Fetching poster artwork for a catalog a channel index cannot carry.
//!
//! Split in two so the resolve-and-download half can be driven by a stub in
//! tests: [`fetch_into`] takes an already-classified list of titles and an
//! `impl TmdbApi`, and never decides which client to use. [`fetch_posters`]
//! is the seam Kotlin calls — it resolves the catalog directory once, before
//! the first request, validates the key, builds the real TMDB client, and
//! hands both to `fetch_into`.

use std::path::Path;

use mlib_spec::Kind;

use mediagram_tmdb::posters::{already_held, download_into, resolve_posters};
use mediagram_tmdb::tmdb_client::{TmdbApi, TmdbClient};

use crate::catalog::PlayableSet;
use crate::dto::PosterReport;

use super::catalog;
use super::{Core, CoreError, http};

/// Everything a fetch does except constructing the client, so a test can
/// drive a stub in place of a real TMDB key.
///
/// `titles` already excludes anything without a provider id — `without_id`
/// is that count, carried through rather than recomputed, so this function
/// never has to know what a course is to report on one correctly.
pub async fn fetch_into(
    api: &impl TmdbApi,
    http: &reqwest::Client,
    posters_dir: &Path,
    titles: &[(Kind, u64)],
    without_id: u32,
) -> PosterReport {
    let refs = resolve_posters(api, titles).await;
    let held = already_held(&refs, posters_dir) as u32;
    // A hard failure here (the posters directory could not even be created)
    // leaves every resolved ref undownloaded rather than panicking — the
    // catalog is the product, the artwork a convenience.
    let written = download_into(http, &refs, posters_dir).await.unwrap_or_default();
    let fetched = (written.len() as u32).saturating_sub(held);
    let failed = (refs.len() - written.len()) as u32;

    PosterReport { fetched, already_held: held, no_provider_id: without_id, failed }
}

/// Fetches artwork for every title the provider numbers.
///
/// The catalog directory is resolved before the first request and not
/// looked up again: a refresh landing mid-run swaps `current` underneath
/// this, and writing into the directory that was current when the run
/// started wastes the work rather than scattering files across two
/// installs.
pub(super) async fn fetch_posters(
    core: &Core,
    tmdb_key: String,
    language: String,
) -> Result<PosterReport, CoreError> {
    let dir = catalog::current_dir(core);
    let conn = catalog::open(core)?;
    let sets = crate::catalog::list_playable(&conn)
        .map_err(|_| CoreError::Io("reading the catalog".into()))?;
    drop(conn);

    let (titles, without_id) = split_titles(&sets);
    if titles.is_empty() {
        // Nothing the provider could answer about — no reason to spend a
        // request validating a key that will never be used.
        return Ok(PosterReport { no_provider_id: without_id, ..PosterReport::default() });
    }

    verify_key(&tmdb_key).await?;

    let posters_dir = dir.join("posters");
    let client = http::client()?;
    let api = TmdbClient::with_cache(&tmdb_key, &dir, &language);
    Ok(fetch_into(&api, &client, &posters_dir, &titles, without_id).await)
}

/// Splits the catalog into what the provider can be asked about and what
/// cannot be asked at all — a course has no provider id, and is counted
/// rather than looked up.
fn split_titles(sets: &[PlayableSet]) -> (Vec<(Kind, u64)>, u32) {
    let mut titles = Vec::new();
    let mut without_id = 0u32;
    for set in sets {
        match (kind_of(&set.kind), set.tmdb) {
            (Some(kind), Some(id)) if id > 0 => titles.push((kind, id as u64)),
            _ => without_id += 1,
        }
    }
    (titles, without_id)
}

/// The `kind` column spells `Kind` exactly as its own serde does — see
/// `mlib_spec::Kind`'s `rename_all = "lowercase"`.
fn kind_of(kind: &str) -> Option<Kind> {
    match kind {
        "movie" => Some(Kind::Movie),
        "ep" => Some(Kind::Ep),
        "tut" => Some(Kind::Tut),
        "doc" => Some(Kind::Doc),
        _ => None,
    }
}

/// Validates the key against TMDB before any resolve or download, so a
/// wrong key is reported once rather than discovered as a report full of
/// zeroes a viewer might retry forever.
async fn verify_key(tmdb_key: &str) -> Result<(), CoreError> {
    let probe = TmdbClient::new(tmdb_key);
    match probe.get_json("/authentication", &[]).await {
        Ok(_) => Ok(()),
        Err(err) if rejected_the_key(&err) => {
            Err(CoreError::NotAuthorized("the artwork provider rejected this key".into()))
        }
        Err(_) => Err(CoreError::Network("could not reach the artwork provider".into())),
    }
}

/// TMDB answers an invalid key with HTTP 401. `TmdbClient::get_json` strips
/// the request URL — the only place the key appears — before formatting any
/// error, so this string carries the endpoint path and the numeric status
/// and never the key itself.
fn rejected_the_key(err: &anyhow::Error) -> bool {
    err.to_string().contains("failed with 401")
}
