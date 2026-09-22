//! Deciding what one fetch run will ask a catalog's provider for.
//!
//! Split so each part can be driven by a stub in tests, and so the two
//! decisions worth pinning are readable from outside: [`plan_fetch`] settles
//! what a run asks for, in which language, and where what comes back goes,
//! before the key is spent; [`verify_then_fetch`] puts the key check ahead
//! of [`super::fetch::fetch_into`] and the disk cache around it, for the
//! reason its own comment gives.

use std::collections::HashSet;
use std::path::{Path, PathBuf};

use mlib_spec::Kind;

use mediagram_tmdb::posters::kind_key;
use mediagram_tmdb::tmdb_client::{DiskCachedApi, HttpStatus, Localized, TmdbApi, TmdbClient};

use crate::catalog::PlayableSet;
use crate::dto::FetchReport;

use super::catalog;
use super::fetch::{fetch_into, language_of};
use super::{Core, CoreError, http};

/// What a fetch will ask the provider for, and where what comes back is kept.
///
/// The directory is part of this answer rather than chosen where the writing
/// happens, so that it is a value a test can read. Where artwork lands is
/// the whole of a defect this crate has shipped once already.
pub struct FetchPlan {
    pub artwork_dir: PathBuf,
    pub titles: Vec<(Kind, u64)>,
    pub without_id: u32,
    /// The language to ask in — see [`language_of`]. Read from the same
    /// snapshot of the index the titles come from, because it is part of
    /// what a run asks for and not a detail of how it asks.
    pub language: String,
}

/// Reads the installed catalog and works out what a fetch would do with it.
///
/// `current` is a symlink a refresh swaps atomically, then deletes the
/// directory it used to point at. `std::fs::canonicalize` resolves it to
/// the real directory exactly once, here, before any request, and that
/// resolved value is used for exactly one thing afterwards: opening
/// `library.db`, which genuinely belongs to this snapshot of the catalog.
/// A refresh landing mid-fetch therefore cannot leave the plan half-read out
/// of a directory a cleanup pass is deleting. The plan, not the whole run:
/// the description walk re-resolves `current` once per title and may see a
/// newer index than this one — `super::fetch::record_descriptions` says why
/// that is the right answer there rather than a missed one.
///
/// The posters a fetch writes and the TMDB disk cache it reads through go
/// to `catalog::artwork_dir`: beside the version directories, not inside
/// one, so no refresh reaches them — and inside `catalog/`, so forgetting
/// the library forgets them too. `fallback` is the caller's own locale,
/// used only for a library nothing has described in any language.
pub fn plan_fetch(core: &Core, fallback: &str) -> Result<FetchPlan, CoreError> {
    let dir = std::fs::canonicalize(catalog::current_dir(core))
        .map_err(|_| CoreError::NotFound("no catalog is loaded yet".into()))?;
    let conn = catalog::open_ro(&catalog::library_db(&dir))?;
    let sets = crate::catalog::list_playable(&conn)
        .map_err(|_| CoreError::Io("reading the catalog".into()))?;
    let language = language_of(&conn, fallback);
    drop(conn);

    let (titles, without_id) = split_titles(&sets);
    Ok(FetchPlan { artwork_dir: catalog::artwork_dir(core), titles, without_id, language })
}

/// Fills both gaps a library leaves, for every title the provider numbers:
/// the artwork an index cannot carry, and descriptions nobody fetched.
pub(super) async fn fetch_missing(
    core: &Core,
    tmdb_key: String,
    language: String,
) -> Result<FetchReport, CoreError> {
    let plan = plan_fetch(core, &language)?;
    if plan.titles.is_empty() {
        // Nothing the provider could answer about — no reason to spend a
        // request validating a key that will never be used.
        return Ok(FetchReport { no_provider_id: plan.without_id, ..FetchReport::default() });
    }

    // Built once and handed on: `TmdbClient` takes this instance rather than
    // building its own (see `mediagram_tmdb::tmdb_client`), so there is one
    // client here, not two, and only `client()`'s `install_provider` to weigh.
    let client = http::client()?;
    let api = TmdbClient::new(client.clone(), tmdb_key);
    let FetchPlan { artwork_dir, titles, without_id, language } = plan;
    verify_then_fetch(core, api, &client, &artwork_dir, &language, &titles, without_id).await
}

/// Validates the key against the provider, then spends it.
///
/// `api` is the provider itself, and the validation is asked of it directly.
/// Asked instead through the disk cache built below, a warm cache would
/// answer `/authentication` out of a file some earlier run wrote — the cache
/// keys on the endpoint and the query, and the key appears in neither — so a
/// rotated or mistyped key would pass validation without ever having been
/// presented, and every already-resolved title would then be served from
/// that same cache. The run would report a library that is entirely
/// "already held" and a key that is entirely fine.
///
/// The cache wraps `api` afterwards, where being answered twice out of one
/// request is the whole point of it: a poster path, then a description.
pub async fn verify_then_fetch<A: TmdbApi>(
    core: &Core,
    api: A,
    http: &reqwest::Client,
    artwork_dir: &Path,
    language: &str,
    titles: &[(Kind, u64)],
    without_id: u32,
) -> Result<FetchReport, CoreError> {
    verify_key(&api).await?;
    let cached = Localized::new(DiskCachedApi::new(api, artwork_dir), language);
    Ok(fetch_into(core, &cached, http, artwork_dir, language, titles, without_id).await)
}

/// Splits the catalog into what the provider can be asked about and what
/// cannot be asked at all — a course has no provider id, and is counted
/// rather than looked up. `pub` because the count it returns is part of what
/// a fetch reports, and a caller needs this classification, not a copy of it.
///
/// Both halves are titles, never sets. A title is what a shelf shows as one
/// card: every episode of a series shares one provider id, every lesson is
/// shelved under its course. Counted per set — which is how the index holds
/// them — one half of this answer meant something else than the other, and a
/// 162-lesson course read as "162 titles" beside "3 posters fetched".
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
                let alone = if set.kind == Kind::Movie.as_str() { set.set_id.as_str() } else { "" };
                unaskable.insert((set.kind.as_str(), held_by.unwrap_or(alone)));
            }
        }
    }
    (titles, unaskable.len() as u32)
}

/// Validates the key against TMDB before any resolve or download, so a
/// wrong key is reported once rather than discovered as a report full of
/// zeroes a viewer might retry forever. Reaches TMDB every time it is
/// asked — see [`verify_then_fetch`] for what it is deliberately not asked
/// through.
async fn verify_key(api: &impl TmdbApi) -> Result<(), CoreError> {
    match api.get_json("/authentication", &[]).await {
        Ok(_) => Ok(()),
        Err(err) if rejected_the_key(&err) => {
            Err(CoreError::NotAuthorized("the artwork provider rejected this key".into()))
        }
        Err(_) => Err(CoreError::Network("could not reach the artwork provider".into())),
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
