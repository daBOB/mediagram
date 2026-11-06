//! Reading ffprobe's output. The fixture is real output from a 4.43 GB
//! episode: 52 streams, 43 of them with no declared bitrate, which is the
//! shape that breaks a naive parser.

mod support;

use mediagram::media::prepare::plan::{Verdict, plan_prepare};
use mediagram::media::streams::{self, StreamKind, parse_probe};
use support::media::{Sub, make_mkv, patch_bytes};

const FIXTURE: &str = include_str!("fixtures/ffprobe/episode_streams.json");

#[test]
fn a_real_probe_parses_every_stream() {
    let probed = parse_probe(FIXTURE).unwrap();

    assert_eq!(probed.streams.len(), 52);
    assert_eq!(
        probed
            .streams
            .iter()
            .filter(|s| s.kind == StreamKind::Video)
            .count(),
        1
    );
    assert_eq!(
        probed
            .streams
            .iter()
            .filter(|s| s.kind == StreamKind::Audio)
            .count(),
        9
    );
    assert_eq!(
        probed
            .streams
            .iter()
            .filter(|s| s.kind == StreamKind::Subtitle)
            .count(),
        42
    );
}

#[test]
fn bitrates_and_languages_are_read_where_present() {
    let probed = parse_probe(FIXTURE).unwrap();
    let first_audio = probed
        .streams
        .iter()
        .find(|s| s.kind == StreamKind::Audio)
        .unwrap();
    assert_eq!(first_audio.bit_rate, Some(768_000));
    assert_eq!(first_audio.language.as_deref(), Some("eng"));
}

/// 43 of 52 streams have no `bit_rate` field at all.
#[test]
fn a_missing_bitrate_is_absent_rather_than_zero_or_an_error() {
    let probed = parse_probe(FIXTURE).unwrap();
    assert!(probed.streams.iter().any(|s| s.bit_rate.is_none()));
}

#[test]
fn the_container_duration_and_size_are_read() {
    let probed = parse_probe(FIXTURE).unwrap();
    assert!(
        (probed.duration - 2547.136).abs() < 0.01,
        "{}",
        probed.duration
    );
    assert_eq!(probed.size, Some(4_426_249_518));
}

/// The parser and planner together must reproduce the measurement that
/// justified this feature: 4.43 GB down to about 3.57 GB.
#[test]
fn the_real_episode_plans_down_to_the_measured_size() {
    let probed = parse_probe(FIXTURE).unwrap();
    let plan = plan_prepare(
        &probed.streams,
        probed.size.unwrap(),
        probed.duration,
        &["ger", "deu", "eng"],
        &["ger", "deu", "eng"],
        3_758_096_384,
    );

    assert_eq!(plan.dropped_audio, 7);
    assert_eq!(plan.verdict, Verdict::Prepare);
    let gb = plan.estimated_bytes as f64 / 1e9;
    assert!((3.5..3.65).contains(&gb), "estimated {gb:.2} GB");
}

#[test]
fn malformed_output_is_an_error_not_a_panic() {
    assert!(parse_probe("not json").is_err());
    assert!(
        parse_probe("{}").is_err(),
        "no streams is not a usable probe"
    );
}

/// The Capture S01E05: its German subtitles run 3 s past the last frame, and
/// Matroska's own duration counts them. Measured against that, a correct MP4
/// of the same picture and sound looked truncated and was rejected.
#[test]
fn the_duration_is_how_long_the_picture_and_sound_run() {
    let mkv = r#"{"streams": [
        {"index": 0, "codec_type": "video", "tags": {"DURATION": "00:59:33.040000000"}},
        {"index": 1, "codec_type": "audio", "tags": {"DURATION": "00:59:33.088000000"}},
        {"index": 2, "codec_type": "subtitle", "tags": {"DURATION": "00:59:36.273000000"}}
    ], "format": {"duration": "3576.273000"}}"#;
    assert!((parse_probe(mkv).unwrap().duration - 3573.088).abs() < 0.001);

    let mp4 = r#"{"streams": [
        {"index": 0, "codec_type": "video", "duration": "3573.040000"},
        {"index": 1, "codec_type": "audio", "duration": "3573.088000"}
    ], "format": {"duration": "3573.088000"}}"#;
    assert!((parse_probe(mp4).unwrap().duration - 3573.088).abs() < 0.001);
}

/// The Expanse S05E03: its picture and sound declare no length at all, only
/// the subtitles do, and they run past the last frame. The container's length
/// counts them, so a correct MP4 of the same picture and sound looked
/// truncated; the last frames say how long the episode really runs.
#[tokio::test]
async fn without_a_declared_length_the_last_frames_decide_it() {
    let dir = tempfile::tempdir().unwrap();
    let late = Sub {
        srt: "1\n00:00:03,500 --> 00:00:05,000\nNachspann\n",
        codec: "subrip",
        lang: "ger",
        title: None,
        forced: false,
    };
    let Some(mkv) = make_mkv(dir.path(), "Late.mkv", &[late]) else {
        return;
    };
    // Matroska writes a DURATION tag per track, video's first, audio's next.
    patch_bytes(&mkv, b"DURATION", b"DURATIOX");
    patch_bytes(&mkv, b"DURATION", b"DURATIOX");

    let probed = streams::probe(&mkv).await.unwrap();

    assert!((probed.duration - 3.0).abs() < 0.1, "{}", probed.duration);
}
