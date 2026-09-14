//! `index::rescan::apply_seen` against fixture caption lists and a real
//! sqlite file; no Telegram connection. `snapshot_to` is covered in
//! `tests/index_snapshot.rs`.

use mediagram::index::{db, rescan, sets};
use mediagram::upload::transport::Seen;
use mlib_spec::caption::{Caption, Kind, Part};
use mlib_spec::ids::ProviderIds;

const CHAT_ID: i64 = -1001234567890;

fn open_db() -> (tempfile::TempDir, rusqlite::Connection) {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    (dir, conn)
}

/// One caption template shared by every part of a set.
fn template(set_id: &str, part_count: u32, total: u64) -> Caption {
    Caption {
        t: Kind::Movie,
        ids: ProviderIds {
            tmdb: Some(42),
            tvdb: None,
            imdb: None,
        },
        show: None,
        title: Some("Dune: Part Two".into()),
        year: Some(2024),
        s: None,
        e: None,
        abs: None,
        q: Some("1080p".into()),
        hdr: Some("SDR".into()),
        container: "mkv".into(),
        vcodec: Some("hevc".into()),
        acodec: Some("aac".into()),
        alang: vec!["en".into()],
        slang: vec![],
        dur: Some(9000),
        variant: None,
        set: set_id.into(),
        part: Part {
            i: 0,
            n: part_count,
            off: 0,
            len: 0,
            sha256: String::new(),
        },
        total,
    }
}

/// One channel message caption for part `idx`, `len` bytes at `off`.
fn part_seen(
    template: &Caption,
    idx: u32,
    off: u64,
    len: u64,
    sha: &str,
    message_id: i64,
    doc_id: i64,
) -> Seen {
    let caption = template.with_part(Part {
        i: idx,
        n: template.part.n,
        off,
        len,
        sha256: sha.into(),
    });
    let text = mlib_spec::to_text(&caption, "").unwrap();
    Seen {
        message_id,
        doc_id: Some(doc_id),
        caption: text,
    }
}

#[test]
fn complete_set_becomes_complete_with_correct_set_hash() {
    let (_dir, conn) = open_db();
    let set_id = "01JQ8F2K9M4XZ00000000001";
    let t = template(set_id, 3, 300);
    let seen = vec![
        part_seen(&t, 0, 0, 100, "aa", 101, 9001),
        part_seen(&t, 1, 100, 100, "bb", 102, 9002),
        part_seen(&t, 2, 200, 100, "cc", 103, 9003),
    ];
    let summary = rescan::apply_seen(&conn, CHAT_ID, &seen).unwrap();
    assert_eq!(summary.sets_seen, 1);
    assert_eq!(summary.parts_seen, 3);
    assert_eq!(summary.sets_complete, 1);
    assert_eq!(summary.sets_incomplete, 0);
    assert_eq!(summary.duplicates_skipped, 0);
    let row = sets::get_set(&conn, set_id).unwrap().unwrap();
    assert_eq!(row.status, "complete");
    let expected = mlib_spec::set_hash::set_hash(&["aa", "bb", "cc"]);
    assert_eq!(row.set_hash.as_deref(), Some(expected.as_str()));
}

#[test]
fn incomplete_set_stays_pending() {
    let (_dir, conn) = open_db();
    let set_id = "01JQ8F2K9M4XZ00000000002";
    let t = template(set_id, 3, 300);
    let seen = vec![
        part_seen(&t, 0, 0, 100, "aa", 201, 9101),
        part_seen(&t, 1, 100, 100, "bb", 202, 9102),
    ];
    let summary = rescan::apply_seen(&conn, CHAT_ID, &seen).unwrap();
    assert_eq!(summary.sets_complete, 0);
    assert_eq!(summary.sets_incomplete, 1);
    let row = sets::get_set(&conn, set_id).unwrap().unwrap();
    assert_eq!(row.status, "pending");
    assert!(row.set_hash.is_none());
}

fn recorded_message_id(conn: &rusqlite::Connection, set_id: &str) -> i64 {
    conn.query_row(
        "SELECT message_id FROM parts WHERE set_id = ?1 AND idx = 0",
        [set_id],
        |row| row.get(0),
    )
    .unwrap()
}

#[test]
fn duplicate_part_keeps_the_higher_message_id() {
    let (_dir, conn) = open_db();

    // Higher message id arrives second: it replaces the recorded one.
    let set_a = "01JQ8F2K9M4XZ0000000000A";
    let ta = template(set_a, 1, 100);
    let seen_a = vec![
        part_seen(&ta, 0, 0, 100, "aa", 10, 9201),
        part_seen(&ta, 0, 0, 100, "aa-again", 20, 9202),
    ];
    let summary_a = rescan::apply_seen(&conn, CHAT_ID, &seen_a).unwrap();
    assert_eq!(summary_a.parts_seen, 1);
    assert_eq!(summary_a.duplicates_skipped, 1);
    assert_eq!(recorded_message_id(&conn, set_a), 20);

    // Lower message id arrives second: the existing higher one is kept.
    let set_b = "01JQ8F2K9M4XZ0000000000B";
    let tb = template(set_b, 1, 100);
    let seen_b = vec![
        part_seen(&tb, 0, 0, 100, "aa", 20, 9301),
        part_seen(&tb, 0, 0, 100, "aa-again", 10, 9302),
    ];
    let summary_b = rescan::apply_seen(&conn, CHAT_ID, &seen_b).unwrap();
    assert_eq!(summary_b.duplicates_skipped, 1);
    assert_eq!(recorded_message_id(&conn, set_b), 20);
}

#[test]
fn index_document_and_plain_text_messages_are_ignored() {
    let (_dir, conn) = open_db();
    let seen = vec![
        Seen {
            message_id: 1,
            doc_id: Some(1),
            caption: "#mlib-index v=2\n{\"pushed_at\":1,\"sets\":0,\"schema\":1}".into(),
        },
        Seen {
            message_id: 2,
            doc_id: Some(2),
            caption: "just a note, not a caption".into(),
        },
    ];

    let summary = rescan::apply_seen(&conn, CHAT_ID, &seen).unwrap();

    assert_eq!(summary.sets_seen, 0);
    assert_eq!(summary.parts_seen, 0);
    assert_eq!(summary.duplicates_skipped, 0);
    let count: i64 = conn
        .query_row("SELECT COUNT(*) FROM sets", [], |row| row.get(0))
        .unwrap();
    assert_eq!(count, 0);
}

#[test]
fn applying_the_same_batch_twice_is_idempotent() {
    let (_dir, conn) = open_db();
    let set_id = "01JQ8F2K9M4XZ00000000003";
    let t = template(set_id, 2, 200);
    let seen = vec![
        part_seen(&t, 0, 0, 100, "aa", 301, 9401),
        part_seen(&t, 1, 100, 100, "bb", 302, 9402),
    ];

    let first = rescan::apply_seen(&conn, CHAT_ID, &seen).unwrap();
    let second = rescan::apply_seen(&conn, CHAT_ID, &seen).unwrap();

    assert_eq!(first, second);
    let row = sets::get_set(&conn, set_id).unwrap().unwrap();
    assert_eq!(row.status, "complete");
}
