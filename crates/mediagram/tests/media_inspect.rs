//! Pins what `media::inspect` reports for a file's codecs, duration, and
//! audio language tags across fixture shapes (single audio stream, none,
//! and multiple tagged streams), plus how `media::mp4_atoms` and
//! `media::remux` classify and fix moov placement. Built against
//! ffmpeg-generated fixtures; skipped with a printed note when ffmpeg is
//! not on PATH.

use mediagram::media;

use media::inspect::inspect;
use media::mp4_atoms::needs_faststart;
use media::remux::ensure_faststart;
use media::test_fixtures::{ffmpeg_required, make_faststart_mp4, make_trailing_moov_mp4};

#[tokio::test]
async fn inspect_yields_expected_fixture_metadata() {
    if !ffmpeg_required("inspect_yields_expected_fixture_metadata") {
        return;
    }
    let dir = tempfile::tempdir().unwrap();
    let path = make_trailing_moov_mp4(dir.path());

    let info = inspect(&path).await.unwrap();
    assert_eq!(info.container, "mp4");
    assert_eq!(info.vcodec.as_deref(), Some("h264"));
    assert_eq!(info.acodec.as_deref(), Some("aac"));
    assert_eq!(info.duration_s, Some(1));
    assert_eq!(info.quality.as_deref(), Some("SD"));
    assert!(info.size > 0);
}

#[test]
fn mp4_atoms_detect_trailing_vs_faststart() {
    if !ffmpeg_required("mp4_atoms_detect_trailing_vs_faststart") {
        return;
    }
    let dir = tempfile::tempdir().unwrap();
    let trailing = make_trailing_moov_mp4(dir.path());
    let faststart = make_faststart_mp4(dir.path());

    assert!(needs_faststart(&trailing).unwrap());
    assert!(!needs_faststart(&faststart).unwrap());
}

#[tokio::test]
async fn ensure_faststart_remuxes_only_when_needed() {
    if !ffmpeg_required("ensure_faststart_remuxes_only_when_needed") {
        return;
    }
    let dir = tempfile::tempdir().unwrap();
    let trailing = make_trailing_moov_mp4(dir.path());
    let faststart = make_faststart_mp4(dir.path());

    let remuxed = ensure_faststart(&trailing, None, false).await.unwrap();
    assert_ne!(remuxed, trailing);
    assert!(!needs_faststart(&remuxed).unwrap());

    let untouched = ensure_faststart(&faststart, None, false).await.unwrap();
    assert_eq!(untouched, faststart);
}

#[test]
fn mkv_extension_never_invokes_atom_scan() {
    // No fixture needed: mp4_atoms short-circuits on the extension before
    // touching the filesystem, so a nonexistent path is safe here.
    let fake = std::path::Path::new("/does/not/exist.mkv");
    assert!(!needs_faststart(fake).unwrap());
}

#[tokio::test]
async fn inspect_reports_no_audio_codec_for_video_only_file() {
    // A file with no audio stream at all reports acodec: None and an empty
    // language list, rather than erroring or defaulting to a codec name.
    if !ffmpeg_required("inspect_reports_no_audio_codec_for_video_only_file") {
        return;
    }
    let dir = tempfile::tempdir().unwrap();
    let out_path = dir.path().join("video_only.mp4");

    let status = std::process::Command::new("ffmpeg")
        .args([
            "-v",
            "error",
            "-y",
            "-f",
            "lavfi",
            "-i",
            "testsrc=duration=1:size=64x64:rate=10",
            "-c:v",
            "libx264",
            "-pix_fmt",
            "yuv420p",
            out_path.to_str().unwrap(),
        ])
        .status()
        .expect("spawning ffmpeg");
    assert!(status.success());

    let info = inspect(&out_path).await.unwrap();
    assert_eq!(info.vcodec.as_deref(), Some("h264"));
    assert_eq!(info.acodec, None);
    assert!(info.alang.is_empty());
}

#[tokio::test]
async fn inspect_collects_language_tags_from_multiple_audio_streams() {
    // With two audio streams tagged eng/deu, inspect surfaces both
    // languages rather than only the first stream's tag.
    if !ffmpeg_required("inspect_collects_language_tags_from_multiple_audio_streams") {
        return;
    }
    let dir = tempfile::tempdir().unwrap();
    let out_path = dir.path().join("dual_audio.mp4");

    let status = std::process::Command::new("ffmpeg")
        .args([
            "-v",
            "error",
            "-y",
            "-f",
            "lavfi",
            "-i",
            "testsrc=duration=1:size=64x64:rate=10",
            "-f",
            "lavfi",
            "-i",
            "sine=duration=1:frequency=440",
            "-f",
            "lavfi",
            "-i",
            "sine=duration=1:frequency=880",
            "-c:v",
            "libx264",
            "-pix_fmt",
            "yuv420p",
            "-c:a",
            "aac",
            "-map",
            "0",
            "-map",
            "1",
            "-map",
            "2",
            "-metadata:s:a:0",
            "language=eng",
            "-metadata:s:a:1",
            "language=deu",
            out_path.to_str().unwrap(),
        ])
        .status()
        .expect("spawning ffmpeg");
    assert!(status.success());

    let info = inspect(&out_path).await.unwrap();
    assert_eq!(info.vcodec.as_deref(), Some("h264"));
    assert_eq!(info.acodec.as_deref(), Some("aac"));
    // ffprobe's 3-letter tags are normalized to 2-letter codes by lang_code.
    assert!(info.alang.contains(&"en".to_string()));
    assert!(info.alang.contains(&"de".to_string()));
}
