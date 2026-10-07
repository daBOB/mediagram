//! The `mediagram_cache` binary itself, run the way an operator pairs a
//! device: `mediagram_cache token` against a config file that names a
//! scratch state directory.

use std::path::Path;
use std::process::{Command, Output};

fn run(args: &[&str], config: &Path) -> Output {
    Command::new(env!("CARGO_BIN_EXE_mediagram_cache"))
        .args(args)
        .arg("--config")
        .arg(config)
        .output()
        .expect("running mediagram_cache")
}

fn printed_token(config: &Path) -> String {
    let out = run(&["token"], config);
    assert!(
        out.status.success(),
        "token failed: {}",
        String::from_utf8_lossy(&out.stderr)
    );
    String::from_utf8(out.stdout)
        .expect("the token is printed as text")
        .trim_end()
        .to_owned()
}

#[test]
fn token_is_created_on_first_run_and_printed_unchanged_after() {
    let dir = tempfile::tempdir().unwrap();
    let state = dir.path().join("state");
    let config = dir.path().join("cache.toml");
    std::fs::write(&config, format!("state_dir = '{}'\n", state.display())).unwrap();

    let first = printed_token(&config);
    assert_eq!(first.len(), 64, "32 bytes, hex-encoded: {first:?}");
    assert!(first.bytes().all(|b| b.is_ascii_hexdigit()), "{first:?}");
    let stored = std::fs::read_to_string(state.join("token")).unwrap();
    assert_eq!(
        stored.trim_end(),
        first,
        "the printed token is the stored one"
    );

    assert_eq!(
        printed_token(&config),
        first,
        "a second run must not re-pair"
    );
}

#[test]
fn a_named_config_that_is_missing_is_an_error_not_the_defaults() {
    let dir = tempfile::tempdir().unwrap();
    let config = dir.path().join("absent.toml");

    let out = run(&["token"], &config);

    assert!(!out.status.success());
    let stderr = String::from_utf8_lossy(&out.stderr);
    assert!(stderr.contains("cannot read config"), "{stderr}");
    assert!(out.stdout.is_empty(), "no token may be printed: {out:?}");
}
