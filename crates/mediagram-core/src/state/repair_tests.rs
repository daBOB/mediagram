use rusqlite::Connection;

use super::add_missing_kids_column;
use crate::state::open::migrate;
use crate::state::schema;

fn has_kids(conn: &Connection) -> bool {
    conn.prepare("SELECT 1 FROM pragma_table_info('profiles') WHERE name = 'kids'")
        .unwrap()
        .exists([])
        .unwrap()
}

/// A file holding this schema's statements up to `applied`, that says it is
/// at `reported`.
fn file(applied: i64, reported: i64) -> Connection {
    let conn = Connection::open_in_memory().unwrap();
    for statement in schema::migrations_up_to(applied) {
        conn.execute(statement, []).unwrap();
    }
    conn.pragma_update(None, "user_version", reported).unwrap();
    conn.execute(
        "INSERT INTO profiles(id, name, created_at) VALUES ('p1', 'André', 0)",
        [],
    )
    .unwrap();
    conn
}

/// A pre-release build numbered another step 3, so its file reports 3
/// without `kids`. The runner skips the column as applied, and a later
/// migration reading it rolls back — unless the column is added first.
#[test]
fn a_version_three_file_without_kids_gains_it_as_an_ordinary_profile() {
    assert!(migrate(&file(2, 3)).is_err());

    let conn = file(2, 3);
    add_missing_kids_column(&conn).unwrap();
    let kids: i64 = conn
        .query_row("SELECT kids FROM profiles WHERE id = 'p1'", [], |r| {
            r.get(0)
        })
        .unwrap();
    assert_eq!(kids, 0);
    migrate(&conn).unwrap();
}

/// Below version 3 the column is that migration's to add; adding it first
/// would make the migration fail as a duplicate.
#[test]
fn a_file_below_version_three_is_left_for_its_migration() {
    let conn = file(2, 2);
    add_missing_kids_column(&conn).unwrap();
    assert!(!has_kids(&conn));
    migrate(&conn).unwrap();
    assert!(has_kids(&conn));
}

#[test]
fn a_file_that_has_the_column_is_untouched() {
    let conn = file(schema::VERSION, schema::VERSION);
    add_missing_kids_column(&conn).unwrap();
    add_missing_kids_column(&conn).unwrap();
    assert!(has_kids(&conn));
}

/// Checked on every open, a brand-new file's included, before any table
/// exists.
#[test]
fn an_empty_file_is_no_error() {
    let conn = Connection::open_in_memory().unwrap();
    add_missing_kids_column(&conn).unwrap();
    migrate(&conn).unwrap();
    assert!(has_kids(&conn));
}
