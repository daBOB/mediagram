use std::path::Path;
use std::sync::Arc;
use std::time::{Duration, SystemTime};

use rusqlite::{Connection, params};
use sha2::{Digest, Sha256};

use super::*;
use crate::api::Core;

fn core(dir: &Path) -> Arc<Core> {
    Core::at(dir)
}

fn index_at(dir: &Path) -> Connection {
    let current = dir.join("catalog").join("current");
    std::fs::create_dir_all(&current).unwrap();
    let conn = Connection::open(current.join("library.db")).unwrap();
    for stmt in mlib_spec::schema::migrations_up_to(mlib_spec::schema::SCHEMA_VERSION) {
        conn.execute(stmt, []).unwrap();
    }
    conn
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

fn add_bundle(conn: &Connection, set_id: &str, chat_id: i64, message_id: i64, sha256: &str, bytes: i64) {
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
    assert_eq!(core.clone().subtitle_text("s1".into(), 0).await.as_deref(), Some("de text"));
    assert_eq!(core.subtitle_text("s1".into(), 1).await.as_deref(), Some("en text"));
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

    assert_eq!(plan.iter().map(|(id, _)| id.as_str()).collect::<Vec<_>>(), vec!["c1", "c3"]);
}

#[tokio::test]
async fn a_bad_sha_shape_or_an_oversize_bundle_is_refused_before_any_fetch() {
    let dir = tempfile::tempdir().unwrap();
    let core = core(dir.path());
    let bad_shape = BundleRef { chat_id: -1, message_id: 1, bytes: 10, sha256: "not-hex".into() };
    let oversize = BundleRef {
        chat_id: -1,
        message_id: 1,
        bytes: mlib_spec::subtitle_bundle::MAX_COMPRESSED_BYTES as u64 + 1,
        sha256: "a".repeat(64),
    };

    assert!(cache::fetch(&core, "s1", &bad_shape).await.is_none());
    assert!(cache::fetch(&core, "s1", &oversize).await.is_none());
}

#[tokio::test]
async fn a_cached_bundle_is_read_without_any_network_route() {
    let dir = tempfile::tempdir().unwrap();
    let core = core(dir.path());
    let doc = mlib_spec::subtitle_bundle::Bundle {
        v: mlib_spec::subtitle_bundle::BUNDLE_VERSION,
        set: "s1".into(),
        tracks: vec![mlib_spec::subtitle_bundle::BundleTrack {
            lang: "de".into(),
            forced: false,
            sdh: false,
            label: "Deutsch".into(),
            source: "embedded".into(),
            codec: "webvtt".into(),
            vtt: "WEBVTT\n\nHallo".into(),
        }],
    };
    let bytes = mlib_spec::subtitle_bundle::encode(&doc);
    let sha = hex::encode(Sha256::digest(&bytes));
    std::fs::create_dir_all(cache::dir(&core)).unwrap();
    std::fs::write(cache::cached_path(&core, &sha), &bytes).unwrap();
    // A route that would fail if it were ever dialled — proving the hit
    // never reaches it.
    let bundle = BundleRef { chat_id: -999, message_id: -999, bytes: bytes.len() as u64, sha256: sha };

    let decoded = cache::fetch(&core, "s1", &bundle).await.unwrap();

    assert_eq!(decoded.tracks[0].vtt, "WEBVTT\n\nHallo");
}

/// A hit that fails to decode is treated as a miss and a refetch is
/// attempted — here failing cleanly for want of a route, the same as the
/// no-route case above, rather than serving the corrupt bytes.
#[tokio::test]
async fn a_corrupt_cached_file_is_never_served_and_a_refetch_is_attempted() {
    let dir = tempfile::tempdir().unwrap();
    let core = core(dir.path());
    let sha = "b".repeat(64);
    std::fs::create_dir_all(cache::dir(&core)).unwrap();
    std::fs::write(cache::cached_path(&core, &sha), b"not a bundle").unwrap();
    let bundle = BundleRef { chat_id: -1, message_id: 1, bytes: 10, sha256: sha };

    assert!(cache::fetch(&core, "s1", &bundle).await.is_none());
}

#[tokio::test]
async fn trim_to_budget_evicts_the_least_recently_used_files_first() {
    let dir = tempfile::tempdir().unwrap();
    let core = core(dir.path());
    std::fs::create_dir_all(cache::dir(&core)).unwrap();
    for (name, age_secs) in [("old.json.gz", 20), ("mid.json.gz", 10), ("new.json.gz", 0)] {
        let path = cache::dir(&core).join(name);
        std::fs::write(&path, vec![0u8; 10]).unwrap();
        let mtime = SystemTime::now() - Duration::from_secs(age_secs);
        std::fs::File::open(&path).unwrap().set_modified(mtime).unwrap();
    }

    cache::trim_to_budget(&core, 15);

    let remaining: Vec<String> = std::fs::read_dir(cache::dir(&core))
        .unwrap()
        .flatten()
        .map(|e| e.file_name().into_string().unwrap())
        .collect();
    assert!(!remaining.contains(&"old.json.gz".to_string()), "{remaining:?}");
    assert!(remaining.contains(&"new.json.gz".to_string()), "{remaining:?}");
}

#[tokio::test]
async fn locks_serialize_the_same_sha_but_leave_others_free() {
    let locks = cache::Locks::default();
    let a1 = locks.get("sha-a");
    let a2 = locks.get("sha-a");
    let b1 = locks.get("sha-b");

    let _held = a1.lock_owned().await;

    assert!(a2.try_lock_owned().is_err());
    assert!(b1.try_lock_owned().is_ok());
}
