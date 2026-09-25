//! Reading a provider's details payload into a row.

use mediagram_tmdb::details::from_details;
use mediagram_tmdb::tmdb_types::DetailsResponse;
use mlib_spec::Kind;

fn details() -> DetailsResponse {
    serde_json::from_value(serde_json::json!({
        "id": 252107,
        "name": "Star City",
        "overview": "Eine Stadt, die wartet.",
        "genres": [{"name": "Drama"}, {"name": "Sci-Fi & Fantasy"}],
        "vote_average": 7.772,
        "popularity": 31.25,
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

/// Popularity is how TMDB ranks what is being looked at now; nought is a title
/// nobody has looked at, which ranks it no better than not knowing.
#[test]
fn a_payload_records_its_popularity_and_nought_is_none() {
    assert_eq!(
        from_details(Kind::Ep, "de-DE", &details()).popularity,
        Some(31.25)
    );
    let mut payload = details();
    payload.popularity = Some(0.0);
    assert_eq!(from_details(Kind::Ep, "de-DE", &payload).popularity, None);
}
