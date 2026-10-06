use super::*;

const PROVIDER_KEYS: [&str; 3] = ["tmdb-movie-550", "tmdb-tv-1396", "tvdb-series-12345"];

/// The writers build keys with these functions and the readers check them
/// with `poster_key_is_valid`; a key one side makes that the other refuses
/// would be artwork uploaded and then never shown.
#[test]
fn every_key_a_builder_makes_is_one_the_validator_accepts() {
    for key in PROVIDER_KEYS {
        assert!(poster_key_is_valid(key), "{key}");
        assert!(poster_key_is_valid(&backdrop_key(key)), "{key} backdrop");
        assert!(
            poster_key_is_valid(&season_poster_key(key, 2)),
            "{key} season"
        );
    }
    for name in [
        "Terra X",
        "  Rust -- The Book!! ",
        "Planet Erde 2",
        "Ausbildung Trading",
    ] {
        let key = title_art_key(name).expect("a name that slugs to something");
        assert!(poster_key_is_valid(&key), "{key}");
        assert!(poster_key_is_valid(&backdrop_key(&key)), "{key} backdrop");
    }
}

#[test]
fn a_title_key_is_the_slug_of_its_name() {
    assert_eq!(title_art_key("Terra X").as_deref(), Some("title-terra-x"));
    assert_eq!(
        title_art_key("  Rust -- The Book!! ").as_deref(),
        Some("title-rust-the-book")
    );
}

/// A name that slugs to nothing would give every such title the same key,
/// `title-`, which the validator refuses anyway — so none is made.
#[test]
fn a_name_that_slugs_to_nothing_has_no_title_key() {
    for name in ["", "!!!", " - ", "日本語"] {
        assert_eq!(title_art_key(name), None, "{name:?}");
    }
}

#[test]
fn a_backdrop_key_is_its_posters_key_with_the_marker_appended() {
    assert_eq!(backdrop_key("tmdb-movie-550"), "tmdb-movie-550-bg");
    assert_eq!(backdrop_key("title-terra-x"), "title-terra-x-bg");
}

#[test]
fn a_backdrop_is_recognised_by_its_key_and_a_poster_is_not() {
    for key in PROVIDER_KEYS.into_iter().chain(["title-terra-x"]) {
        assert!(is_backdrop_key(&backdrop_key(key)), "{key} backdrop");
        assert!(!is_backdrop_key(key), "{key} poster");
    }
    assert!(!is_backdrop_key(&season_poster_key("tmdb-tv-1396", 2)));
}

/// Only a whole trailing `-bg` part marks a backdrop: a name that merely
/// ends in the letters, or is nothing but them, is not one.
#[test]
fn ending_in_the_letters_bg_is_not_enough_to_be_a_backdrop() {
    for key in ["bg", "title-xbg", "tmdb-movie-550bg", "tmdb-movie-550-BG"] {
        assert!(!is_backdrop_key(key), "{key}");
    }
}

#[test]
fn a_season_key_sits_beside_its_shows_key_as_one_more_part() {
    assert_eq!(season_poster_key("tmdb-tv-1396", 2), "tmdb-tv-1396-s2");
    // Specials are season 0 at TMDB, and still a season with artwork.
    assert_eq!(season_poster_key("tmdb-tv-1396", 0), "tmdb-tv-1396-s0");
    assert!(poster_key_is_valid("tmdb-tv-1396-s0"));
}

/// A key that is cut short has no id to tell one title from another.
#[test]
fn a_provider_key_needs_a_source_a_kind_and_an_id() {
    for bad in ["tmdb", "tmdb-movie", "-movie-550", "tmdb--550"] {
        assert!(!poster_key_is_valid(bad), "`{bad}` must be rejected");
    }
}

/// In a title key the marker is only ever the last part, and only once —
/// and with nothing before it, there is no title left for it to mark.
#[test]
fn the_backdrop_marker_may_only_end_a_title_key() {
    assert!(poster_key_is_valid("title-terra-bg"));
    for bad in [
        "title-bg-terra",
        "title-terra-bg-x",
        "title-bg",
        "title-bg-bg",
    ] {
        assert!(!poster_key_is_valid(bad), "`{bad}` must be rejected");
    }
}

/// A title key's slug must be exactly what `slug` produces, so that a
/// reader can trust it in a path the same way as a provider key.
#[test]
fn a_title_key_whose_slug_could_not_have_come_from_slug_is_refused() {
    for bad in [
        "title-terra--x",
        "title-terra-x-",
        "title-Terra",
        "title-terra.x",
        "title-terra/x",
    ] {
        assert!(!poster_key_is_valid(bad), "`{bad}` must be rejected");
    }
}
