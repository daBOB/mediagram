use super::*;
use mlib_spec::caption::{Caption, Kind, Part};
use mlib_spec::ids::ProviderIds;

fn caption_text(set_id: &str, title: &str) -> String {
    let caption = Caption {
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
        title: Some(title.to_string()),
        year: Some(2020),
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
        set: set_id.to_string(),
        part: Part {
            i: 0,
            n: 1,
            off: 0,
            len: 10,
            sha256: "a".repeat(64),
        },
        total: 10,
    };
    mlib_spec::to_text(&caption, "").unwrap()
}

fn seeded_set(conn: &Connection, set_id: &str) {
    let caption = {
        let text = caption_text(set_id, "Old Title");
        mlib_spec::parse(&text).unwrap()
    };
    let row = SetRow::from_caption(&caption, 1_700_000_000);
    sets::insert_set(conn, &row).unwrap();
}

#[test]
fn the_first_parseable_caption_for_this_set_rewrites_the_row() {
    let dir = tempfile::tempdir().unwrap();
    let conn = crate::index::db::open(dir.path()).unwrap();
    seeded_set(&conn, "01JQ8F2K9M4XZ00000000001");

    let captions = vec![
        "not mlib at all".to_string(),
        caption_text("01JQ8F2K9M4XZ00000000099", "Wrong Set"),
        caption_text("01JQ8F2K9M4XZ00000000001", "New Title"),
    ];
    let outcome = resolve_from_captions(&conn, "01JQ8F2K9M4XZ00000000001", &captions).unwrap();
    assert!(outcome.applied);

    let row = sets::get_set(&conn, "01JQ8F2K9M4XZ00000000001")
        .unwrap()
        .unwrap();
    assert_eq!(row.title.as_deref(), Some("New Title"));
    // Metadata-only: the byte-describing fields stay as they were.
    assert_eq!(row.total, 10);
}

#[test]
fn no_matching_caption_applies_nothing() {
    let dir = tempfile::tempdir().unwrap();
    let conn = crate::index::db::open(dir.path()).unwrap();
    seeded_set(&conn, "01JQ8F2K9M4XZ00000000001");

    let captions = vec!["gone".to_string()];
    let outcome = resolve_from_captions(&conn, "01JQ8F2K9M4XZ00000000001", &captions).unwrap();
    assert!(!outcome.applied);

    let row = sets::get_set(&conn, "01JQ8F2K9M4XZ00000000001")
        .unwrap()
        .unwrap();
    assert_eq!(row.title.as_deref(), Some("Old Title"));
}

/// A conflicting set whose own `kind` is not one this build can decode —
/// from a newer uploader — is left exactly as it was, named by kind, rather
/// than failing the whole merge on a decode error.
#[test]
fn a_set_of_an_unknown_kind_is_left_as_it_was() {
    let dir = tempfile::tempdir().unwrap();
    let conn = crate::index::db::open(dir.path()).unwrap();
    conn.execute(
        "INSERT INTO sets(set_id, kind, container, total, part_count, status, created_at, spec_version, title)
         VALUES ('01JQ8F2K9M4XZ00000000002', 'vr', 'mkv', 10, 1, 'complete', 1700000000, 4, 'Old Title')",
        [],
    )
    .unwrap();

    let outcome = resolve_from_captions(&conn, "01JQ8F2K9M4XZ00000000002", &[]).unwrap();

    assert!(!outcome.applied);
    assert_eq!(outcome.unknown_kind.as_deref(), Some("vr"));
}

/// A candidate caption whose `#mlib v=N` this build cannot read is counted,
/// not silently dropped the way a garbled one is.
#[test]
fn a_caption_from_a_newer_uploader_is_counted_not_applied() {
    let dir = tempfile::tempdir().unwrap();
    let conn = crate::index::db::open(dir.path()).unwrap();
    seeded_set(&conn, "01JQ8F2K9M4XZ00000000001");

    let captions = vec!["#mlib v=9\n{\"set\":\"01JQ8F2K9M4XZ00000000001\"}".to_string()];
    let outcome = resolve_from_captions(&conn, "01JQ8F2K9M4XZ00000000001", &captions).unwrap();

    assert!(!outcome.applied);
    assert_eq!(outcome.newer_captions, 1);
}
