//! Re-running `add-course` after an interruption must skip what finished and
//! hand what did not to `resume`. Identity is the collection id plus the two
//! numbers, so it survives renaming or moving the course folder.

use mediagram::course::report::{Outcome, Summary, dry_run_table};
use mediagram::course::walk::Lesson;
use mediagram::index::status::SetStatus;
use mediagram::index::{db, set_lookup, set_row::SetRow, sets};
use mlib_spec::caption::{Caption, Episode, Kind, Part};
use mlib_spec::ids::ProviderIds;

fn lesson_caption(set: &str, cid: &str, chapter: u32, lesson: u32) -> Caption {
    Caption {
        t: Kind::Tut,
        ids: ProviderIds::default(),
        cid: Some(cid.into()),
        show: Some("Rust Course".into()),
        chap: Some("Ownership".into()),
        path: None,
        title: Some("Borrowing".into()),
        year: None,
        s: Some(chapter),
        e: Some(Episode::Single(lesson)),
        abs: None,
        q: None,
        hdr: None,
        container: "mp4".into(),
        vcodec: None,
        acodec: None,
        alang: vec![],
        slang: vec![],
        dur: None,
        variant: None,
        set: set.into(),
        part: Part {
            i: 0,
            n: 1,
            off: 0,
            len: 10,
            sha256: String::new(),
        },
        total: 10,
    }
}

fn index_with(rows: &[(&str, &str, u32, u32, SetStatus)]) -> (tempfile::TempDir, rusqlite::Connection) {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    for (set, cid, chapter, lesson, status) in rows {
        let row = SetRow::from_caption(&lesson_caption(set, cid, *chapter, *lesson), 1_700_000_000)
            .unwrap();
        sets::insert_set(&conn, &row).unwrap();
        sets::set_status(&conn, set, *status).unwrap();
    }
    (dir, conn)
}

#[test]
fn a_finished_lesson_is_recognised_by_its_identity() {
    let (_d, conn) = index_with(&[("01SET0000000000000000001", "rust-course", 2, 2, SetStatus::Complete)]);
    assert!(set_lookup::complete_lesson_exists(&conn, "rust-course", 2, 2).unwrap());
}

#[test]
fn a_different_chapter_or_lesson_is_a_different_thing() {
    let (_d, conn) = index_with(&[("01SET0000000000000000001", "rust-course", 2, 2, SetStatus::Complete)]);
    assert!(!set_lookup::complete_lesson_exists(&conn, "rust-course", 2, 3).unwrap());
    assert!(!set_lookup::complete_lesson_exists(&conn, "rust-course", 1, 2).unwrap());
}

/// The reason the id is carried in the caption rather than derived from the
/// title: two courses can share a chapter and lesson number.
#[test]
fn two_courses_do_not_collide() {
    let (_d, conn) = index_with(&[
        ("01SET0000000000000000001", "rust-course", 1, 1, SetStatus::Complete),
        ("01SET0000000000000000002", "go-course", 1, 1, SetStatus::Complete),
    ]);
    assert!(set_lookup::complete_lesson_exists(&conn, "rust-course", 1, 1).unwrap());
    assert!(set_lookup::complete_lesson_exists(&conn, "go-course", 1, 1).unwrap());
    assert!(!set_lookup::complete_lesson_exists(&conn, "zig-course", 1, 1).unwrap());
}

/// An interrupted lesson is `resume`'s business. Re-running the walk must
/// neither upload it again nor silently ignore it.
#[test]
fn an_unfinished_lesson_is_reported_rather_than_re_uploaded() {
    let (_d, conn) = index_with(&[("01SET0000000000000000001", "rust-course", 1, 1, SetStatus::Pending)]);

    assert!(!set_lookup::complete_lesson_exists(&conn, "rust-course", 1, 1).unwrap());
    assert_eq!(
        set_lookup::lesson_status(&conn, "rust-course", 1, 1).unwrap(),
        Some(SetStatus::Pending)
    );
}

#[test]
fn a_lesson_never_seen_has_no_status() {
    let (_d, conn) = index_with(&[]);
    assert_eq!(
        set_lookup::lesson_status(&conn, "rust-course", 1, 1).unwrap(),
        None
    );
}

#[test]
fn the_summary_tells_the_user_what_to_do_about_unfinished_lessons() {
    let lesson = Lesson {
        path: "x.mp4".into(),
        rel_path: String::new(),
        chapter: 1,
        chapter_title: None,
        lesson: 1,
        title: None,
    };
    let _ = &lesson;
    let mut summary = Summary::default();
    summary.record_lesson(Outcome::Uploaded);
    summary.record_lesson(Outcome::AlreadyDone);
    summary.record_lesson(Outcome::Pending);
    summary.record_lesson(Outcome::Failed);

    let lines = summary.lines().join("\n");
    assert!(lines.contains("1 lesson(s) uploaded, 1 already done, 1 failed"));
    assert!(lines.contains("resume"), "{lines}");
    assert!(
        !lines.contains("document"),
        "a course with no documents says nothing about them: {lines}"
    );
}

/// Documents are counted apart from lessons, because they fail apart: which
/// of the two went wrong is the first thing anyone needs to know.
#[test]
fn the_summary_counts_documents_separately() {
    let mut summary = Summary::default();
    summary.record_lesson(Outcome::Uploaded);
    summary.record_document(Outcome::Uploaded);
    summary.record_document(Outcome::Failed);

    let lines = summary.lines().join("\n");
    assert!(lines.contains("1 lesson(s) uploaded"), "{lines}");
    assert!(lines.contains("1 document(s) uploaded, 0 already done, 1 failed"), "{lines}");
    assert_eq!(summary.failed(), 1);
    assert!(summary.uploaded_anything());
}

#[test]
fn the_dry_run_table_shows_the_id_that_identity_is_built_from() {
    let lessons = vec![Lesson {
        path: "01 Install.mp4".into(),
        rel_path: String::new(),
        chapter: 1,
        chapter_title: Some("Getting Started".into()),
        lesson: 1,
        title: Some("Install".into()),
    }];
    let walked = mediagram::course::walk::Course {
        lessons,
        documents: vec![],
    };
    let table = dry_run_table("Rust Course", "rust-course", &walked).join("\n");
    assert!(table.contains("rust-course"));
    assert!(table.contains("Install"));
    // Folders, not chapter numbers: the numbers are made unique across a
    // course and no longer describe the shape anyone recognises.
    assert!(table.contains("1 lesson(s) across 1 folder(s)"));
}

/// The upload pipeline rebuilds each part's caption from the stored row, so
/// anything the row cannot express never reaches the channel. A course lesson
/// must survive that trip with its collection id and chapter intact.
#[test]
fn a_lesson_row_rebuilds_the_caption_it_came_from() {
    let original = lesson_caption("01SET0000000000000000001", "rust-course", 2, 2);
    let row = SetRow::from_caption(&original, 1_700_000_000).unwrap();

    let rebuilt = row.caption_template().unwrap();

    assert_eq!(rebuilt.t, Kind::Tut, "the kind must round-trip");
    assert_eq!(
        rebuilt.cid.as_deref(),
        Some("rust-course"),
        "collection id lost"
    );
    assert_eq!(
        rebuilt.chap.as_deref(),
        Some("Ownership"),
        "chapter title lost"
    );
    assert_eq!(rebuilt.show, original.show);
    assert_eq!(rebuilt.s, original.s);
    assert_eq!(rebuilt.e, original.e);
}

#[test]
fn a_movie_row_still_rebuilds_without_course_fields() {
    let mut movie = lesson_caption("01SET0000000000000000002", "x", 1, 1);
    movie.t = Kind::Movie;
    movie.cid = None;
    movie.chap = None;
    let row = SetRow::from_caption(&movie, 1_700_000_000).unwrap();

    let rebuilt = row.caption_template().unwrap();

    assert_eq!(rebuilt.t, Kind::Movie);
    assert_eq!(rebuilt.cid, None);
    assert_eq!(rebuilt.chap, None);
}
