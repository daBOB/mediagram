use super::*;
use crate::test_fakes::upload::sample_caption;
use mlib_spec::caption::Kind;

fn set(kind: Kind, container: &str) -> SetRow {
    let mut row = SetRow::from_caption(&sample_caption("setA", 10, 1), 0);
    row.kind = kind;
    row.container = container.to_string();
    row
}

fn key(kind: &str, container: &str) -> (String, String) {
    (kind.to_string(), container.to_string())
}

/// Only a file with a de/en text track counts towards "with de/en text":
/// picture-only subtitles, or no probe at all, give a backfill nothing to
/// send.
#[test]
fn matches_add_up_per_kind_and_container() {
    let mut totals = BTreeMap::new();
    let movie = set(Kind::Movie, "mkv");
    let episode = set(Kind::Ep, "mp4");

    add_match(&mut totals, Some(&movie), 100, Some((1, 0)));
    add_match(&mut totals, Some(&movie), 50, Some((0, 2)));
    add_match(&mut totals, Some(&episode), 7, None);

    let movies = totals[&key("movie", "mkv")];
    assert_eq!(
        (movies.count, movies.bytes, movies.with_de_en_text),
        (2, 150, 1)
    );
    let episodes = totals[&key("ep", "mp4")];
    assert_eq!(
        (episodes.count, episodes.bytes, episodes.with_de_en_text),
        (1, 7, 0)
    );
    assert_eq!(totals.len(), 2);
}

#[test]
fn a_match_naming_no_candidate_set_is_in_no_total() {
    let mut totals = BTreeMap::new();

    add_match(&mut totals, None, 100, Some((1, 0)));

    assert!(totals.is_empty());
}

#[test]
fn sizes_are_given_in_binary_gigabytes() {
    assert_eq!(gb(1 << 30), 1.0);
    assert_eq!(gb(3 << 29), 1.5);
}
