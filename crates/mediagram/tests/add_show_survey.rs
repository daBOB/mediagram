//! The command must disclose files it could not probe before proceeding.

fn run(dry_run: bool) -> (std::process::Output, tempfile::TempDir) {
    let dir = tempfile::tempdir().unwrap();
    let source = dir.path().join("Show.S01E01.mkv");
    std::fs::write(&source, b"not a media container").unwrap();
    let config = dir.path().join("config.toml");
    std::fs::write(
        &config,
        "api_id = 1\napi_hash = 'offline-fixture'\nchannel = 'offline-fixture'\ndata_dir = 'unused-data'\n",
    )
    .unwrap();
    if !dry_run {
        // Stop at library opening, after confirmation and before any upload.
        std::fs::write(dir.path().join("unused-data"), "not a directory").unwrap();
    }
    let mut command = std::process::Command::new(env!("CARGO_BIN_EXE_mediagram"));
    command
        .env_clear()
        .envs([("PATH", std::env::var_os("PATH").unwrap_or_default())])
        .current_dir(dir.path())
        .arg("--config")
        .arg(config)
        .arg("add-show")
        .arg(dir.path())
        .args(["--tmdb", "1"]);
    if dry_run {
        command.arg("--dry-run");
    }
    let output = command.output().unwrap();
    assert_eq!(std::fs::read(source).unwrap(), b"not a media container");
    (output, dir)
}

fn report(output: &std::process::Output) -> String {
    let report = String::from_utf8(output.stdout.clone()).unwrap();
    assert!(report.contains("unknown browser compatibility"), "{report}");
    assert!(
        report.contains("checking browser compatibility of"),
        "{report}"
    );
    assert!(report.contains("Show.S01E01.mkv"), "{report}");
    assert!(report.contains("ffprobe exited"), "{report}");
    report
}

#[test]
fn dry_run_reports_unknown_compatibility_without_opening_the_library() {
    let (output, dir) = run(true);
    assert!(output.status.success(), "{output:?}");
    let report = report(&output);
    assert!(
        report.contains("1 file(s) have unknown browser compatibility"),
        "{report}"
    );
    assert!(report.contains("dry run: nothing was uploaded"), "{report}");
    assert!(!dir.path().join("unused-data").exists());
}

#[test]
fn unknown_compatibility_enters_the_existing_confirmation_flow() {
    let (output, dir) = run(false);
    assert!(!output.status.success());
    let report = report(&output);
    assert!(report.contains("not a terminal, continuing"), "{report}");
    assert_eq!(
        std::fs::read_to_string(dir.path().join("unused-data")).unwrap(),
        "not a directory"
    );
}
