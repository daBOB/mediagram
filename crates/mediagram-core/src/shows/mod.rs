//! The `shows` table: what a provider said about a title — a film or a whole
//! series. It sits in the index, where the uploader writes it when a title is
//! added, and in a device's own sidecar ([`sidecar`]), where a fetch on the
//! phone writes it; both are read and written only through here.
//!
//! Keyed the way a poster key is, because TMDB numbers films and series
//! independently and one row belongs to a whole series rather than to each
//! episode of it.

mod facts;
mod genres;
mod poster_key;
pub mod sidecar;

pub use facts::{ShowFacts, facts};
pub use genres::genres;
pub use poster_key::{read, title_of};

use mediagram_tmdb::details::TitleDetailsRow;
use mediagram_tmdb::posters::kind_key;
use mlib_spec::Kind;
use std::collections::HashMap;

use rusqlite::{Connection, OptionalExtension, params};

/// The provider this table records. Only TMDB is written today; the column
/// exists so a second one would not need a migration to sit beside it.
pub const SOURCE: &str = "tmdb";

/// Writes a title's entry, replacing whatever was there.
///
/// The one writer of this table: the uploader fills the index it owns with
/// it, and a device fills the sidecar beside the index it only reads.
/// Replacing rather than merging is the point — a later fetch is a
/// correction, not a second opinion, and asking again in another language
/// must not leave half the row in the old one.
pub fn upsert(conn: &Connection, row: &TitleDetailsRow) -> rusqlite::Result<()> {
    conn.execute(
        "INSERT INTO shows(source, kind, id, lang, overview, tagline, genres, rating,
                           network, status, first_air, last_air,
                           total_seasons, total_episodes, certification, popularity,
                           collection_id, collection_name, series_type)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15, ?16, ?17, ?18, ?19)
         ON CONFLICT(source, kind, id) DO UPDATE SET
             lang = excluded.lang, overview = excluded.overview,
             tagline = excluded.tagline, genres = excluded.genres,
             rating = excluded.rating, network = excluded.network,
             status = excluded.status, first_air = excluded.first_air,
             last_air = excluded.last_air, total_seasons = excluded.total_seasons,
             total_episodes = excluded.total_episodes,
             certification = excluded.certification,
             popularity = excluded.popularity,
             collection_id = excluded.collection_id,
             collection_name = excluded.collection_name,
             series_type = excluded.series_type",
        params![
            SOURCE,
            kind_key(row.kind),
            row.id,
            row.lang,
            row.overview,
            row.tagline,
            row.genres,
            row.rating,
            row.network,
            row.status,
            row.first_air,
            row.last_air,
            row.total_seasons,
            row.total_episodes,
            row.certification,
            row.popularity,
            row.collection_id,
            row.collection_name,
            row.series_type,
        ],
    )?;
    Ok(())
}

/// A title's entry, or `None` when nothing has been recorded for it. Films
/// and series are numbered independently, so `kind` is half the key.
pub fn get(conn: &Connection, kind: Kind, id: u64) -> rusqlite::Result<Option<TitleDetailsRow>> {
    let certification = optional_column(conn, "certification")?;
    let popularity = optional_column(conn, "popularity")?;
    let collection_id = optional_column(conn, "collection_id")?;
    let collection_name = optional_column(conn, "collection_name")?;
    let series_type = optional_column(conn, "series_type")?;
    conn.query_row(
        &format!(
            "SELECT lang, overview, tagline, genres, rating, network, status, first_air, last_air,
                    total_seasons, total_episodes, {certification}, {popularity},
                    {collection_id}, {collection_name}, {series_type}
               FROM shows WHERE source = ?1 AND kind = ?2 AND id = ?3"
        ),
        params![SOURCE, kind_key(kind), id],
        |row| {
            Ok(TitleDetailsRow {
                kind,
                id,
                lang: row.get(0)?,
                overview: row.get(1)?,
                tagline: row.get(2)?,
                genres: row.get(3)?,
                rating: row.get(4)?,
                network: row.get(5)?,
                status: row.get(6)?,
                first_air: row.get(7)?,
                last_air: row.get(8)?,
                total_seasons: row.get(9)?,
                total_episodes: row.get(10)?,
                certification: row.get(11)?,
                popularity: row.get(12)?,
                collection_id: row.get(13)?,
                collection_name: row.get(14)?,
                series_type: row.get(15)?,
            })
        },
    )
    .optional()
}

/// Every rated title's age rating, keyed by the poster key that names it —
/// the key a catalog row already carries, so a listing attaches a rating
/// without a query per row. A blank rating is no rating.
///
/// Empty for an index written before the column existed: a snapshot from a
/// machine not yet upgraded is still a catalog, and its titles are unrated.
pub fn certifications(conn: &Connection) -> rusqlite::Result<HashMap<String, String>> {
    if optional_column(conn, "certification")? == "NULL" {
        return Ok(HashMap::new());
    }
    let mut stmt = conn.prepare(
        "SELECT kind, id, certification FROM shows
          WHERE source = ?1 AND trim(coalesce(certification, '')) <> ''",
    )?;
    let rows = stmt.query_map([SOURCE], |row| {
        let kind: String = row.get(0)?;
        let id: i64 = row.get(1)?;
        let rating: String = row.get(2)?;
        // Spelled as `mediagram_tmdb::posters::poster_key` spells it.
        Ok((format!("tmdb-{kind}-{id}"), rating.trim().to_string()))
    })?;
    rows.collect()
}

/// `column`, or `NULL` where the table predates it — `certification` came in
/// v7, `popularity` in v8, `collection_id`/`collection_name`/`series_type`
/// in v9.
///
/// A snapshot is written by whichever machine uploads, and that machine may
/// still be on an older schema (see `mlib_spec::schema::OLDEST_READABLE_SCHEMA`).
/// Reading a missing column fails the whole statement, so an old index would
/// lose every description rather than only the field it never had.
///
/// `column` is always one of this file's literals, never outside input: the
/// name is spliced into SQL.
pub(super) fn optional_column(
    conn: &Connection,
    column: &'static str,
) -> rusqlite::Result<&'static str> {
    let present: bool = conn.query_row(
        "SELECT EXISTS(SELECT 1 FROM pragma_table_info('shows') WHERE name = ?1)",
        [column],
        |row| row.get(0),
    )?;
    Ok(if present { column } else { "NULL" })
}

/// The language most rows are written in, if any row names one. A library
/// described twice is still mostly one language, and a fetch should keep
/// asking in it rather than deepen the split. An empty `lang` — the column's
/// default, and what rows written before it existed carry — is no answer.
pub fn language(conn: &Connection) -> rusqlite::Result<Option<String>> {
    conn.query_row(
        "SELECT lang FROM shows WHERE lang <> '' GROUP BY lang ORDER BY COUNT(*) DESC LIMIT 1",
        [],
        |row| row.get(0),
    )
    .optional()
}
