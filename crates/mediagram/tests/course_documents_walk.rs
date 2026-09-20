//! Walking the documents in a course, and the guarantee that walking them
//! changes nothing about the lessons.
//!
//! A course folder holds PDFs in two places: beside a lesson, as its handout,
//! and in a folder holding no video at all. Both are uploaded; neither may
//! disturb a lesson number, because chapter plus lesson is what decides
//! whether a lesson is skipped as already uploaded.

use std::fs;
use std::path::Path;

use mediagram::course::walk::{Course, Lesson, walk_course};

fn tree(files: &[&str]) -> tempfile::TempDir {
    let dir = tempfile::tempdir().unwrap();
    for f in files {
        let path = dir.path().join(f);
        fs::create_dir_all(path.parent().unwrap()).unwrap();
        fs::write(&path, b"bytes").unwrap();
    }
    dir
}

/// A lesson's identity and everything shown about it. What must not move.
fn identities(lessons: &[Lesson]) -> Vec<(u32, u32, String, String)> {
    lessons
        .iter()
        .map(|l| {
            (
                l.chapter,
                l.lesson,
                l.title.clone().unwrap_or_default(),
                l.rel_path.clone(),
            )
        })
        .collect()
}

fn documents_of(root: &Path) -> Vec<(u32, u32, String)> {
    walk_course(root)
        .unwrap()
        .documents
        .into_iter()
        .map(|d| (d.chapter, d.number, d.title.unwrap_or_default()))
        .collect()
}

/// **The regression this feature is most able to cause.**
///
/// `assign_unique_numbers` renumbers a whole group the moment two declared
/// numbers collide. Had document-only folders joined the chapter numbering,
/// every chapter after one would shift, no lesson would match what the index
/// already holds, and a re-run of `add-course` would upload the entire course
/// a second time.
#[test]
fn documents_anywhere_in_the_tree_leave_every_lesson_number_alone() {
    let lessons_only = tree(&[
        "1. Grundlagen/01 Einstieg.mp4",
        "1. Grundlagen/02 Ausstieg.mp4",
        "1. Grundlagen/3. Signal/01 Trend.mp4",
        "2. Praxis/01 Erste.mp4",
    ]);
    let with_documents = tree(&[
        "1. Grundlagen/01 Einstieg.mp4",
        "1. Grundlagen/01 Einstieg.pdf",
        "1. Grundlagen/02 Ausstieg.mp4",
        "1. Grundlagen/3. Signal/01 Trend.mp4",
        "1. Grundlagen/3. Signal/Spickzettel.pdf",
        "2. Praxis/01 Erste.mp4",
        "Ressourcen/Arbeitsbuch.pdf",
        "Ressourcen/Vorlagen/Checkliste.pdf",
    ]);

    let bare = walk_course(lessons_only.path()).unwrap();
    let full = walk_course(with_documents.path()).unwrap();

    assert_eq!(
        identities(&bare.lessons),
        identities(&full.lessons),
        "adding documents moved a lesson"
    );
    assert!(bare.documents.is_empty());
    assert_eq!(full.documents.len(), 4);
}

/// The whole point of numbering a document the way a lesson is numbered: a
/// handout lands on the row beside the lesson it belongs to, and nothing had
/// to pair the two to make that happen.
#[test]
fn a_handout_takes_the_number_of_the_lesson_it_sits_beside() {
    let dir = tree(&[
        "Kapitel/01 Einstieg.mp4",
        "Kapitel/02 Signal.mp4",
        "Kapitel/02 Signal.pdf",
        "Kapitel/03 Ausstieg.mp4",
    ]);

    let course = walk_course(dir.path()).unwrap();

    let lesson = course
        .lessons
        .iter()
        .find(|l| l.title.as_deref() == Some("Signal"))
        .unwrap();
    let handout = &course.documents[0];
    assert_eq!(
        (handout.chapter, handout.number),
        (lesson.chapter, lesson.lesson)
    );
}

/// The layout a walk could not even see before: a folder with no video in it
/// was never visited, so its contents did not exist.
#[test]
fn a_folder_holding_only_documents_is_still_a_chapter() {
    let dir = tree(&[
        "1. Grundlagen/01 Einstieg.mp4",
        "Ressourcen/Arbeitsbuch.pdf",
        "Ressourcen/02 Vorlage.pdf",
    ]);

    let course = walk_course(dir.path()).unwrap();

    let resources: Vec<_> = course
        .documents
        .iter()
        .filter(|d| d.rel_path == "Ressourcen")
        .collect();
    assert_eq!(resources.len(), 2);
    assert!(
        resources.iter().all(|d| d.chapter > course.lessons[0].chapter),
        "a document-only folder is numbered after the video chapters"
    );
    assert_eq!(
        resources[0].chapter_title.as_deref(),
        Some("Ressourcen"),
        "it is a chapter with a name like any other"
    );
}

/// Documents are numbered in their own sequence, so a handout sharing a
/// number with its lesson is the intended case rather than a collision. Two
/// documents in one folder still may not share one.
#[test]
fn document_numbers_are_unique_within_a_chapter() {
    let dir = tree(&[
        "Kapitel/1. Definition.pdf",
        "Kapitel/1. Einfuehrung.pdf",
        "Kapitel/2. Varianten.pdf",
    ]);

    let numbers: Vec<u32> = documents_of(dir.path()).iter().map(|(_, n, _)| *n).collect();
    let unique: std::collections::BTreeSet<u32> = numbers.iter().copied().collect();
    assert_eq!(numbers.len(), unique.len(), "{numbers:?}");
}

#[test]
fn two_walks_of_a_course_with_documents_agree() {
    let dir = tree(&["K/01 A.mp4", "K/01 A.pdf", "Extra/b.pdf", "Extra/a.pdf"]);
    assert_eq!(
        walk_course(dir.path()).unwrap(),
        walk_course(dir.path()).unwrap()
    );
}

#[test]
fn a_course_of_documents_alone_is_not_empty() {
    let dir = tree(&["Handbuch.pdf"]);
    let course: Course = walk_course(dir.path()).unwrap();
    assert!(!course.is_empty());
    assert!(course.lessons.is_empty());
    assert_eq!(course.documents.len(), 1);
}

#[test]
fn an_uppercase_extension_is_still_a_document() {
    let dir = tree(&["K/01 Lesson.mp4", "K/Handout.PDF"]);
    assert_eq!(walk_course(dir.path()).unwrap().documents.len(), 1);
}
