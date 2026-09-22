//! The narrow surface Kotlin calls through UniFFI. No network calls here:
//! these exercise only what a caller can observe without ever reaching
//! Telegram — a fresh install's authorization state, and lookups against a
//! catalog that either has never been refreshed or was built by hand.

use std::path::Path;

use rusqlite::Connection;

fn core(dir: &Path) -> std::sync::Arc<mediagram_core::api::Core> {
    mediagram_core::api::Core::new(dir.display().to_string(), 1, "test-hash".into())
}

/// Builds `<dir>/catalog/current/library.db` directly — bypassing
/// `refresh_catalog`, which needs the network — with one set inserted, so a
/// lookup for a *different* id exercises "found the catalog, did not find
/// this set" rather than "found no catalog at all".
fn seed_one_set(dir: &Path, set_id: &str) {
    let current = dir.join("catalog").join("current");
    std::fs::create_dir_all(&current).unwrap();
    let conn = Connection::open(current.join("library.db")).unwrap();
    for stmt in mlib_spec::schema::migrations_up_to(mlib_spec::schema::SCHEMA_VERSION) {
        conn.execute(stmt, []).unwrap();
    }
    conn.execute(
        "INSERT INTO sets(set_id, kind, container, total, part_count, status, created_at, spec_version)
         VALUES (?1, 'movie', 'mkv', 0, 0, 'complete', 0, 1)",
        [set_id],
    )
    .unwrap();
}

#[test]
fn a_fresh_core_is_not_authorized() {
    let dir = tempfile::tempdir().unwrap();
    assert!(!core(dir.path()).is_authorized());
}

#[tokio::test]
async fn reading_an_unknown_set_with_no_catalog_at_all_is_not_found() {
    let dir = tempfile::tempdir().unwrap();
    let err = core(dir.path()).total_size("nosuchset".into()).await.unwrap_err();
    assert!(matches!(err, mediagram_core::api::CoreError::NotFound(_)));
}

/// The case the earlier version of this test claimed to cover but never
/// reached: a catalog exists, and the set asked for still is not in it.
#[tokio::test]
async fn reading_a_set_that_is_not_in_an_existing_catalog_is_not_found() {
    let dir = tempfile::tempdir().unwrap();
    seed_one_set(dir.path(), "01SETHELD00000000000000001");

    let err = core(dir.path())
        .total_size("01NOSUCHSET0000000000000001".into()).await
        .unwrap_err();
    assert!(matches!(err, mediagram_core::api::CoreError::NotFound(_)));
    // And the one that *is* there is found, so "not found" really is about
    // this id and not a broken catalog.
    assert_eq!(
        core(dir.path())
            .total_size("01SETHELD00000000000000001".into()).await
            .unwrap(),
        0
    );
}

#[tokio::test]
async fn listing_sets_before_any_refresh_is_empty_not_an_error() {
    let dir = tempfile::tempdir().unwrap();
    assert_eq!(core(dir.path()).list_sets().await.unwrap(), Vec::new());
}

#[test]
fn a_poster_key_of_the_wrong_shape_is_never_looked_up() {
    let dir = tempfile::tempdir().unwrap();
    assert_eq!(core(dir.path()).poster_path("../escape".into()), None);
}
