//! What the export reads out of the snapshot, and the draft pointer whose
//! identifying fields become the cipher's associated data.

use mediagram::export::pointer;
use mediagram::export::titles::{counts, distinct_titles};
use mediagram::index::db;
use mediagram::index::set_row::SetRow;
use mediagram::index::sets;
use mlib_spec::caption::{Caption, Kind, Part};
use mlib_spec::ids::ProviderIds;

fn caption(set: &str, kind: Kind, tmdb: Option<u64>) -> Caption {
    Caption {
        cid: None,
        chap: None,
        path: None,
        t: kind,
        ids: ProviderIds {
            tmdb,
            tvdb: None,
            imdb: None,
        },
        show: None,
        title: Some("Title".into()),
        year: Some(2024),
        s: None,
        e: None,
        abs: None,
        q: None,
        hdr: None,
        container: "mkv".into(),
        vcodec: None,
        acodec: None,
        alang: vec![],
        slang: vec![],
        dur: None,
        variant: None,
        set: set.into(),
        part: Part {
            i: 0,
            n: 1,
            off: 0,
            len: 10,
            sha256: String::new(),
        },
        total: 10,
    }
}

fn db_with(rows: &[(&str, Kind, Option<u64>)]) -> (tempfile::TempDir, rusqlite::Connection) {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    for (set, kind, tmdb) in rows {
        let row = SetRow::from_caption(&caption(set, *kind, *tmdb), 1_700_000_000).unwrap();
        sets::insert_set(&conn, &row).unwrap();
    }
    (dir, conn)
}

#[test]
fn titles_are_distinct_and_carry_their_kind() {
    let (_d, conn) = db_with(&[
        ("01A", Kind::Movie, Some(550)),
        ("01B", Kind::Movie, Some(550)),
        ("01C", Kind::Ep, Some(550)),
    ]);

    let found = distinct_titles(&conn).unwrap();

    assert_eq!(found.len(), 2, "the duplicate movie collapses: {found:?}");
    assert!(found.contains(&(Kind::Movie, 550)));
    assert!(
        found.contains(&(Kind::Ep, 550)),
        "a show id is not a movie id"
    );
}

#[test]
fn a_set_with_no_provider_id_contributes_no_title() {
    let (_d, conn) = db_with(&[("01A", Kind::Movie, None), ("01B", Kind::Movie, Some(7))]);
    assert_eq!(distinct_titles(&conn).unwrap(), vec![(Kind::Movie, 7)]);
}

#[test]
fn counts_report_sets_and_parts() {
    let (_d, conn) = db_with(&[("01A", Kind::Movie, Some(1)), ("01B", Kind::Ep, Some(2))]);
    let (sets_count, parts_count) = counts(&conn).unwrap();
    assert_eq!(sets_count, 2);
    assert_eq!(parts_count, 0, "no part rows were inserted");
}

#[test]
fn an_empty_index_reports_nothing() {
    let (_d, conn) = db_with(&[]);
    assert!(distinct_titles(&conn).unwrap().is_empty());
    assert_eq!(counts(&conn).unwrap(), (0, 0));
}

/// The draft carries only what the cipher authenticates. The download fields
/// are filled in later, once the ciphertext those fields describe exists.
#[test]
fn a_draft_pointer_has_the_identifying_fields_and_no_download_fields() {
    let key = [3u8; 32];
    let draft = pointer::draft(1_781_568_000, &key);

    assert_eq!(draft.format, mlib_spec::package::PACKAGE_FORMAT);
    assert_eq!(draft.created_at, 1_781_568_000);
    assert_eq!(draft.key_id, mlib_spec::package::key_id(&key));
    assert_eq!(draft.cipher, mlib_spec::package::CIPHER);
    assert!(draft.file.is_empty() && draft.url.is_empty());
    assert_eq!(draft.bytes, 0);
    assert!(draft.sha256.is_empty());
}

#[test]
fn the_draft_produces_the_same_associated_data_as_the_published_pointer() {
    let key = [3u8; 32];
    let draft = pointer::draft(1_781_568_000, &key);
    let mut published = draft.clone();
    published.file = "prebuilt_mediagram_db_20260616-3d7e10c4.tar.gz.enc".into();
    published.url = "https://example.com/x".into();
    published.bytes = 1234;
    published.sha256 = "a".repeat(64);

    assert_eq!(
        mlib_spec::package::associated_data(&draft),
        mlib_spec::package::associated_data(&published),
        "filling in the download fields must not change what was authenticated"
    );
}

#[test]
fn sha256_matches_a_known_digest() {
    assert_eq!(
        hex::encode(pointer::sha256(b"abc")),
        "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"
    );
}
