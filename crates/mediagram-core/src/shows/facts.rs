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
}

/// Every title's tagline, rating and popularity, by the poster key that
/// names it — the same key [`super::certifications`] and [`super::genres`]
/// use, so a listing attaches all four without a query per row.
///
/// `popularity` reads as `None` throughout for an index written before v8
/// added the column — the same accommodation `certifications` makes for
/// `certification`.
pub fn facts(conn: &Connection) -> rusqlite::Result<HashMap<String, ShowFacts>> {
    let popularity = optional_column(conn, "popularity")?;
    let mut stmt = conn.prepare(&format!(
        "SELECT kind, id, tagline, rating, {popularity} FROM shows WHERE source = ?1"
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
    }
}
