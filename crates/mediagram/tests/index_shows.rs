//! The `shows` table: what a provider says about a title, recorded once.

use mediagram::index::shows::{count, get, upsert};
use mediagram_tmdb::details::TitleDetailsRow;
use mediagram_tmdb::posters::kind_key;
use mediagram_tmdb::tmdb_types::NamedRef;
use mlib_spec::Kind;
use rusqlite::Connection;

fn db() -> Connection {
    let conn = mediagram::index::sqlite_init::open(":memory:").unwrap();
    for statement in mlib_spec::schema::migrations_up_to(mlib_spec::schema::SCHEMA_VERSION) {
        conn.execute_batch(statement).unwrap();
    }
    conn
}

fn row(kind: Kind, id: u64) -> TitleDetailsRow {
    TitleDetailsRow {
        kind,
        id,
        lang: "de-DE".into(),
        overview: Some("Eine Stadt, die wartet.".into()),
        tagline: None,
        genres: Some("Drama".into()),
        rating: Some(7.8),
        network: Some("Apple TV".into()),
        status: Some("Returning Series".into()),
        first_air: Some("2026-01-08".into()),
        last_air: None,
        total_seasons: Some(2),
        total_episodes: Some(16),
        certification: Some("12".into()),
    }
}

#[test]
fn a_show_is_written_and_read_back_whole() {
    let conn = db();
    upsert(&conn, &row(Kind::Ep, 252107)).unwrap();

    assert_eq!(
        get(&conn, Kind::Ep, 252107).unwrap().as_ref(),
        Some(&row(Kind::Ep, 252107))
    );
}

#[test]
fn a_show_nobody_recorded_is_absent_rather_than_empty() {
    assert_eq!(get(&db(), Kind::Ep, 999).unwrap(), None);
}

/// TMDB numbers films and series independently, so one id is two titles.
#[test]
fn a_film_and_a_series_sharing_an_id_are_two_entries() {
    let conn = db();
    upsert(&conn, &row(Kind::Movie, 550)).unwrap();
    upsert(&conn, &row(Kind::Ep, 550)).unwrap();

    assert_eq!(count(&conn).unwrap(), 2);
    assert_eq!(kind_key(Kind::Movie), "movie");
    assert_eq!(kind_key(Kind::Ep), "tv");
}

/// Asking again in another language must replace the text, not leave half the
/// row in the old one.
#[test]
fn recording_a_show_again_replaces_every_field() {
    let conn = db();
    upsert(&conn, &row(Kind::Ep, 1)).unwrap();

    let mut english = row(Kind::Ep, 1);
    english.lang = "en-US".into();
    english.overview = Some("A town that waits.".into());
    english.status = None;
    upsert(&conn, &english).unwrap();

    let held = get(&conn, Kind::Ep, 1).unwrap().unwrap();
    assert_eq!(held.lang, "en-US");
    assert_eq!(held.overview.as_deref(), Some("A town that waits."));
    assert_eq!(held.status, None, "the old status must not survive");
    assert_eq!(count(&conn).unwrap(), 1, "one show, not two");
}

/// A NamedRef is how TMDB spells a genre and a network alike.
#[test]
fn a_named_ref_parses_from_the_provider_shape() {
    let parsed: NamedRef = serde_json::from_value(serde_json::json!({"name": "STARZ"})).unwrap();
    assert_eq!(parsed.name, "STARZ");
}
