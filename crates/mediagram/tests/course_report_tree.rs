//! The dry-run shows the shape a course will be uploaded with.
//!
//! A flat table of chapter numbers was unreadable for a 162-lesson course and
//! actively misleading: chapter numbers are unique across the whole course,
//! so two sections' "chapter 1" appear as 2 and 5 with nothing saying which
//! section either came from. Grouping by folder shows what a person will
//! recognise.

use std::path::PathBuf;

use mediagram::course::report::dry_run_table;
use mediagram::course::walk::Lesson;

fn lesson(rel_path: &str, chapter: u32, number: u32, title: &str) -> Lesson {
    Lesson {
        path: PathBuf::from(rel_path).join(format!("{number}. {title}.mp4")),
        rel_path: rel_path.to_string(),
        chapter,
        chapter_title: Some(title.to_string()),
        lesson: number,
        title: Some(title.to_string()),
    }
}

#[test]
fn lessons_are_shown_under_the_folder_they_came_from() {
    let lessons = vec![
        lesson("Basislektionen/1. Start", 1, 1, "Begrüßung"),
        lesson(
            "Ausbildung Trading/1. Grundlagen/1. Trading",
            2,
            1,
            "Einführung",
        ),
        lesson(
            "Ausbildung Trading/1. Grundlagen/1. Trading",
            2,
            2,
            "Definition",
        ),
    ];

    let text = dry_run_table("Geldhochschule", "geldhochschule", &lessons).join("\n");

    assert!(text.contains("Basislektionen/1. Start"), "{text}");
    assert!(
        text.contains("Ausbildung Trading/1. Grundlagen/1. Trading"),
        "{text}"
    );
    // Each folder is named once, not once per lesson.
    assert_eq!(
        text.matches("Ausbildung Trading/1. Grundlagen/1. Trading")
            .count(),
        1
    );
}

#[test]
fn the_summary_counts_lessons_and_folders() {
    let lessons = vec![
        lesson("A", 1, 1, "One"),
        lesson("A", 1, 2, "Two"),
        lesson("B", 2, 1, "Three"),
    ];

    let text = dry_run_table("C", "c", &lessons).join("\n");

    assert!(text.contains("3 lesson(s)"), "{text}");
    assert!(text.contains("2 folder(s)"), "{text}");
}

#[test]
fn a_lesson_at_the_root_is_shown_under_the_course_itself() {
    let lessons = vec![lesson("", 1, 1, "Einzelvideo")];

    let text = dry_run_table("C", "c", &lessons).join("\n");

    assert!(text.contains("Einzelvideo"), "{text}");
    assert!(text.contains("(course root)"), "{text}");
}

#[test]
fn the_course_and_its_id_are_still_stated() {
    let text = dry_run_table("Geldhochschule", "geldhochschule", &[]).join("\n");

    assert!(text.contains("Geldhochschule"));
    assert!(text.contains("geldhochschule"));
}
