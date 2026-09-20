//! Flattening a catalog row for the binding surface: the episode field is
//! JSON text on disk and two plain numbers past this boundary, and a poster
//! key is derived rather than stored.

use mediagram_core::catalog::PlayableSet;
use mediagram_core::dto::summary_from;

/// Every field at a harmless default except the one under test.
fn playable_with(episode: Option<&str>) -> PlayableSet {
    PlayableSet {
        set_id: "01SET0000000000000000001".into(),
        kind: "ep".into(),
        title: None,
        show: Some("Severance".into()),
        chap: None,
        path: None,
        season: Some(2),
        episode: episode.map(str::to_string),
        tmdb: None,
        year: Some(2025),
        container: "mkv".into(),
        vcodec: None,
        acodec: None,
        duration: Some(3000),
        total: 1_000_000,
        part_count: 1,
    }
}

fn movie_with_tmdb(tmdb: Option<i64>) -> PlayableSet {
    PlayableSet {
        kind: "movie".into(),
        tmdb,
        ..playable_with(None)
    }
}

#[test]
fn a_single_episode_flattens_to_one_number_twice() {
    let s = summary_from(&playable_with(Some("4")));
    assert_eq!(s.episode_first, Some(4));
    assert_eq!(s.episode_last, Some(4));
}

#[test]
fn a_range_flattens_to_its_bounds() {
    let s = summary_from(&playable_with(Some("[11,12]")));
    assert_eq!(s.episode_first, Some(11));
    assert_eq!(s.episode_last, Some(12));
}

#[test]
fn a_film_has_no_episode_at_all() {
    let s = summary_from(&playable_with(None));
    assert_eq!(s.episode_first, None);
    assert_eq!(s.episode_last, None);
}

/// An index written by a newer uploader, or corrupted, must not take the
/// catalog down: the set still lists, with no episode numbers.
#[test]
fn an_unparseable_episode_degrades_instead_of_failing() {
    let s = summary_from(&playable_with(Some("{\"unexpected\":true}")));
    assert_eq!(s.episode_first, None);
    assert_eq!(s.episode_last, None);
}

#[test]
fn a_poster_key_is_derived_from_kind_and_tmdb() {
    assert_eq!(
        summary_from(&movie_with_tmdb(Some(603))).poster_key.as_deref(),
        Some("tmdb-movie-603")
    );
    assert_eq!(summary_from(&movie_with_tmdb(None)).poster_key, None);
}

#[test]
fn a_show_s_poster_key_is_filed_under_tv_not_the_episode() {
    let s = PlayableSet {
        tmdb: Some(95396),
        ..playable_with(Some("1"))
    };
    assert_eq!(summary_from(&s).poster_key.as_deref(), Some("tmdb-tv-95396"));
}
