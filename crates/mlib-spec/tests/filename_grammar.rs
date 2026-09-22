use mlib_spec::filename::parse_filename;

#[test]
fn movie_with_id_and_edition() {
    let g = parse_filename("Dune Part Two (2024) {tmdb-693134} - IMAX.mkv").unwrap();
    assert_eq!(
        (g.title.as_str(), g.year, g.ids.tmdb),
        ("Dune Part Two", Some(2024), Some(693134))
    );
    assert_eq!(g.extra.as_deref(), Some("IMAX"));
    assert_eq!(g.ext, "mkv");
    assert!(!g.is_episode());
}

#[test]
fn jellyfin_style_bracket_id() {
    let g = parse_filename("Interstellar (2014) [tmdbid-157336].mkv").unwrap();
    assert_eq!(g.ids.tmdb, Some(157336));
    assert_eq!(g.title, "Interstellar");
}

#[test]
fn episode_forms() {
    let g = parse_filename("Severance (2022) - s02e01 {tvdb-371980}.mkv").unwrap();
    assert_eq!(
        (g.title.as_str(), g.year, g.season, g.episode),
        ("Severance", Some(2022), Some(2), Some(1))
    );
    assert_eq!(g.ids.tvdb, Some(371980));

    let g = parse_filename("Show (2020) - s01e01-e02.mp4").unwrap();
    assert_eq!((g.episode, g.episode_end), (Some(1), Some(2)));

    let g = parse_filename("The Simpsons - S24E03 - Adventures in Baby-Getting.mkv").unwrap();
    assert_eq!(
        (g.title.as_str(), g.season, g.episode),
        ("The Simpsons", Some(24), Some(3))
    );
    assert_eq!(g.extra.as_deref(), Some("Adventures in Baby-Getting"));
}

#[test]
fn scene_release_junk_is_stripped() {
    let g = parse_filename("The.Movie.2021.2160p.WEB-DL.x265-GROUP.mkv").unwrap();
    assert_eq!((g.title.as_str(), g.year), ("The Movie", Some(2021)));

    let g = parse_filename("Some.Show.S03E12.1080p.BluRay.x264-NAME.mkv").unwrap();
    assert_eq!(
        (g.title.as_str(), g.season, g.episode),
        ("Some Show", Some(3), Some(12))
    );
}

#[test]
fn absolute_numbering_and_fallback() {
    let g = parse_filename("One Piece - 1075 - The Title.mkv").unwrap();
    assert_eq!((g.title.as_str(), g.abs), ("One Piece", Some(1075)));
    assert!(g.is_episode());

    let g = parse_filename("home video.mp4").unwrap();
    assert_eq!(g.title, "home video");
    assert_eq!(g.year, None);
    assert!(parse_filename(".mkv").is_none());
}

#[test]
fn junk_words_inside_titles_are_not_stripped() {
    for (name, title, year) in [
        ("Internal Affairs (1990).mkv", "Internal Affairs", 1990),
        ("Multi-Facial (1995).mkv", "Multi-Facial", 1995),
        ("Proper Villains (2020).mkv", "Proper Villains", 2020),
    ] {
        let g = parse_filename(name).unwrap();
        assert_eq!((g.title.as_str(), g.year), (title, Some(year)), "{name}");
    }
}

#[test]
fn scene_episode_title_and_trailing_id_survive() {
    let g = parse_filename("Some.Show.S01E01.Pilot.1080p.WEB-DL.mkv").unwrap();
    assert_eq!(
        (g.season, g.episode, g.extra.as_deref()),
        (Some(1), Some(1), Some("Pilot"))
    );
    let g = parse_filename("Movie (2020) 1080p {tmdb-99}.mkv").unwrap();
    assert_eq!((g.title.as_str(), g.ids.tmdb), ("Movie", Some(99)));
    let g = parse_filename("Fahrenheit 451 - 2018.mkv").unwrap();
    assert_eq!((g.title.as_str(), g.year), ("Fahrenheit 451", Some(2018)));
}

/// A number inside the title itself, before or after the real year, is never
/// mistaken for the year the parenthesised group actually gives.
#[test]
fn a_number_inside_the_title_is_not_mistaken_for_the_year() {
    for (name, title, year) in [
        (
            "2001 A Space Odyssey (1968).mkv",
            "2001 A Space Odyssey",
            1968,
        ),
        ("Blade Runner 2049 (2017).mkv", "Blade Runner 2049", 2017),
    ] {
        let g = parse_filename(name).unwrap();
        assert_eq!((g.title.as_str(), g.year), (title, Some(year)), "{name}");
    }
}

/// With no show name in front of it, a bare episode code reads as a plain
/// title instead of an episode: nothing to strip it off of.
#[test]
fn a_bare_episode_code_with_no_show_name_is_not_read_as_an_episode() {
    let g = parse_filename("S01E01.mkv").unwrap();
    assert_eq!(g.title, "S01E01");
    assert!(!g.is_episode());
}

/// The show name can sit directly against `SxxEyy` with a plain space and no
/// year, for both a single episode and a range.
#[test]
fn episode_code_with_space_separator_and_no_year_parses() {
    let g = parse_filename("The Office S01E01.mkv").unwrap();
    assert_eq!(
        (g.title.as_str(), g.season, g.episode, g.episode_end),
        ("The Office", Some(1), Some(1), None)
    );

    let g = parse_filename("Breaking Bad S05E14-E16.mkv").unwrap();
    assert_eq!(
        (g.title.as_str(), g.season, g.episode, g.episode_end),
        ("Breaking Bad", Some(5), Some(14), Some(16))
    );
}

/// Absolute episode numbers keep their leading zeros as plain magnitude, at
/// both ends of the 2-4 digit width the grammar accepts.
#[test]
fn absolute_numbering_strips_leading_zeros() {
    for (name, abs) in [
        ("Anime Show - 001.mkv", 1),
        ("Anime Show - 042.mkv", 42),
        ("Anime Show - 0100.mkv", 100),
    ] {
        let g = parse_filename(name).unwrap();
        assert_eq!((g.title.as_str(), g.abs), ("Anime Show", Some(abs)), "{name}");
    }
}

/// Junk stripping only starts after a year or an episode code anchors it, so
/// a name with neither keeps its release-group junk right in the title. The
/// id tag is still pulled out, because that runs as its own step first.
#[test]
fn junk_with_no_year_or_episode_anchor_stays_in_the_title() {
    let g = parse_filename("Film - 1080p - BluRay - x265 [tmdb-693134].mkv").unwrap();
    assert_eq!(g.title, "Film - 1080p - BluRay - x265");
    assert_eq!(g.ids.tmdb, Some(693134));
}

/// The id tag pattern only recognises `{}` and `[]`; a parenthesised
/// `(imdb-...)` is not an id tag and is left as ordinary title text.
#[test]
fn a_parenthesised_provider_id_is_not_recognized() {
    let g = parse_filename("Film (imdb-tt15239678).mkv").unwrap();
    assert!(g.ids.is_empty());
    assert_eq!(g.title, "Film (imdb-tt15239678)");
}
