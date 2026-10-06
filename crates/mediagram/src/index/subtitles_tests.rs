use super::*;
use crate::index::{assets, db, pins};

fn index_with_set(id: &str) -> (tempfile::TempDir, Connection) {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    conn.execute(
        "INSERT INTO sets(set_id, kind, container, total, part_count, created_at, spec_version)
         VALUES (?1, 'movie', 'mkv', 1, 1, 0, 1)",
        [id],
    )
    .unwrap();
    (dir, conn)
}

fn file(message_id: i32) -> FileRef {
    FileRef {
        chat_id: -100,
        message_id,
        bytes: 42,
        sha256: "ab".repeat(32),
    }
}

fn track(lang: &str, forced: bool, label: &str) -> BundleTrack {
    BundleTrack {
        lang: lang.into(),
        forced,
        sdh: false,
        label: label.into(),
        source: "embedded".into(),
        codec: "subrip".into(),
        vtt: "WEBVTT\n".into(),
    }
}

fn labels(conn: &Connection) -> Vec<String> {
    let mut stmt = conn
        .prepare("SELECT label FROM subtitle_tracks WHERE set_id = 'A' ORDER BY track")
        .unwrap();
    stmt.query_map([], |row| row.get(0))
        .unwrap()
        .collect::<rusqlite::Result<_>>()
        .unwrap()
}

#[test]
fn recording_replaces_tracks_drops_inline_rows_and_owes_a_publish() {
    let (_dir, conn) = index_with_set("A");
    assets::put(&conn, "A", assets::Kind::Subtitle, "deu", "WEBVTT\n").unwrap();
    assets::put(&conn, "A", assets::Kind::Summary, "", "kurz").unwrap();
    assert_eq!(pins::publish_owed(&conn).unwrap(), None);

    record(
        &conn,
        "A",
        &file(7),
        &[
            track("de", true, "German (Forced)"),
            track("de", false, "German"),
        ],
    )
    .unwrap();

    assert_eq!(bundle_message(&conn, "A").unwrap(), Some(7));
    assert_eq!(labels(&conn), ["German (Forced)", "German"]);
    assert!(assets::languages(&conn, "A").unwrap().is_empty());
    assert!(assets::has_summary(&conn, "A").unwrap(), "a summary stays");
    assert!(pins::publish_owed(&conn).unwrap().is_some());
}

#[test]
fn recording_again_replaces_the_file_and_its_tracks() {
    let (_dir, conn) = index_with_set("A");
    record(
        &conn,
        "A",
        &file(7),
        &[track("de", false, "German"), track("en", false, "English")],
    )
    .unwrap();

    record(&conn, "A", &file(9), &[track("en", false, "English")]).unwrap();

    assert_eq!(bundle_message(&conn, "A").unwrap(), Some(9));
    assert_eq!(labels(&conn), ["English"]);
}

#[test]
fn a_set_that_is_gone_records_nothing() {
    let (_dir, conn) = index_with_set("A");

    assert!(record(&conn, "missing", &file(7), &[track("de", false, "German")]).is_err());

    assert_eq!(bundle_message(&conn, "missing").unwrap(), None);
    assert_eq!(pins::publish_owed(&conn).unwrap(), None);
}

#[test]
fn removing_a_set_removes_its_bundle_record() {
    let (_dir, conn) = index_with_set("A");
    record(&conn, "A", &file(7), &[track("de", false, "German")]).unwrap();

    crate::index::lifecycle::delete_rows(&conn, "A").unwrap();

    assert_eq!(bundle_message(&conn, "A").unwrap(), None);
    assert!(labels(&conn).is_empty());
}
