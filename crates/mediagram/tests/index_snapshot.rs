//! `index::snapshot::snapshot_to` against a real sqlite file: the resulting
//! file opens independently and carries the same data as the live db.

use mediagram::index::{db, rescan, snapshot};
use mediagram::upload::transport::Seen;
use mlib_spec::caption::{Caption, Kind, Part};
use mlib_spec::ids::ProviderIds;

const CHAT_ID: i64 = -1001234567890;

fn sample_seen() -> Seen {
    let caption = Caption {
        cid: None,
        chap: None,
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
        set: "01JQ8F2K9M4XZ00000000004".into(),
        part: Part {
            i: 0,
            n: 1,
            off: 0,
            len: 100,
            sha256: "aa".into(),
        },
        total: 100,
    };
    Seen {
        message_id: 401,
        doc_id: Some(9501),
        caption: mlib_spec::to_text(&caption, "").unwrap(),
    }
}

#[test]
fn snapshot_to_produces_an_openable_copy_with_matching_meta() {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    rescan::apply_seen(&conn, CHAT_ID, &[sample_seen()]).unwrap();

    snapshot::checkpoint(&conn).unwrap();
    let dest = dir.path().join("library.push.db");
    snapshot::snapshot_to(&conn, &dest).unwrap();

    let live_count: i64 = conn
        .query_row("SELECT COUNT(*) FROM sets", [], |row| row.get(0))
        .unwrap();
    let live_pushed_at: String = db::get_meta(&conn, "last_push_at").unwrap().unwrap();

    let snapshot_conn = rusqlite::Connection::open(&dest).unwrap();
    let snapshot_count: i64 = snapshot_conn
        .query_row("SELECT COUNT(*) FROM sets", [], |row| row.get(0))
        .unwrap();
    let snapshot_pushed_at: String = snapshot_conn
        .query_row(
            "SELECT value FROM meta WHERE key = 'last_push_at'",
            [],
            |row| row.get(0),
        )
        .unwrap();

    assert_eq!(snapshot_count, live_count);
    assert_eq!(snapshot_pushed_at, live_pushed_at);
}
