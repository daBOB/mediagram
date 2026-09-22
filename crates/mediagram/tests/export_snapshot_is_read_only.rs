//! The export must not write to the index it is copying. `snapshot_to` does
//! (it records `last_push_at`), which is correct for `push-index` and wrong
//! for an export, so the copy used by the export is a separate function.

use mediagram::export::stage::Staging;
use mediagram::index::{db, snapshot};
use std::os::unix::fs::PermissionsExt;

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

/// The defect this file exists to prevent, exercised through the path the
/// command actually uses. A WAL is present and uncheckpointed, as it is
/// whenever another process holds the index open, which is the only case
/// where the difference is observable.
#[test]
fn staging_copies_the_index_without_writing_to_it_even_with_a_live_wal() {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    conn.execute(
        "INSERT INTO meta(key, value) VALUES('probe', 'x')
         ON CONFLICT(key) DO UPDATE SET value = excluded.value",
        [],
    )
    .unwrap();
    // A second connection keeps the WAL from being folded away on close.
    let live = dir.path().join("library.db");
    let holder = rusqlite::Connection::open(&live).unwrap();
    holder
        .query_row("SELECT COUNT(*) FROM sets", [], |r| r.get::<_, i64>(0))
        .unwrap();

    let before = digest_of(&live);

    let staging = Staging::create(dir.path(), "export-staging").unwrap();
    let reader = rusqlite::Connection::open(&live).unwrap();
    let bytes = staging.copy_index(&reader).unwrap();

    assert!(bytes > 0);
    assert_eq!(
        digest_of(&live),
        before,
        "copying the index for an export must not write to it"
    );
}

/// Staged files hold the private channel id and every message id, so the
/// directory itself must not be group- or world-readable.
#[test]
fn a_staging_directory_is_owner_only() {
    let parent = tempfile::tempdir().unwrap();
    let staging = Staging::create(parent.path(), "test_stage").unwrap();

    let mode = std::fs::metadata(staging.path()).unwrap().permissions().mode();
    assert_eq!(mode & 0o777, 0o700);
}

/// Staging is removed whether the export succeeds or fails, so nothing
/// decrypted is ever left behind on disk.
#[test]
fn a_staging_directory_is_removed_when_dropped() {
    let parent = tempfile::tempdir().unwrap();
    let stage_path = {
        let staging = Staging::create(parent.path(), "removable").unwrap();
        staging.path().to_path_buf()
    };

    assert!(
        !stage_path.exists(),
        "staging dir should be removed on drop"
    );
}

/// A staging directory left behind by a crash still has to be usable on the
/// next run, so `create` clears it instead of failing or reusing its
/// contents.
#[test]
fn a_stale_staging_directory_from_a_crash_is_cleared() {
    let parent = tempfile::tempdir().unwrap();
    let stage_name = "stale_dir";

    let stale_path = parent.path().join(stage_name);
    std::fs::create_dir_all(&stale_path).unwrap();
    std::fs::write(stale_path.join("old_file.txt"), b"old data").unwrap();

    let staging = Staging::create(parent.path(), stage_name).unwrap();

    assert!(!staging.path().join("old_file.txt").exists());
    assert!(staging.path().is_dir());
}

/// Once dropped, a staging directory can be created again at the same path
/// without the second `create` seeing anything left by the first.
#[test]
fn a_staging_directory_can_be_recreated_after_being_dropped() {
    let parent = tempfile::tempdir().unwrap();

    let first = Staging::create(parent.path(), "idempotent").unwrap();
    drop(first);

    let second = Staging::create(parent.path(), "idempotent").unwrap();
    assert!(second.path().exists());
}

#[test]
fn a_staging_directory_name_has_no_practical_length_limit() {
    let parent = tempfile::tempdir().unwrap();
    let long_name = "a".repeat(100);

    let staging = Staging::create(parent.path(), &long_name).unwrap();
    assert!(staging.path().exists());
}
