use rusqlite::params;

use super::*;

fn index() -> Connection {
    let conn = Connection::open_in_memory().unwrap();
    for stmt in mlib_spec::schema::migrations_up_to(mlib_spec::schema::SCHEMA_VERSION) {
        conn.execute(stmt, []).unwrap();
    }
    conn
}

fn v12_index() -> Connection {
    let conn = Connection::open_in_memory().unwrap();
    for stmt in mlib_spec::schema::migrations_up_to(12) {
        conn.execute(stmt, []).unwrap();
    }
    conn
}

fn add_set(conn: &Connection, set_id: &str, group_key: Option<&str>, season: i64, episode: &str) {
    conn.execute(
        "INSERT INTO sets(set_id, kind, container, total, part_count, status, created_at, spec_version, group_key, season, episode)
         VALUES (?1, 'tut', 'mkv', 0, 0, 'complete', 0, 1, ?2, ?3, ?4)",
        params![set_id, group_key, season, episode],
    )
    .unwrap();
}

fn add_bundle(conn: &Connection, set_id: &str) {
    conn.execute("INSERT OR IGNORE INTO sets(set_id, kind, container, total, part_count, status, created_at, spec_version) VALUES (?1, 'movie', 'mkv', 0, 0, 'complete', 0, 1)", [set_id]).unwrap();
    conn.execute(
        "INSERT INTO subtitle_files(set_id, chat_id, message_id, bytes, sha256, uploaded_at) VALUES (?1, -1, 100, 42, 'sha', 1)",
        [set_id],
    )
    .unwrap();
    conn.execute(
        "INSERT INTO subtitle_tracks(set_id, track, lang, forced, sdh, label) VALUES (?1, 0, 'de', 0, 0, 'Deutsch')",
        [set_id],
    )
    .unwrap();
}

fn add_legacy(conn: &Connection, set_id: &str, lang: &str, body: &str) {
    conn.execute("INSERT OR IGNORE INTO sets(set_id, kind, container, total, part_count, status, created_at, spec_version) VALUES (?1, 'movie', 'mkv', 0, 0, 'complete', 0, 1)", [set_id]).unwrap();
    conn.execute(
        "INSERT INTO assets(set_id, kind, lang, body) VALUES (?1, 'subtitle', ?2, ?3)",
        params![set_id, lang, body],
    )
    .unwrap();
}

#[test]
fn a_bundled_set_reads_its_tracks_in_bundle_position() {
    let conn = index();
    add_bundle(&conn, "s1");

    let tracks = tracks_by_set(&conn).unwrap();

    assert_eq!(tracks.get("s1").unwrap(), &vec![SubtitleTrack {
        track: 0,
        lang: "de".into(),
        forced: false,
        sdh: false,
        label: "Deutsch".into(),
    }]);
}

#[test]
fn an_unbundled_set_synthesises_tracks_from_its_inline_rows_sorted_by_lang() {
    let conn = index();
    add_legacy(&conn, "s1", "en", "en text");
    add_legacy(&conn, "s1", "de", "de text");

    let tracks = tracks_by_set(&conn).unwrap();

    let got = tracks.get("s1").unwrap();
    assert_eq!(got[0].lang, "de");
    assert_eq!(got[0].track, 0);
    assert_eq!(got[1].lang, "en");
    assert_eq!(got[1].track, 1);
}

#[test]
fn a_bundled_set_s_leftover_inline_rows_are_ignored() {
    let conn = index();
    add_bundle(&conn, "s1");
    // A merge always deletes these once a bundle replaces them, but a reader
    // must not trust that: bundled tracks win regardless.
    add_legacy(&conn, "s1", "fr", "fr text");

    let tracks = tracks_by_set(&conn).unwrap();

    assert_eq!(tracks.get("s1").unwrap().len(), 1);
    assert_eq!(tracks.get("s1").unwrap()[0].lang, "de");
}

#[test]
fn an_index_older_than_v13_has_no_bundled_tracks_anywhere() {
    let conn = v12_index();
    add_legacy(&conn, "s1", "en", "en text");

    let tracks = tracks_by_set(&conn).unwrap();

    assert_eq!(tracks.get("s1").unwrap()[0].lang, "en");
}

#[test]
fn a_set_with_no_subtitles_at_all_is_absent_from_the_map() {
    let conn = index();
    assert_eq!(tracks_by_set(&conn).unwrap().get("nosuch"), None);
}

#[test]
fn bundle_ref_reads_the_channel_address_and_checksum() {
    let conn = index();
    add_bundle(&conn, "s1");

    let bundle = bundle_ref(&conn, "s1").unwrap().unwrap();

    assert_eq!(bundle.chat_id, -1);
    assert_eq!(bundle.message_id, 100);
    assert_eq!(bundle.bytes, 42);
}

#[test]
fn bundle_ref_is_none_for_an_unbundled_set_or_a_pre_v13_index() {
    let conn = index();
    assert!(bundle_ref(&conn, "nosuch").unwrap().is_none());

    let old = v12_index();
    assert!(bundle_ref(&old, "nosuch").unwrap().is_none());
}

#[test]
fn legacy_body_reads_a_set_s_own_language() {
    let conn = index();
    add_legacy(&conn, "s1", "de", "de text");

    assert_eq!(legacy_body(&conn, "s1", "de").unwrap().as_deref(), Some("de text"));
    assert_eq!(legacy_body(&conn, "s1", "fr").unwrap(), None);
}

#[test]
fn course_run_orders_by_chapter_then_lesson_and_caps_at_the_end() {
    let conn = index();
    add_set(&conn, "c1", Some("course"), 1, "1");
    add_set(&conn, "c2", Some("course"), 1, "2");
    add_set(&conn, "c3", Some("course"), 2, "1");

    assert_eq!(course_run(&conn, "c1", 10).unwrap(), vec!["c1", "c2", "c3"]);
    assert_eq!(course_run(&conn, "c2", 1).unwrap(), vec!["c2", "c3"]);
    assert_eq!(course_run(&conn, "c3", 5).unwrap(), vec!["c3"]);
}

#[test]
fn a_set_with_no_group_key_is_its_own_whole_run() {
    let conn = index();
    add_set(&conn, "m1", None, 0, "");

    assert_eq!(course_run(&conn, "m1", 10).unwrap(), vec!["m1"]);
}
