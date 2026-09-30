use mlib_spec::caption::{Caption, Episode, Kind, Part};
use mlib_spec::filename::Guess;
use mlib_spec::ids::ProviderIds;

use super::*;

#[allow(clippy::too_many_arguments)]
fn set(
    id: &str,
    kind: Kind,
    show: Option<&str>,
    title: Option<&str>,
    year: Option<u16>,
    season: Option<u32>,
    episode: Option<Episode>,
    total: u64,
    dur: Option<u32>,
) -> SetRow {
    SetRow::from_caption(
        &Caption {
            t: kind,
            ids: ProviderIds::default(),
            cid: None,
            show: show.map(String::from),
            chap: None,
            path: None,
            title: title.map(String::from),
            year,
            s: season,
            e: episode,
            abs: None,
            q: None,
            hdr: None,
            container: "mp4".into(),
            vcodec: None,
            acodec: None,
            alang: vec![],
            slang: vec![],
            dur,
            variant: None,
            set: id.into(),
            part: Part {
                i: 0,
                n: 1,
                off: 0,
                len: 0,
                sha256: String::new(),
            },
            total,
        },
        1_700_000_000,
    )
}

fn file(path: &str, size: u64) -> SourceFile {
    SourceFile {
        path: path.into(),
        size,
        is_mp4: path.ends_with(".mp4"),
        duration: None,
        guess: None,
    }
}

fn ep_guess(show: &str, season: u32, episode: u32) -> Guess {
    Guess {
        title: show.into(),
        season: Some(season),
        episode: Some(episode),
        ..Default::default()
    }
}

fn movie_guess(title: &str, year: u16) -> Guess {
    Guess {
        title: title.into(),
        year: Some(year),
        ..Default::default()
    }
}

fn verdict_of<'a>(matches: &'a [Match], path: &str) -> &'a Verdict {
    &matches.iter().find(|m| m.path.to_str() == Some(path)).unwrap().verdict
}

#[test]
fn exact_size_names_the_one_set() {
    let sets = vec![set("A", Kind::Movie, None, Some("Alpha"), Some(2001), None, None, 1000, Some(5400))];
    let matches = match_sources(&[file("alpha.mkv", 1000)], &sets);
    assert_eq!(*verdict_of(&matches, "alpha.mkv"), Verdict::Matched("A".into()));
}

#[test]
fn a_shared_size_is_ambiguous() {
    let sets = vec![
        set("A", Kind::Movie, None, Some("Alpha"), None, None, None, 1000, None),
        set("B", Kind::Movie, None, Some("Beta"), None, None, None, 1000, None),
    ];
    let matches = match_sources(&[file("either.mkv", 1000)], &sets);
    assert_eq!(*verdict_of(&matches, "either.mkv"), Verdict::Ambiguous);
}

#[test]
fn a_bundled_set_is_still_matchable() {
    // match_source never reads subtitle_files; a bundle existing already
    // changes nothing about whether its own set can still be matched.
    let sets = vec![set("A", Kind::Ep, Some("Show"), None, None, Some(1), Some(Episode::Single(1)), 1000, None)];
    let matches = match_sources(&[file("s01e01.mkv", 1000)], &sets);
    assert_eq!(*verdict_of(&matches, "s01e01.mkv"), Verdict::Matched("A".into()));
}

#[test]
fn a_size_that_matches_some_total_never_falls_back() {
    // The file's real match is A, by size. Its name and duration would
    // otherwise name the unrelated decoy B, but a resolved size match
    // must never be second-guessed by the fallback.
    let sets = vec![
        set("A", Kind::Movie, None, Some("Unrelated"), None, None, None, 1000, Some(100)),
        set("B", Kind::Movie, None, Some("Alpha"), Some(2001), None, None, 2000, Some(5400)),
    ];
    let mut f = file("alpha.2001.mp4", 1000);
    f.duration = Some(5400.0);
    f.guess = Some(movie_guess("Alpha", 2001));
    let matches = match_sources(&[f], &sets);
    assert_eq!(*verdict_of(&matches, "alpha.2001.mp4"), Verdict::Matched("A".into()));
}

#[test]
fn fallback_needs_show_and_episode_within_two_seconds() {
    let sets = vec![set("A", Kind::Ep, Some("Show"), None, None, Some(1), Some(Episode::Single(3)), 999, Some(2530))];
    let mut f = file("show.s01e03.mp4", 1234);
    f.duration = Some(2531.0);
    f.guess = Some(ep_guess("Show", 1, 3));
    let matches = match_sources(&[f], &sets);
    assert_eq!(*verdict_of(&matches, "show.s01e03.mp4"), Verdict::Fallback("A".into()));
}

#[test]
fn fallback_needs_title_and_year_for_a_film() {
    let sets = vec![set("A", Kind::Movie, None, Some("Alpha"), Some(2001), None, None, 999, Some(5400))];
    let mut f = file("alpha.2001.mp4", 1234);
    f.duration = Some(5401.0);
    f.guess = Some(movie_guess("Alpha", 2001));
    let matches = match_sources(&[f], &sets);
    assert_eq!(*verdict_of(&matches, "alpha.2001.mp4"), Verdict::Fallback("A".into()));
}

#[test]
fn an_unrelated_lecture_of_the_same_duration_is_not_matched() {
    let sets = vec![set("A", Kind::Movie, None, Some("Alpha"), Some(2001), None, None, 999, Some(2530))];
    let mut f = file("lecture-03.mp4", 1234);
    f.duration = Some(2530.0);
    f.guess = Some(Guess {
        title: "lecture-03".into(),
        ..Default::default()
    });
    let matches = match_sources(&[f], &sets);
    assert_eq!(*verdict_of(&matches, "lecture-03.mp4"), Verdict::Unmatched);
}

#[test]
fn a_fallback_ambiguous_between_two_candidates_is_not_matched() {
    let sets = vec![
        set("A", Kind::Ep, Some("Show"), None, None, Some(1), Some(Episode::Single(3)), 999, Some(2530)),
        set("B", Kind::Ep, Some("Show"), None, None, Some(1), Some(Episode::Single(3)), 998, Some(2531)),
    ];
    let mut f = file("show.s01e03.mp4", 1234);
    f.duration = Some(2530.5);
    f.guess = Some(ep_guess("Show", 1, 3));
    let matches = match_sources(&[f], &sets);
    assert_eq!(*verdict_of(&matches, "show.s01e03.mp4"), Verdict::Unmatched);
}

#[test]
fn two_files_naming_one_set_both_become_a_conflict() {
    let sets = vec![set("A", Kind::Movie, None, Some("Alpha"), None, None, None, 1000, None)];
    let matches = match_sources(&[file("copy1.mkv", 1000), file("copy2.mkv", 1000)], &sets);
    // Same size, same unique set: each is individually a size-unique match
    // until the second file's identical claim downgrades both.
    assert_eq!(*verdict_of(&matches, "copy1.mkv"), Verdict::Conflict("A".into()));
    assert_eq!(*verdict_of(&matches, "copy2.mkv"), Verdict::Conflict("A".into()));
}
