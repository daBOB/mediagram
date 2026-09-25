//! The walk a fetch run makes over a library's titles, filling both gaps it
//! can have — artwork and descriptions — from one request per title.
//! [`super::artwork`] plans the run; this spends it, and takes the client as
//! a parameter so a test can drive it with a stub.

use mediagram_tmdb::details::{details, from_details};
use mediagram_tmdb::poster_files::{already_held, download_into};
use mediagram_tmdb::posters::{kind_key, resolve_backdrops, resolve_posters};
use mediagram_tmdb::tmdb_client::TmdbApi;
use mlib_spec::Kind;
use rusqlite::Connection;

use super::artwork::FetchPlan;
use crate::dto::FetchReport;

use super::details as store;
use crate::api::Core;

/// Fetches the artwork, then the descriptions, for `plan`'s titles, counting
/// titles throughout; see [`FetchReport`].
///
/// `backdrop_width` is the caller's own choice — the desktop web player asks
/// widest, a phone asks by its screen class — passed all the way from
/// Kotlin rather than fixed here, the one place both meet.
///
/// The description half costs no second request for a title the provider
/// answered: the caller wraps `api` in a disk cache, which the poster half
/// has just filled. A title it refused is asked again — nothing is cached for
/// a failure — and that second refusal is what counts it as `failed`.
/// `tests/fetch_cache.rs` counts the requests.
pub async fn fetch_into(
    core: &Core,
    api: &impl TmdbApi,
    http: &reqwest::Client,
    plan: &FetchPlan,
    backdrop_width: u32,
) -> FetchReport {
    let FetchPlan {
        artwork_dir,
        titles,
        without_id,
        language,
    } = plan;
    let posters = resolve_posters(api, titles).await;
    let backdrops = resolve_backdrops(api, titles, backdrop_width).await;
    let posters_held = already_held(&posters, artwork_dir) as u32;
    let backdrops_held = already_held(&backdrops, artwork_dir) as u32;

    // One list to download, so a poster and its title's backdrop share the
    // same directory walk; split back apart afterwards to report each half.
    let mut refs = posters;
    refs.extend(backdrops.iter().cloned());
    // A hard failure here (the artwork directory could not even be created)
    // leaves every resolved ref undownloaded rather than panicking — the
    // catalog is the product, the artwork a convenience.
    let written = download_into(http, &refs, artwork_dir)
        .await
        .unwrap_or_else(|err| {
            tracing::warn!(error = %err, "the artwork directory is unavailable");
            Vec::new()
        });
    let backdrop_keys: std::collections::HashSet<&str> =
        backdrops.iter().map(|poster| poster.key.as_str()).collect();
    let backdrops_written = written.iter().filter(|key| backdrop_keys.contains(key.as_str())).count() as u32;
    let posters_written = written.len() as u32 - backdrops_written;
    let posters_fetched = posters_written.saturating_sub(posters_held);
    let backdrops_fetched = backdrops_written.saturating_sub(backdrops_held);

    // Collected as keys rather than added up, so a title that lost both its
    // poster and its description is one failure and not two.
    let mut lost: Vec<String> = refs
        .iter()
        .map(|poster| poster.key.clone())
        .filter(|key| !written.contains(key))
        .collect();
    let described = record_descriptions(core, api, language, titles).await;
    for key in described.lost {
        if !lost.contains(&key) {
            lost.push(key);
        }
    }

    FetchReport {
        posters_fetched,
        posters_already_held: posters_held,
        backdrops_fetched,
        backdrops_already_held: backdrops_held,
        details_recorded: described.recorded,
        details_already_known: described.already_known,
        no_provider_id: *without_id,
        failed: lost.len() as u32,
    }
}

/// What one walk of the titles learned, counted in titles.
#[derive(Default)]
struct Described {
    recorded: u32,
    already_known: u32,
    /// The keys of the titles that went undescribed, so the caller can tell
    /// them apart from the ones that lost only their artwork.
    lost: Vec<String>,
}

/// Records what the provider says about every title nothing already
/// describes — in the index or from an earlier run — the same way a poster
/// already on disk is not downloaded again.
///
/// The store is opened on the first title that needs recording and never
/// before, so a run that learned nothing leaves no database file behind.
///
/// Unlike the rest of a run this is not pinned to one catalog version:
/// `title_info` follows `current` on every title. A refresh landing mid-run
/// costs at worst one redundant fetch, whose row the index outranks on every
/// later read.
async fn record_descriptions(
    core: &Core,
    api: &impl TmdbApi,
    language: &str,
    titles: &[(Kind, u64)],
) -> Described {
    let mut described = Described::default();
    let mut opened: Option<Connection> = None;
    for (kind, id) in titles {
        let key = format!("tmdb-{}-{id}", kind_key(*kind));
        if store::title_info(core, key.clone()).is_some() {
            described.already_known += 1;
            continue;
        }
        let payload = match details(api, *kind, *id).await {
            Ok(payload) => payload,
            Err(err) => {
                tracing::warn!(id, error = %err, "no description for this title");
                described.lost.push(key);
                continue;
            }
        };
        if opened.is_none() {
            opened = store::open_or_create(core)
                .inspect_err(
                    |err| tracing::warn!(error = %err, "the description store is unavailable"),
                )
                .ok();
        }
        let row = from_details(*kind, language, &payload);
        let Some(conn) = opened.as_ref() else {
            described.lost.push(key);
            continue;
        };
        match store::upsert(conn, &row) {
            Ok(()) => described.recorded += 1,
            Err(err) => {
                tracing::warn!(id, error = %err, "a description could not be recorded");
                described.lost.push(key);
            }
        }
    }
    described
}

/// The language to ask the provider in: the one the library is already
/// described in, or `fallback` when it is described in none. TMDB answers in
/// English unless asked, and a shelf half in German and half in English is
/// worse than either.
pub(in crate::api) fn language_of(conn: &Connection, fallback: &str) -> String {
    match crate::shows::language(conn) {
        Ok(language) => language.unwrap_or_else(|| fallback.to_string()),
        Err(err) => {
            tracing::warn!(error = %err, "the library's language could not be read");
            fallback.to_string()
        }
    }
}

#[cfg(test)]
#[path = "fetch_tests.rs"]
mod tests;
