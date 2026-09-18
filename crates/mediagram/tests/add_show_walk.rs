//! Which files `add-show` decides are episodes, and which it refuses.
//!
//! The walk is the part that can quietly do the wrong thing: a file filed as
//! the wrong episode, or two files filed as the same one, is discovered after
//! the bytes have moved.

use std::fs;
use std::path::Path;

use mediagram::commands::add_show::{duplicate_episode, walk};

fn touch(dir: &Path, name: &str) {
    fs::create_dir_all(dir).unwrap();
    fs::write(dir.join(name), b"not really a video").unwrap();
}

#[test]
fn season_and_episode_come_from_the_name_despite_a_release_prefix() {
    let tmp = tempfile::tempdir().unwrap();
    let season = tmp.path().join("Season 1");
    touch(
        &season,
        "A+ - Star City (2026) (US) - S01E01 - The Eyes.mkv",
    );
    touch(
        &season,
        "DE - 30 Rock (2006) - S07E13 - Die letzte Sendung.mkv",
    );

    let found = walk(tmp.path()).unwrap();

    let mut pairs: Vec<(u32, u32)> = found.iter().map(|e| (e.season, e.episode)).collect();
    pairs.sort_unstable();
    assert_eq!(pairs, [(1, 1), (7, 13)]);
}

#[test]
fn season_folders_are_walked_however_they_are_named() {
    let tmp = tempfile::tempdir().unwrap();
    touch(&tmp.path().join("Staffel 2"), "Show - S02E05 - Eins.mkv");
    touch(&tmp.path().join("Season 3"), "Show - S03E01 - Zwei.mkv");

    assert_eq!(walk(tmp.path()).unwrap().len(), 2);
}

/// An extra or a trailer has no episode number, and guessing one would file
/// it over a real episode.
#[test]
fn a_file_with_no_episode_number_is_not_an_episode() {
    let tmp = tempfile::tempdir().unwrap();
    touch(tmp.path(), "Show - Behind the Scenes.mkv");
    touch(tmp.path(), "Show - S01E01 - Real.mkv");

    let found = walk(tmp.path()).unwrap();

    assert_eq!(found.len(), 1);
    assert_eq!((found[0].season, found[0].episode), (1, 1));
}

#[test]
fn files_that_are_not_video_are_ignored() {
    let tmp = tempfile::tempdir().unwrap();
    touch(tmp.path(), "Show - S01E01 - Real.mkv");
    touch(tmp.path(), "Show - S01E02 - Subs.srt");
    touch(tmp.path(), "poster.jpg");

    assert_eq!(walk(tmp.path()).unwrap().len(), 1);
}

/// A folder holding both an original and its converted copy is the ordinary
/// way this happens, and picking one by sort order would be a coin toss.
#[test]
fn two_files_claiming_one_episode_are_reported_rather_than_resolved() {
    let tmp = tempfile::tempdir().unwrap();
    touch(tmp.path(), "Show - S01E01 - Same.mkv");
    touch(tmp.path(), "Show - S01E01 - Same.mp4");
    touch(tmp.path(), "Show - S01E02 - Other.mkv");

    let found = walk(tmp.path()).unwrap();
    let (season, episode, files) = duplicate_episode(&found).expect("the clash must be reported");

    assert_eq!((season, episode), (1, 1));
    assert_eq!(files.len(), 2);
}

#[test]
fn one_file_per_episode_is_not_a_clash() {
    let tmp = tempfile::tempdir().unwrap();
    touch(tmp.path(), "Show - S01E01 - A.mkv");
    touch(tmp.path(), "Show - S01E02 - B.mkv");
    // Same episode number in a different season is a different episode.
    touch(tmp.path(), "Show - S02E01 - C.mkv");

    let found = walk(tmp.path()).unwrap();

    assert!(duplicate_episode(&found).is_none());
}

#[test]
fn a_path_that_is_not_a_directory_is_refused() {
    let tmp = tempfile::tempdir().unwrap();
    touch(tmp.path(), "Show - S01E01 - A.mkv");

    assert!(walk(&tmp.path().join("Show - S01E01 - A.mkv")).is_err());
}
