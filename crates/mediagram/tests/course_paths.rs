//! A lesson remembers the folders it came from.
//!
//! Chapter numbers are assigned uniquely across a course, because sections
//! routinely number their own chapters from 1 and the identity
//! (collection, chapter, lesson) has to stay distinct. That renumbering is
//! correct and also destroys the shape: "1. Trading" under one section and
//! "1. Grundlagen" under another end up as chapters 2 and 5 with nothing
//! saying which section either belonged to.
//!
//! The path is what carries the shape, so the walk records it.

use std::fs;
use std::path::Path;

use mediagram::course::walk::walk_course;

/// Builds a course tree; each entry is a path relative to the root.
fn course(files: &[&str]) -> tempfile::TempDir {
    let dir = tempfile::tempdir().unwrap();
    for file in files {
        let full = dir.path().join(file);
        fs::create_dir_all(full.parent().unwrap()).unwrap();
        fs::write(&full, b"not really a video").unwrap();
    }
    dir
}

fn paths_of(root: &Path) -> Vec<String> {
    walk_course(root)
        .unwrap()
        .into_iter()
        .map(|lesson| lesson.rel_path)
        .collect()
}

#[test]
fn a_lesson_carries_the_folders_between_it_and_the_course_root() {
    let dir = course(&["Ausbildung Trading/1. Grundlagen/1. Trading/1. Einführung.mp4"]);

    assert_eq!(
        paths_of(dir.path()),
        vec!["Ausbildung Trading/1. Grundlagen/1. Trading"]
    );
}

/// The shape that broke the old model: uneven depth in one course.
#[test]
fn depth_may_differ_between_branches_of_one_course() {
    let dir = course(&[
        "Basislektionen/1. Start/Begrüßung.mp4",
        "Ausbildung Trading/1. Grundlagen/1. Trading/1. Einführung.mp4",
        "Der erleuchtete Investor/Teil 1.mp4",
    ]);

    let mut paths = paths_of(dir.path());
    paths.sort();
    assert_eq!(
        paths,
        vec![
            "Ausbildung Trading/1. Grundlagen/1. Trading",
            "Basislektionen/1. Start",
            "Der erleuchtete Investor",
        ]
    );
}

/// One folder in the real course holds twenty videos beside a subfolder.
#[test]
fn a_folder_may_hold_videos_and_subfolders_at_once() {
    let dir = course(&[
        "3. Signal/1. Signal.mp4",
        "3. Signal/14. Exkurs/1. Vertiefung.mp4",
    ]);

    let mut paths = paths_of(dir.path());
    paths.sort();
    assert_eq!(paths, vec!["3. Signal", "3. Signal/14. Exkurs"]);
}

/// A video sitting at the course root belongs to no folder at all.
#[test]
fn a_lesson_at_the_root_has_an_empty_path() {
    let dir = course(&["Einzelvideo.mp4"]);

    assert_eq!(paths_of(dir.path()), vec![""]);
}

/// The separator is the spec's, not the platform's: this is a label inside a
/// course, and it has to mean the same thing wherever it is read.
#[test]
fn the_separator_is_always_a_forward_slash() {
    let dir = course(&["A/B/C/x.mp4"]);

    let path = paths_of(dir.path()).remove(0);
    assert_eq!(path, "A/B/C");
    assert!(!path.contains('\\'));
}

/// Whatever the walk produces has to survive the caption codec, which refuses
/// anything that could be aimed at a filesystem.
#[test]
fn every_path_the_walk_produces_is_a_valid_caption_path() {
    let dir = course(&[
        "Basislektionen/1. Start/Begrüßung.mp4",
        "Ausbildung Trading/1. Grundlagen/3. Signal (Short)/4. PJM-Signal.mp4",
        "Der erleuchtete Investor/Teil 1.mp4",
    ]);

    for lesson in walk_course(dir.path()).unwrap() {
        if lesson.rel_path.is_empty() {
            continue;
        }
        assert!(!lesson.rel_path.starts_with('/'), "{}", lesson.rel_path);
        assert!(!lesson.rel_path.contains(".."), "{}", lesson.rel_path);
        assert!(!lesson.rel_path.contains("//"), "{}", lesson.rel_path);
        assert!(
            lesson.rel_path.split('/').count() <= 16,
            "{}",
            lesson.rel_path
        );
    }
}
