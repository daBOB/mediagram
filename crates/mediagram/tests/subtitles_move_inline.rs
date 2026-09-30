//! Moving the lessons' inline subtitle rows into bundles, against the
//! in-memory channel: the rows go only after the bundle read back matches.

mod support;

use std::sync::Arc;
use std::sync::atomic::AtomicBool;
use std::time::Duration;

use mediagram::commands::subtitles::move_inline::move_sets;
use mediagram::commands::subtitles::session::Session;
use mediagram::index::{assets, db, subtitles};
use mlib_spec::subtitle_bundle::decode;
use rusqlite::Connection;
use support::channel::FakeChannel;
use tempfile::TempDir;

fn index(dir: &TempDir, sets: &[&str]) -> Connection {
    let conn = db::open(dir.path()).unwrap();
    for set in sets {
        conn.execute(
            "INSERT INTO sets(set_id, kind, container, total, part_count, created_at, spec_version)
             VALUES (?1, 'tut', 'mp4', 1, 1, 0, 1)",
            [set],
        )
        .unwrap();
        assets::put(
            &conn,
            set,
            assets::Kind::Subtitle,
            "eng",
            &format!("WEBVTT\n\nEN {set}"),
        )
        .unwrap();
        assets::put(
            &conn,
            set,
            assets::Kind::Subtitle,
            "deu",
            &format!("WEBVTT\n\nDE {set}"),
        )
        .unwrap();
        assets::put(&conn, set, assets::Kind::Summary, "en", "a summary").unwrap();
    }
    conn
}

fn session<'a>(
    conn: &'a Connection,
    channel: &'a FakeChannel,
    dir: &TempDir,
) -> Session<'a, FakeChannel> {
    Session::new(
        conn,
        channel,
        dir.path().to_path_buf(),
        false,
        100,
        Duration::ZERO,
        Arc::new(AtomicBool::new(false)),
    )
}

fn ids(sets: &[&str]) -> Vec<String> {
    sets.iter().map(|s| s.to_string()).collect()
}

#[tokio::test]
async fn rows_go_after_a_matching_read_back_and_numbering_is_kept() {
    let dir = tempfile::tempdir().unwrap();
    let conn = index(&dir, &["A"]);
    let channel = FakeChannel::new();

    let done = move_sets(&mut session(&conn, &channel, &dir), &ids(&["A"]))
        .await
        .unwrap();

    assert_eq!((done.moved, done.mismatched.len()), (1, 0));
    assert_eq!(channel.with(|c| c.downloads), 1);
    let message = subtitles::bundle_message(&conn, "A").unwrap().unwrap();
    let bundle = decode(&channel.document(message as i32)).unwrap();
    // `deu` sorts before `eng`: track 0 is German, as the inline fallback numbers it.
    let langs: Vec<_> = bundle
        .tracks
        .iter()
        .map(|t| (t.lang.as_str(), t.label.as_str()))
        .collect();
    assert_eq!(langs, [("de", "German"), ("en", "English")]);
    assert_eq!(bundle.tracks[0].vtt, "WEBVTT\n\nDE A");
    assert!(assets::inline_subtitles(&conn, "A").unwrap().is_empty());
    assert_eq!(
        assets::get(&conn, "A", assets::Kind::Summary, "en")
            .unwrap()
            .as_deref(),
        Some("a summary")
    );
}

#[tokio::test]
async fn a_mismatched_read_back_leaves_the_rows_and_records_nothing() {
    let dir = tempfile::tempdir().unwrap();
    let conn = index(&dir, &["A"]);
    let channel = FakeChannel::new();
    channel.with(|c| c.corrupt_downloads = true);

    let done = move_sets(&mut session(&conn, &channel, &dir), &ids(&["A"]))
        .await
        .unwrap();

    assert_eq!((done.moved, done.mismatched), (0, ids(&["A"])));
    assert_eq!(assets::inline_subtitles(&conn, "A").unwrap().len(), 2);
    assert_eq!(subtitles::bundle_message(&conn, "A").unwrap(), None);
}

#[tokio::test]
async fn the_run_stops_after_three_mismatches() {
    let dir = tempfile::tempdir().unwrap();
    let sets = ["A", "B", "C", "D"];
    let conn = index(&dir, &sets);
    let channel = FakeChannel::new();
    channel.with(|c| c.corrupt_downloads = true);

    let done = move_sets(&mut session(&conn, &channel, &dir), &ids(&sets))
        .await
        .unwrap();

    assert_eq!(done.mismatched.len(), 3);
    assert_eq!(channel.with(|c| c.sends), 3);
    assert_eq!(assets::sets_with_inline_subtitles(&conn).unwrap().len(), 4);
}

#[tokio::test]
async fn a_set_that_already_has_a_bundle_is_left_alone() {
    let dir = tempfile::tempdir().unwrap();
    let conn = index(&dir, &["A"]);
    let channel = FakeChannel::new();
    let mut run = session(&conn, &channel, &dir);
    move_sets(&mut run, &ids(&["A"])).await.unwrap();
    assets::put(&conn, "A", assets::Kind::Subtitle, "deu", "WEBVTT\n").unwrap();

    let again = move_sets(&mut run, &ids(&["A"])).await.unwrap();

    assert_eq!(again.moved, 0);
    assert_eq!(channel.with(|c| c.sends), 1);
}

#[tokio::test]
async fn dry_runs_of_every_mode_write_nothing() {
    use mediagram::commands::subtitles::{BackfillArgs, MoveInlineArgs, SubtitlesAction, run};
    let dir = tempfile::tempdir().unwrap();
    let conn = index(&dir, &["A"]);
    let mut cfg: mediagram::config::Config =
        toml::from_str("api_id = 1\napi_hash = 'offline'\nchannel = 'offline'\n").unwrap();
    cfg.data_dir = Some(dir.path().to_path_buf());
    let snapshot = |conn: &Connection| -> (i64, i64, i64) {
        let count = |sql: &str| conn.query_row(sql, [], |r| r.get(0)).unwrap();
        (
            count("SELECT COUNT(*) FROM assets"),
            count("SELECT COUNT(*) FROM subtitle_files"),
            count("SELECT COUNT(*) FROM meta"),
        )
    };
    let before = snapshot(&conn);

    let move_inline = MoveInlineArgs {
        dry_run: true,
        no_push: false,
    };
    run(&cfg, SubtitlesAction::MoveInline(move_inline))
        .await
        .unwrap();
    let backfill = BackfillArgs {
        folders: vec![],
        dry_run: true,
        accept_fallback: vec![],
        redo: vec![],
        no_push: false,
        channel: true,
        mkv: true,
        limit: None,
    };
    run(&cfg, SubtitlesAction::Backfill(backfill))
        .await
        .unwrap();

    assert_eq!(snapshot(&conn), before);
}

#[test]
fn more_than_one_upload_slot_is_refused() {
    use mediagram::commands::subtitles::session::ensure_single_slot;
    assert!(ensure_single_slot(1).is_ok());
    assert!(ensure_single_slot(2).unwrap_err().to_string().contains("upload_slots is 2"));
}
