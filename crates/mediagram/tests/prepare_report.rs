//! Exercise the actual CLI dry run: probing, planning, reporting and no rewrite.

use std::path::{Path, PathBuf};
use std::process::Command;

use mediagram::media::test_fixtures::{ffmpeg_required, make_faststart_mp4};

struct Fixture {
    dir: tempfile::TempDir,
    source: PathBuf,
    original: Vec<u8>,
}

impl Fixture {
    fn new() -> Self {
        let dir = tempfile::tempdir().unwrap();
        let source = make_faststart_mp4(dir.path());
        let named = dir.path().join(format!("{}.mp4", "épisode".repeat(8)));
        std::fs::rename(source, &named).unwrap();
        let original = std::fs::read(&named).unwrap();
        std::fs::write(
            dir.path().join("config.toml"),
            "api_id = 1\napi_hash = 'offline-fixture'\nchannel = 'offline-fixture'\n",
        )
        .unwrap();
        Self {
            dir,
            source: named,
            original,
        }
    }

    fn dry_run(&self, path: &Path, args: &[&str]) -> String {
        let output = Command::new(env!("CARGO_BIN_EXE_mediagram"))
            .env_clear()
            .env("PATH", std::env::var_os("PATH").unwrap_or_default())
            .env("MEDIAGRAM_DATA_DIR", self.dir.path().join("unused-data"))
            .current_dir(self.dir.path())
            .arg("--config")
            .arg(self.dir.path().join("config.toml"))
            .arg("prepare")
            .arg(path)
            .args(args)
            .output()
            .unwrap();
        assert!(
            output.status.success(),
            "{}",
            String::from_utf8_lossy(&output.stderr)
        );
        assert_eq!(std::fs::read(&self.source).unwrap(), self.original);
        assert!(
            !self.dir.path().join("unused-data").exists(),
            "dry run must not open a Telegram session or library"
        );
        let stdout = String::from_utf8(output.stdout).unwrap();
        assert!(stdout.contains("dry run: nothing was changed"), "{stdout}");
        stdout
    }
}

#[test]
fn dry_run_reports_each_verdict_and_preserves_original_bytes() {
    if !ffmpeg_required("prepare dry-run report") {
        return;
    }
    let fixture = Fixture::new();
    let size = fixture.original.len() as u64;
    let expected_name = "épisode".repeat(8).chars().take(43).collect::<String>() + "…";
    let cases = [
        (1_000_000_000, "und", "already fits", "0", "1.00"),
        (1, "und", "nothing to drop", "0", "0.00"),
        (size - 1, "eng", "prepare", "1", "0.00"),
        (1, "eng", "prepare, still needs 2 parts", "1", "0.00"),
    ];
    for (limit, audio, verdict, dropped, part_gb) in cases {
        let output = fixture.dry_run(
            &fixture.source,
            &["--mp4", "--limit", &limit.to_string(), "--audio", audio],
        );
        let row = output
            .lines()
            .find(|line| line.starts_with(&expected_name))
            .expect("file table row");
        assert!(row.ends_with(verdict), "{row}");
        let columns: Vec<_> = row.split_whitespace().collect();
        assert_eq!(columns[2], dropped, "dropped audio in {row}");
        assert_eq!(columns[3], "0", "dropped subtitles in {row}");
        let summary = output
            .lines()
            .find(|line| line.contains("GB ->"))
            .expect("size summary");
        assert!(
            summary.starts_with("0.0 GB -> 0.0 GB, saving 0.0 GB."),
            "{summary}"
        );
        assert!(
            summary.ends_with(&format!("One part is {part_gb} GB.")),
            "{summary}"
        );
        assert!(
            !output.contains("warning:"),
            "playable H.264 has no video warning"
        );
    }
    assert_eq!(
        std::fs::read_dir(fixture.dir.path()).unwrap().count(),
        2,
        "no output or working file may be created"
    );
}

#[test]
fn mp4_dry_run_warns_about_unfixable_video_but_not_wrapper_or_audio() {
    if !ffmpeg_required("prepare dry-run codec warning") {
        return;
    }
    let fixture = Fixture::new();
    let unsupported = fixture.dir.path().join("unsupported.mkv");
    let status = Command::new("ffmpeg")
        .args(["-v", "error", "-i"])
        .arg(&fixture.source)
        .args(["-c:v", "mpeg4", "-c:a", "ac3"])
        .arg(&unsupported)
        .status()
        .unwrap();
    assert!(status.success());
    let bytes = std::fs::read(&unsupported).unwrap();
    let duplicate = fixture.dir.path().join("second.mkv");
    std::fs::copy(&unsupported, &duplicate).unwrap();
    let output = fixture.dry_run(fixture.dir.path(), &["--mp4", "--audio", "und"]);
    let warning = output
        .lines()
        .find(|line| line.starts_with("warning:"))
        .unwrap();
    assert!(warning.contains("2 file(s) carry mpeg4 video"), "{warning}");
    assert!(
        !warning.contains("Matroska") && !warning.contains("ac3"),
        "{warning}"
    );
    assert!(
        warning.contains("still be converted on every play"),
        "{warning}"
    );
    assert_eq!(std::fs::read(&unsupported).unwrap(), bytes);
    assert_eq!(std::fs::read(&duplicate).unwrap(), bytes);
    assert_eq!(std::fs::read_dir(fixture.dir.path()).unwrap().count(), 4);
}
