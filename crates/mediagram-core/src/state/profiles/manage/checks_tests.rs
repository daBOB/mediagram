//! The checks on their own, against a real store; `manage_tests.rs` holds
//! the operations built from them to the web's order case by case.

use rusqlite::Connection;

use super::*;
use crate::state::open::migrate;

const T: i64 = 1_700_000_000_000;

fn store() -> Connection {
    let conn = Connection::open_in_memory().unwrap();
    migrate(&conn).unwrap();
    conn
}

fn add(conn: &Connection, name: &str, role: NewProfile) -> String {
    role_rows::insert(conn, name, &role, T).unwrap().unwrap().id
}

fn grown_up<'a>(pin: Option<&'a str>, admin: bool) -> NewProfile<'a> {
    NewProfile {
        pin,
        admin,
        ..Default::default()
    }
}

fn kid(parent_id: &str) -> NewProfile<'_> {
    NewProfile {
        kids: true,
        parent_id: Some(parent_id),
        ..Default::default()
    }
}

fn manager(conn: &Connection, now: i64) -> ProfileManager<'_> {
    ProfileManager { conn, now }
}

/// Whether any wrong-PIN count is held, which only a compared PIN writes.
fn counting(conn: &Connection) -> bool {
    conn.prepare("SELECT 1 FROM state_meta WHERE key = 'pin_wait'")
        .unwrap()
        .exists([])
        .unwrap()
}

/// Sync knows a viewer by the normalised name, so a second profile
/// answering to it would merge with the first everywhere.
#[test]
fn a_new_name_must_be_storable_and_answer_to_nobody_here() {
    let conn = store();
    add(&conn, "André", grown_up(None, false));
    let m = manager(&conn, T);
    assert_eq!(m.unusable("   ").unwrap(), Some(Invalid));
    for taken in [" andré ", "ANDRÉ", "Andre\u{301}"] {
        assert_eq!(m.unusable(taken).unwrap(), Some(NameTaken), "{taken:?}");
    }
    assert_eq!(m.unusable("Bea").unwrap(), None);
}

#[test]
fn inserting_answers_done_only_for_a_name_that_was_stored() {
    let conn = store();
    let m = manager(&conn, T);
    assert_eq!(m.insert(" \t ", &NewProfile::default()).unwrap(), Invalid);
    assert!(m.rows().unwrap().is_empty());
    assert_eq!(m.insert("  Bea  ", &NewProfile::default()).unwrap(), Done);
    assert_eq!(m.rows().unwrap()[0].name, "Bea");
}

#[test]
fn somebody_has_to_be_there_before_anything_else_is_asked() {
    let conn = store();
    let admin = add(&conn, "André", grown_up(Some("1234"), true));
    let mut m = manager(&conn, T);
    assert_eq!(
        m.check("nobody", "1234", Action::CreateKid, None, false)
            .unwrap(),
        Some(NotFound)
    );
    assert_eq!(
        m.check(&admin, "0000", Action::Remove, Some("nobody"), false)
            .unwrap(),
        Some(NotFound)
    );
    assert!(!counting(&conn), "no PIN was compared");
}

/// A kid has no PIN to prove anything with, so its refusal comes before a
/// PIN is compared or counted.
#[test]
fn a_kid_acting_is_refused_before_any_pin_is_compared() {
    let conn = store();
    let admin = add(&conn, "André", grown_up(Some("1234"), true));
    let mia = add(&conn, "Mia", kid(&admin));
    let mut m = manager(&conn, T);
    assert_eq!(
        m.check(&mia, "0000", Action::CreateKid, None, false)
            .unwrap(),
        Some(NotAllowed)
    );
    assert!(!counting(&conn));
}

#[test]
fn a_grown_up_proves_its_pin_and_then_the_rule_decides() {
    let conn = store();
    let admin = add(&conn, "André", grown_up(Some("1234"), true));
    let bea = add(&conn, "Bea", grown_up(Some("5678"), false));
    let mut m = manager(&conn, T);
    assert_eq!(
        m.check(&bea, "1234", Action::CreateGrownUp, None, false)
            .unwrap(),
        Some(WrongPin)
    );
    assert_eq!(
        m.check(&bea, "5678", Action::CreateGrownUp, None, false)
            .unwrap(),
        Some(NotAllowed),
        "only the admin adds a grown-up"
    );
    assert_eq!(
        m.check(&admin, "1234", Action::CreateGrownUp, None, false)
            .unwrap(),
        None
    );
}

/// A grown-up from before PINs has none to prove; setting its first one is
/// the call that asks with `unproven`, and only the rule is left.
#[test]
fn a_grown_up_without_a_pin_is_asked_for_one_unless_unproven() {
    let conn = store();
    let old = add(&conn, "André", grown_up(None, false));
    let mut m = manager(&conn, T);
    assert_eq!(
        m.check(&old, "", Action::SetPin, Some(&old), false)
            .unwrap(),
        Some(NoPin)
    );
    assert_eq!(
        m.check(&old, "", Action::SetPin, Some(&old), true).unwrap(),
        None
    );
    assert!(!counting(&conn));
}

/// Each wrong guess is stored before its answer leaves — a new call sees
/// it — and the fifth starts a minute in which no PIN, not even the right
/// one, is compared.
#[test]
fn five_wrong_pins_in_a_row_start_a_wait_that_runs_out() {
    let conn = store();
    add(&conn, "André", grown_up(Some("1234"), false));
    let row = |conn: &Connection| role_rows::load(conn).unwrap().remove(0);
    for guess in ["0000", "12345", "abcd", "", "4321"] {
        let m = manager(&conn, T);
        assert_eq!(
            m.prove(&row(&conn), guess).unwrap(),
            Some(WrongPin),
            "{guess:?}"
        );
    }
    let m = manager(&conn, T + 1_000);
    assert_eq!(
        m.prove(&row(&conn), "1234").unwrap(),
        Some(Wait { seconds: 59 })
    );

    let m = manager(&conn, T + 60_000);
    assert_eq!(m.prove(&row(&conn), "1234").unwrap(), None);
    assert!(
        !counting(&conn),
        "the wait ran out and the right PIN cleared the count"
    );
}

#[test]
fn the_right_pin_starts_the_count_again() {
    let conn = store();
    add(&conn, "André", grown_up(Some("1234"), false));
    let row = role_rows::load(&conn).unwrap().remove(0);
    let m = manager(&conn, T);
    for _ in 0..4 {
        assert_eq!(m.prove(&row, "0000").unwrap(), Some(WrongPin));
    }
    assert_eq!(m.prove(&row, "1234").unwrap(), None);
    for _ in 0..4 {
        assert_eq!(m.prove(&row, "0000").unwrap(), Some(WrongPin));
    }
    assert_eq!(m.prove(&row, "1234").unwrap(), None, "no wait after four");
}

#[test]
fn a_call_runs_against_the_store_at_the_clock_it_was_given() {
    let dir = tempfile::tempdir().unwrap();
    let db = StateDb::new(dir.path().to_path_buf());
    let outcome = db.manage_at(T, |m| {
        assert_eq!(m.now, T);
        m.insert("André", &grown_up(Some("1234"), true))
    });
    assert_eq!(outcome, Done);
    let claimed: i64 = db
        .with(|c| c.query_row("SELECT admin_claimed_at FROM profiles", [], |r| r.get(0)))
        .unwrap();
    assert_eq!(claimed, T);
}

/// Nothing on this surface throws: a store that cannot be opened, or a call
/// whose read fails, is answered `Invalid`.
#[test]
fn a_store_that_fails_answers_invalid() {
    let dir = tempfile::tempdir().unwrap();
    let occupied = dir.path().join("not-a-directory");
    std::fs::write(&occupied, b"held file").unwrap();
    let unopenable = StateDb::new(occupied);
    assert_eq!(
        unopenable.manage(|m| m.insert("André", &NewProfile::default())),
        Invalid
    );

    let db = StateDb::new(dir.path().join("state"));
    let failing = db.manage(|m| {
        m.conn.execute("DROP TABLE profiles", [])?;
        m.insert("André", &NewProfile::default())
    });
    assert_eq!(failing, Invalid);
}
