use super::*;
use rusqlite::Connection;

fn conn_with_credits() -> Connection {
    let conn = Connection::open_in_memory().unwrap();
    for stmt in mlib_spec::schema::migrations_up_to(mlib_spec::schema::SCHEMA_VERSION) {
        conn.execute(stmt, []).unwrap();
    }
    conn
}

fn row(
    person_id: u64,
    name: &str,
    role: Option<&str>,
    dept: &str,
    ord: u32,
    profile: Option<&str>,
) -> CreditRow {
    CreditRow {
        person_id,
        name: name.to_string(),
        role: role.map(str::to_string),
        dept: dept.to_string(),
        ord,
        profile_path: profile.map(str::to_string),
    }
}

#[test]
fn upsert_replaces_a_titles_whole_credit_list() {
    let conn = conn_with_credits();
    upsert(
        &conn,
        Kind::Movie,
        550,
        &[
            row(1, "Lead", Some("Hero"), "cast", 0, Some("/a.jpg")),
            row(9, "A Director", Some("Director"), "crew", 1, None),
        ],
    )
    .unwrap();
    assert!(has(&conn, Kind::Movie, 550).unwrap());
    assert!(!has(&conn, Kind::Movie, 999).unwrap());

    // A second fetch with a shorter cast list must not leave the first
    // fetch's extra row behind.
    upsert(
        &conn,
        Kind::Movie,
        550,
        &[row(1, "Lead", Some("Hero"), "cast", 0, Some("/a.jpg"))],
    )
    .unwrap();
    let count: i64 = conn
        .query_row(
            "SELECT COUNT(*) FROM credits WHERE source='tmdb' AND kind='movie' AND id=550",
            [],
            |r| r.get(0),
        )
        .unwrap();
    assert_eq!(count, 1);
}

#[test]
fn portraits_are_deduplicated_across_titles_and_keyed_by_person() {
    let conn = conn_with_credits();
    upsert(
        &conn,
        Kind::Movie,
        1,
        &[row(
            1,
            "Recurring",
            Some("A"),
            "cast",
            0,
            Some("/shared.jpg"),
        )],
    )
    .unwrap();
    upsert(
        &conn,
        Kind::Movie,
        2,
        &[row(
            1,
            "Recurring",
            Some("B"),
            "cast",
            0,
            Some("/shared.jpg"),
        )],
    )
    .unwrap();

    let found = portraits(&conn).unwrap();
    assert_eq!(found.len(), 1);
    assert_eq!(found[0].key, "tmdb-person-1");
    assert_eq!(found[0].path, "/shared.jpg");
    assert_eq!(found[0].backdrop_width, Some(PORTRAIT_WIDTH));
}

#[test]
fn a_credit_with_no_profile_is_not_a_portrait() {
    let conn = conn_with_credits();
    upsert(
        &conn,
        Kind::Movie,
        1,
        &[row(1, "No Photo", None, "cast", 0, None)],
    )
    .unwrap();
    assert!(portraits(&conn).unwrap().is_empty());
}

#[test]
fn a_malformed_profile_path_is_rejected() {
    let conn = conn_with_credits();
    upsert(
        &conn,
        Kind::Movie,
        1,
        &[row(1, "Odd", None, "cast", 0, Some("/../etc/passwd"))],
    )
    .unwrap();
    assert!(portraits(&conn).unwrap().is_empty());
}

#[test]
fn an_index_predating_credits_reads_as_no_portraits() {
    let conn = Connection::open_in_memory().unwrap();
    for stmt in mlib_spec::schema::migrations_up_to(8) {
        conn.execute(stmt, []).unwrap();
    }
    assert!(portraits(&conn).unwrap().is_empty());
}

#[test]
fn a_write_that_fails_partway_leaves_the_previous_cast_whole() {
    let conn = conn_with_credits();
    upsert(
        &conn,
        Kind::Movie,
        550,
        &[row(1, "Lead", Some("Hero"), "cast", 0, None)],
    )
    .unwrap();

    // Two rows at the same position: the second insert fails after the delete
    // and the first insert already ran.
    let failed = upsert(
        &conn,
        Kind::Movie,
        550,
        &[
            row(2, "New", Some("Hero"), "cast", 0, None),
            row(3, "Clash", Some("Villain"), "cast", 0, None),
        ],
    );
    assert!(failed.is_err());
    let names: Vec<String> = conn
        .prepare("SELECT name FROM credits WHERE kind='movie' AND id=550 ORDER BY ord")
        .unwrap()
        .query_map([], |r| r.get(0))
        .unwrap()
        .collect::<Result<_, _>>()
        .unwrap();
    assert_eq!(names, vec!["Lead".to_string()]);
    // And the connection is usable afterwards: no savepoint left open.
    upsert(
        &conn,
        Kind::Movie,
        551,
        &[row(1, "Lead", None, "cast", 0, None)],
    )
    .unwrap();
}
