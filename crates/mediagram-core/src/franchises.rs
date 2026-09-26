//! The `franchises` table: TMDB's "collection" a film belongs to, recorded
//! once per collection rather than once per film in it — the way a season's
//! poster is recorded once, not copied onto every episode.

use mediagram_tmdb::franchise::Franchise;
use rusqlite::{Connection, OptionalExtension, params};

use crate::shows::SOURCE;

/// Writes a franchise's entry, replacing whatever was there.
pub fn upsert(conn: &Connection, franchise: &Franchise) -> rusqlite::Result<()> {
    conn.execute(
        "INSERT INTO franchises(source, id, name, overview) VALUES (?1, ?2, ?3, ?4)
         ON CONFLICT(source, id) DO UPDATE SET
             name = excluded.name, overview = excluded.overview",
        params![SOURCE, franchise.id, franchise.name, franchise.overview],
    )?;
    Ok(())
}

/// A franchise's entry, or `None` when nothing has been recorded for it.
pub fn get(conn: &Connection, id: u64) -> rusqlite::Result<Option<Franchise>> {
    conn.query_row(
        "SELECT id, name, overview FROM franchises WHERE source = ?1 AND id = ?2",
        params![SOURCE, id],
        |row| {
            Ok(Franchise {
                id: row.get(0)?,
                name: row.get(1)?,
                overview: row.get(2)?,
            })
        },
    )
    .optional()
}

/// Whether a franchise already has a row — the check the backfill uses to
/// avoid asking TMDB again for a collection it already recorded.
pub fn has(conn: &Connection, id: u64) -> rusqlite::Result<bool> {
    conn.query_row(
        "SELECT EXISTS(SELECT 1 FROM franchises WHERE source = ?1 AND id = ?2)",
        params![SOURCE, id],
        |row| row.get(0),
    )
}

#[cfg(test)]
mod tests {
    use super::*;

    fn conn_with_franchises() -> Connection {
        let conn = Connection::open_in_memory().unwrap();
        for stmt in mlib_spec::schema::migrations_up_to(mlib_spec::schema::SCHEMA_VERSION) {
            conn.execute(stmt, []).unwrap();
        }
        conn
    }

    #[test]
    fn a_franchise_round_trips_and_can_be_replaced() {
        let conn = conn_with_franchises();
        assert!(!has(&conn, 115).unwrap());
        upsert(
            &conn,
            &Franchise {
                id: 115,
                name: "Star Trek: The Original Series Collection".into(),
                overview: Some("The films that started it all.".into()),
            },
        )
        .unwrap();
        assert!(has(&conn, 115).unwrap());
        let found = get(&conn, 115).unwrap().unwrap();
        assert_eq!(found.name, "Star Trek: The Original Series Collection");

        upsert(
            &conn,
            &Franchise {
                id: 115,
                name: "Renamed".into(),
                overview: None,
            },
        )
        .unwrap();
        assert_eq!(get(&conn, 115).unwrap().unwrap().name, "Renamed");
    }

    #[test]
    fn an_unknown_franchise_is_none() {
        let conn = conn_with_franchises();
        assert_eq!(get(&conn, 999).unwrap(), None);
    }
}
