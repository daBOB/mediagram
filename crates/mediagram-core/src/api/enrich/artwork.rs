//! Deciding what one fetch run will ask a catalog's provider for.
//!
//! Split so each part can be driven by a stub in tests, and so the two
//! decisions worth pinning are readable from outside: [`plan_fetch`] settles
//! what a run asks for, in which language, and where what comes back goes,
//! before the key is spent; [`verify_then_fetch`] checks the key against the
//! provider itself before [`super::fetch::fetch_into`] reads anything
//! through the disk cache, so a rejected key cannot pass on cached answers.

use std::collections::HashSet;
use std::path::PathBuf;

use mlib_spec::Kind;

use mediagram_tmdb::disk_cache::DiskCachedApi;
use mediagram_tmdb::localized::Localized;
use mediagram_tmdb::posters::kind_key;
use mediagram_tmdb::tmdb_client::{HttpStatus, TmdbApi, TmdbClient};

use crate::catalog::PlayableSet;
use crate::dto::FetchReport;

use super::fetch::{fetch_into, language_of};
use crate::api::{Core, CoreError, store};
use crate::http;
use crate::versions;

/// What a fetch will ask the provider for, and where what comes back is kept
/// — a value, so a test can check where artwork lands.
pub struct FetchPlan {
    pub artwork_dir: PathBuf,
    pub titles: Vec<(Kind, u64)>,
    pub without_id: u32,
    /// The language to ask in, read from the same index as the titles.
    pub language: String,
}

/// Reads the installed catalog and works out what a fetch would do with it.
///
/// `current` is resolved once, here, so a refresh landing mid-plan cannot
/// leave it half-read out of a version being deleted. Artwork goes to
/// `store::artwork_dir`, which no refresh reaches. `fallback` is the caller's
/// locale, for a library described in no language at all.
pub fn plan_fetch(core: &Core, fallback: &str) -> Result<FetchPlan, CoreError> {
    let dir = std::fs::canonicalize(store::current_dir(core))
        .map_err(CoreError::NotFound("no catalog is loaded yet".into()).logged())?;
    let conn = versions::open_ro(&versions::library_db(&dir))?;
    let sets =
        crate::catalog::list_playable(&conn).map_err(CoreError::io("reading the catalog"))?;
    let language = language_of(&conn, fallback);
    drop(conn);

    let (titles, without_id) = split_titles(&sets);
    Ok(FetchPlan {
        artwork_dir: store::artwork_dir(core),
        titles,
        without_id,
        language,
    })
}

/// Fills both gaps a library leaves, for every title the provider numbers:
/// the artwork an index cannot carry, and descriptions nobody fetched.
pub(in crate::api) async fn fetch_missing(
    core: &Core,
    tmdb_key: String,
    language: String,
) -> Result<FetchReport, CoreError> {
    let plan = plan_fetch(core, &language)?;
    if plan.titles.is_empty() {
        // Nothing the provider could answer about — no reason to spend a
        // request validating a key that will never be used.
        return Ok(FetchReport {
            no_provider_id: plan.without_id,
            ..FetchReport::default()
        });
    }

    // One client for both the provider and the downloads.
    let client = http::client()?;
    let api = TmdbClient::new(client.clone(), tmdb_key);
    verify_then_fetch(core, api, &client, &plan).await
}

/// Validates the key against the provider, then spends it.
///
/// The validation goes to `api` directly, never through the disk cache: the
/// cache keys on endpoint and query, not the key, so a warm one would pass a
/// rotated or mistyped key without it ever being presented. The cache wraps
/// `api` afterwards, where answering twice from one request is its point.
pub async fn verify_then_fetch<A: TmdbApi>(
    core: &Core,
    api: A,
    http: &reqwest::Client,
    plan: &FetchPlan,
) -> Result<FetchReport, CoreError> {
    verify_key(&api).await?;
    let cached = Localized::new(DiskCachedApi::new(api, &plan.artwork_dir), &plan.language);
    Ok(fetch_into(core, &cached, http, plan).await)
}

/// Splits the catalog into the titles the provider can be asked about, and
/// how many cannot be asked at all — a course has no provider id.
///
/// Both halves count titles, never sets: a title is what a shelf shows as
/// one card, so a series is one and a 162-lesson course is one.
pub fn split_titles(sets: &[PlayableSet]) -> (Vec<(Kind, u64)>, u32) {
    let mut titles = Vec::new();
    let mut seen: HashSet<String> = HashSet::new();
    let mut unaskable: HashSet<(&str, &str)> = HashSet::new();
    for set in sets {
        match (set.kind.parse::<Kind>().ok(), set.tmdb) {
            (Some(kind), Some(id)) => {
                if seen.insert(format!("tmdb-{}-{id}", kind_key(kind))) {
                    titles.push((kind, id));
                }
            }
            _ => {
                // A film is its own card; anything else is shelved under its
                // collection, and the ones naming none share the single card
                // `Shelves.kt` gives them instead of being counted apart.
                let held_by = set.show.as_deref().map(str::trim).filter(|s| !s.is_empty());
                let alone = if set.kind == Kind::Movie.as_str() {
                    set.set_id.as_str()
                } else {
                    ""
                };
                unaskable.insert((set.kind.as_str(), held_by.unwrap_or(alone)));
            }
        }
    }
    (titles, unaskable.len() as u32)
}

/// Validates the key before any resolve or download, so a wrong key is
/// reported once rather than as a report full of zeroes.
async fn verify_key(api: &impl TmdbApi) -> Result<(), CoreError> {
    match api.get_json("/authentication", &[]).await {
        Ok(_) => Ok(()),
        Err(err) if rejected_the_key(&err) => Err(CoreError::NotAuthorized(
            "the artwork provider rejected this key".into(),
        )),
        Err(err) => Err(CoreError::network("could not reach the artwork provider")(
            err,
        )),
    }
}

/// TMDB answers an invalid key with HTTP 401, which `TmdbClient::get_json`
/// reports as a typed [`HttpStatus`] wherever in the error chain a cache or
/// localizing wrapper left it.
fn rejected_the_key(err: &anyhow::Error) -> bool {
    err.chain()
        .filter_map(|cause| cause.downcast_ref::<HttpStatus>())
        .any(|answer| answer.status == 401)
}

#[cfg(test)]
#[path = "artwork_tests.rs"]
mod tests;
