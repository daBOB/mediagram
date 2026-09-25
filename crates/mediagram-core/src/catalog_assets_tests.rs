use super::*;

fn index() -> Connection {
    let conn = Connection::open_in_memory().unwrap();
    for stmt in mlib_spec::schema::migrations_up_to(mlib_spec::schema::SCHEMA_VERSION) {
        conn.execute(stmt, []).unwrap();
    }
    conn
}

/// An asset row, on a set minimal enough only to satisfy `assets`' foreign
/// key — `OR IGNORE`, so a second asset for the same set need not repeat it.
fn add_asset(conn: &Connection, set_id: &str, kind: &str, lang: &str, body: &str) {
    conn.execute(
        "INSERT OR IGNORE INTO sets(set_id, kind, container, total, part_count, status, created_at, spec_version)
         VALUES (?1, 'movie', 'mkv', 0, 0, 'complete', 0, 1)",
        [set_id],
    )
    .unwrap();
    conn.execute(
        "INSERT INTO assets(set_id, kind, lang, body) VALUES (?1, ?2, ?3, ?4)",
        params![set_id, kind, lang, body],
    )
    .unwrap();
}

#[test]
fn subtitle_languages_are_grouped_by_set_and_sorted() {
    let conn = index();
    add_asset(&conn, "s1", "subtitle", "en", "en text");
    add_asset(&conn, "s1", "subtitle", "de", "de text");
    add_asset(&conn, "s2", "subtitle", "fr", "fr text");

    let by_set = subtitle_languages(&conn).unwrap();

    assert_eq!(by_set.get("s1"), Some(&vec!["de".to_string(), "en".to_string()]));
    assert_eq!(by_set.get("s2"), Some(&vec!["fr".to_string()]));
}

#[test]
fn a_set_with_no_subtitle_rows_is_absent_from_the_map() {
    let conn = index();
    assert_eq!(subtitle_languages(&conn).unwrap().get("nosuch"), None);
}

#[test]
fn summaries_lists_only_the_sets_that_have_one() {
    let conn = index();
    add_asset(&conn, "s1", "summary", "", "What happens.");
    add_asset(&conn, "s2", "subtitle", "en", "en text");

    let summarized = summaries(&conn).unwrap();

    assert!(summarized.contains("s1"));
    assert!(!summarized.contains("s2"));
}

#[test]
fn text_reads_a_summary_and_ignores_the_language_asked_for() {
    let conn = index();
    add_asset(&conn, "s1", "summary", "", "What happens.");

    assert_eq!(text(&conn, "s1", "summary", "de").unwrap().as_deref(), Some("What happens."));
}

#[test]
fn text_reads_the_subtitle_track_in_the_language_asked_for() {
    let conn = index();
    add_asset(&conn, "s1", "subtitle", "en", "en text");
    add_asset(&conn, "s1", "subtitle", "de", "de text");

    assert_eq!(text(&conn, "s1", "subtitle", "de").unwrap().as_deref(), Some("de text"));
    assert_eq!(text(&conn, "s1", "subtitle", "fr").unwrap(), None);
}

/// A kind this store does not know is refused before it reaches SQL, the
/// same way `Core::set_text` refuses it before this function is ever called.
#[test]
fn an_unknown_kind_answers_nothing_even_when_a_row_would_otherwise_match() {
    let conn = index();
    add_asset(&conn, "s1", "chapter-marks", "", "00:00 Intro");

    assert_eq!(text(&conn, "s1", "chapter-marks", "").unwrap(), None);
}
