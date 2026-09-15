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

#[test]
fn nesting_below_a_chapter_still_belongs_to_that_chapter() {
    let dir = tree(&["01 Basics/extras/01 Bonus.mp4", "01 Basics/01 Main.mp4"]);
    let lessons = walk_course(dir.path()).unwrap();
    assert!(lessons.iter().all(|l| l.chapter == 1), "{lessons:?}");
    assert_eq!(lessons.len(), 2);
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
