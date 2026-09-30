//! The backfill's sending mode over folders, against the in-memory channel
//! and ffmpeg-made files (those tests skip with a note when ffmpeg is not
//! on PATH).

mod support;

use std::path::{Path, PathBuf};
use std::sync::Arc;
use std::sync::atomic::AtomicBool;
use std::time::Duration;

use mediagram::commands::subtitles::backfill::{Sent, send_matches, send_set};
use mediagram::commands::subtitles::match_source::{Match, Verdict};
use mediagram::commands::subtitles::session::Session;
use mediagram::index::{db, subtitles};
use mediagram::subtitles::Input;
use rusqlite::Connection;
use support::channel::FakeChannel;
use support::media::{HALLO, Sub, make_mkv};
use tempfile::TempDir;

const SET: &str = "01J0000000000000000000BKF1";

fn index(dir: &TempDir) -> Connection {
    let conn = db::open(dir.path()).unwrap();
    conn.execute(
        "INSERT INTO sets(set_id, kind, container, total, part_count, created_at, spec_version)
         VALUES (?1, 'movie', 'mkv', 1, 1, 0, 1)",
        [SET],
    )
    .unwrap();
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

fn german() -> Sub<'static> {
    Sub {
        srt: HALLO,
        codec: "subrip",
        lang: "ger",
        title: None,
        forced: false,
    }
}

fn fallback(path: &Path) -> Vec<Match> {
    vec![Match {
        path: path.to_path_buf(),
        verdict: Verdict::Fallback(SET.into()),
    }]
}

#[tokio::test]
async fn a_set_with_a_bundle_is_skipped_without_reading_its_source() {
    let dir = tempfile::tempdir().unwrap();
    let (conn, channel) = (index(&dir), FakeChannel::new());
    let mut run = session(&conn, &channel, &dir);
    let source = Input::File(PathBuf::from("/nonexistent/film.mkv"));
    conn.execute(
        "INSERT INTO subtitle_files(set_id, chat_id, message_id, bytes, sha256, uploaded_at)
         VALUES (?1, 1, 7, 1, ?2, 5)",
        rusqlite::params![SET, "a".repeat(64)],
    )
    .unwrap();

    assert_eq!(
        send_set(&mut run, SET, &source, false).await.unwrap(),
        Sent::Skipped
    );
    // Recorded before this run started, so a redo is not a repeat of one:
    // it goes on to read the (missing) source.
    assert!(send_set(&mut run, SET, &source, true).await.is_err());
    assert_eq!(channel.with(|c| c.sends), 0);
}

#[tokio::test]
async fn a_fallback_match_is_sent_only_when_its_file_is_named() {
    let (media, state) = (tempfile::tempdir().unwrap(), tempfile::tempdir().unwrap());
    let Some(video) = make_mkv(media.path(), "film.mkv", &[german()]) else {
        return;
    };
    let (conn, channel) = (index(&state), FakeChannel::new());
    let mut run = session(&conn, &channel, &state);

    let unnamed = send_matches(&mut run, &fallback(&video), &[], &[]).await;
    assert_eq!((unnamed.bundled, channel.with(|c| c.sends)), (0, 0));

    let named = send_matches(
        &mut run,
        &fallback(&video),
        std::slice::from_ref(&video),
        &[],
    )
    .await;
    assert_eq!((named.bundled, channel.with(|c| c.sends)), (1, 1));
}

async fn redo_with(push: bool) -> (Vec<i64>, i64, i64) {
    let (media, state) = (tempfile::tempdir().unwrap(), tempfile::tempdir().unwrap());
    let video = make_mkv(media.path(), "film.mkv", &[german()]).expect("ffmpeg");
    let (conn, channel) = (index(&state), FakeChannel::new());
    let mut run = Session::new(
        &conn,
        &channel,
        state.path().to_path_buf(),
        push,
        100,
        Duration::ZERO,
        Arc::new(AtomicBool::new(false)),
    );
    let input = Input::File(video);
    send_set(&mut run, SET, &input, false).await.unwrap();
    let old = subtitles::bundle_message(&conn, SET).unwrap().unwrap();
    conn.execute("UPDATE subtitle_files SET uploaded_at = 1", []).unwrap();

    let redone = send_set(&mut run, SET, &input, true).await.unwrap();
    assert!(matches!(redone, Sent::Bundled(_)));
    // Done in this run now: a second redo of it is skipped.
    assert_eq!(send_set(&mut run, SET, &input, true).await.unwrap(), Sent::Skipped);
    run.finish().await.unwrap();

    let new = subtitles::bundle_message(&conn, SET).unwrap().unwrap();
    assert_ne!(new, old);
    let ids = channel.with(|c| c.messages.iter().map(|m| i64::from(m.id)).collect());
    (ids, old, new)
}

#[tokio::test]
async fn a_redo_deletes_the_old_message_once_the_new_index_is_published() {
    if !mediagram::media::test_fixtures::ffmpeg_required("redo") {
        return;
    }
    let (ids, old, new) = redo_with(true).await;
    assert!(!ids.contains(&old) && ids.contains(&new), "{ids:?}");
}

#[tokio::test]
async fn a_redo_without_a_publish_keeps_the_old_message() {
    if !mediagram::media::test_fixtures::ffmpeg_required("redo") {
        return;
    }
    let (ids, old, new) = redo_with(false).await;
    assert!(ids.contains(&old) && ids.contains(&new), "{ids:?}");
}
