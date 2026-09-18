//! What a provider says about a show, as opposed to about a file.
//!
//! The rest of the index describes files: a set is bytes with a name on them.
//! A synopsis belongs to the whole show and would be a lie repeated on every
//! episode, so it lives in its own table keyed the way a poster key is.
//!
//! Every field here comes out of the TMDB payload `add` already fetched to
//! resolve a title, so filling this table for a library that predates it is a
//! read of the cache on disk, not a round of requests.

use anyhow::{Context, Result};
use mlib_spec::Kind;
use rusqlite::{Connection, OptionalExtension, params};

use crate::metadata::tmdb_types::DetailsResponse;

/// The provider this table records. Only TMDB is written today; the column
/// exists so a second one would not need a migration to sit beside it.
const SOURCE: &str = "tmdb";

/// What one show's entry holds. Every field is optional because TMDB answers
/// for an obscure title with a record that is mostly empty.
#[derive(Debug, Clone, PartialEq)]
pub struct ShowRow {
    pub kind: Kind,
    pub id: u64,
    pub lang: String,
    pub overview: Option<String>,
    pub tagline: Option<String>,
    /// Comma-separated, in the order TMDB lists them.
    pub genres: Option<String>,
    pub rating: Option<f64>,
    pub network: Option<String>,
    pub status: Option<String>,
    pub first_air: Option<String>,
    pub last_air: Option<String>,
    /// What the provider says exists, against which a library can be counted.
    pub total_seasons: Option<u32>,
    pub total_episodes: Option<u32>,
}

/// How a kind is spelled in the key, matching the poster keys exactly: TMDB
/// numbers films and series independently, so 550 is two different titles.
pub fn kind_key(kind: Kind) -> &'static str {
    match kind {
        Kind::Movie => "movie",
        // A course has no provider entry; it never reaches this table.
        Kind::Ep | Kind::Tut => "tv",
    }
}

/// Reads a details payload into a row, keeping only what a viewer would read.
pub fn from_details(kind: Kind, lang: &str, details: &DetailsResponse) -> ShowRow {
    let join = |items: &[crate::metadata::tmdb_types::NamedRef]| {
        let joined = items
            .iter()
            .map(|item| item.name.as_str())
            .collect::<Vec<_>>()
            .join(", ");
        (!joined.is_empty()).then_some(joined)
    };
    ShowRow {
        kind,
        id: details.id,
        lang: lang.to_string(),
        // An empty string is TMDB's way of saying it has no synopsis, and is
        // worth no more than a missing one.
        overview: details.overview.clone().filter(|t| !t.trim().is_empty()),
        tagline: details.tagline.clone().filter(|t| !t.trim().is_empty()),
        genres: join(&details.genres),
        // Zero is what an unrated title scores, which is not a rating.
        rating: details.vote_average.filter(|r| *r > 0.0),
        network: join(&details.networks),
        status: details.status.clone().filter(|t| !t.trim().is_empty()),
        first_air: details
            .first_air_date
            .clone()
            .or_else(|| details.release_date.clone())
            .filter(|t| !t.is_empty()),
        last_air: details.last_air_date.clone().filter(|t| !t.is_empty()),
        // Zero seasons is a record nobody has filled in, not a show with none.
        total_seasons: details.number_of_seasons.filter(|n| *n > 0),
        total_episodes: details.number_of_episodes.filter(|n| *n > 0),
    }
}

/// Writes a show's entry, replacing whatever was there.
///
/// Replacing rather than merging is the point: asking again in another
/// language must not leave half the row in the old one.
pub fn upsert(conn: &Connection, row: &ShowRow) -> Result<()> {
    conn.execute(
        "INSERT INTO shows(source, kind, id, lang, overview, tagline, genres, rating,
                           network, status, first_air, last_air,
                           total_seasons, total_episodes)
         VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14)
         ON CONFLICT(source, kind, id) DO UPDATE SET
             lang = excluded.lang, overview = excluded.overview,
             tagline = excluded.tagline, genres = excluded.genres,
             rating = excluded.rating, network = excluded.network,
             status = excluded.status, first_air = excluded.first_air,
             last_air = excluded.last_air, total_seasons = excluded.total_seasons,
             total_episodes = excluded.total_episodes",
        params![
            SOURCE,
            kind_key(row.kind),
            row.id as i64,
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
        ],
    )
    .with_context(|| format!("recording {} {}", kind_key(row.kind), row.id))?;
    Ok(())
}

/// One show's entry, or `None` when nothing has been recorded for it.
pub fn get(conn: &Connection, kind: Kind, id: u64) -> Result<Option<ShowRow>> {
    conn.query_row(
        "SELECT lang, overview, tagline, genres, rating, network, status, first_air, last_air,
                total_seasons, total_episodes
           FROM shows WHERE source = ?1 AND kind = ?2 AND id = ?3",
        params![SOURCE, kind_key(kind), id as i64],
        |row| {
            Ok(ShowRow {
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
            })
        },
    )
    .optional()
    .context("reading a show entry")
}

/// How many shows have an entry. What `metadata` reports having done.
pub fn count(conn: &Connection) -> Result<i64> {
    conn.query_row("SELECT COUNT(*) FROM shows", [], |row| row.get(0))
        .context("counting show entries")
}
