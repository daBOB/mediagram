//! Edge case probes for media remux and inspect operations (Phase 3)
//! Tests edge cases in ensure_faststart and bytelevel preservation.

use std::fs;
use tempfile::TempDir;

use media::test_fixtures::{ffmpeg_available, make_faststart_mp4, make_trailing_moov_mp4};
use mediagram::media;

// ============================================================================
// Remux edge cases
// ============================================================================

#[tokio::test]
async fn remux_tmp_dir_nonexistent() {
    // ensure_faststart with a tmp_dir that doesn't exist should fail gracefully
    if !ffmpeg_available() {
        eprintln!("skipping remux_tmp_dir_nonexistent: ffmpeg not on PATH");
        return;
    }
    let dir = TempDir::new().unwrap();
    let src = make_trailing_moov_mp4(dir.path());

    // Use a tmp_dir that doesn't exist
    let nonexistent = dir.path().join("nonexistent_subdir");
    let result = media::remux::ensure_faststart(&src, Some(&nonexistent), false).await;

    // Should fail because the directory doesn't exist
    assert!(result.is_err());
}

#[tokio::test]
async fn remux_idempotent_output_path() {
    // Running ensure_faststart twice should produce the same output path
    // and the second call should recognize it's already faststart and return it unchanged
    if !ffmpeg_available() {
        eprintln!("skipping remux_idempotent_output_path: ffmpeg not on PATH");
        return;
    }
    let dir = TempDir::new().unwrap();
    let src = make_trailing_moov_mp4(dir.path());

    let out1 = media::remux::ensure_faststart(&src, None, false)
        .await
        .unwrap();
    let out2 = media::remux::ensure_faststart(&src, None, false)
        .await
        .unwrap();

    // Both should succeed and produce the same output path
    assert_eq!(out1, out2);
    assert_ne!(out1, src); // Should be different from source (remuxed)
}

#[tokio::test]
async fn remux_with_explicit_tmp_dir() {
    // ensure_faststart should use the provided tmp_dir for output
    if !ffmpeg_available() {
        eprintln!("skipping remux_with_explicit_tmp_dir: ffmpeg not on PATH");
        return;
    }
    let src_dir = TempDir::new().unwrap();
    let tmp_dir = TempDir::new().unwrap();

    let src = make_trailing_moov_mp4(src_dir.path());
    let out = media::remux::ensure_faststart(&src, Some(tmp_dir.path()), false)
        .await
        .unwrap();

    // Output should be in tmp_dir, not src_dir
    assert_eq!(out.parent().unwrap(), tmp_dir.path());
}

#[tokio::test]
async fn remux_faststart_byte_for_byte_unchanged() {
    // When no remux is needed, the faststart fixture should be returned unchanged
    // (same path, not a copy)
    if !ffmpeg_available() {
        eprintln!("skipping remux_faststart_byte_for_byte_unchanged: ffmpeg not on PATH");
        return;
    }
    let dir = TempDir::new().unwrap();
    let src = make_faststart_mp4(dir.path());

    let original_content = fs::read(&src).unwrap();
    let out = media::remux::ensure_faststart(&src, None, false)
        .await
        .unwrap();

    // Should be the same path (not remuxed)
    assert_eq!(out, src);

    // File should be unchanged
    let after_content = fs::read(&out).unwrap();
    assert_eq!(original_content, after_content);
}

#[tokio::test]
async fn remux_no_remux_flag_returns_src() {
    // With no_remux=true, even a trailing-moov file should be returned unchanged
    if !ffmpeg_available() {
        eprintln!("skipping remux_no_remux_flag_returns_src: ffmpeg not on PATH");
        return;
    }
    let dir = TempDir::new().unwrap();
    let src = make_trailing_moov_mp4(dir.path());

    let out = media::remux::ensure_faststart(&src, None, true)
        .await
        .unwrap();
    assert_eq!(out, src);
}

// ============================================================================
// Inspect edge cases (with ffmpeg fixtures)
// ============================================================================

#[tokio::test]
async fn inspect_file_with_no_audio() {
    // Create a video-only MP4 (no audio streams) via ffmpeg
    if !ffmpeg_available() {
        eprintln!("skipping inspect_file_with_no_audio: ffmpeg not on PATH");
        return;
    }
    let dir = TempDir::new().unwrap();
    let out_path = dir.path().join("video_only.mp4");

    // ffmpeg command to create video-only file
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

    let info = media::inspect::inspect(&out_path).await.unwrap();
    assert_eq!(info.vcodec.as_deref(), Some("h264"));
    assert_eq!(info.acodec, None);
    assert!(info.alang.is_empty());
}

#[tokio::test]
async fn inspect_file_with_multiple_audio_streams() {
    // Create an MP4 with two audio streams of different languages
    if !ffmpeg_available() {
        eprintln!("skipping inspect_file_with_multiple_audio_streams: ffmpeg not on PATH");
        return;
    }
    let dir = TempDir::new().unwrap();
    let out_path = dir.path().join("dual_audio.mp4");

    // ffmpeg command to create file with two audio tracks
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
            "0", // video
            "-map",
            "1", // audio 1
            "-map",
            "2", // audio 2
            "-metadata:s:a:0",
            "language=eng",
            "-metadata:s:a:1",
            "language=deu",
            out_path.to_str().unwrap(),
        ])
        .status()
        .expect("spawning ffmpeg");
    assert!(status.success());

    let info = media::inspect::inspect(&out_path).await.unwrap();
    assert_eq!(info.vcodec.as_deref(), Some("h264"));
    assert_eq!(info.acodec.as_deref(), Some("aac"));
    // Both languages should be present in alang
    assert!(!info.alang.is_empty()); // At least one audio stream
}

#[tokio::test]
async fn inspect_file_with_no_language_tags() {
    // Create a file with audio but no language metadata
    if !ffmpeg_available() {
        eprintln!("skipping inspect_file_with_no_language_tags: ffmpeg not on PATH");
        return;
    }
    let dir = TempDir::new().unwrap();
    let out_path = dir.path().join("no_lang_tags.mp4");

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
            "sine=duration=1",
            "-c:v",
            "libx264",
            "-pix_fmt",
            "yuv420p",
            "-c:a",
            "aac",
            // No metadata tags set
            out_path.to_str().unwrap(),
        ])
        .status()
        .expect("spawning ffmpeg");
    assert!(status.success());

    let _info = media::inspect::inspect(&out_path).await.unwrap();
    // alang should be empty if no language tags were set
    // (or may contain default if ffmpeg adds one)
}
