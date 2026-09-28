use super::*;
use crate::index::db;

fn db() -> Connection {
    let dir = tempfile::tempdir().unwrap();
    db::open(dir.path()).unwrap()
}

#[test]
fn a_unit_nobody_categorised_reads_as_none() {
    assert_eq!(get(&db(), "tutorials", "title-rust-course").unwrap(), None);
}

#[test]
fn setting_a_category_is_read_back() {
    let conn = db();
    set(&conn, "tutorials", "title-rust-course", Some("Trading"), 1_700_000_000).unwrap();
    assert_eq!(
        get(&conn, "tutorials", "title-rust-course").unwrap(),
        Some("Trading".to_string())
    );
}

/// Clearing keeps the row rather than deleting it — a merge needs a
/// timestamp to carry the clear to the other machine.
#[test]
fn clearing_keeps_the_row_but_reads_back_as_none() {
    let conn = db();
    set(&conn, "tutorials", "title-rust-course", Some("Trading"), 1_700_000_000).unwrap();
    set(&conn, "tutorials", "title-rust-course", None, 1_700_000_100).unwrap();

    assert_eq!(get(&conn, "tutorials", "title-rust-course").unwrap(), None);
    let rows: i64 = conn
        .query_row("SELECT COUNT(*) FROM categories", [], |row| row.get(0))
        .unwrap();
    assert_eq!(rows, 1, "a cleared category stays a row, not a deletion");
}

#[test]
fn setting_it_again_replaces_rather_than_duplicates() {
    let conn = db();
    set(&conn, "tutorials", "title-rust-course", Some("Trading"), 1_700_000_000).unwrap();
    set(&conn, "tutorials", "title-rust-course", Some("Finance"), 1_700_000_100).unwrap();

    assert_eq!(
        get(&conn, "tutorials", "title-rust-course").unwrap(),
        Some("Finance".to_string())
    );
    let rows: i64 = conn
        .query_row("SELECT COUNT(*) FROM categories", [], |row| row.get(0))
        .unwrap();
    assert_eq!(rows, 1);
}

/// A course and a documentary of the same slug are kept apart by
/// `department` — their custom art is shared today, their categories need
/// not be.
#[test]
fn a_course_and_a_documentary_of_the_same_slug_hold_separate_categories() {
    let conn = db();
    set(&conn, "tutorials", "title-everest", Some("Trading"), 1_700_000_000).unwrap();
    set(&conn, "documentaries", "title-everest", Some("Nature"), 1_700_000_000).unwrap();

    assert_eq!(
        get(&conn, "tutorials", "title-everest").unwrap(),
        Some("Trading".to_string())
    );
    assert_eq!(
        get(&conn, "documentaries", "title-everest").unwrap(),
        Some("Nature".to_string())
    );
}

#[test]
fn in_use_excludes_the_callers_own_key_and_cleared_rows() {
    let conn = db();
    set(&conn, "tutorials", "title-rust-course", Some("Trading"), 1_700_000_000).unwrap();
    set(&conn, "tutorials", "title-geldhochschule", Some("Trading"), 1_700_000_000).unwrap();
    set(&conn, "tutorials", "title-cleared-course", None, 1_700_000_000).unwrap();

    let mut in_use = in_use(&conn, "tutorials", "title-rust-course").unwrap();
    in_use.sort();
    assert_eq!(in_use, ["Trading"]);
}
