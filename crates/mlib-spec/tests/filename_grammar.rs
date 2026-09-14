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
