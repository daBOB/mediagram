//! End-to-end tests for `media::inspect`, `media::mp4_atoms`, and
//! `media::remux` against ffmpeg-generated fixtures; skipped with a printed
//! note when ffmpeg is not on PATH.

use mediagram::media;

use media::inspect::inspect;
use media::mp4_atoms::needs_faststart;
use media::remux::ensure_faststart;
use media::test_fixtures::{ffmpeg_available, make_faststart_mp4, make_trailing_moov_mp4};

#[tokio::test]
async fn inspect_yields_expected_fixture_metadata() {
    if !ffmpeg_available() {
        eprintln!("skipping inspect_yields_expected_fixture_metadata: ffmpeg not on PATH");
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
    if !ffmpeg_available() {
        eprintln!("skipping mp4_atoms_detect_trailing_vs_faststart: ffmpeg not on PATH");
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
    if !ffmpeg_available() {
        eprintln!("skipping ensure_faststart_remuxes_only_when_needed: ffmpeg not on PATH");
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
