use rusqlite::Connection;

use super::{follow_library, mark_round_imported, round_imported};
use crate::state::open::migrate;

fn state() -> Connection {
    let conn = Connection::open_in_memory().unwrap();
    migrate(&conn).unwrap();
    conn
}

#[test]
fn a_fresh_device_has_heard_no_round() {
    let conn = state();
    assert!(!round_imported(&conn).unwrap());
    follow_library(&conn, "household").unwrap();
    assert!(!round_imported(&conn).unwrap());
}

#[test]
fn a_round_of_the_followed_library_counts() {
    let conn = state();
    follow_library(&conn, "household").unwrap();
    mark_round_imported(&conn, "household").unwrap();
    assert!(round_imported(&conn).unwrap());
}

/// Choosing another library is joining another household, whose names the
/// last library's rounds never brought.
#[test]
fn switching_libraries_waits_for_a_round_of_the_new_one() {
    let conn = state();
    follow_library(&conn, "first").unwrap();
    mark_round_imported(&conn, "first").unwrap();

    follow_library(&conn, "second").unwrap();
    assert!(!round_imported(&conn).unwrap());
    mark_round_imported(&conn, "second").unwrap();
    assert!(round_imported(&conn).unwrap());

    follow_library(&conn, "first").unwrap();
    assert!(
        !round_imported(&conn).unwrap(),
        "only the latest round is remembered"
    );
}

/// A device installed before the followed library was recorded follows the
/// one it has always synced with, so any imported round counts.
#[test]
fn a_device_from_before_the_followed_mark_counts_any_round() {
    let conn = state();
    mark_round_imported(&conn, "household").unwrap();
    assert!(round_imported(&conn).unwrap());
}
