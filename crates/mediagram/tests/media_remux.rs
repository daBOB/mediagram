//! Pins behavior of `media::remux::ensure_faststart` beyond what its own
//! unit tests cover: how it reports a bad `tmp_dir`, where it writes when
//! given one, and that its output path for a given source is stable across
//! repeated calls. Skipped with a printed note when ffmpeg is not on PATH.

use tempfile::TempDir;

use mediagram::media;

use media::test_fixtures::{ffmpeg_required, make_trailing_moov_mp4};

#[tokio::test]
async fn ensure_faststart_errors_when_tmp_dir_is_missing() {
    // A tmp_dir that doesn't exist should surface as an error, not a panic
    // or a silent fallback to the source's own directory.
    if !ffmpeg_required("ensure_faststart_errors_when_tmp_dir_is_missing") {
        return;
    }
    let dir = TempDir::new().unwrap();
    let src = make_trailing_moov_mp4(dir.path());
    let nonexistent = dir.path().join("nonexistent_subdir");

    let result = media::remux::ensure_faststart(&src, Some(&nonexistent), false).await;

    assert!(result.is_err());
}

#[tokio::test]
async fn ensure_faststart_output_path_is_stable_across_repeated_calls() {
    // The destination path is derived from the source's stem, not from a
    // timestamp or random name, so remuxing the same source twice in a row
    // (overwriting the first output) succeeds and yields the same path.
    if !ffmpeg_required("ensure_faststart_output_path_is_stable_across_repeated_calls") {
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

    assert_eq!(out1, out2);
    assert_ne!(out1, src);
}

#[tokio::test]
async fn ensure_faststart_writes_remuxed_output_into_given_tmp_dir() {
    // When a tmp_dir is provided, the remuxed file is written there instead
    // of alongside the source.
    if !ffmpeg_required("ensure_faststart_writes_remuxed_output_into_given_tmp_dir") {
        return;
    }
    let src_dir = TempDir::new().unwrap();
    let tmp_dir = TempDir::new().unwrap();

    let src = make_trailing_moov_mp4(src_dir.path());
    let out = media::remux::ensure_faststart(&src, Some(tmp_dir.path()), false)
        .await
        .unwrap();

    assert_eq!(out.parent().unwrap(), tmp_dir.path());
}
