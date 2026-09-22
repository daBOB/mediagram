//! What one fetch run actually does to a library, and the one thing it has
//! to settle before it starts: which language to ask the provider in.
//!
//! [`super::artwork`] decides what a run will ask for and whether the key is
//! any good; this is the walk that spends it. Split out so that neither file
//! has to be read to understand the other, and so the walk can be driven by
//! a stub with no key and no network.
//!
//! A library can be missing two things, and a viewer who asked for the
//! missing pieces did not ask for half of them: the artwork a channel index
//! cannot carry, and the descriptions nobody ran `mediagram metadata` for
//! before pushing it. Both answers sit in the same payload, so both are
//! taken from the one request per title [`fetch_into`] makes.

use mediagram_tmdb::details::{details, from_details};
use mediagram_tmdb::poster_files::{already_held, download_into};
use mediagram_tmdb::posters::{kind_key, resolve_posters};
use mediagram_tmdb::tmdb_client::TmdbApi;
use mlib_spec::Kind;
use rusqlite::Connection;

use super::artwork::FetchPlan;
use crate::dto::FetchReport;

use crate::api::Core;
use super::details as store;

/// Everything a fetch does except constructing the client, so a test can
/// drive a stub in place of a real TMDB key.
///
/// `titles` is already deduplicated and already excludes anything without a
/// provider id — `without_id` is that count, carried through rather than
/// recomputed, so this function never has to know what a course is to
/// report on one correctly. Every count it returns is therefore a number of
/// titles; see [`FetchReport`].
///
/// The description half runs second and asks the same questions of the same
/// client. A title the provider answered for costs no second request:
/// `resolve_posters` has just put its payload in the disk cache the caller
/// wrapped this client in, and the description is read out of the very
/// payload the poster path came from. A title it refused is asked twice —
/// `DiskCachedApi` stores nothing for a call that failed — and that second
/// refusal is what puts the title in `failed` rather than leaving it
/// silently undescribed. `tests/fetch_cache.rs` counts the requests.
pub async fn fetch_into(
    core: &Core,
    api: &impl TmdbApi,
    http: &reqwest::Client,
    plan: &FetchPlan,
) -> FetchReport {
    let FetchPlan { artwork_dir, titles, without_id, language } = plan;
    let refs = resolve_posters(api, titles).await;
    let held = already_held(&refs, artwork_dir) as u32;
    // A hard failure here (the posters directory could not even be created)
    // leaves every resolved ref undownloaded rather than panicking — the
    // catalog is the product, the artwork a convenience.
    let written = download_into(http, &refs, artwork_dir).await.unwrap_or_default();
    let fetched = (written.len() as u32).saturating_sub(held);

    // Collected as keys rather than added up, so a title that lost both its
    // poster and its description is one failure and not two.
    let mut lost: Vec<String> =
        refs.iter().map(|poster| poster.key.clone()).filter(|key| !written.contains(key)).collect();
    let described = record_descriptions(core, api, language, titles).await;
    for key in described.lost {
        if !lost.contains(&key) {
            lost.push(key);
        }
    }

    FetchReport {
        posters_fetched: fetched,
        posters_already_held: held,
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
/// describes.
///
/// A title something already describes is left alone, the same way a poster
/// already on disk is not downloaded again. `title_info` asks both stores at
/// once: the index's own row was written in the library's language by
/// whoever curated it and this device has no better claim on it, and a row
/// an earlier run left here is this run's own answer, already given.
///
/// The store is opened on the first title that actually needs recording and
/// never before, because a run over a library that is already described
/// must not leave a database file behind on a device that learned nothing —
/// the same rule the reading side keeps in `details::fetched`.
///
/// `title_info` re-resolves the `current` symlink on every title, so this is
/// the one reader in a run that is not pinned to the snapshot `plan_fetch`
/// canonicalised. That is the right answer rather than a missed one, in all
/// three cases a refresh landing mid-run can produce: a newer index that
/// describes the title means the title is worth skipping, and its row is
/// the one `title_info` would prefer anyway; a newer index that does not
/// means the fetch should happen; and a symlink caught mid-swap reads as
/// "nothing describes it", costing one redundant fetch whose row the index
/// outranks on every later read. Pinning it instead would mean carrying the
/// resolved directory through two signatures and handing this function a
/// connection it does not otherwise need.
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
        let Ok(payload) = details(api, *kind, *id).await else {
            described.lost.push(key);
            continue;
        };
        if opened.is_none() {
            opened = store::open_or_create(core).ok();
        }
        let row = from_details(*kind, language, &payload);
        match opened.as_ref() {
            Some(conn) if store::upsert(conn, &row).is_ok() => described.recorded += 1,
            _ => described.lost.push(key),
        }
    }
    described
}

/// The language the library was described in, or `fallback` when it was
/// described in none.
///
/// TMDB answers in English unless asked otherwise, so a run that ignores
/// this produces a shelf where some titles read in German and the rest in
/// English — worse than either, and not something a viewer can fix. The
/// rows the index already carries say which language that is, so nothing
/// needs configuring and nothing needs guessing.
///
/// Whichever language most of the rows use wins: a library described twice
/// is still mostly one language, and the run should keep asking in it
/// rather than deepen the split. `lang` is `''` for a row that names no
/// language — the column's own default, and what an index written before
/// that column existed carries — so an empty value is no answer at all; it
/// would reach the provider as `language=`, which asks for nothing rather
/// than for the caller's locale.
pub(in crate::api) fn language_of(conn: &Connection, fallback: &str) -> String {
    conn.query_row(
        "SELECT lang FROM shows WHERE lang <> '' GROUP BY lang ORDER BY COUNT(*) DESC LIMIT 1",
        [],
        |row| row.get::<_, String>(0),
    )
    .unwrap_or_else(|_| fallback.to_string())
}

#[cfg(test)]
#[path = "fetch_tests.rs"]
mod tests;
