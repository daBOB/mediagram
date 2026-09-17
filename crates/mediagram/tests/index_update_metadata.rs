//! Writing a correction back to the index.
//!
//! Only the words move. The set id, the part geometry, the hashes, the total
//! and the status describe the bytes in the channel: `verify` checks them and
//! a player seeks with them, so an update that touched them would turn a
//! correction into corruption.

use mediagram::index::{db, parts, sets};
use mlib_spec::caption::{Caption, Episode, Kind, Part};
use mlib_spec::ids::ProviderIds;

const SET: &str = "01SET0000000000000000001";

fn caption() -> Caption {
    Caption {
        t: Kind::Ep,
        ids: ProviderIds {
            tmdb: Some(240459),
            tvdb: None,
            imdb: None,
        },
        cid: None,
        show: Some("Spartacus: House of Ashur".into()),
        chap: None,
        path: None,
        title: Some("Forsaken".into()),
        year: Some(2025),
        s: Some(1),
        e: Some(Episode::Single(2)),
        abs: None,
        q: Some("1080p".into()),
        hdr: None,
        container: "mkv".into(),
        vcodec: Some("h264".into()),
        acodec: Some("aac".into()),
        alang: vec!["deu".into()],
        slang: vec![],
        dur: Some(3000),
        variant: None,
        set: SET.into(),
        part: Part {
            i: 0,
            n: 1,
            off: 0,
            len: 3000,
            sha256: "a".repeat(64),
        },
        total: 3000,
    }
}

fn seeded() -> (tempfile::TempDir, rusqlite::Connection) {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    let row = sets::SetRow::from_caption(&caption(), 1_700_000_000).unwrap();
    sets::insert_set(&conn, &row).unwrap();
    parts::insert_parts(
        &conn,
        SET,
        &[mlib_spec::PartRange {
            idx: 0,
            off: 0,
            len: 3000,
        }],
    )
    .unwrap();
    parts::mark_done(&conn, SET, 0, -1001, 100, 900, &"a".repeat(64)).unwrap();
    sets::set_hash_and_complete(&conn, SET, &"b".repeat(64)).unwrap();
    (dir, conn)
}

#[test]
fn a_corrected_title_is_written() {
    let (_d, conn) = seeded();
    let mut row = sets::get_set(&conn, SET).unwrap().unwrap();
    row.title = Some("Verlassen".into());

    sets::update_metadata(&conn, &row).unwrap();

    assert_eq!(
        sets::get_set(&conn, SET).unwrap().unwrap().title.as_deref(),
        Some("Verlassen")
    );
}

/// The whole point of the guard: a row carrying wrong byte facts must not be
/// able to write them.
#[test]
fn the_bytes_are_not_touched_even_by_a_row_that_claims_otherwise() {
    let (_d, conn) = seeded();
    let before = sets::get_set(&conn, SET).unwrap().unwrap();

    let mut lying = before.clone();
    lying.title = Some("Verlassen".into());
    lying.total = 999;
    lying.part_count = 42;
    lying.set_hash = Some("c".repeat(64));
    lying.status = "pending".into();
    lying.container = "avi".into();
    sets::update_metadata(&conn, &lying).unwrap();

    let after = sets::get_set(&conn, SET).unwrap().unwrap();
    assert_eq!(after.title.as_deref(), Some("Verlassen"), "the words moved");
    assert_eq!(after.total, before.total);
    assert_eq!(after.part_count, before.part_count);
    assert_eq!(after.set_hash, before.set_hash);
    assert_eq!(after.status, before.status);
    assert_eq!(after.container, before.container);
}

#[test]
fn the_set_remains_playable_after_a_correction() {
    let (_d, conn) = seeded();
    let mut row = sets::get_set(&conn, SET).unwrap().unwrap();
    row.title = Some("Verlassen".into());
    sets::update_metadata(&conn, &row).unwrap();

    let playable: i64 = conn
        .query_row(
            &format!(
                "SELECT COUNT(*) FROM sets s WHERE {}",
                mlib_spec::schema::PLAYABLE_SQL
            ),
            [],
            |r| r.get(0),
        )
        .unwrap();
    assert_eq!(playable, 1);
}

#[test]
fn every_uploaded_part_is_listed_in_order() {
    let (_d, conn) = seeded();

    let all = parts::all_parts(&conn, SET).unwrap();

    assert_eq!(all.len(), 1);
    assert_eq!(all[0].idx, 0);
    assert_eq!(all[0].message_id, Some(100));
}

#[test]
fn an_unknown_set_has_no_parts() {
    let (_d, conn) = seeded();
    assert!(
        parts::all_parts(&conn, "01NOSUCHSET00000000000001")
            .unwrap()
            .is_empty()
    );
}
