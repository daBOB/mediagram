use mlib_spec::caption::{Caption, Episode, Part};
use mlib_spec::ids::ProviderIds;

use super::*;
use crate::index::db;

fn caption(kind: Kind, show: Option<&str>, title: Option<&str>) -> Caption {
    Caption {
        t: kind,
        ids: ProviderIds::default(),
        cid: None,
        show: show.map(String::from),
        chap: None,
        path: None,
        title: title.map(String::from),
        year: None,
        s: Some(1),
        e: Some(Episode::Single(1)),
        abs: None,
        q: None,
        hdr: None,
        container: "mkv".into(),
        vcodec: None,
        acodec: None,
        alang: vec![],
        slang: vec![],
        dur: None,
        variant: None,
        set: "01CATEGORYSET00000000001".into(),
        part: Part {
            i: 0,
            n: 1,
            off: 0,
            len: 0,
            sha256: String::new(),
        },
        total: 0,
    }
}

fn row(kind: Kind, show: Option<&str>, title: Option<&str>) -> SetRow {
    SetRow::from_caption(&caption(kind, show, title), 1_700_000_000)
}

fn db() -> Connection {
    let dir = tempfile::tempdir().unwrap();
    db::open(dir.path()).unwrap()
}

#[test]
fn whitespace_is_trimmed_and_collapsed() {
    let (name, from) = normalise("  Trading   Places  ", &[]).unwrap();
    assert_eq!(name, "Trading Places");
    assert_eq!(from, None);
}

#[test]
fn an_empty_name_is_refused() {
    let error = normalise("   ", &[]).unwrap_err();
    assert!(error.to_string().contains("--clear-category"));
}

#[test]
fn other_in_any_case_is_refused() {
    for raw in ["Other", "OTHER", "other"] {
        let error = normalise(raw, &[]).unwrap_err();
        assert!(error.to_string().contains("already sit under Other"));
    }
}

#[test]
fn a_case_variant_adopts_the_spelling_already_in_use() {
    let (name, from) = normalise("trading", &["Trading".to_string()]).unwrap();
    assert_eq!(name, "Trading");
    assert_eq!(from, Some("trading".to_string()));
}

#[test]
fn an_exact_match_is_kept_with_no_adoption_note() {
    let (name, from) = normalise("Trading", &["Trading".to_string()]).unwrap();
    assert_eq!(name, "Trading");
    assert_eq!(from, None);
}

#[test]
fn setting_a_category_on_a_tutorial_writes_a_row_keyed_to_the_course() {
    let conn = db();
    let row = row(Kind::Tut, Some("Rust Course"), None);

    run(&conn, &row, Some("Trading"), false).unwrap();

    assert_eq!(
        categories::get(&conn, "tutorials", "title-rust-course").unwrap(),
        Some("Trading".to_string())
    );
}

/// A course's lesson and its document share one row: both key by the course.
#[test]
fn a_course_document_shares_its_lesson_s_category_row() {
    let conn = db();
    let lesson = row(Kind::Tut, Some("Rust Course"), None);
    run(&conn, &lesson, Some("Trading"), false).unwrap();

    let doc = row(Kind::Doc, Some("Rust Course"), None);
    let error = run(&conn, &doc, Some("Trading"), false).unwrap_err();

    assert!(error.to_string().contains("already says Trading"));
}

/// A documentary collection and a standalone documentary key apart: one by
/// its show, the other by its own title.
#[test]
fn a_collection_and_a_single_hold_separate_categories() {
    let conn = db();
    let collection = row(Kind::Docu, Some("Terra X"), None);
    let single = row(Kind::Docu, None, Some("Free Solo"));

    run(&conn, &collection, Some("Nature"), false).unwrap();
    run(&conn, &single, Some("Climbing"), false).unwrap();

    assert_eq!(
        categories::get(&conn, "documentaries", "title-terra-x").unwrap(),
        Some("Nature".to_string())
    );
    assert_eq!(
        categories::get(&conn, "documentaries", "title-free-solo").unwrap(),
        Some("Climbing".to_string())
    );
}

#[test]
fn dry_run_writes_nothing() {
    let conn = db();
    let row = row(Kind::Tut, Some("Rust Course"), None);

    run(&conn, &row, Some("Trading"), true).unwrap();

    assert_eq!(categories::get(&conn, "tutorials", "title-rust-course").unwrap(), None);
}

/// Clearing keeps the row (a merge needs a timestamp to carry the clear),
/// but a clear with nothing set is refused as a no-op.
#[test]
fn clearing_keeps_the_row_and_clearing_nothing_is_a_no_op() {
    let conn = db();
    let row = row(Kind::Tut, Some("Rust Course"), None);
    run(&conn, &row, Some("Trading"), false).unwrap();

    run(&conn, &row, None, false).unwrap();

    assert_eq!(categories::get(&conn, "tutorials", "title-rust-course").unwrap(), None);
    let rows: i64 = conn
        .query_row("SELECT COUNT(*) FROM categories", [], |r| r.get(0))
        .unwrap();
    assert_eq!(rows, 1, "a cleared category stays a row, not a deletion");

    let error = run(&conn, &row, None, false).unwrap_err();
    assert!(error.to_string().contains("has no category"));
}

#[test]
fn a_film_is_refused() {
    let conn = db();
    let row = row(Kind::Movie, None, Some("A Film"));

    let error = run(&conn, &row, Some("Anything"), false).unwrap_err();

    assert!(error.to_string().contains("categories are for courses and documentaries"));
}

#[test]
fn asking_for_the_value_it_already_has_is_a_no_op() {
    let conn = db();
    let row = row(Kind::Tut, Some("Rust Course"), None);
    run(&conn, &row, Some("Trading"), false).unwrap();

    let error = run(&conn, &row, Some("Trading"), false).unwrap_err();

    assert!(error.to_string().contains("already says Trading"));
}

#[test]
fn planned_refuses_before_any_database_is_open() {
    let error = planned(Kind::Tut, Some("Rust Course"), "Other").unwrap_err();
    assert!(error.to_string().contains("already sit under Other"));
}

#[test]
fn planned_keys_a_course_by_its_title() {
    let planned = planned(Kind::Tut, Some("Rust Course"), "Trading").unwrap();
    assert_eq!(planned.department, "tutorials");
    assert_eq!(planned.item_key, "title-rust-course");
    assert_eq!(planned.category, Some("Trading".to_string()));
}

/// `write` adopts an existing spelling `planned` could not have known
/// about, since it has no database to check `in_use` against.
#[test]
fn write_adopts_a_spelling_planned_alone_could_not_see() {
    let conn = db();
    categories::set(&conn, "tutorials", "title-geldhochschule", Some("Trading"), 1_700_000_000).unwrap();

    let planned = planned(Kind::Tut, Some("Rust Course"), "trading").unwrap();
    write(&conn, &planned).unwrap();

    assert_eq!(
        categories::get(&conn, "tutorials", "title-rust-course").unwrap(),
        Some("Trading".to_string())
    );
}
