//! Reading a course off disk. The numbers and titles a walk infers are what
//! the dry-run table shows and what identity is built from, so they have to
//! be predictable and stable across runs.

use std::fs;

use mediagram::course::walk::{Lesson, walk_course};

fn tree(files: &[&str]) -> tempfile::TempDir {
    let dir = tempfile::tempdir().unwrap();
    for f in files {
        let path = dir.path().join(f);
        fs::create_dir_all(path.parent().unwrap()).unwrap();
        fs::write(&path, b"video").unwrap();
    }
    dir
}

fn summary(lessons: &[Lesson]) -> Vec<(u32, String, u32, String)> {
    lessons
        .iter()
        .map(|l| {
            (
                l.chapter,
                l.chapter_title.clone().unwrap_or_default(),
                l.lesson,
                l.title.clone().unwrap_or_default(),
            )
        })
        .collect()
}

#[test]
fn chapters_are_subdirectories_and_lessons_are_the_files_inside() {
    let dir = tree(&[
        "01 Getting Started/01 Install.mp4",
        "01 Getting Started/02 Hello World.mp4",
        "02 Ownership/01 Moves.mp4",
    ]);

    let lessons = walk_course(dir.path()).unwrap();

    assert_eq!(
        summary(&lessons),
        vec![
            (1, "Getting Started".into(), 1, "Install".into()),
            (1, "Getting Started".into(), 2, "Hello World".into()),
            (2, "Ownership".into(), 1, "Moves".into()),
        ]
    );
}

#[test]
fn a_flat_folder_is_one_chapter() {
    let dir = tree(&["01 First.mp4", "02 Second.mp4"]);
    let lessons = walk_course(dir.path()).unwrap();
    assert_eq!(lessons.len(), 2);
    assert!(lessons.iter().all(|l| l.chapter == 1));
    assert_eq!(lessons[1].lesson, 2);
}

#[test]
fn non_video_files_are_ignored() {
    let dir = tree(&[
        "01 Intro.mp4",
        "notes.pdf",
        "subtitles.srt",
        "cover.jpg",
        "archive.zip",
    ]);
    let lessons = walk_course(dir.path()).unwrap();
    assert_eq!(lessons.len(), 1);
    assert_eq!(lessons[0].title.as_deref(), Some("Intro"));
}

/// Unnumbered entries still need stable numbers, and they must not collide
/// with the explicit ones.
#[test]
fn unnumbered_entries_are_numbered_after_the_explicit_ones() {
    let dir = tree(&["02 Second.mp4", "Appendix.mp4", "01 First.mp4"]);

    let lessons = walk_course(dir.path()).unwrap();

    assert_eq!(
        summary(&lessons)
            .iter()
            .map(|(_, _, n, t)| (*n, t.clone()))
            .collect::<Vec<_>>(),
        vec![
            (1, "First".into()),
            (2, "Second".into()),
            (3, "Appendix".into())
        ]
    );
}

#[test]
fn two_walks_of_the_same_tree_agree() {
    let dir = tree(&["b.mp4", "a.mp4", "03 c.mp4"]);
    assert_eq!(
        walk_course(dir.path()).unwrap(),
        walk_course(dir.path()).unwrap()
    );
}

#[test]
fn separators_and_extensions_are_stripped_from_titles() {
    let dir = tree(&["01 - Getting_Started.the.basics.mp4"]);
    let lessons = walk_course(dir.path()).unwrap();
    assert_eq!(
        lessons[0].title.as_deref(),
        Some("Getting Started the basics")
    );
}

/// This once asserted that a nested folder's files joined their parent
/// chapter. A real course disproved it: sub-folders each numbered their own
/// files from 1, so merging them collapsed several lessons onto one identity
/// and all but the first were skipped as already uploaded. A folder holding
/// videos is now its own chapter.
#[test]
fn a_nested_folder_becomes_its_own_chapter() {
    let dir = tree(&["01 Basics/extras/01 Bonus.mp4", "01 Basics/01 Main.mp4"]);

    let lessons = walk_course(dir.path()).unwrap();

    assert_eq!(lessons.len(), 2);
    let chapters: std::collections::BTreeSet<u32> = lessons.iter().map(|l| l.chapter).collect();
    assert_eq!(chapters.len(), 2, "parent and nested folder are separate");
    let bonus = lessons
        .iter()
        .find(|l| l.title.as_deref() == Some("Bonus"))
        .unwrap();
    assert!(
        bonus.chapter_title.as_deref().unwrap().contains("extras"),
        "{:?}",
        bonus.chapter_title
    );
}

#[test]
fn an_empty_folder_yields_no_lessons() {
    let dir = tempfile::tempdir().unwrap();
    assert!(walk_course(dir.path()).unwrap().is_empty());
}

#[test]
fn a_missing_folder_is_an_error_not_an_empty_course() {
    assert!(walk_course(std::path::Path::new("/definitely/not/here")).is_err());
}

#[test]
fn a_title_that_is_only_a_number_keeps_a_usable_title() {
    let dir = tree(&["01.mp4", "02.mp4"]);
    let lessons = walk_course(dir.path()).unwrap();
    assert_eq!(lessons.len(), 2);
    assert_eq!(lessons[0].lesson, 1);
    assert_eq!(
        lessons[0].title, None,
        "a bare number is a number, not a title"
    );
}

/// Real courses nest deeper than course/chapter/lesson. The directory that
/// directly holds the videos is the chapter, wherever it sits, because that
/// is the grouping a person actually made.
#[test]
fn the_directory_holding_the_videos_is_the_chapter_however_deep() {
    let dir = tree(&[
        "Ausbildung/1. Grundlagen/3. Signal/01 Einstieg.mp4",
        "Ausbildung/1. Grundlagen/3. Signal/02 Ausstieg.mp4",
        "Ausbildung/1. Grundlagen/4. Risiko/01 Stop.mp4",
        "Basis/1. Start/01 Hallo.mp4",
    ]);

    let lessons = walk_course(dir.path()).unwrap();

    let chapters: std::collections::BTreeSet<u32> = lessons.iter().map(|l| l.chapter).collect();
    assert_eq!(chapters.len(), 3, "three leaf folders, three chapters");
    assert_eq!(lessons.len(), 4);
}

/// The chapter number is a single integer, so the path is what carries the
/// hierarchy a reader needs to make sense of it.
#[test]
fn a_chapter_title_keeps_the_path_that_gives_it_meaning() {
    let dir = tree(&["Ausbildung/1. Grundlagen/3. Signal/01 Einstieg.mp4"]);
    let lessons = walk_course(dir.path()).unwrap();
    let title = lessons[0].chapter_title.clone().unwrap();
    assert!(
        title.contains("Grundlagen") && title.contains("Signal"),
        "{title}"
    );
}

/// The defect a real course exposed: several sub-folders each contributing a
/// file numbered 1 collapsed onto one identity, so all but the first would be
/// skipped as already uploaded. Numbers must be unique within a chapter.
#[test]
fn lesson_numbers_are_unique_within_a_chapter() {
    let dir = tree(&[
        "Kapitel/1. Definition.mp4",
        "Kapitel/1. Einfuehrung.mp4",
        "Kapitel/2. Varianten.mp4",
    ]);

    let lessons = walk_course(dir.path()).unwrap();

    let numbers: Vec<u32> = lessons.iter().map(|l| l.lesson).collect();
    let unique: std::collections::BTreeSet<u32> = numbers.iter().copied().collect();
    assert_eq!(
        numbers.len(),
        unique.len(),
        "duplicate lesson numbers: {numbers:?}"
    );
}

#[test]
fn no_two_lessons_in_a_course_share_an_identity() {
    let dir = tree(&[
        "A/1. one.mp4",
        "A/1. two.mp4",
        "B/1. one.mp4",
        "B/sub/1. one.mp4",
    ]);

    let lessons = walk_course(dir.path()).unwrap();

    let mut seen = std::collections::BTreeSet::new();
    for l in &lessons {
        assert!(
            seen.insert((l.chapter, l.lesson)),
            "identity ({}, {}) repeats",
            l.chapter,
            l.lesson
        );
    }
    assert_eq!(lessons.len(), 4);
}

/// Files directly in the course root and files in folders can coexist.
#[test]
fn a_course_mixing_loose_files_and_folders_still_works() {
    let dir = tree(&["intro.mp4", "Kapitel/01 Erste.mp4"]);
    let lessons = walk_course(dir.path()).unwrap();
    assert_eq!(lessons.len(), 2);
    let mut seen = std::collections::BTreeSet::new();
    for l in &lessons {
        assert!(seen.insert((l.chapter, l.lesson)));
    }
}
