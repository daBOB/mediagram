use super::{cut_from_file_name, split_number_and_title};

#[test]
fn outline_prefix_takes_its_last_segment() {
    assert_eq!(
        split_number_and_title("6.10 – Deep stage 1"),
        (Some(10), Some("Deep stage 1".to_string()))
    );
    assert_eq!(
        split_number_and_title("1.2.3 Intro"),
        (Some(3), Some("Intro".to_string()))
    );
}

#[test]
fn a_dot_before_a_title_is_not_an_outline() {
    assert_eq!(
        split_number_and_title("10. Moving Averages"),
        (Some(10), Some("Moving Averages".to_string()))
    );
    assert_eq!(
        split_number_and_title("01 Getting Started"),
        (Some(1), Some("Getting Started".to_string()))
    );
    assert_eq!(
        split_number_and_title("Appendix"),
        (None, Some("Appendix".to_string()))
    );
}

#[test]
fn release_tags_after_the_year_name_one_canonical_cut() {
    let cases = [
        ("Blade.Runner.1982.Final.Cut.2160p.UHD.mkv", "Final Cut"),
        (
            "Apocalypse.Now.1979.Directors.Cut.1080p.mkv",
            "Director's Cut",
        ),
        ("Apocalypse Now (1979) Director's Cut.mkv", "Director's Cut"),
        ("Apocalypse_Now_1979_DC_1080p.mkv", "Director's Cut"),
        ("Kingdom.of.Heaven.2005.Directors-Cut.mkv", "Director's Cut"),
        ("Aliens.1986.EXTENDED.EDITION.1080p.mkv", "Extended"),
        ("Das.Boot.1981.German.Langfassung.mkv", "Extended"),
        ("Robocop.1987.UNCUT.mkv", "Uncut"),
        ("Robocop 1987 Ungeschnitten.mkv", "Uncut"),
        ("Hostel.2005.Unrated.720p.mkv", "Uncut"),
        ("Jaws [1975] [Remastered].mkv", "Remastered"),
    ];
    for (name, cut) in cases {
        assert_eq!(cut_from_file_name(name), Some(cut), "{name}");
    }
}

#[test]
fn a_name_without_a_year_is_read_whole() {
    assert_eq!(
        cut_from_file_name("Blade Runner Final Cut.mkv"),
        Some("Final Cut")
    );
}

#[test]
fn theatrical_and_untagged_names_are_the_standard_cut() {
    for name in [
        "Blade.Runner.1982.Theatrical.1080p.mkv",
        "Das.Boot.1981.Kinofassung.mkv",
        "Blade.Runner.1982.2160p.mkv",
        "Blade Runner.mkv",
    ] {
        assert_eq!(cut_from_file_name(name), None, "{name}");
    }
}

#[test]
fn tags_in_the_title_or_inside_a_longer_word_are_not_cuts() {
    for name in [
        "DC.League.of.Super-Pets.2022.1080p.mkv",
        "The.Extended.Family.2019.1080p.mkv",
        "Justice.League.2017.DCEU.1080p.mkv",
        "Overextended.mkv",
        "Final.Destination.2000.mkv",
        "The.Directors.2020.mkv",
        "Movie.2010.Final.Countdown.mkv",
    ] {
        assert_eq!(cut_from_file_name(name), None, "{name}");
    }
}
