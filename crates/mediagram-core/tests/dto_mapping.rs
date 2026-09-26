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
        quality: None,
        hdr: None,
        duration: Some(3000),
        total: 1_000_000,
        part_count: 1,
        created_at: 1_781_568_000,
    }
}

fn movie_with_tmdb(tmdb: Option<u64>) -> PlayableSet {
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
        summary_from(&movie_with_tmdb(Some(603)))
            .poster_key
            .as_deref(),
        Some("tmdb-movie-603")
    );
    assert_eq!(summary_from(&movie_with_tmdb(None)).poster_key, None);
}

/// A course lesson has no TMDB id, but its course name still names a stable
/// key — the same one `mediagram artwork` would resolve to.
#[test]
fn a_course_lesson_with_no_tmdb_id_is_keyed_by_its_course_slug() {
    let s = PlayableSet {
        kind: "tut".into(),
        tmdb: None,
        show: Some("Terra X".into()),
        ..playable_set_fixture()
    };
    assert_eq!(
        summary_from(&s).poster_key.as_deref(),
        Some("title-terra-x")
    );
}

/// A standalone documentary carries no `show`, only its own `title`.
#[test]
fn a_standalone_documentary_is_keyed_by_its_own_title() {
    let s = PlayableSet {
        kind: "docu".into(),
        tmdb: None,
        show: None,
        title: Some("Deep Ocean".into()),
        ..playable_set_fixture()
    };
    assert_eq!(
        summary_from(&s).poster_key.as_deref(),
        Some("title-deep-ocean")
    );
}

/// A manually-entered film or episode with no TMDB id is not this stable —
/// two of them sharing a title would collide onto one key — so it gets none.
#[test]
fn a_manual_movie_with_no_tmdb_id_gets_no_title_key() {
    let s = PlayableSet {
        kind: "movie".into(),
        tmdb: None,
        title: Some("Home Video".into()),
        ..playable_set_fixture()
    };
    assert_eq!(summary_from(&s).poster_key, None);
}

#[test]
fn a_show_s_poster_key_is_filed_under_tv_not_the_episode() {
    let s = PlayableSet {
        tmdb: Some(95396),
        ..playable_with(Some("1"))
    };
    assert_eq!(
        summary_from(&s).poster_key.as_deref(),
        Some("tmdb-tv-95396")
    );
}

/// A minimal `PlayableSet` with every field at its empty value and
/// `set_id`, `kind`, and `container` filled. Used as a base for building
/// test fixtures.
fn playable_set_fixture() -> PlayableSet {
    PlayableSet {
        set_id: "01SET0000000000000000001".into(),
        kind: "ep".into(),
        title: None,
        show: None,
        chap: None,
        path: None,
        season: None,
        episode: None,
        tmdb: None,
        year: None,
        container: "mkv".into(),
        vcodec: None,
        acodec: None,
        quality: None,
        hdr: None,
        duration: None,
        total: 0,
        part_count: 1,
        created_at: 1_781_568_000,
    }
}

/// The detail screen prints what a file is, in the order the web player
/// prints it. Every one of these columns is in the index already; the DTO
/// simply did not carry them.
#[test]
fn a_summary_carries_what_the_file_is() {
    let set = PlayableSet {
        container: "mkv".into(),
        vcodec: Some("hevc".into()),
        acodec: Some("eac3".into()),
        quality: Some("1080p".into()),
        hdr: Some("HDR10".into()),
        ..playable_set_fixture()
    };

    let summary = summary_from(&set);

    assert_eq!(summary.container, "mkv");
    assert_eq!(summary.vcodec.as_deref(), Some("hevc"));
    assert_eq!(summary.acodec.as_deref(), Some("eac3"));
    assert_eq!(summary.quality.as_deref(), Some("1080p"));
    assert_eq!(summary.hdr.as_deref(), Some("HDR10"));
}

/// `SDR` is the absence of a fact rather than a fact, and the web player
/// drops it rather than printing it on every card. The DTO carries whatever
/// the index holds and lets the surface decide, so this pins that the column
/// survives the trip — deciding is `hdrLabel`'s job, on the other side.
#[test]
fn an_sdr_title_still_reports_its_dynamic_range() {
    let set = PlayableSet {
        hdr: Some("SDR".into()),
        ..playable_set_fixture()
    };

    assert_eq!(summary_from(&set).hdr.as_deref(), Some("SDR"));
}

/// A franchise, series type and status are never on the row itself — the
/// index keeps them per title, and a listing attaches them (see
/// `store::list_sets`) — so this flattening alone always answers `None`.
#[test]
fn a_franchise_and_series_type_are_not_on_the_row_itself() {
    let summary = summary_from(&playable_set_fixture());
    assert_eq!(summary.collection_id, None);
    assert_eq!(summary.collection_name, None);
    assert_eq!(summary.series_type, None);
    assert_eq!(summary.show_status, None);
}
