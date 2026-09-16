//! The names the real library actually uses.
//!
//! Taken verbatim from `/media/andre/Storage18TB/andre/Videos/xstream`: a
//! language prefix, underscores standing in for colons the filesystem will
//! not take, a release year, a region tag, and an episode title after the
//! `SxxEyy`. Anything the parser misreads here becomes wrong metadata in the
//! channel, where correcting it costs a re-upload.

use mlib_spec::filename::parse_filename;

#[test]
fn an_episode_of_the_series_in_the_library() {
    let guess = parse_filename("DE - Spartacus_ Das Haus Ashur (2025) (US) - S01E01 - Dominus.mkv")
        .expect("this name should parse");

    assert_eq!(guess.season, Some(1));
    assert_eq!(guess.episode, Some(1));
}

#[test]
fn every_episode_of_that_season_is_numbered_distinctly() {
    let seen: Vec<(Option<u32>, Option<u32>)> = (1..=8)
        .map(|n| {
            let name =
                format!("DE - Spartacus_ Das Haus Ashur (2025) (US) - S01E{n:02} - Folge.mkv");
            let guess = parse_filename(&name).expect(&name);
            (guess.season, guess.episode)
        })
        .collect();

    assert_eq!(seen.len(), 8);
    for (index, (season, episode)) in seen.iter().enumerate() {
        assert_eq!(*season, Some(1), "episode {}", index + 1);
        assert_eq!(*episode, Some(index as u32 + 1), "episode {}", index + 1);
    }
}

/// Films in that folder are loose files with a language prefix and a year.
#[test]
fn the_films_in_the_library_give_up_a_title_and_a_year() {
    for (name, title, year) in [
        ("DE - Anaconda (2025).mkv", "Anaconda", 2025),
        (
            "DE - The Wrecking Crew (2026).mkv",
            "The Wrecking Crew",
            2026,
        ),
        ("Krieg der Sterne (1977).mkv", "Krieg der Sterne", 1977),
        ("Blood & Sinners (2025).mkv", "Blood & Sinners", 2025),
    ] {
        let guess = parse_filename(name).unwrap_or_else(|| panic!("{name} should parse"));
        assert_eq!(guess.year, Some(year), "{name}");
        assert_eq!(guess.season, None, "{name} is a film, not an episode");
        assert!(
            guess.title.contains(title),
            "{name} gave title {:?}, wanted something containing {title:?}",
            guess.title
        );
    }
}

/// A film must never be mistaken for an episode: that would file it under a
/// show that does not exist, and `add` would ask for a season it has none of.
#[test]
fn a_film_is_not_read_as_an_episode() {
    for name in [
        "DE - 22 Bahnen (2025).mkv",
        "DE - LEGO Disney_ Die Eiskönigin_ Operation Papageientaucher (2025).mkv",
        "DE - Die Schule der magischen Tiere 4 (2025).mkv",
    ] {
        let guess = parse_filename(name).unwrap_or_else(|| panic!("{name} should parse"));
        assert_eq!(guess.season, None, "{name}");
        assert_eq!(guess.episode, None, "{name}");
    }
}
