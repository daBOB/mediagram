use rusqlite::Connection;

use super::table_exists;

#[test]
fn only_a_table_of_that_name_is_found() {
    let conn = Connection::open_in_memory().unwrap();
    conn.execute_batch(
        "CREATE TABLE credits(id INTEGER);
         CREATE INDEX credits_by_id ON credits(id);",
    )
    .unwrap();
    assert!(table_exists(&conn, "credits").unwrap());
    assert!(!table_exists(&conn, "credits_by_id").unwrap());
    assert!(!table_exists(&conn, "artwork").unwrap());
}
