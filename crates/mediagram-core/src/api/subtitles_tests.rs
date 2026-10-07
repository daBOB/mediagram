use std::path::Path;
use std::sync::Arc;

use rusqlite::{Connection, params};

use super::*;
use crate::api::Core;
use crate::api::test_support::index_at;

fn core(dir: &Path) -> Arc<Core> {
    Core::at(dir)
}

fn add_bare_set(conn: &Connection, set_id: &str) {
    conn.execute(
        "INSERT OR IGNORE INTO sets(set_id, kind, container, total, part_count, status, created_at, spec_version)
         VALUES (?1, 'movie', 'mkv', 0, 0, 'complete', 0, 1)",
        [set_id],
    )
    .unwrap();
}

fn add_lesson(conn: &Connection, set_id: &str, group_key: &str, season: i64, episode: &str) {
    conn.execute(
        "INSERT INTO sets(set_id, kind, container, total, part_count, status, created_at, spec_version, group_key, season, episode)
         VALUES (?1, 'tut', 'mkv', 0, 0, 'complete', 0, 1, ?2, ?3, ?4)",
        params![set_id, group_key, season, episode],
    )
    .unwrap();
}

fn add_legacy(conn: &Connection, set_id: &str, lang: &str, body: &str) {
    add_bare_set(conn, set_id);
    conn.execute(
        "INSERT INTO assets(set_id, kind, lang, body) VALUES (?1, 'subtitle', ?2, ?3)",
        params![set_id, lang, body],
    )
    .unwrap();
}

fn add_bundle(
    conn: &Connection,
    set_id: &str,
    chat_id: i64,
    message_id: i64,
    sha256: &str,
    bytes: i64,
) {
    add_bare_set(conn, set_id);
    conn.execute(
        "INSERT INTO subtitle_files(set_id, chat_id, message_id, bytes, sha256, uploaded_at) VALUES (?1, ?2, ?3, ?4, ?5, 1)",
        params![set_id, chat_id, message_id, bytes, sha256],
    )
    .unwrap();
}

#[tokio::test]
async fn subtitle_text_reads_a_legacy_track_by_its_sorted_position() {
    let dir = tempfile::tempdir().unwrap();
    let conn = index_at(dir.path());
    add_legacy(&conn, "s1", "en", "en text");
    add_legacy(&conn, "s1", "de", "de text");
    drop(conn);

    let core = core(dir.path());
    assert_eq!(
        core.clone().subtitle_text("s1".into(), 0).await.as_deref(),
        Some("de text")
    );
    assert_eq!(
        core.subtitle_text("s1".into(), 1).await.as_deref(),
        Some("en text")
    );
}

#[tokio::test]
async fn subtitle_text_is_none_for_a_missing_set_or_an_out_of_range_track() {
    let dir = tempfile::tempdir().unwrap();
    let conn = index_at(dir.path());
    add_legacy(&conn, "s1", "en", "en text");
    drop(conn);

    let core = core(dir.path());
    assert_eq!(core.clone().subtitle_text("nosuch".into(), 0).await, None);
    assert_eq!(core.subtitle_text("s1".into(), 9).await, None);
}

#[tokio::test]
async fn hold_subtitles_is_false_for_a_set_with_no_bundle() {
    let dir = tempfile::tempdir().unwrap();
    let conn = index_at(dir.path());
    add_legacy(&conn, "s1", "en", "en text");
    drop(conn);

    assert!(!core(dir.path()).hold_subtitles("s1".into()).await);
}

/// No library entry is recorded for the bundle's channel, so the fetch fails
/// resolving a route before it ever reaches Telegram — the same path a real
/// device takes for a channel it has lost access to. Neither call panics.
#[tokio::test]
async fn a_bundled_set_with_no_reachable_channel_fails_cleanly() {
    let dir = tempfile::tempdir().unwrap();
    let conn = index_at(dir.path());
    add_bundle(&conn, "s1", -1, 100, &"a".repeat(64), 10);
    drop(conn);

    let core = core(dir.path());
    assert!(!core.clone().hold_subtitles("s1".into()).await);
    assert_eq!(core.subtitle_text("s1".into(), 0).await, None);
}

#[test]
fn course_hold_plan_includes_the_opened_lesson_and_its_bundled_followers_in_order() {
    let dir = tempfile::tempdir().unwrap();
    let conn = index_at(dir.path());
    add_lesson(&conn, "c1", "course", 1, "1");
    add_lesson(&conn, "c2", "course", 1, "2");
    add_lesson(&conn, "c3", "course", 1, "3");
    add_bundle(&conn, "c1", -1, 10, &"1".repeat(64), 5);
    // c2 has no bundle yet: skipped, not fetched.
    add_bundle(&conn, "c3", -1, 30, &"3".repeat(64), 5);
    drop(conn);

    let plan = course_hold_plan(&core(dir.path()), "c1");

    assert_eq!(
        plan.iter().map(|(id, _)| id.as_str()).collect::<Vec<_>>(),
        vec!["c1", "c3"]
    );
}
