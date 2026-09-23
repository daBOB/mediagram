//! A channel snapshot is read at whatever schema its uploader wrote, down to
//! the floor; this machine's own index is held to the current schema.
//!
//! The web player runs `mediagram posters --index <snapshot>` after every
//! new index. When the schema moved to v7, that call started refusing every
//! snapshot from an uploader still on v6, and new titles stopped getting
//! covers. This is that case.

use mediagram::index::db;
use mlib_spec::schema::{OLDEST_READABLE_SCHEMA, SCHEMA_VERSION};

/// An index recording `version` as its schema, as an uploader at that
/// version leaves it. Built by this crate's own opener, then marked older: the
/// gate reads only the recorded version.
fn index_at(dir: &std::path::Path, version: i64) -> std::path::PathBuf {
    let conn = db::open(dir).unwrap();
    conn.execute(
        "UPDATE meta SET value = ?1 WHERE key = 'schema_version'",
        [version.to_string()],
    )
    .unwrap();
    drop(conn);
    dir.join(mlib_spec::schema::INDEX_FILE)
}

#[test]
fn a_snapshot_one_schema_behind_is_still_read() {
    let dir = tempfile::tempdir().unwrap();
    let path = index_at(dir.path(), OLDEST_READABLE_SCHEMA);
    const { assert!(OLDEST_READABLE_SCHEMA < SCHEMA_VERSION, "the floor is below the current schema") };
    let conn = db::open_snapshot(&path).expect("a v6 snapshot opens");
    let sets: i64 = conn.query_row("SELECT COUNT(*) FROM sets", [], |row| row.get(0)).unwrap();
    assert_eq!(sets, 0);
}

#[test]
fn a_snapshot_below_the_floor_is_refused_and_says_why() {
    let dir = tempfile::tempdir().unwrap();
    let path = index_at(dir.path(), OLDEST_READABLE_SCHEMA - 1);
    let err = db::open_snapshot(&path).expect_err("too old to read").to_string();
    assert!(err.contains(&format!("expects v{OLDEST_READABLE_SCHEMA}")), "{err}");
}
