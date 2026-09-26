use super::*;

fn conn_with_credits() -> Connection {
    let conn = Connection::open_in_memory().unwrap();
    for stmt in mlib_spec::schema::migrations_up_to(mlib_spec::schema::SCHEMA_VERSION) {
        conn.execute(stmt, []).unwrap();
    }
    conn
}

fn conn_predating_credits() -> Connection {
    let conn = Connection::open_in_memory().unwrap();
    for stmt in mlib_spec::schema::migrations_up_to(8) {
        conn.execute(stmt, []).unwrap();
    }
    conn
}

#[allow(clippy::too_many_arguments)] // a test fixture builder, not a public API
fn insert_credit(conn: &Connection, kind: &str, id: u64, ord: u32, person_id: u64, name: &str, role: Option<&str>, dept: &str) {
    conn.execute(
        "INSERT INTO credits(source, kind, id, ord, person_id, name, role, dept)
         VALUES ('tmdb', ?1, ?2, ?3, ?4, ?5, ?6, ?7)",
        rusqlite::params![kind, id, ord, person_id, name, role, dept],
    )
    .unwrap();
}

#[test]
fn a_titles_cast_comes_back_in_billing_order_apart_from_its_crew() {
    let conn = conn_with_credits();
    insert_credit(&conn, "movie", 550, 1, 2, "Second Billed", Some("Marla"), "cast");
    insert_credit(&conn, "movie", 550, 0, 1, "Lead", Some("The Narrator"), "cast");
    insert_credit(&conn, "movie", 550, 2, 9, "A Director", Some("Director"), "crew");

    let credits = for_title(&conn, Kind::Movie, 550).unwrap();

    assert_eq!(credits.cast.len(), 2);
    assert_eq!(credits.cast[0].name, "Lead");
    assert_eq!(credits.cast[0].role.as_deref(), Some("The Narrator"));
    assert_eq!(credits.cast[1].name, "Second Billed");
    assert_eq!(credits.crew.len(), 1);
    assert_eq!(credits.crew[0].name, "A Director");
}

#[test]
fn a_blank_role_reads_as_none() {
    let conn = conn_with_credits();
    insert_credit(&conn, "movie", 550, 0, 1, "Extra", Some("  "), "cast");
    let credits = for_title(&conn, Kind::Movie, 550).unwrap();
    assert_eq!(credits.cast[0].role, None);
}

#[test]
fn an_index_predating_credits_has_no_title_credits() {
    let conn = conn_predating_credits();
    assert_eq!(for_title(&conn, Kind::Movie, 550).unwrap(), TitleCredits::default());
}

#[test]
fn a_persons_titles_are_deduplicated_and_ordered_by_id() {
    let conn = conn_with_credits();
    insert_credit(&conn, "tv", 1399, 0, 5, "Recurring", Some("Role"), "cast");
    insert_credit(&conn, "movie", 550, 0, 5, "Recurring", Some("Role"), "cast");

    let found = for_person(&conn, 5).unwrap().expect("credited");
    assert_eq!(found.name, "Recurring");
    assert_eq!(found.title_keys, vec!["tmdb-movie-550".to_string(), "tmdb-tv-1399".to_string()]);
}

#[test]
fn a_person_nobody_credits_is_none() {
    let conn = conn_with_credits();
    assert_eq!(for_person(&conn, 999).unwrap(), None);
}

#[test]
fn an_index_predating_credits_has_no_person() {
    let conn = conn_predating_credits();
    assert_eq!(for_person(&conn, 1).unwrap(), None);
}

#[test]
fn people_matching_finds_either_spelling_of_an_umlaut() {
    let conn = conn_with_credits();
    insert_credit(&conn, "movie", 1, 0, 1, "Jürgen Vogel", None, "cast");

    for query in ["jurgen", "jürgen", "juergen"] {
        let hits = people_matching(&conn, query).unwrap();
        assert_eq!(hits.len(), 1, "query {query:?} should match");
        assert_eq!(hits[0].name, "Jürgen Vogel");
    }
}

#[test]
fn people_matching_ranks_the_most_credited_first() {
    let conn = conn_with_credits();
    insert_credit(&conn, "movie", 1, 0, 1, "Anna Busy", None, "cast");
    insert_credit(&conn, "movie", 2, 0, 1, "Anna Busy", None, "cast");
    insert_credit(&conn, "movie", 3, 0, 2, "Anna Quiet", None, "cast");

    let hits = people_matching(&conn, "anna").unwrap();
    assert_eq!(hits.len(), 2);
    assert_eq!(hits[0].name, "Anna Busy");
    assert_eq!(hits[0].title_keys.len(), 2);
    assert_eq!(hits[1].name, "Anna Quiet");
}

#[test]
fn people_matching_requires_every_word_of_the_query() {
    let conn = conn_with_credits();
    insert_credit(&conn, "movie", 1, 0, 1, "Anna Busy", None, "cast");
    assert!(people_matching(&conn, "anna quiet").unwrap().is_empty());
}

#[test]
fn an_empty_query_matches_nobody() {
    let conn = conn_with_credits();
    insert_credit(&conn, "movie", 1, 0, 1, "Anna Busy", None, "cast");
    assert!(people_matching(&conn, "   ").unwrap().is_empty());
}

#[test]
fn an_index_predating_credits_finds_nobody() {
    let conn = conn_predating_credits();
    assert!(people_matching(&conn, "anna").unwrap().is_empty());
}
