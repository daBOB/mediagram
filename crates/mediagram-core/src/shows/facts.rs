//! A title's tagline, rating and popularity — what the home page's
//! editorial picks read, from the same row [`super::certifications`] and
//! [`super::genres`] read their own facts from.

use std::collections::HashMap;

use rusqlite::Connection;

use super::{SOURCE, optional_column};

/// One title's editorial facts, absent where the provider recorded none.
#[derive(Debug, Clone, Default, PartialEq)]
pub struct ShowFacts {
    pub tagline: Option<String>,
    pub rating: Option<f64>,
    pub popularity: Option<f64>,
    /// What TMDB currently says this title's status is (`Ended`, `Returning
    /// Series`, …) — a film's own `status` (`Released`) counts too, the way
    /// the web player reads it. Present since the base `shows` table (v5);
    /// only named `show_status` past this boundary to keep it apart from a
    /// set's own playback `status`.
    pub show_status: Option<String>,
    /// The franchise (TMDB "collection") a film belongs to — absent for a
    /// series, and for an index written before v9.
    pub collection_id: Option<u64>,
    pub collection_name: Option<String>,
    /// What TMDB calls a series: `Scripted`, `Miniseries`, … Absent for a
    /// film, and for an index written before v9.
    pub series_type: Option<String>,
}

/// Every title's tagline, rating, popularity, status and franchise, by the
/// poster key that names it — the same key [`super::certifications`] and
/// [`super::genres`] use, so a listing attaches all of these without a query
/// per row.
///
/// `popularity`, `collection_id`, `collection_name` and `series_type` read
/// as `None` throughout for an index written before the column existed —
/// the same accommodation `certifications` makes for `certification`.
pub fn facts(conn: &Connection) -> rusqlite::Result<HashMap<String, ShowFacts>> {
    let popularity = optional_column(conn, "popularity")?;
    let collection_id = optional_column(conn, "collection_id")?;
    let collection_name = optional_column(conn, "collection_name")?;
    let series_type = optional_column(conn, "series_type")?;
    let mut stmt = conn.prepare(&format!(
        "SELECT kind, id, tagline, rating, {popularity}, status, {collection_id}, {collection_name}, {series_type}
           FROM shows WHERE source = ?1"
    ))?;
    let rows = stmt.query_map([SOURCE], |row| {
        let kind: String = row.get(0)?;
        let id: i64 = row.get(1)?;
        Ok((
            format!("tmdb-{kind}-{id}"),
            ShowFacts {
                tagline: row.get(2)?,
                rating: row.get(3)?,
                popularity: row.get(4)?,
                show_status: row.get(5)?,
                collection_id: row.get(6)?,
                collection_name: row.get(7)?,
                series_type: row.get(8)?,
            },
        ))
    })?;
    rows.collect()
}

#[cfg(test)]
mod tests {
    use super::*;
    use rusqlite::params;

    fn conn_with_shows() -> Connection {
        let conn = Connection::open_in_memory().unwrap();
        for stmt in mlib_spec::schema::migrations_up_to(mlib_spec::schema::SCHEMA_VERSION) {
            conn.execute(stmt, []).unwrap();
        }
        conn
    }

    #[test]
    fn reads_tagline_rating_and_popularity_by_poster_key() {
        let conn = conn_with_shows();
        conn.execute(
            "INSERT INTO shows(source, kind, id, tagline, rating, popularity)
               VALUES ('tmdb', 'movie', 550, 'A story.', 8.4, 42.0)",
            [],
        )
        .unwrap();

        let facts = facts(&conn).unwrap();

        let row = facts.get("tmdb-movie-550").expect("row present");
        assert_eq!(row.tagline.as_deref(), Some("A story."));
        assert_eq!(row.rating, Some(8.4));
        assert_eq!(row.popularity, Some(42.0));
    }

    #[test]
    fn reads_status_and_franchise_by_poster_key() {
        let conn = conn_with_shows();
        conn.execute(
            "INSERT INTO shows(source, kind, id, status, collection_id, collection_name, series_type)
               VALUES ('tmdb', 'movie', 550, 'Released', 9735, 'Fight Club Collection', NULL)",
            [],
        )
        .unwrap();
        conn.execute(
            "INSERT INTO shows(source, kind, id, status, series_type)
               VALUES ('tmdb', 'tv', 1399, 'Ended', 'Scripted')",
            [],
        )
        .unwrap();

        let facts = facts(&conn).unwrap();

        let film = facts.get("tmdb-movie-550").expect("row present");
        assert_eq!(film.show_status.as_deref(), Some("Released"));
        assert_eq!(film.collection_id, Some(9735));
        assert_eq!(film.collection_name.as_deref(), Some("Fight Club Collection"));
        assert_eq!(film.series_type, None);

        let series = facts.get("tmdb-tv-1399").expect("row present");
        assert_eq!(series.show_status.as_deref(), Some("Ended"));
        assert_eq!(series.collection_id, None);
        assert_eq!(series.series_type.as_deref(), Some("Scripted"));
    }

    #[test]
    fn a_title_with_no_row_is_absent_from_the_map() {
        let conn = conn_with_shows();
        assert!(facts(&conn).unwrap().is_empty());
    }

    #[test]
    fn an_index_predating_popularity_still_reads_tagline_and_rating() {
        // v7: `shows.certification` exists, `shows.popularity` (v8) does not
        // — the same snapshot `an index predating certification` fixtures
        // elsewhere in this crate use.
        let conn = Connection::open_in_memory().unwrap();
        for stmt in mlib_spec::schema::migrations_up_to(7) {
            conn.execute(stmt, []).unwrap();
        }
        conn.execute(
            "INSERT INTO shows(source, kind, id, tagline, rating) VALUES ('tmdb', 'movie', 550, 'A story.', 8.4)",
            params![],
        )
        .unwrap();

        let row = facts(&conn).unwrap().remove("tmdb-movie-550").expect("row present");
        assert_eq!(row.tagline.as_deref(), Some("A story."));
        assert_eq!(row.rating, Some(8.4));
        assert_eq!(row.popularity, None);
        assert_eq!(row.collection_id, None, "v9 columns predate this index too");
        assert_eq!(row.collection_name, None);
        assert_eq!(row.series_type, None);
    }
}
