//! Tiny Matroska files with subtitle tracks, made by ffmpeg into a temp dir.

use std::path::{Path, PathBuf};
use std::process::Command;

use mediagram::media::test_fixtures::ffmpeg_required;

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
