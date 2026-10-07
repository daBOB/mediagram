use super::*;
use mlib_spec::caption::{Caption, Kind as CaptionKind, Part};
use mlib_spec::ids::ProviderIds;

fn caption_text(set_id: &str) -> String {
    let caption = Caption {
        cid: None,
        chap: None,
        path: None,
        t: CaptionKind::Movie,
        ids: ProviderIds {
            tmdb: Some(1),
            tvdb: None,
            imdb: None,
        },
        show: None,
        title: Some("A Movie".into()),
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

fn seed_pending_movie(conn: &Connection, set_id: &str) {
    let caption = mlib_spec::parse(&caption_text(set_id)).unwrap();
    let row = SetRow::from_caption(&caption, 1_700_000_000);
    crate::index::sets::insert_set(conn, &row).unwrap();
}

/// A pending row of an unknown `kind` — as a newer uploader's own
/// upload would leave it, before this build is reinstalled — is
/// counted and left out, not a decode failure that aborts the list.
#[test]
fn a_pending_set_of_an_unknown_kind_is_skipped_and_counted() {
    let dir = tempfile::tempdir().unwrap();
    let conn = crate::index::db::open(dir.path()).unwrap();
    seed_pending_movie(&conn, "01JQ8F2K9M4XZ00000000001");
    conn.execute(
        "INSERT INTO sets(set_id, kind, container, total, part_count, status, created_at, spec_version)
         VALUES ('01JQ8F2K9M4XZ00000000002', 'vr', 'mkv', 10, 1, 'pending', 1700000001, 4)",
        [],
    )
    .unwrap();

    let (pending, skipped) = list_pending(&conn).unwrap();

    assert_eq!(
        pending
            .iter()
            .map(|s| s.set_id.as_str())
            .collect::<Vec<_>>(),
        ["01JQ8F2K9M4XZ00000000001"]
    );
    assert_eq!(
        skipped,
        vec![SkippedKind {
            kind: "vr".to_string(),
            count: 1
        }]
    );
}

/// Today's index — every kind known — lists exactly as before, with
/// nothing skipped.
#[test]
fn every_pending_set_of_a_known_kind_is_listed_with_nothing_skipped() {
    let dir = tempfile::tempdir().unwrap();
    let conn = crate::index::db::open(dir.path()).unwrap();
    seed_pending_movie(&conn, "01JQ8F2K9M4XZ00000000003");

    let (pending, skipped) = list_pending(&conn).unwrap();

    assert_eq!(pending.len(), 1);
    assert!(skipped.is_empty());
}
