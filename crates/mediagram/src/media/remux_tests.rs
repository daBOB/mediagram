use super::*;
use crate::media::test_fixtures::{
    ffmpeg_required, make_faststart_mp4, make_trailing_moov_mp4, make_trailing_moov_mp4_multi,
    make_trailing_moov_mp4_unmuxable_stream,
};

fn stream(index: u32, kind: StreamKind, language: Option<&str>, codec: &str) -> Stream {
    Stream {
        index,
        kind,
        language: language.map(str::to_string),
        bit_rate: None,
        codec: Some(codec.to_string()),
        title: None,
        default: false,
        forced: false,
        hearing_impaired: false,
    }
}

#[test]
fn faststart_args_keeps_mp4_legal_streams_only() {
    let streams = vec![
        stream(0, StreamKind::Video, None, "h264"),
        stream(1, StreamKind::Audio, Some("ger"), "aac"),
        stream(2, StreamKind::Audio, Some("eng"), "aac"),
        stream(3, StreamKind::Subtitle, Some("ger"), "mov_text"),
        stream(4, StreamKind::Subtitle, Some("eng"), "dvd_subtitle"),
        stream(5, StreamKind::Subtitle, Some("eng"), "eia_608"),
        stream(6, StreamKind::Other, None, "bin_data"),
    ];

    let args = faststart_args(&streams);

    let expected: Vec<String> = [
        "-map",
        "0:V?",
        "-map",
        "0:a?",
        "-map",
        "0:3",
        "-map",
        "0:4",
        "-c",
        "copy",
        "-movflags",
        "+faststart",
    ]
    .into_iter()
    .map(String::from)
    .collect();
    assert_eq!(args, expected);
}

#[tokio::test]
async fn remuxes_trailing_moov_fixture() {
    if !ffmpeg_required("remuxes_trailing_moov_fixture") {
        return;
    }
    let dir = tempfile::tempdir().unwrap();
    let src = make_trailing_moov_mp4(dir.path());

    let out = ensure_faststart(&src, None, false).await.unwrap();
    assert_ne!(out, src);
    assert!(!mp4_atoms::needs_faststart(&out).unwrap());
}

#[tokio::test]
async fn leaves_faststart_fixture_untouched() {
    if !ffmpeg_required("leaves_faststart_fixture_untouched") {
        return;
    }
    let dir = tempfile::tempdir().unwrap();
    let src = make_faststart_mp4(dir.path());

    let out = ensure_faststart(&src, None, false).await.unwrap();
    assert_eq!(out, src);
}

#[tokio::test]
async fn no_remux_flag_bypasses_even_when_needed() {
    if !ffmpeg_required("no_remux_flag_bypasses_even_when_needed") {
        return;
    }
    let dir = tempfile::tempdir().unwrap();
    let src = make_trailing_moov_mp4(dir.path());

    let out = ensure_faststart(&src, None, true).await.unwrap();
    assert_eq!(out, src);
}

#[tokio::test]
async fn remux_keeps_every_stream_and_the_forced_flag() {
    if !ffmpeg_required("remux_keeps_every_stream_and_the_forced_flag") {
        return;
    }
    let dir = tempfile::tempdir().unwrap();
    let src = make_trailing_moov_mp4_multi(dir.path());

    let out = ensure_faststart(&src, None, false).await.unwrap();
    assert_ne!(out, src);
    assert!(!mp4_atoms::needs_faststart(&out).unwrap());

    let probed = streams::probe(&out).await.unwrap();
    let video = probed
        .streams
        .iter()
        .filter(|s| s.kind == StreamKind::Video)
        .count();
    let audio_langs: Vec<_> = probed
        .streams
        .iter()
        .filter(|s| s.kind == StreamKind::Audio)
        .filter_map(|s| s.language.as_deref())
        .collect();
    let sub_langs: Vec<_> = probed
        .streams
        .iter()
        .filter(|s| s.kind == StreamKind::Subtitle)
        .filter_map(|s| s.language.as_deref())
        .collect();
    assert_eq!(video, 1);
    assert_eq!(audio_langs, vec!["ger", "eng"]);
    assert_eq!(sub_langs, vec!["ger", "eng"]);

    assert!(subtitle_forced(&out, 0).await, "the German track is forced");
    assert!(
        !subtitle_forced(&out, 1).await,
        "the English track is not forced"
    );
}

#[tokio::test]
async fn falls_back_when_the_mapped_remux_is_refused() {
    if !ffmpeg_required("falls_back_when_the_mapped_remux_is_refused") {
        return;
    }
    let dir = tempfile::tempdir().unwrap();
    let src = make_trailing_moov_mp4_unmuxable_stream(dir.path());

    let out = ensure_faststart(&src, None, false).await.unwrap();
    assert_ne!(out, src);
    assert!(!mp4_atoms::needs_faststart(&out).unwrap());
}

/// Reads `disposition.forced` for one subtitle stream directly with
/// `ffprobe`: [`Stream`] has no disposition field, and this phase does not
/// add one.
async fn subtitle_forced(path: &Path, sub_index: u32) -> bool {
    let output = tokio::process::Command::new("ffprobe")
        .args(["-v", "error", "-select_streams"])
        .arg(format!("s:{sub_index}"))
        .args(["-show_entries", "stream_disposition=forced"])
        .args(["-of", "default=noprint_wrappers=1:nokey=1"])
        .arg(path)
        .output()
        .await
        .expect("running ffprobe for a disposition check");
    String::from_utf8_lossy(&output.stdout).trim() == "1"
}
