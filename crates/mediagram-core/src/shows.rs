//! What a provider said about a title, read back out of the index.
//!
//! The uploader fetched this when the title was added and wrote it here, so
//! reading it costs no request and no API key. Keyed the way a poster key is,
//! because TMDB numbers films and series independently and one row belongs to
//! a whole series rather than to each episode of it.

use anyhow::Result;
use rusqlite::{Connection, OptionalExtension};

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
    Some(("tmdb", kind, id.parse().ok()?))
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
