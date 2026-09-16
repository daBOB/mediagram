//! Text that belongs to a set: subtitles, and a summary when there is one.
//!
//! These live in the index rather than as their own channel messages. A whole
//! course's subtitles are about 2 MB, which rides the published package
//! without noticing, and it means a player can show a summary or attach a
//! subtitle track with no Telegram round trip at all.

use mediagram::index::{assets, db};

const SET: &str = "01SET0000000000000000001";

fn index() -> (tempfile::TempDir, rusqlite::Connection) {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    conn.execute(
        "INSERT INTO sets(set_id, kind, container, total, part_count, status, created_at, spec_version)
         VALUES (?1, 'tut', 'mp4', 10, 1, 'complete', 1, 4)",
        [SET],
    )
    .unwrap();
    (dir, conn)
}

#[test]
fn a_summary_is_stored_and_read_back() {
    let (_d, conn) = index();

    assets::put(&conn, SET, assets::Kind::Summary, "", "Was du hier lernst.").unwrap();

    assert_eq!(
        assets::get(&conn, SET, assets::Kind::Summary, "")
            .unwrap()
            .as_deref(),
        Some("Was du hier lernst.")
    );
}

#[test]
fn subtitles_are_kept_per_language() {
    let (_d, conn) = index();

    assets::put(
        &conn,
        SET,
        assets::Kind::Subtitle,
        "deu",
        "WEBVTT\n\ndeutsch",
    )
    .unwrap();
    assets::put(
        &conn,
        SET,
        assets::Kind::Subtitle,
        "eng",
        "WEBVTT\n\nenglish",
    )
    .unwrap();

    assert_eq!(
        assets::get(&conn, SET, assets::Kind::Subtitle, "deu")
            .unwrap()
            .as_deref(),
        Some("WEBVTT\n\ndeutsch")
    );
    assert_eq!(assets::languages(&conn, SET).unwrap(), vec!["deu", "eng"]);
}

/// Re-uploading a lesson should correct its subtitle, not accumulate copies.
#[test]
fn storing_the_same_asset_twice_replaces_it() {
    let (_d, conn) = index();

    assets::put(&conn, SET, assets::Kind::Summary, "", "first").unwrap();
    assets::put(&conn, SET, assets::Kind::Summary, "", "second").unwrap();

    assert_eq!(
        assets::get(&conn, SET, assets::Kind::Summary, "")
            .unwrap()
            .as_deref(),
        Some("second")
    );
    assert_eq!(assets::count(&conn).unwrap(), 1);
}

#[test]
fn a_set_with_no_assets_reports_none() {
    let (_d, conn) = index();

    assert_eq!(
        assets::get(&conn, SET, assets::Kind::Summary, "").unwrap(),
        None
    );
    assert!(assets::languages(&conn, SET).unwrap().is_empty());
    assert!(!assets::has_summary(&conn, SET).unwrap());
}

#[test]
fn has_summary_answers_without_reading_the_text() {
    let (_d, conn) = index();
    assets::put(&conn, SET, assets::Kind::Summary, "", "something").unwrap();

    assert!(assets::has_summary(&conn, SET).unwrap());
}

/// The index is snapshotted to the channel and published in a package with a
/// 64 MB ceiling. One oversized file should be refused rather than discovered
/// later as a package that will not fit.
#[test]
fn an_oversized_asset_is_refused() {
    let (_d, conn) = index();
    let huge = "x".repeat(assets::MAX_ASSET_BYTES + 1);

    let refused = assets::put(&conn, SET, assets::Kind::Subtitle, "deu", &huge);

    assert!(refused.is_err());
    assert_eq!(assets::count(&conn).unwrap(), 0);
}

/// Deleting a set must not leave its text behind.
#[test]
fn assets_go_when_their_set_goes() {
    let (_d, conn) = index();
    assets::put(&conn, SET, assets::Kind::Summary, "", "text").unwrap();

    conn.execute("PRAGMA foreign_keys = ON", []).unwrap();
    conn.execute("DELETE FROM sets WHERE set_id = ?1", [SET])
        .unwrap();

    assert_eq!(assets::count(&conn).unwrap(), 0);
}
