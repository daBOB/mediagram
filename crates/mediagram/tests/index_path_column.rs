//! The folders a set came from, stored and read back.
//!
//! A course nests unevenly — one to four folders deep in the one this was
//! built for — so the index keeps the path rather than trying to describe the
//! shape with a chapter number.

use mediagram::index::{db, set_row::SetRow, sets};
use mlib_spec::caption::{Caption, Episode, Kind, Part};
use mlib_spec::ids::ProviderIds;

fn lesson(set: &str, path: Option<&str>) -> Caption {
    Caption {
        t: Kind::Tut,
        ids: ProviderIds {
            tmdb: None,
            tvdb: None,
            imdb: None,
        },
        cid: Some("geldhochschule".into()),
        show: Some("Geldhochschule".into()),
        chap: Some("1. Trading".into()),
        path: path.map(str::to_string),
        title: Some("Einführung".into()),
        year: None,
        s: Some(1),
        e: Some(Episode::Single(1)),
        abs: None,
        q: None,
        hdr: None,
        container: "mp4".into(),
        vcodec: Some("h264".into()),
        acodec: Some("aac".into()),
        alang: vec!["deu".into()],
        slang: vec![],
        dur: Some(120),
        variant: None,
        set: set.into(),
        part: Part {
            i: 0,
            n: 1,
            off: 0,
            len: 100,
            sha256: "a".repeat(64),
        },
        total: 100,
    }
}

#[test]
fn a_path_survives_the_round_trip_through_the_index() {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    let deep = "Ausbildung Trading/1. Grundlagen/1. Trading";

    let row = SetRow::from_caption(&lesson("01SET0000000000000000001", Some(deep)), 1).unwrap();
    sets::insert_set(&conn, &row).unwrap();

    let read = sets::get_set(&conn, "01SET0000000000000000001")
        .unwrap()
        .unwrap();
    assert_eq!(read.path.as_deref(), Some(deep));
    assert_eq!(read.caption_template().unwrap().path.as_deref(), Some(deep));
}

#[test]
fn a_set_with_no_path_reads_back_as_none() {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();

    let row = SetRow::from_caption(&lesson("01SET0000000000000000002", None), 1).unwrap();
    sets::insert_set(&conn, &row).unwrap();

    assert_eq!(
        sets::get_set(&conn, "01SET0000000000000000002")
            .unwrap()
            .unwrap()
            .path,
        None
    );
}

/// An index written by an older build must open and gain the column, not be
/// refused: the uploader's own library predates this.
#[test]
fn an_older_index_migrates_and_keeps_its_rows() {
    let dir = tempfile::tempdir().unwrap();

    // Build a v2 database by hand, exactly as the previous release left it.
    {
        let conn = rusqlite::Connection::open(dir.path().join("library.db")).unwrap();
        for statement in mlib_spec::schema::migrations_up_to(2) {
            conn.execute(statement, []).unwrap();
        }
        conn.execute(
            "INSERT INTO meta(key, value) VALUES ('schema_version', '2')",
            [],
        )
        .unwrap();
        conn.execute(
            "INSERT INTO sets(set_id, kind, container, total, part_count, status, created_at, spec_version)
             VALUES ('01OLDSET0000000000000001', 'tut', 'mp4', 10, 1, 'complete', 1, 3)",
            [],
        )
        .unwrap();
    }

    let conn = db::open(dir.path()).unwrap();

    let version: String = conn
        .query_row(
            "SELECT value FROM meta WHERE key = 'schema_version'",
            [],
            |r| r.get(0),
        )
        .unwrap();
    assert_eq!(version, mlib_spec::schema::SCHEMA_VERSION.to_string());
    let kept: i64 = conn
        .query_row(
            "SELECT COUNT(*) FROM sets WHERE set_id = '01OLDSET0000000000000001'",
            [],
            |r| r.get(0),
        )
        .unwrap();
    assert_eq!(kept, 1, "migrating must not drop what was already there");
    let path: Option<String> = conn
        .query_row(
            "SELECT path FROM sets WHERE set_id = '01OLDSET0000000000000001'",
            [],
            |r| r.get(0),
        )
        .unwrap();
    assert_eq!(path, None);
}
