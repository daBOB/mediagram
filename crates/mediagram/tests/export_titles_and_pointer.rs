//! What the export reads out of the snapshot, and the draft pointer whose
//! identifying fields become the cipher's associated data.

use mediagram::export::pointer;
use mediagram::export::titles::{distinct_titles, set_and_part_counts};
use mediagram::index::db;
use mlib_spec::caption::Kind;

mod support;
use support::export::db_with;

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
    let (sets_count, parts_count) = set_and_part_counts(&conn).unwrap();
    assert_eq!(sets_count, 2);
    assert_eq!(parts_count, 0, "no part rows were inserted");
}

#[test]
fn an_empty_index_reports_nothing() {
    let (_d, conn) = db_with(&[]);
    assert!(distinct_titles(&conn).unwrap().is_empty());
    assert_eq!(set_and_part_counts(&conn).unwrap(), (0, 0));
}

/// A `kind` outside the enum can only exist in a database from before the
/// value was validated on write, or written by hand. Either way it must not
/// surface as a title `distinct_titles` cannot even name.
#[test]
fn a_row_with_an_unrecognized_kind_contributes_no_title() {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    conn.execute(
        "INSERT INTO sets (set_id, kind, tmdb, title, container, total, part_count, created_at, spec_version) VALUES ('unknown_set', 'unknown', 999, 'Unknown Title', 'mkv', 0, 0, 1700000000, 2)",
        [],
    )
    .unwrap();

    assert_eq!(distinct_titles(&conn).unwrap().len(), 0);
}

/// `tmdb` is stored as SQLite's `INTEGER`, which does not enforce
/// non-negativity, so a negative value has to be filtered rather than
/// rejected by the column type.
#[test]
fn a_row_with_a_negative_tmdb_id_contributes_no_title() {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    conn.execute(
        "INSERT INTO sets (set_id, kind, tmdb, title, container, total, part_count, created_at, spec_version) VALUES ('negative_set', 'movie', -1, 'Negative ID', 'mkv', 0, 0, 1700000000, 2)",
        [],
    )
    .unwrap();

    assert_eq!(distinct_titles(&conn).unwrap().len(), 0);
}

#[test]
fn a_tmdb_id_near_i64_max_still_round_trips() {
    let large_id = i64::MAX as u64 - 1_000_000;
    let (_d, conn) = db_with(&[("01A", Kind::Movie, Some(large_id))]);
    assert_eq!(
        distinct_titles(&conn).unwrap(),
        vec![(Kind::Movie, large_id)]
    );
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
    assert_eq!(
        hex::encode(pointer::sha256(b"")),
        "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    );
}

/// `created_at` is stored and echoed verbatim; nothing about the draft
/// special-cases the ends of the range it lives in.
#[test]
fn a_draft_carries_created_at_verbatim_at_the_extremes_of_i64() {
    let key = [7u8; 32];
    let earliest = pointer::draft(0, &key);
    assert_eq!(earliest.created_at, 0);
    assert_eq!(earliest.format, mlib_spec::package::PACKAGE_FORMAT);

    let latest = pointer::draft(i64::MAX, &key);
    assert_eq!(latest.created_at, i64::MAX);
    assert_eq!(latest.format, mlib_spec::package::PACKAGE_FORMAT);
}

/// The cipher's associated data has to be valid, parseable JSON, not just
/// bytes that happen to authenticate.
#[test]
fn the_associated_data_is_valid_json_with_the_identifying_fields() {
    let key = [9u8; 32];
    let draft = pointer::draft(1234567890, &key);
    let aad = mlib_spec::package::associated_data(&draft);

    let json_str = std::str::from_utf8(&aad).expect("AAD is valid UTF-8");
    let json_val: serde_json::Value = serde_json::from_str(json_str).expect("AAD parses as JSON");

    assert_eq!(json_val["format"], mlib_spec::package::PACKAGE_FORMAT);
    assert_eq!(json_val["created_at"], 1234567890);
}
