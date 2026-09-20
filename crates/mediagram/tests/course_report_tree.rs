//! The dry-run shows the shape a course will be uploaded with.
//!
//! A flat table of chapter numbers was unreadable for a 162-lesson course and
//! actively misleading: chapter numbers are unique across the whole course,
//! so two sections' "chapter 1" appear as 2 and 5 with nothing saying which
//! section either came from. Grouping by folder shows what a person will
//! recognise.

use std::path::PathBuf;

use mediagram::course::report::dry_run_table;
use mediagram::course::walk::{Course, Document, Lesson};

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

fn document(rel_path: &str, chapter: u32, number: u32, title: &str) -> Document {
    Document {
        path: PathBuf::from(rel_path).join(format!("{number}. {title}.pdf")),
        rel_path: rel_path.to_string(),
        chapter,
        chapter_title: Some(title.to_string()),
        number,
        title: Some(title.to_string()),
    }
}

fn course_of(lessons: Vec<Lesson>) -> Course {
    Course {
        lessons,
        documents: vec![],
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

    let text = dry_run_table("Geldhochschule", "geldhochschule", &course_of(lessons)).join("\n");

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

    let text = dry_run_table("C", "c", &course_of(lessons)).join("\n");

    assert!(text.contains("3 lesson(s)"), "{text}");
    assert!(text.contains("2 folder(s)"), "{text}");
}

#[test]
fn a_lesson_at_the_root_is_shown_under_the_course_itself() {
    let lessons = vec![lesson("", 1, 1, "Einzelvideo")];

    let text = dry_run_table("C", "c", &course_of(lessons)).join("\n");

    assert!(text.contains("Einzelvideo"), "{text}");
    assert!(text.contains("(course root)"), "{text}");
}

#[test]
fn the_course_and_its_id_are_still_stated() {
    let text = dry_run_table("Geldhochschule", "geldhochschule", &Course::default()).join("\n");

    assert!(text.contains("Geldhochschule"));
    assert!(text.contains("geldhochschule"));
}

/// A handout carries the number of the lesson it sits beside, so the two rows
/// are identical but for the mark that says which is which.
#[test]
fn a_document_is_marked_apart_from_the_lesson_it_shares_a_number_with() {
    let walked = Course {
        lessons: vec![lesson("Kapitel", 1, 2, "Signal")],
        documents: vec![document("Kapitel", 1, 2, "Signal")],
    };

    let text = dry_run_table("C", "c", &walked).join("\n");

    assert!(text.contains("L   2  Signal"), "{text}");
    assert!(text.contains("D   2  Signal"), "{text}");
    assert!(text.contains("(1 lesson(s), 1 document(s))"), "{text}");
}

/// The layout that was invisible before: a folder holding no video at all.
#[test]
fn a_folder_of_documents_alone_is_shown_with_its_documents() {
    let walked = Course {
        lessons: vec![lesson("Kapitel", 1, 1, "Einstieg")],
        documents: vec![
            document("Ressourcen", 2, 1, "Arbeitsbuch"),
            document("Ressourcen", 2, 2, "Checkliste"),
        ],
    };

    let text = dry_run_table("C", "c", &walked).join("\n");

    assert!(text.contains("Ressourcen  (0 lesson(s), 2 document(s))"), "{text}");
    assert!(text.contains("Arbeitsbuch") && text.contains("Checkliste"), "{text}");
    assert!(text.contains("1 lesson(s), 2 document(s) across 2 folder(s)"), "{text}");
}

/// A course of pure video reads exactly as it did before documents existed.
#[test]
fn a_course_without_documents_says_nothing_about_them() {
    let text = dry_run_table("C", "c", &course_of(vec![lesson("A", 1, 1, "One")])).join("\n");
    assert!(!text.contains("document"), "{text}");
}

/// A handout must read as belonging to the lesson above it. Sorting the mark
/// alphabetically put every `D` row above its `L` row, which made a handout
/// look like it came first.
#[test]
fn a_handout_is_listed_under_its_lesson_not_above_it() {
    let walked = Course {
        lessons: vec![
            lesson("Kapitel", 1, 1, "Einstieg"),
            lesson("Kapitel", 1, 2, "Ausstieg"),
        ],
        documents: vec![document("Kapitel", 1, 1, "Einstieg")],
    };

    let rows: Vec<String> = dry_run_table("C", "c", &walked)
        .into_iter()
        .filter(|line| line.starts_with("  L") || line.starts_with("  D"))
        .collect();

    assert_eq!(
        rows,
        vec![
            "  L   1  Einstieg".to_string(),
            "  D   1  Einstieg".to_string(),
            "  L   2  Ausstieg".to_string(),
        ]
    );
}
