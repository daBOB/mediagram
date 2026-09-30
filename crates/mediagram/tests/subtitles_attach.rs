//! Giving a finished set its subtitle bundle, against ffmpeg-made files and
//! the in-memory channel. Skipped with a printed note when ffmpeg is not on
//! PATH.

mod support;

use mediagram::index::{assets, db, pins, subtitles as index_subtitles};
use mediagram::subtitles::{Input, attach, attach_and_report};
use mlib_spec::subtitle_bundle::{Bundle, decode};
use rusqlite::Connection;
use support::channel::{CHAT_ID, FakeChannel};
use support::media::{HALLO, HELLO, Sub, listing, make_mkv, patch_bytes};
use tempfile::TempDir;

const SET: &str = "01J0000000000000000000SUB1";

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

fn sent_bundle(channel: &FakeChannel) -> Bundle {
    let id = channel.with(|c| c.messages.last().expect("a message").id);
    decode(&channel.document(id)).unwrap()
}

fn sub<'a>(srt: &'a str, codec: &'a str, lang: &'a str) -> Sub<'a> {
    Sub {
        srt,
        codec,
        lang,
        title: None,
        forced: false,
    }
}

#[tokio::test]
async fn embedded_tracks_are_sent_as_one_bundle_and_recorded() {
    let (media, state) = (tempfile::tempdir().unwrap(), tempfile::tempdir().unwrap());
    let Some(video) = make_mkv(
        media.path(),
        "film.mkv",
        &[sub(HALLO, "subrip", "ger"), sub(HELLO, "ass", "eng")],
    ) else {
        return;
    };
    let before = listing(media.path());
    let conn = index(&state);
    assets::put(&conn, SET, assets::Kind::Subtitle, "deu", "WEBVTT\n").unwrap();
    let channel = FakeChannel::new();

    let done = attach(&conn, &channel, SET, &Input::File(video))
        .await
        .unwrap()
        .expect("a bundle");

    assert_eq!(done.labels, ["German", "English"]);
    assert_eq!(channel.with(|c| c.sends), 1);
    let bundle = sent_bundle(&channel);
    assert_eq!(bundle.set, SET);
    assert!(
        bundle.tracks[0].vtt.starts_with("WEBVTT") && bundle.tracks[0].vtt.contains("Hallo Welt")
    );
    assert_eq!(bundle.tracks[1].codec, "ass");
    assert!(
        bundle.tracks[1].vtt.contains("Hello world"),
        "{}",
        bundle.tracks[1].vtt
    );
    let message = channel.with(|c| c.messages[0].id);
    assert_eq!(
        index_subtitles::bundle_message(&conn, SET).unwrap(),
        Some(i64::from(message))
    );
    let (chat, tracks): (i64, i64) = conn
        .query_row(
            "SELECT f.chat_id, (SELECT COUNT(*) FROM subtitle_tracks t WHERE t.set_id = f.set_id)
             FROM subtitle_files f WHERE f.set_id = ?1",
            [SET],
            |row| Ok((row.get(0)?, row.get(1)?)),
        )
        .unwrap();
    assert_eq!((chat, tracks), (CHAT_ID, 2));
    assert!(assets::languages(&conn, SET).unwrap().is_empty());
    assert!(pins::publish_owed(&conn).unwrap().is_some());
    assert_eq!(
        listing(media.path()),
        before,
        "nothing is written beside the source"
    );
}

#[tokio::test]
async fn a_forced_flag_and_an_sdh_title_name_their_tracks() {
    let (media, state) = (tempfile::tempdir().unwrap(), tempfile::tempdir().unwrap());
    let forced = Sub {
        forced: true,
        ..sub(HALLO, "subrip", "ger")
    };
    let sdh = Sub {
        title: Some("English (SDH)"),
        ..sub(HELLO, "subrip", "eng")
    };
    let Some(video) = make_mkv(media.path(), "film.mkv", &[sdh, forced]) else {
        return;
    };
    let conn = index(&state);

    let done = attach(&conn, &FakeChannel::new(), SET, &Input::File(video))
        .await
        .unwrap()
        .unwrap();

    assert_eq!(done.labels, ["German (Forced)", "English (SDH)"]);
}

/// A transcript saved by a Windows tool is not UTF-8; it must arrive as
/// UTF-8 WebVTT with its umlauts.
#[tokio::test]
async fn a_cp1252_srt_beside_a_video_becomes_utf8_webvtt() {
    let (media, state) = (tempfile::tempdir().unwrap(), tempfile::tempdir().unwrap());
    let Some(video) = make_mkv(media.path(), "Lektion 1.mkv", &[]) else {
        return;
    };
    let mut srt = b"1\n00:00:00,000 --> 00:00:01,000\nGr".to_vec();
    srt.extend_from_slice(&[0xfc, 0xdf]);
    srt.extend_from_slice(b"e\n");
    std::fs::write(media.path().join("Lektion 1.srt"), srt).unwrap();
    let conn = index(&state);
    let channel = FakeChannel::new();

    let done = attach(&conn, &channel, SET, &Input::File(video))
        .await
        .unwrap()
        .unwrap();

    // The video's audio is German, so a transcript without a language is.
    assert_eq!(done.labels, ["German"]);
    let bundle = sent_bundle(&channel);
    assert_eq!(bundle.tracks[0].source, "sidecar");
    assert!(
        bundle.tracks[0].vtt.contains("Grüße"),
        "{}",
        bundle.tracks[0].vtt
    );
    assert_eq!(listing(media.path()), ["Lektion 1.mkv", "Lektion 1.srt"]);
}

/// The backfill reads the channel copy through a URL; a URL has no folder.
#[tokio::test]
async fn a_url_input_reads_embedded_tracks_and_no_sidecars() {
    let (media, state) = (tempfile::tempdir().unwrap(), tempfile::tempdir().unwrap());
    let Some(video) = make_mkv(media.path(), "film.mkv", &[sub(HALLO, "subrip", "ger")]) else {
        return;
    };
    std::fs::write(media.path().join("film.en.srt"), HELLO).unwrap();
    let conn = index(&state);

    let done = attach(
        &conn,
        &FakeChannel::new(),
        SET,
        &Input::Url(format!("file://{}", video.display())),
    )
    .await
    .unwrap()
    .unwrap();

    assert_eq!(done.labels, ["German"]);
}

#[tokio::test]
async fn a_file_with_nothing_to_attach_sends_nothing() {
    let (media, state) = (tempfile::tempdir().unwrap(), tempfile::tempdir().unwrap());
    let Some(video) = make_mkv(media.path(), "film.mkv", &[sub(HALLO, "subrip", "fre")]) else {
        return;
    };
    let conn = index(&state);
    let channel = FakeChannel::new();

    let done = attach(&conn, &channel, SET, &Input::File(video))
        .await
        .unwrap();

    assert!(done.is_none());
    assert_eq!(channel.with(|c| c.sends), 0);
    assert_eq!(index_subtitles::bundle_message(&conn, SET).unwrap(), None);
}

#[tokio::test]
async fn a_send_that_fails_leaves_no_record_and_reports_instead_of_failing() {
    let (media, state) = (tempfile::tempdir().unwrap(), tempfile::tempdir().unwrap());
    let Some(video) = make_mkv(media.path(), "film.mkv", &[sub(HALLO, "subrip", "ger")]) else {
        return;
    };
    let conn = index(&state);
    let channel = FakeChannel::new();
    channel.with(|c| c.send_fails = true);
    let input = Input::File(video);

    assert!(attach(&conn, &channel, SET, &input).await.is_err());
    attach_and_report(&conn, &channel, SET, &input).await;

    assert_eq!(index_subtitles::bundle_message(&conn, SET).unwrap(), None);
    assert_eq!(pins::publish_owed(&conn).unwrap(), None);
}

#[tokio::test]
async fn a_file_ffprobe_cannot_read_is_an_error_for_the_caller_to_report() {
    let (media, state) = (tempfile::tempdir().unwrap(), tempfile::tempdir().unwrap());
    if make_mkv(media.path(), "unused.mkv", &[]).is_none() {
        return;
    }
    let junk = media.path().join("junk.mkv");
    std::fs::write(&junk, b"not media").unwrap();
    let conn = index(&state);

    assert!(
        attach(&conn, &FakeChannel::new(), SET, &Input::File(junk))
            .await
            .is_err()
    );
}

/// A release that stored Windows-1252 text in a SubRip track: ffmpeg exits 0
/// and drops every line with an umlaut, unless that one stream is read as
/// Windows-1252. A valid UTF-8 track beside it must not be garbled by that.
#[tokio::test]
async fn a_cp1252_embedded_track_keeps_its_umlauts_beside_a_utf8_one() {
    let (media, state) = (tempfile::tempdir().unwrap(), tempfile::tempdir().unwrap());
    let cp1252 = "1\n00:00:00,000 --> 00:00:01,000\nGrQQe\n";
    let utf8 = "1\n00:00:00,000 --> 00:00:01,000\nGr\u{fc}\u{df}e\n";
    let Some(video) = make_mkv(
        media.path(),
        "film.mkv",
        &[sub(cp1252, "subrip", "ger"), sub(utf8, "subrip", "eng")],
    ) else {
        return;
    };
    patch_bytes(&video, b"GrQQe", &[b'G', b'r', 0xfc, 0xdf, b'e']);
    let conn = index(&state);
    let channel = FakeChannel::new();

    attach(&conn, &channel, SET, &Input::File(video))
        .await
        .unwrap()
        .unwrap();

    let bundle = sent_bundle(&channel);
    assert_eq!(bundle.tracks.len(), 2, "{:?}", bundle.tracks);
    assert!(
        bundle.tracks[0].vtt.contains("Grüße"),
        "{}",
        bundle.tracks[0].vtt
    );
    assert!(
        bundle.tracks[1].vtt.contains("Grüße"),
        "{}",
        bundle.tracks[1].vtt
    );
}
