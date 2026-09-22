//! `index::sets` and `index::parts` round trips against a real sqlite file.

use mediagram::index::status::SetStatus;
use mediagram::index::{db, parts, sets};
use mlib_spec::caption::{Caption, Episode, Kind, Part};
use mlib_spec::ids::ProviderIds;
use mlib_spec::part_plan::PartRange;

fn sample_caption() -> Caption {
    Caption {
        cid: None,
        chap: None,
        path: None,
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
        e: None::<Episode>,
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
        set: "01JQ8F2K9M4XZ00000000000".into(),
        part: Part {
            i: 0,
            n: 2,
            off: 0,
            len: 0,
            sha256: String::new(),
        },
        total: 100,
    }
}

#[test]
fn sets_insert_get_list_pending_and_complete() {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    let row = sets::SetRow::from_caption(&sample_caption(), 1_700_000_000).unwrap();
    sets::insert_set(&conn, &row).unwrap();

    let fetched = sets::get_set(&conn, &row.set_id).unwrap().unwrap();
    assert_eq!(fetched, row);
    assert_eq!(sets::list_pending(&conn).unwrap().len(), 1);

    sets::set_hash_and_complete(&conn, &row.set_id, "abc").unwrap();
    assert_eq!(sets::list_pending(&conn).unwrap().len(), 0);
    let done = sets::get_set(&conn, &row.set_id).unwrap().unwrap();
    assert_eq!(done.status, SetStatus::Complete);
    assert_eq!(done.set_hash.as_deref(), Some("abc"));
}

#[test]
fn get_set_is_none_for_unknown_id() {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    assert!(sets::get_set(&conn, "nope").unwrap().is_none());
}

#[test]
fn parts_round_trip_in_idx_order() {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    let caption = sample_caption();
    let row = sets::SetRow::from_caption(&caption, 1_700_000_000).unwrap();
    sets::insert_set(&conn, &row).unwrap();

    let ranges = vec![
        PartRange {
            idx: 0,
            off: 0,
            len: 50,
        },
        PartRange {
            idx: 1,
            off: 50,
            len: 50,
        },
    ];
    parts::insert_parts(&conn, &row.set_id, &ranges).unwrap();

    let pending = parts::pending_parts(&conn, &row.set_id).unwrap();
    assert_eq!(pending.len(), 2);
    assert_eq!(pending[0].idx, 0);
    assert_eq!(pending[1].idx, 1);
    assert_eq!(pending[0].byte_length, 50);

    parts::mark_done(
        &conn,
        &row.set_id,
        1,
        &parts::Landed {
            chat_id: -100,
            message_id: 55,
            doc_id: 9955,
            sha256: "hash1".to_string(),
        },
    )
    .unwrap();
    let pending = parts::pending_parts(&conn, &row.set_id).unwrap();
    assert_eq!(pending.len(), 1);
    assert_eq!(pending[0].idx, 0);

    parts::mark_done(
        &conn,
        &row.set_id,
        0,
        &parts::Landed {
            chat_id: -100,
            message_id: 54,
            doc_id: 9954,
            sha256: "hash0".to_string(),
        },
    )
    .unwrap();
    assert!(parts::pending_parts(&conn, &row.set_id).unwrap().is_empty());
    assert_eq!(
        parts::done_hashes(&conn, &row.set_id).unwrap(),
        vec!["hash0".to_string(), "hash1".to_string()]
    );
}

#[test]
fn meta_roundtrip_used_for_resume_source_paths() {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    assert_eq!(db::get_meta(&conn, "source:x").unwrap(), None);
    db::set_meta(&conn, "source:x", "/tmp/a.mkv").unwrap();
    assert_eq!(
        db::get_meta(&conn, "source:x").unwrap(),
        Some("/tmp/a.mkv".to_string())
    );
    db::delete_meta(&conn, "source:x").unwrap();
    assert_eq!(db::get_meta(&conn, "source:x").unwrap(), None);
}
