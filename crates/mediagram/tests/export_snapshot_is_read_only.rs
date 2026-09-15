//! The export must not write to the index it is copying. `snapshot_to` does
//! (it records `last_push_at`), which is correct for `push-index` and wrong
//! for an export, so the copy used by the export is a separate function.

use mediagram::index::{db, snapshot};

fn digest_of(path: &std::path::Path) -> String {
    use sha2::{Digest, Sha256};
    let bytes = std::fs::read(path).unwrap();
    hex::encode(Sha256::digest(&bytes))
}

#[test]
fn copying_for_export_leaves_the_live_database_byte_identical() {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    snapshot::checkpoint(&conn).unwrap();
    drop(conn);

    let live = dir.path().join("library.db");
    let before = digest_of(&live);

    let conn = rusqlite::Connection::open(&live).unwrap();
    let dest = dir.path().join("export.db");
    snapshot::copy_to(&conn, &dest).unwrap();
    drop(conn);

    assert_eq!(digest_of(&live), before, "export must not modify the index");
    assert!(dest.exists());
}

#[test]
fn copying_for_export_does_not_record_a_push_time() {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    let dest = dir.path().join("export.db");

    snapshot::copy_to(&conn, &dest).unwrap();

    let copied = rusqlite::Connection::open(&dest).unwrap();
    let pushed: Option<String> = copied
        .query_row(
            "SELECT value FROM meta WHERE key = 'last_push_at'",
            [],
            |r| r.get(0),
        )
        .ok();
    assert_eq!(pushed, None, "an export is not a push");
}

/// `push-index` must keep recording the push time, since the pinned copy's
/// freshness signal is read from it.
#[test]
fn the_push_path_still_records_a_push_time() {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    let dest = dir.path().join("push.db");

    snapshot::snapshot_to(&conn, &dest).unwrap();

    let copied = rusqlite::Connection::open(&dest).unwrap();
    let pushed: String = copied
        .query_row(
            "SELECT value FROM meta WHERE key = 'last_push_at'",
            [],
            |r| r.get(0),
        )
        .unwrap();
    assert!(pushed.parse::<i64>().unwrap() > 0);
}
