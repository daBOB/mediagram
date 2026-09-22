//! Publishing: completing the pointer, and handing files to a configured
//! command. Argv only, never a shell, because a shell reports the exit status
//! of the last element of a pipeline and would call a failed upload a success.

use std::path::Path;

use mediagram::export::latest::complete;
use mediagram::export::pointer;
use mediagram::export::publish::{child_env, run_publish, substitute};

fn draft() -> mlib_spec::package::LatestPointer {
    pointer::draft(1_781_568_000, &[5u8; 32])
}

#[test]
fn completing_a_pointer_fills_the_download_fields_only() {
    let before = mlib_spec::package::associated_data(&draft());

    let done = complete(
        &draft(),
        "pkg.tar.gz.enc",
        "https://example.com/",
        4096,
        &"a".repeat(64),
    );

    assert_eq!(done.file, "pkg.tar.gz.enc");
    assert_eq!(done.url, "https://example.com/pkg.tar.gz.enc");
    assert_eq!(done.bytes, 4096);
    assert_eq!(done.sha256, "a".repeat(64));
    assert_eq!(
        mlib_spec::package::associated_data(&done),
        before,
        "completing must not disturb what the cipher authenticated"
    );
}

#[test]
fn a_base_url_without_a_trailing_slash_still_joins_correctly() {
    let done = complete(
        &draft(),
        "pkg.enc",
        "https://example.com/pkgs",
        1,
        &"b".repeat(64),
    );
    assert_eq!(done.url, "https://example.com/pkgs/pkg.enc");
}

#[test]
fn a_completed_pointer_passes_the_readers_own_check() {
    let done = complete(
        &draft(),
        "pkg.enc",
        "https://example.com/",
        4096,
        &"c".repeat(64),
    );
    assert!(
        mlib_spec::package::pointer_is_readable(&done, &[mlib_spec::schema::SCHEMA_VERSION])
            .is_ok()
    );
}

#[test]
fn the_file_placeholder_is_replaced_wherever_it_appears() {
    let argv: Vec<String> = ["rclone", "copy", "{file}", "r2:bucket/"]
        .iter()
        .map(|s| s.to_string())
        .collect();
    let out = substitute(&argv, Path::new("/tmp/a b/pkg.enc"));
    assert_eq!(
        out,
        vec!["rclone", "copy", "/tmp/a b/pkg.enc", "r2:bucket/"]
    );
}

#[test]
fn a_path_with_spaces_stays_one_argument() {
    let argv = vec!["echo".to_string(), "{file}".to_string()];
    let out = substitute(&argv, Path::new("/tmp/with space/pkg.enc"));
    assert_eq!(out.len(), 2);
    assert_eq!(out[1], "/tmp/with space/pkg.enc");
}

#[tokio::test]
async fn a_command_that_succeeds_is_accepted() {
    let argv = vec!["true".to_string(), "{file}".to_string()];
    assert!(run_publish(&argv, Path::new("/tmp/pkg.enc")).await.is_ok());
}

/// The bug this design exists to prevent: a failed upload must fail the run.
#[tokio::test]
async fn a_command_that_fails_fails_the_publish() {
    let argv = vec!["false".to_string(), "{file}".to_string()];
    let err = run_publish(&argv, Path::new("/tmp/pkg.enc"))
        .await
        .unwrap_err();
    assert!(format!("{err:#}").contains("publish command"), "{err:#}");
}

#[tokio::test]
async fn a_command_that_does_not_exist_fails_clearly() {
    let argv = vec!["mediagram-no-such-binary".to_string()];
    assert!(run_publish(&argv, Path::new("/tmp/pkg.enc")).await.is_err());
}

#[tokio::test]
async fn an_empty_command_is_refused_rather_than_spawning_something_odd() {
    assert!(run_publish(&[], Path::new("/tmp/pkg.enc")).await.is_err());
}

/// The upload tool needs its own credentials, not mediagram's Telegram hash
/// or TMDB key. Decided on a given environment rather than by setting a
/// variable on this process, which every other test thread shares.
#[test]
fn the_child_does_not_inherit_mediagram_secrets() {
    let given = [
        ("MEDIAGRAM_API_HASH", "leaked-secret-value"),
        ("MEDIAGRAM_TMDB_KEY", "another"),
        ("RCLONE_CONFIG", "/home/me/rclone.conf"),
    ]
    .map(|(key, value)| (key.into(), value.into()));

    let kept = child_env(given);

    assert_eq!(kept, vec![("RCLONE_CONFIG".into(), "/home/me/rclone.conf".into())]);
}

/// The filtered environment is the one the child actually runs with, and
/// what it keeps still reaches it.
#[tokio::test]
async fn the_child_runs_with_the_rest_of_the_environment() {
    let dir = tempfile::tempdir().unwrap();
    let out = dir.path().join("env.txt");
    let argv = vec![
        "sh".to_string(), // a real binary, invoked with explicit args, not a shell string
        "-c".to_string(),
        format!("env > {}", out.display()),
    ];
    run_publish(&argv, Path::new("/tmp/pkg.enc")).await.unwrap();

    let seen = std::fs::read_to_string(&out).unwrap();
    assert!(seen.lines().any(|line| line.starts_with("PATH=")), "{seen}");
    assert!(!seen.lines().any(|line| line.starts_with("MEDIAGRAM_")), "{seen}");
}
