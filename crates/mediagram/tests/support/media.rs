//! Media files built by ffmpeg into a temp dir: small MP4s with `moov` before
//! or after `mdat`, and tiny Matroska files with subtitle tracks. Shared by
//! the integration tests and, through `test_fakes`, the unit tests.

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

/// Builds a trailing-moov MP4 with two audio languages and two `mov_text`
/// subtitle streams (`ger` forced, `eng` plain), proving a mapped remux
/// keeps every stream a plain `-c copy` (no `-map`) would have kept only
/// one language of, and no subtitle at all.
pub fn make_trailing_moov_mp4_multi(dir: &Path) -> PathBuf {
    let ger_srt = dir.join("multi_ger.srt");
    let eng_srt = dir.join("multi_eng.srt");
    std::fs::write(&ger_srt, "1\n00:00:00,000 --> 00:00:01,000\nHallo\n")
        .expect("writing the German subtitle fixture");
    std::fs::write(&eng_srt, "1\n00:00:00,000 --> 00:00:01,000\nHello\n")
        .expect("writing the English subtitle fixture");

    let out = dir.join("trailing_moov_multi.mp4");
    let status = Command::new("ffmpeg")
        .args(["-v", "error", "-y"])
        .args(["-f", "lavfi", "-i", "testsrc=duration=1:size=64x64:rate=10"])
        .args(["-f", "lavfi", "-i", "sine=duration=1"])
        .args(["-f", "lavfi", "-i", "sine=duration=1:frequency=880"])
        .arg("-i")
        .arg(&ger_srt)
        .arg("-i")
        .arg(&eng_srt)
        .args([
            "-map",
            "0:v",
            "-map",
            "1:a",
            "-map",
            "2:a",
            "-map",
            "3",
            "-map",
            "4",
            "-c:v",
            "libx264",
            "-pix_fmt",
            "yuv420p",
            "-c:a",
            "aac",
            "-c:s",
            "mov_text",
            "-metadata:s:a:0",
            "language=ger",
            "-metadata:s:a:1",
            "language=eng",
            "-metadata:s:s:0",
            "language=ger",
            "-metadata:s:s:1",
            "language=eng",
            "-disposition:s:0",
            "forced",
        ])
        .arg(&out)
        .status()
        .expect("spawning ffmpeg for the multi-stream test fixture");
    assert!(status.success(), "ffmpeg multi-stream fixture build failed");
    out
}

/// Builds a trailing-moov file, named `.mp4` as a real source might be even
/// when it is really QuickTime's more lenient `mov` layout, whose second
/// video stream is raw, uncompressed video — a codec the mp4 muxer refuses
/// to copy in. Today's unmapped remux silently keeps only the first video
/// stream and so never notices; an explicit `-map 0:V?` remux forces the
/// raw stream in and is refused, proving `ensure_faststart`'s one-time
/// fallback to today's arguments.
pub fn make_trailing_moov_mp4_unmuxable_stream(dir: &Path) -> PathBuf {
    let raw = dir.join("second_stream.yuv");
    // One second of 64x64 yuv420p at 10fps: 6144 bytes/frame * 10 frames.
    // Content is irrelevant; only the raw, uncompressed codec matters here.
    std::fs::write(&raw, vec![0u8; 6144 * 10]).expect("writing the raw video fixture frames");

    let out = dir.join("trailing_moov_unmuxable.mp4");
    let status = Command::new("ffmpeg")
        .args(["-v", "error", "-y"])
        .args(["-f", "lavfi", "-i", "testsrc=duration=1:size=64x64:rate=10"])
        .args([
            "-f", "rawvideo", "-pix_fmt", "yuv420p", "-s", "64x64", "-r", "10", "-i",
        ])
        .arg(&raw)
        .args(["-f", "lavfi", "-i", "sine=duration=1"])
        .args([
            "-map",
            "0:v",
            "-map",
            "1:v",
            "-map",
            "2:a",
            "-c:v:0",
            "libx264",
            "-pix_fmt:v:0",
            "yuv420p",
            "-c:v:1",
            "copy",
            "-c:a",
            "aac",
            "-f",
            "mov",
        ])
        .arg(&out)
        .status()
        .expect("spawning ffmpeg for the unmuxable-stream test fixture");
    assert!(
        status.success(),
        "ffmpeg unmuxable-stream fixture build failed"
    );
    out
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

/// One subtitle track to embed.
pub struct Sub<'a> {
    /// SubRip text the track is made from.
    pub srt: &'a str,
    /// `subrip` or `ass`.
    pub codec: &'a str,
    pub lang: &'a str,
    pub title: Option<&'a str>,
    pub forced: bool,
}

pub const HALLO: &str = "1\n00:00:00,000 --> 00:00:01,000\nHallo Welt\n\n2\n00:00:01,500 --> 00:00:02,500\nZweite Zeile\n";
pub const HELLO: &str = "1\n00:00:00,000 --> 00:00:01,000\nHello world\n\n2\n00:00:01,500 --> 00:00:02,500\nSecond line\n";

/// A three-second `.mkv` named `name` in `dir`, with German audio and `subs`.
/// `None` when the test should be skipped for want of ffmpeg.
pub fn make_mkv(dir: &Path, name: &str, subs: &[Sub]) -> Option<PathBuf> {
    if !ffmpeg_required(name) {
        return None;
    }
    let scratch = tempfile::tempdir().unwrap();
    let out = dir.join(name);
    let mut cmd = Command::new("ffmpeg");
    cmd.args(["-v", "error", "-y"])
        .args(["-f", "lavfi", "-i", "testsrc=duration=3:size=64x64:rate=10"])
        .args(["-f", "lavfi", "-i", "sine=duration=3"]);
    for (n, sub) in subs.iter().enumerate() {
        let srt = scratch.path().join(format!("in{n}.srt"));
        std::fs::write(&srt, sub.srt).unwrap();
        cmd.arg("-i").arg(srt);
    }
    cmd.args(["-map", "0:v", "-map", "1:a"]);
    for n in 0..subs.len() {
        cmd.args(["-map".to_string(), format!("{}", n + 2)]);
    }
    cmd.args(["-c:v", "libx264", "-pix_fmt", "yuv420p", "-c:a", "aac"])
        .args(["-metadata:s:a:0", "language=ger"]);
    for (n, sub) in subs.iter().enumerate() {
        let codec = if sub.codec == "ass" { "ass" } else { "srt" };
        cmd.args([format!("-c:s:{n}"), codec.to_string()]).args([
            format!("-metadata:s:s:{n}"),
            format!("language={}", sub.lang),
        ]);
        if let Some(title) = sub.title {
            cmd.args([format!("-metadata:s:s:{n}"), format!("title={title}")]);
        }
        if sub.forced {
            cmd.args([format!("-disposition:s:{n}"), "forced".to_string()]);
        }
    }
    let status = cmd.arg(&out).status().expect("spawning ffmpeg");
    assert!(status.success(), "ffmpeg could not build {name}");
    Some(out)
}

/// Every file name in `dir`, sorted.
pub fn listing(dir: &Path) -> Vec<String> {
    let mut names: Vec<String> = std::fs::read_dir(dir)
        .unwrap()
        .map(|e| e.unwrap().file_name().to_string_lossy().to_string())
        .collect();
    names.sort();
    names
}

/// Replaces the one occurrence of `from` in the file with `to`, which must
/// be the same length: how a fixture gets bytes no muxer would write.
pub fn patch_bytes(path: &Path, from: &[u8], to: &[u8]) {
    assert_eq!(from.len(), to.len());
    let mut bytes = std::fs::read(path).unwrap();
    let at = bytes
        .windows(from.len())
        .position(|w| w == from)
        .expect("the pattern is in the file");
    bytes[at..at + to.len()].copy_from_slice(to);
    std::fs::write(path, bytes).unwrap();
}
