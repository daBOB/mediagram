//! The `mediagram` binary's own decision before any command runs: only
//! `login` may create a missing config, so every other command fails fast
//! instead of stalling a non-interactive run on a prompt.

use std::path::Path;
use std::process::{Command, Output, Stdio};

fn run(cmd: &str, config: &Path) -> Output {
    Command::new(env!("CARGO_BIN_EXE_mediagram"))
        .arg(cmd)
        .arg("--config")
        .arg(config)
        .stdin(Stdio::null())
        .output()
        .expect("running mediagram")
}

#[test]
fn a_missing_config_fails_an_ordinary_command_with_the_way_out() {
    let dir = tempfile::tempdir().unwrap();
    let config = dir.path().join("absent.toml");

    let out = run("status", &config);

    assert!(!out.status.success());
    let stderr = String::from_utf8_lossy(&out.stderr);
    assert!(
        stderr.contains("run `mediagram login` to create one"),
        "{stderr}"
    );
    assert!(!config.exists(), "only login may write a config");
}

#[test]
fn login_with_a_missing_config_starts_first_run_setup() {
    let dir = tempfile::tempdir().unwrap();
    let config = dir.path().join("absent.toml");

    // With no terminal to answer the prompts, setup stops at the first one;
    // what matters here is that login reached it at all.
    let out = run("login", &config);

    assert!(!out.status.success());
    let stdout = String::from_utf8_lossy(&out.stdout);
    assert!(
        stdout.contains(&format!("No config at {}", config.display())),
        "{stdout}"
    );
    assert!(!config.exists(), "an abandoned setup writes nothing");
}
