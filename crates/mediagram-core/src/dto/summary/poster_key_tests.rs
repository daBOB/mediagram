use super::poster_key_for;

fn key(kind: &str, tmdb: Option<u64>, show: Option<&str>, title: Option<&str>) -> Option<String> {
    poster_key_for(kind, tmdb, show, title)
}

/// TMDB numbers films and series independently, so only a film is keyed
/// apart; every other kind, a course's included, is filed under `tv`.
#[test]
fn a_tmdb_id_keys_a_film_as_a_movie_and_everything_else_as_tv() {
    assert_eq!(
        key("movie", Some(603), None, None).as_deref(),
        Some("tmdb-movie-603")
    );
    for kind in ["ep", "tut", "docu"] {
        assert_eq!(
            key(kind, Some(95396), None, None).as_deref(),
            Some("tmdb-tv-95396"),
            "{kind}"
        );
    }
}

/// That art overrides a title's own, so the id wins over the course name.
#[test]
fn a_tmdb_id_wins_over_a_name() {
    assert_eq!(
        key("tut", Some(7), Some("Terra X"), Some("Lesson 1")).as_deref(),
        Some("tmdb-tv-7")
    );
}

/// A kind written by a newer uploader keys as a series, as the web player
/// keys it, and like a series gets nothing without an id.
#[test]
fn a_kind_this_build_does_not_know_keys_as_a_series() {
    assert_eq!(
        key("vr", Some(42), None, None).as_deref(),
        Some("tmdb-tv-42")
    );
    assert_eq!(key("vr", None, Some("Show"), Some("Title")), None);
}

#[test]
fn without_an_id_a_course_is_keyed_by_its_name_before_its_lessons() {
    assert_eq!(
        key("tut", None, Some("Terra X"), Some("Lesson 1")).as_deref(),
        Some("title-terra-x")
    );
    assert_eq!(
        key("docu", None, None, Some("Deep Ocean")).as_deref(),
        Some("title-deep-ocean")
    );
}

/// A film, episode or handout missing its id is not stable enough: two such
/// entries sharing a title would collide onto one key.
#[test]
fn without_an_id_nothing_but_a_course_or_documentary_is_keyed() {
    for kind in ["movie", "ep", "doc"] {
        assert_eq!(key(kind, None, Some("Show"), Some("Title")), None, "{kind}");
    }
}

#[test]
fn a_name_that_slugs_to_nothing_is_no_key() {
    assert_eq!(key("docu", None, None, Some("!!!")), None);
    assert_eq!(key("tut", None, None, None), None);
}
