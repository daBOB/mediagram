//! What a provider said about a title, read back out of the index.
//!
//! The uploader fetched this when the title was added and wrote it here, so
//! reading it costs no request and no API key. Keyed the way a poster key is,
//! because TMDB numbers films and series independently and one row belongs to
//! a whole series rather than to each episode of it.

use anyhow::Result;
use mediagram_tmdb::details::ShowRow;
use mediagram_tmdb::posters::kind_key;
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
pub fn upsert(conn: &Connection, row: &ShowRow) -> rusqlite::Result<()> {
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
    )?;
    Ok(())
}

/// What a viewer would read about a title.
#[derive(Debug, Clone, PartialEq)]
pub struct ShowRecord {
    pub overview: Option<String>,
    pub tagline: Option<String>,
    pub genres: Option<String>,
    pub rating: Option<f64>,
    pub network: Option<String>,
    pub status: Option<String>,
}

/// Splits `tmdb-movie-1234` into the source, kind and id the table is keyed
/// by. Returns `None` for anything that is not a poster key, so a malformed
/// value is refused here rather than reaching SQL.
pub fn key_parts(poster_key: &str) -> Option<(&str, &str, i64)> {
    if !mlib_spec::package::poster_key_is_valid(poster_key) {
        return None;
    }
    let rest = poster_key.strip_prefix("tmdb-")?;
    let (kind, id) = rest.split_once('-')?;
    Some((SOURCE, kind, id.parse().ok()?))
}

pub fn read(conn: &Connection, poster_key: &str) -> Result<Option<ShowRecord>> {
    let Some((source, kind, id)) = key_parts(poster_key) else {
        return Ok(None);
    };
    let row = conn
        .query_row(
            "SELECT overview, tagline, genres, rating, network, status
             FROM shows WHERE source = ?1 AND kind = ?2 AND id = ?3",
            rusqlite::params![source, kind, id],
            |row| {
                Ok(ShowRecord {
                    overview: row.get(0)?,
                    tagline: row.get(1)?,
                    genres: row.get(2)?,
                    rating: row.get(3)?,
                    network: row.get(4)?,
                    status: row.get(5)?,
                })
            },
        )
        .optional()?;
    Ok(row)
}
