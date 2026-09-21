//! What one fetch run actually does to a library, and the one thing it has
//! to settle before it starts: which language to ask the provider in.
//!
//! [`super::artwork`] decides what a run will ask for and whether the key is
//! any good; this is the walk that spends it. Split out so that neither file
//! has to be read to understand the other, and so the walk can be driven by
//! a stub with no key and no network.

use std::path::Path;

use mlib_spec::Kind;
use rusqlite::Connection;

use mediagram_tmdb::posters::{already_held, download_into, resolve_posters};
use mediagram_tmdb::tmdb_client::TmdbApi;

use crate::dto::PosterReport;

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
pub(super) fn language_of(conn: &Connection, fallback: &str) -> String {
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
