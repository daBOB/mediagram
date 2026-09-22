//! Shared ffmpeg-backed fixture builders for media tests, used by unit tests
//! in this crate and by `tests/media_inspect.rs`; never called at runtime.

use std::path::{Path, PathBuf};
use std::process::Command;

/// Whether a test that needs `ffmpeg` should run. A missing `ffmpeg` fails
/// the test: the uploader needs it in production, and a test that returns
/// early is counted as passed, which would report coverage nobody got. An
/// environment that truly lacks it opts out with `MEDIAGRAM_SKIP_FFMPEG_TESTS`,
/// and only then does this return `false`.
pub fn ffmpeg_required(test: &str) -> bool {
    let available = Command::new("ffmpeg")
        .arg("-version")
        .output()
        .map(|o| o.status.success())
        .unwrap_or(false);
    if available {
        return true;
    }
    if std::env::var_os("MEDIAGRAM_SKIP_FFMPEG_TESTS").is_some() {
        eprintln!("skipping {test}: ffmpeg not on PATH and MEDIAGRAM_SKIP_FFMPEG_TESTS is set");
        return false;
    }
    panic!("{test} needs ffmpeg on PATH; install it, or set MEDIAGRAM_SKIP_FFMPEG_TESTS=1 to skip");
}

/// Builds a 1-second H.264/AAC MP4 in `dir` using ffmpeg's default MP4
/// layout, which writes `moov` after `mdat`. Returns the fixture's path.
pub fn make_trailing_moov_mp4(dir: &Path) -> PathBuf {
    build_fixture(dir, "trailing_moov.mp4", &[])
}

/// Builds the same fixture with `-movflags +faststart`, so `moov` precedes
/// `mdat`.
pub fn make_faststart_mp4(dir: &Path) -> PathBuf {
    build_fixture(dir, "faststart.mp4", &["-movflags", "+faststart"])
}

fn build_fixture(dir: &Path, name: &str, extra_args: &[&str]) -> PathBuf {
    let out = dir.join(name);
    let status = Command::new("ffmpeg")
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
        ])
        .args(extra_args)
        .arg(&out)
        .status()
        .expect("spawning ffmpeg for test fixture");
    assert!(status.success(), "ffmpeg fixture build failed for {name}");
    out
}
