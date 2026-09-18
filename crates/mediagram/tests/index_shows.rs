//! The `shows` table: what a provider says about a title, recorded once.

use mediagram::index::shows::{ShowRow, count, from_details, get, kind_key, upsert};
use mediagram::metadata::tmdb_types::{DetailsResponse, NamedRef};
use mlib_spec::Kind;
use rusqlite::Connection;

fn db() -> Connection {
    let conn = Connection::open_in_memory().unwrap();
    for statement in mlib_spec::schema::migrations_up_to(mlib_spec::schema::SCHEMA_VERSION) {
        conn.execute_batch(statement).unwrap();
    }
    conn
}

fn row(kind: Kind, id: u64) -> ShowRow {
    ShowRow {
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
    }
}

fn details() -> DetailsResponse {
    serde_json::from_value(serde_json::json!({
        "id": 252107,
        "name": "Star City",
        "overview": "Eine Stadt, die wartet.",
        "genres": [{"name": "Drama"}, {"name": "Sci-Fi & Fantasy"}],
        "vote_average": 7.772,
        "networks": [{"name": "Apple TV"}],
        "status": "Returning Series",
        "first_air_date": "2026-01-08",
        "tagline": "",
        "number_of_seasons": 1,
        "number_of_episodes": 8
    }))
    .unwrap()
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

#[test]
fn a_details_payload_becomes_a_row() {
    let built = from_details(Kind::Ep, "de-DE", &details());

    assert_eq!(built.id, 252107);
    assert_eq!(built.genres.as_deref(), Some("Drama, Sci-Fi & Fantasy"));
    assert_eq!(built.network.as_deref(), Some("Apple TV"));
    assert_eq!(built.first_air.as_deref(), Some("2026-01-08"));
    // An empty tagline is TMDB saying it has none.
    assert_eq!(built.tagline, None);
}

/// Zero is what an unrated title scores, and is not a rating.
#[test]
fn an_unrated_title_records_no_rating() {
    let mut payload = details();
    payload.vote_average = Some(0.0);

    assert_eq!(from_details(Kind::Ep, "de-DE", &payload).rating, None);
}

#[test]
fn a_payload_with_nothing_in_it_yields_a_row_of_nothing() {
    let bare: DetailsResponse = serde_json::from_value(serde_json::json!({ "id": 7 })).unwrap();

    let built = from_details(Kind::Movie, "en-US", &bare);

    assert_eq!(built.id, 7);
    assert_eq!(built.overview, None);
    assert_eq!(built.genres, None);
    assert_eq!(built.rating, None);
}

/// A film has a release date where a series has a first air date, and the
/// column means the same thing for both.
#[test]
fn a_film_records_its_release_date_as_the_first_air_date() {
    let film: DetailsResponse = serde_json::from_value(serde_json::json!({
        "id": 36648, "release_date": "2004-12-08"
    }))
    .unwrap();

    assert_eq!(
        from_details(Kind::Movie, "de-DE", &film)
            .first_air
            .as_deref(),
        Some("2004-12-08")
    );
}

/// The index knows what it holds; only the provider knows what exists.
#[test]
fn a_payload_records_how_much_of_the_show_there_is() {
    let built = from_details(Kind::Ep, "de-DE", &details());

    assert_eq!(built.total_seasons, Some(1));
    assert_eq!(built.total_episodes, Some(8));
}

/// Zero is a record nobody filled in, not a show with no episodes.
#[test]
fn a_show_the_provider_has_not_counted_records_no_totals() {
    let mut payload = details();
    payload.number_of_seasons = Some(0);
    payload.number_of_episodes = Some(0);

    let built = from_details(Kind::Ep, "de-DE", &payload);

    assert_eq!(built.total_seasons, None);
    assert_eq!(built.total_episodes, None);
}

#[test]
fn genres_keep_the_order_the_provider_listed_them() {
    assert_eq!(
        from_details(Kind::Ep, "de-DE", &details())
            .genres
            .as_deref(),
        Some("Drama, Sci-Fi & Fantasy")
    );
}

/// A NamedRef is how TMDB spells a genre and a network alike.
#[test]
fn a_named_ref_parses_from_the_provider_shape() {
    let parsed: NamedRef = serde_json::from_value(serde_json::json!({"name": "STARZ"})).unwrap();
    assert_eq!(parsed.name, "STARZ");
}
