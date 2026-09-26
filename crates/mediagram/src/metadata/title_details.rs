//! Reading a title's own description — a film's or a whole series' — out of
//! the provider payload, and backfilling what it names: its cast and crew,
//! and (a film) the franchise it belongs to.

use anyhow::Result;
use mlib_spec::Kind;
use rusqlite::Connection;

use mediagram_tmdb::certification::{certification, region_of};
use mediagram_tmdb::details;
use mediagram_tmdb::details::{TitleDetailsRow, from_details};
use mediagram_tmdb::tmdb_client::TmdbApi;

/// What a provider says about one title, ready to record.
///
/// `lang` is only carried through to the row: the client sends the configured
/// language itself, and recording which one answered is how a later change of
/// language is known to have replaced the text.
pub async fn fetch(api: &impl TmdbApi, kind: Kind, id: u64, lang: &str) -> Result<TitleDetailsRow> {
    let mut row = from_details(kind, lang, &details(api, kind, id).await?);
    // The age rating in the language's country. A title whose rating cannot
    // be had keeps its description: a missing rating means "not known to be
    // kid-safe", which is the safe reading, not a reason to record nothing.
    match certification(api, kind, id, &region_of(lang)).await {
        Ok(rating) => row.certification = rating,
        Err(err) => tracing::warn!(id, error = %err, "no age rating for this title"),
    }
    Ok(row)
}

/// Records a title's cast and crew, unless it already has some: a re-run of
/// `mediagram metadata` fills in what an earlier run could not reach, not
/// what it already answered. Returns whether it fetched and wrote anything.
pub async fn backfill_credits(
    conn: &Connection,
    api: &impl TmdbApi,
    kind: Kind,
    id: u64,
) -> Result<bool> {
    if mediagram_core::credits::has(conn, kind, id)? {
        return Ok(false);
    }
    match mediagram_tmdb::credits::credits(api, kind, id).await {
        Ok(rows) => {
            mediagram_core::credits::upsert(conn, kind, id, &rows)?;
            Ok(true)
        }
        Err(err) => {
            tracing::warn!(id, error = %err, "no credits for this title");
            Ok(false)
        }
    }
}

/// Records a film's franchise, unless this index already has it — a
/// franchise is shared by every film in it, so the first film to reach here
/// answers for the rest. Returns whether it fetched and wrote anything.
pub async fn backfill_franchise(
    conn: &Connection,
    api: &impl TmdbApi,
    collection_id: u64,
) -> Result<bool> {
    if mediagram_core::franchises::has(conn, collection_id)? {
        return Ok(false);
    }
    match mediagram_tmdb::franchise::franchise(api, collection_id).await {
        Ok(franchise) => {
            mediagram_core::franchises::upsert(conn, &franchise)?;
            Ok(true)
        }
        Err(err) => {
            tracing::warn!(collection_id, error = %err, "no franchise details");
            Ok(false)
        }
    }
}
