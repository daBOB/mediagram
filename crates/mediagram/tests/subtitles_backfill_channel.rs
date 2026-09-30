//! The backfill that reads a title's subtitles from the copy in the channel:
//! which sets it picks, in what order, and — through the real router over a
//! local byte source — that a bundle comes out of the served bytes.

mod support;

use std::sync::Arc;
use std::sync::atomic::AtomicBool;
use std::time::Duration;

use mediagram::commands::subtitles::backfill_channel::{Candidate, candidates, read_and_send};
use mediagram::commands::subtitles::loopback::Loopback;
use mediagram::commands::subtitles::session::Session;
use mediagram::index::{db, subtitles};
use mediagram_core::catalog::PartLocation;
use mediagram_core::range::{CHUNK, Step};
use mediagram_core::transport::stream::{ByteSource, ByteStream};
use mlib_spec::subtitle_bundle::decode;
use rusqlite::Connection;
use support::channel::FakeChannel;
use support::media::{HALLO, Sub, make_mkv};
use tempfile::TempDir;

fn add(conn: &Connection, set: &str, kind: &str, container: &str, slang: &str, total: u64) {
    conn.execute(
        "INSERT INTO sets(set_id, kind, container, total, part_count, status, slang, created_at, spec_version)
         VALUES (?1, ?2, ?3, ?4, 1, 'complete', ?5, 0, 1)",
        rusqlite::params![set, kind, container, total, slang],
    )
    .unwrap();
    conn.execute(
        "INSERT INTO parts(set_id, idx, byte_offset, byte_length, chat_id, message_id, doc_id, sha256, status)
         VALUES (?1, 0, 0, ?2, -1001, 100, 100, 'ab', 'done')",
        rusqlite::params![set, total],
    )
    .unwrap();
}

fn ids(found: &[Candidate]) -> Vec<(&str, bool)> {
    found.iter().map(|c| (c.set_id.as_str(), c.redo)).collect()
}

#[test]
fn candidates_are_mp4_first_and_skip_what_has_subtitles_or_none() {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    add(&conn, "a-mkv", "movie", "mkv", r#"["de"]"#, 10);
    add(&conn, "b-mp4", "ep", "mp4", r#"["en"]"#, 10);
    add(&conn, "c-mp4-fr", "movie", "mp4", r#"["fr"]"#, 10);
    add(&conn, "d-doc", "doc", "mp4", r#"["de"]"#, 10);
    add(&conn, "e-bundled", "movie", "mp4", r#"["de"]"#, 10);
    add(&conn, "f-none", "movie", "mp4", r#"["de"]"#, 10);
    add(&conn, "g-mp4", "tut", "mp4", r#"["de","en"]"#, 10);
    conn.execute(
        "INSERT INTO subtitle_files(set_id, chat_id, message_id, bytes, sha256, uploaded_at)
         VALUES ('e-bundled', 1, 7, 1, 'x', 5)",
        [],
    )
    .unwrap();
    db::set_meta(&conn, "subs-none:f-none", "1").unwrap();
    // Still inline: move-inline's to do, not a read from the channel.
    add(&conn, "h-inline", "tut", "mp4", r#"["de"]"#, 10);
    mediagram::index::assets::put(&conn, "h-inline", mediagram::index::assets::Kind::Subtitle, "deu", "WEBVTT\n").unwrap();

    let without_mkv = candidates(&conn, false, &[]).unwrap();
    assert_eq!(ids(&without_mkv), [("b-mp4", false), ("g-mp4", false)]);

    let with_mkv = candidates(&conn, true, &[]).unwrap();
    assert_eq!(
        ids(&with_mkv),
        [("b-mp4", false), ("g-mp4", false), ("a-mkv", false)]
    );

    // A redo comes first and is found again even with a bundle or a verdict.
    let redo = candidates(&conn, false, &["e-bundled".into(), "f-none".into()]).unwrap();
    assert_eq!(ids(&redo)[..2], [("e-bundled", true), ("f-none", true)]);
}

/// Serves one known file as the single part of a set.
struct OnePart(Vec<u8>);

impl ByteSource for OnePart {
    fn stream(&self, _: Vec<PartLocation>, steps: Vec<Step>) -> ByteStream {
        let mut out = Vec::new();
        for step in steps {
            let from = (step.skip_chunks as u64 * CHUNK + step.head_drop) as usize;
            out.extend_from_slice(&self.0[from..from + step.take as usize]);
        }
        Box::pin(futures::stream::once(async move {
            Ok(axum::body::Bytes::from(out))
        }))
    }
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

#[tokio::test]
async fn a_bundle_is_made_from_the_bytes_the_loopback_server_serves() {
    let (media, state) = (tempfile::tempdir().unwrap(), tempfile::tempdir().unwrap());
    let german = Sub {
        srt: HALLO,
        codec: "subrip",
        lang: "ger",
        title: None,
        forced: false,
    };
    let plain = Sub {
        srt: HALLO,
        codec: "subrip",
        lang: "fre",
        title: None,
        forced: false,
    };
    let (Some(with), Some(without)) = (
        make_mkv(media.path(), "with.mkv", &[german]),
        make_mkv(media.path(), "without.mkv", &[plain]),
    ) else {
        return;
    };
    let conn = db::open(state.path()).unwrap();
    let (with_bytes, without_bytes) = (
        std::fs::read(with).unwrap(),
        std::fs::read(without).unwrap(),
    );
    add(
        &conn,
        "with",
        "movie",
        "mkv",
        r#"["de"]"#,
        with_bytes.len() as u64,
    );
    add(
        &conn,
        "without",
        "movie",
        "mkv",
        r#"["de"]"#,
        without_bytes.len() as u64,
    );
    // Both sets are served from one source; the part's message id tells them apart.
    conn.execute(
        "UPDATE parts SET message_id = 200 WHERE set_id = 'without'",
        [],
    )
    .unwrap();
    struct Either(OnePart, OnePart);
    impl ByteSource for Either {
        fn stream(&self, locations: Vec<PartLocation>, steps: Vec<Step>) -> ByteStream {
            if locations[0].message_id == 200 {
                self.1.stream(locations, steps)
            } else {
                self.0.stream(locations, steps)
            }
        }
    }
    let read_only = db::open_read_only(state.path(), "test").unwrap();
    let server = Loopback::start(
        read_only,
        Arc::new(Either(OnePart(with_bytes), OnePart(without_bytes))),
    )
    .await
    .unwrap();
    let channel = FakeChannel::new();
    let mut run = session(&conn, &channel, &state);
    let found = candidates(&conn, true, &[]).unwrap();

    let tally = read_and_send(&mut run, |id| server.stream_url(id), found, None).await;
    server.shutdown().await;

    assert_eq!((tally.bundled, tally.nothing, tally.failed), (1, 1, 0));
    let message = subtitles::bundle_message(&conn, "with").unwrap().unwrap();
    let bundle = decode(&channel.document(message as i32)).unwrap();
    assert_eq!(bundle.tracks[0].lang, "de");
    assert!(bundle.tracks[0].vtt.contains("Hallo Welt"));
    // The set without a German or English track is not read again.
    assert!(db::get_meta(&conn, "subs-none:without").unwrap().is_some());
    assert!(candidates(&conn, true, &[]).unwrap().is_empty());
}

/// Serves one file as the single part of a set, then fails at `limit`, as a
/// Telegram fetch error does mid-read: the response ends short of the
/// `Content-Length` it announced.
struct CutAt {
    file: Vec<u8>,
    limit: usize,
}

impl ByteSource for CutAt {
    fn stream(&self, _: Vec<PartLocation>, steps: Vec<Step>) -> ByteStream {
        let mut items: Vec<Result<axum::body::Bytes, std::io::Error>> = Vec::new();
        for step in steps {
            let from = (step.skip_chunks as u64 * CHUNK + step.head_drop) as usize;
            let to = from + step.take as usize;
            if to > self.limit {
                let kept = self.limit.saturating_sub(from);
                items.push(Ok(axum::body::Bytes::copy_from_slice(&self.file[from..from + kept])));
                items.push(Err(std::io::Error::other("the channel stopped answering")));
                break;
            }
            items.push(Ok(axum::body::Bytes::copy_from_slice(&self.file[from..to])));
        }
        Box::pin(futures::stream::iter(items))
    }
}

#[tokio::test]
async fn a_read_cut_short_is_a_failure_never_a_bundle_or_a_none_mark() {
    let (media, state) = (tempfile::tempdir().unwrap(), tempfile::tempdir().unwrap());
    let german = Sub { srt: HALLO, codec: "subrip", lang: "ger", title: None, forced: false };
    let Some(video) = make_mkv(media.path(), "cut.mkv", &[german]) else {
        return;
    };
    let file = std::fs::read(video).unwrap();
    // Halfway through, and just short of the end (past the subtitle samples).
    for limit in [file.len() / 2, file.len() - 64] {
        let conn = db::open(state.path()).unwrap();
        conn.execute("DELETE FROM sets", []).unwrap();
        add(&conn, "cut", "movie", "mkv", r#"["de"]"#, file.len() as u64);
        let read_only = db::open_read_only(state.path(), "test").unwrap();
        let source = CutAt { file: file.clone(), limit };
        let server = Loopback::start(read_only, Arc::new(source)).await.unwrap();
        let channel = FakeChannel::new();
        let mut run = session(&conn, &channel, &state);
        let found = candidates(&conn, true, &[]).unwrap();

        let tally = read_and_send(&mut run, |id| server.stream_url(id), found, None).await;
        server.shutdown().await;

        assert_eq!((tally.failed, tally.bundled, tally.nothing), (1, 0, 0), "cut at {limit}");
        assert_eq!(subtitles::bundle_message(&conn, "cut").unwrap(), None);
        assert_eq!(db::get_meta(&conn, "subs-none:cut").unwrap(), None);
        assert_eq!(channel.with(|c| c.sends), 0);
    }
}
