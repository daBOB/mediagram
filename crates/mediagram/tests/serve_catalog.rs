//! What the player is allowed to offer, and where a set's bytes live.
//!
//! "Playable" is not a new idea invented here: `PLAYABLE_SQL` in the spec
//! crate already defines it as complete, with every part done and the lengths
//! summing to the recorded total. The catalog must use that and not a second
//! opinion, or the player will offer titles that stall halfway.

use mediagram::index::{db, parts, set_row::SetRow, sets};
use mediagram::serve::catalog::{list_playable, part_locations};
use mlib_spec::PartRange;
use mlib_spec::caption::{Caption, Kind, Part};
use mlib_spec::ids::ProviderIds;

fn caption(set: &str, parts_n: u32, total: u64) -> Caption {
    Caption {
        t: Kind::Movie,
        ids: ProviderIds {
            tmdb: Some(603),
            tvdb: None,
            imdb: None,
        },
        cid: None,
        show: None,
        chap: None,
        path: None,
        title: Some("The Matrix".into()),
        year: Some(1999),
        s: None,
        e: None,
        abs: None,
        q: Some("1080p".into()),
        hdr: None,
        container: "mkv".into(),
        vcodec: Some("hevc".into()),
        acodec: Some("ac3".into()),
        alang: vec!["eng".into()],
        slang: vec![],
        dur: Some(8160),
        variant: None,
        set: set.into(),
        part: Part {
            i: 0,
            n: parts_n,
            off: 0,
            len: 0,
            sha256: String::new(),
        },
        total,
    }
}

/// A set with `parts_n` parts, all done, summing to `total`.
fn complete_set(conn: &rusqlite::Connection, set: &str, spans: &[(u64, u64)]) {
    let total: u64 = spans.iter().map(|(_, len)| len).sum();
    let row =
        SetRow::from_caption(&caption(set, spans.len() as u32, total), 1_700_000_000).unwrap();
    sets::insert_set(conn, &row).unwrap();
    let ranges: Vec<PartRange> = spans
        .iter()
        .enumerate()
        .map(|(idx, (off, len))| PartRange {
            idx: idx as u32,
            off: *off,
            len: *len,
        })
        .collect();
    parts::insert_parts(conn, set, &ranges).unwrap();
    for (idx, _) in spans.iter().enumerate() {
        parts::mark_done(
            conn,
            set,
            idx as u32,
            -1001,
            100 + idx as i64,
            900 + idx as i64,
            &"a".repeat(64),
        )
        .unwrap();
    }
    sets::set_hash_and_complete(conn, set, &"b".repeat(64)).unwrap();
}

fn open() -> (tempfile::TempDir, rusqlite::Connection) {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    (dir, conn)
}

#[test]
fn a_complete_set_is_offered_with_what_a_player_needs_to_decide() {
    let (_d, conn) = open();
    complete_set(&conn, "01SET0000000000000000001", &[(0, 1000)]);

    let listed = list_playable(&conn).unwrap();

    assert_eq!(listed.len(), 1);
    let set = &listed[0];
    assert_eq!(set.title.as_deref(), Some("The Matrix"));
    assert_eq!(set.container, "mkv");
    assert_eq!(set.vcodec.as_deref(), Some("hevc"));
    assert_eq!(set.acodec.as_deref(), Some("ac3"));
    assert_eq!(set.total, 1000);
    assert_eq!(set.duration, Some(8160));
}

/// The uploader's own definition of playable, not a second opinion.
#[test]
fn a_pending_set_is_not_offered() {
    let (_d, conn) = open();
    let row =
        SetRow::from_caption(&caption("01SET0000000000000000002", 1, 1000), 1_700_000_000).unwrap();
    sets::insert_set(&conn, &row).unwrap();
    parts::insert_parts(
        &conn,
        "01SET0000000000000000002",
        &[PartRange {
            idx: 0,
            off: 0,
            len: 1000,
        }],
    )
    .unwrap();

    assert!(list_playable(&conn).unwrap().is_empty());
}

#[test]
fn a_set_whose_parts_do_not_sum_to_its_total_is_not_offered() {
    let (_d, conn) = open();
    // Marked complete, but a part is missing: exactly what PLAYABLE_SQL catches.
    let row =
        SetRow::from_caption(&caption("01SET0000000000000000003", 2, 2000), 1_700_000_000).unwrap();
    sets::insert_set(&conn, &row).unwrap();
    parts::insert_parts(
        &conn,
        "01SET0000000000000000003",
        &[PartRange {
            idx: 0,
            off: 0,
            len: 1000,
        }],
    )
    .unwrap();
    parts::mark_done(
        &conn,
        "01SET0000000000000000003",
        0,
        -1001,
        100,
        900,
        &"a".repeat(64),
    )
    .unwrap();
    sets::set_hash_and_complete(&conn, "01SET0000000000000000003", &"b".repeat(64)).unwrap();

    assert!(list_playable(&conn).unwrap().is_empty());
}

#[test]
fn part_locations_come_back_in_order_with_their_messages() {
    let (_d, conn) = open();
    complete_set(
        &conn,
        "01SET0000000000000000004",
        &[(0, 3_758_096_384), (3_758_096_384, 3_253_467_079)],
    );

    let located = part_locations(&conn, "01SET0000000000000000004").unwrap();

    assert_eq!(located.len(), 2);
    assert_eq!(located[0].span.idx, 0);
    assert_eq!(located[0].span.off, 0);
    assert_eq!(located[0].span.len, 3_758_096_384);
    assert_eq!(located[1].span.off, 3_758_096_384);
    assert_eq!(located[0].message_id, 100);
    assert_eq!(located[1].message_id, 101);
    assert_eq!(located[0].chat_id, -1001);
}

#[test]
fn an_unknown_set_has_no_locations() {
    let (_d, conn) = open();
    assert!(
        part_locations(&conn, "01NOSUCHSET00000000000001")
            .unwrap()
            .is_empty()
    );
}

#[test]
fn an_empty_index_offers_nothing() {
    let (_d, conn) = open();
    assert!(list_playable(&conn).unwrap().is_empty());
}

/// The stream route needs one set's details, and must reach the same verdict
/// as the listing: one definition of playable, asked two ways.
#[test]
fn a_single_set_can_be_looked_up_by_id() {
    let (_d, conn) = open();
    complete_set(&conn, "01SET0000000000000000005", &[(0, 4096)]);

    let found = mediagram::serve::catalog::playable_set(&conn, "01SET0000000000000000005")
        .unwrap()
        .expect("a complete set is playable");

    assert_eq!(found.set_id, "01SET0000000000000000005");
    assert_eq!(found.total, 4096);
    assert_eq!(found.part_count, 1);
}

#[test]
fn looking_up_a_set_that_is_not_playable_finds_nothing() {
    let (_d, conn) = open();
    let row =
        SetRow::from_caption(&caption("01SET0000000000000000006", 1, 1000), 1_700_000_000).unwrap();
    sets::insert_set(&conn, &row).unwrap();

    assert!(
        mediagram::serve::catalog::playable_set(&conn, "01SET0000000000000000006")
            .unwrap()
            .is_none()
    );
    assert!(
        mediagram::serve::catalog::playable_set(&conn, "01NOSUCHSET00000000000001")
            .unwrap()
            .is_none()
    );
}
