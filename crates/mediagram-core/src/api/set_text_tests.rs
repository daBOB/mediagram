use rusqlite::{Connection, params};

use super::*;
use crate::api::test_support::index_at;

fn add_text(conn: &Connection, set_id: &str, kind: &str, lang: &str, body: &str) {
    conn.execute(
        "INSERT OR IGNORE INTO sets(set_id, kind, container, total, part_count, status, created_at, spec_version)
         VALUES (?1, 'movie', 'mkv', 0, 0, 'complete', 0, 1)",
        [set_id],
    )
    .unwrap();
    conn.execute(
        "INSERT INTO assets(set_id, kind, lang, body) VALUES (?1, ?2, ?3, ?4)",
        params![set_id, kind, lang, body],
    )
    .unwrap();
}

#[tokio::test]
async fn a_sets_summary_is_read_from_the_installed_index() {
    let dir = tempfile::tempdir().unwrap();
    let conn = index_at(dir.path());
    add_text(&conn, "s1", "summary", "", "A heist goes wrong.");
    drop(conn);
    let core = Core::at(dir.path());

    let summary = core
        .clone()
        .set_text("s1".into(), "summary".into(), "".into())
        .await;
    let missing = core
        .set_text("s2".into(), "summary".into(), "".into())
        .await;

    assert_eq!(summary.as_deref(), Some("A heist goes wrong."));
    assert_eq!(missing, None);
}

/// A subtitle is read by its track through `subtitle_text`; asking for one
/// here by language answers nothing, even where an inline track sits in
/// the index under that language.
#[tokio::test]
async fn a_subtitle_is_never_answered_as_a_sets_text() {
    let dir = tempfile::tempdir().unwrap();
    let conn = index_at(dir.path());
    add_text(&conn, "s1", "subtitle", "en", "WEBVTT\n\nHello");
    drop(conn);
    let core = Core::at(dir.path());

    let text = core
        .set_text("s1".into(), "subtitle".into(), "en".into())
        .await;

    assert_eq!(text, None);
}

#[tokio::test]
async fn with_no_catalog_or_an_unreadable_one_there_is_no_text() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let ask = || {
        core.clone()
            .set_text("s1".into(), "summary".into(), "".into())
    };

    let before_any_install = ask().await;
    let current = dir.path().join("catalog").join("current");
    std::fs::create_dir_all(&current).unwrap();
    std::fs::write(current.join("library.db"), b"not a database").unwrap();
    let unreadable = ask().await;

    assert_eq!(before_any_install, None);
    assert_eq!(unreadable, None);
}
