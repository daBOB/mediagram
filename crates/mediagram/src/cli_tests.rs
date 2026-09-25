use super::*;
use clap::error::ErrorKind;

#[test]
fn global_config_is_accepted_before_and_after_public_and_hidden_commands() {
    for args in [
        vec![
            "mediagram",
            "--config",
            "/tmp/does-not-exist.toml",
            "status",
        ],
        vec![
            "mediagram",
            "status",
            "--config",
            "/tmp/does-not-exist.toml",
        ],
    ] {
        let parsed = Cli::try_parse_from(args).unwrap();
        assert_eq!(
            parsed.config,
            Some(PathBuf::from("/tmp/does-not-exist.toml"))
        );
        assert!(matches!(parsed.cmd, Cmd::Status));
    }
    let parsed =
        Cli::try_parse_from(["mediagram", "finish-set", "set-a", "--config", "other.toml"])
            .unwrap();
    assert_eq!(parsed.config, Some(PathBuf::from("other.toml")));
    assert!(matches!(parsed.cmd, Cmd::FinishSet { set_id, .. } if set_id == "set-a"));
}

#[test]
fn removing_titles_requires_an_explicit_yes_independent_of_dry_run() {
    for (flags, expected_dry_run, expected_yes) in [
        (vec![], false, false),
        (vec!["--dry-run"], true, false),
        (vec!["--yes"], false, true),
        (vec!["--dry-run", "--yes"], true, true),
    ] {
        let args = [vec!["mediagram", "remove", "set-a", "set-b"], flags].concat();
        let Cmd::Remove {
            set_id,
            dry_run,
            yes,
        } = Cli::try_parse_from(args).unwrap().cmd
        else {
            panic!("expected remove");
        };
        assert_eq!(set_id, ["set-a", "set-b"]);
        assert_eq!((dry_run, yes), (expected_dry_run, expected_yes));
    }
}

#[test]
fn the_background_finish_command_does_not_delete_or_suppress_push_by_default() {
    let Cmd::FinishSet {
        set_id,
        delete,
        no_push,
    } = Cli::try_parse_from(["mediagram", "finish-set", "set-a"])
        .unwrap()
        .cmd
    else {
        panic!("expected finish-set");
    };
    assert_eq!(set_id, "set-a");
    assert_eq!(delete, None);
    assert!(!no_push);
    let Cmd::FinishSet {
        delete, no_push, ..
    } = Cli::try_parse_from([
        "mediagram",
        "finish-set",
        "set-a",
        "--delete",
        "original movie.mkv",
        "--no-push",
    ])
    .unwrap()
    .cmd
    else {
        panic!("expected finish-set");
    };
    assert_eq!(delete, Some(PathBuf::from("original movie.mkv")));
    assert!(no_push);
}

#[test]
fn add_preserves_typed_metadata_and_source_deletion_is_opt_in() {
    let Cmd::Add(args) = Cli::try_parse_from([
        "mediagram",
        "add",
        "movie.mkv",
        "--tmdb",
        "42",
        "--season",
        "2",
        "--episode",
        "3",
        "--alang",
        "de,en",
        "--watch",
        "--no-push",
    ])
    .unwrap()
    .cmd
    else {
        panic!("expected add");
    };
    assert_eq!(args.file, PathBuf::from("movie.mkv"));
    assert_eq!(
        (args.tmdb, args.season, args.episode),
        (Some(42), Some(2), Some(3))
    );
    assert_eq!(args.alang, Some(vec!["de".into(), "en".into()]));
    assert!(args.watch && args.no_push);
    assert!(!args.delete_source);
}

#[test]
fn verification_and_hidden_smoke_commands_parse_without_executing_them() {
    let Cmd::Verify {
        set_id,
        all,
        full,
        since,
    } = Cli::try_parse_from([
        "mediagram",
        "verify",
        "--all",
        "--full",
        "--since",
        "123456",
    ])
    .unwrap()
    .cmd
    else {
        panic!("expected verify");
    };
    assert_eq!(set_id, None);
    assert!(all && full);
    assert_eq!(since, Some(123456));
    let Cmd::SmokeUpload { file } = Cli::try_parse_from(["mediagram", "smoke-upload", "small.bin"])
        .unwrap()
        .cmd
    else {
        panic!("expected smoke-upload");
    };
    assert_eq!(file, PathBuf::from("small.bin"));
}

#[test]
fn invalid_arguments_are_rejected_by_the_parser_before_any_handler() {
    for (args, kind) in [
        (
            vec!["mediagram", "status", "--typo"],
            ErrorKind::UnknownArgument,
        ),
        (
            vec!["mediagram", "finish-set"],
            ErrorKind::MissingRequiredArgument,
        ),
        (
            vec!["mediagram", "accept-login"],
            ErrorKind::MissingRequiredArgument,
        ),
        (vec!["mediagram", "add"], ErrorKind::MissingRequiredArgument),
        (
            vec!["mediagram", "add", "movie.mkv", "--tmdb", "not-a-number"],
            ErrorKind::ValueValidation,
        ),
        (
            vec!["mediagram", "verify", "--since", "yesterday"],
            ErrorKind::ValueValidation,
        ),
        (
            vec!["mediagram", "prepare", "movie.mkv", "--limit", "large"],
            ErrorKind::ValueValidation,
        ),
        (
            vec!["mediagram", "finish-set", "set-a", "--delete"],
            ErrorKind::InvalidValue,
        ),
    ] {
        let error = Cli::try_parse_from(args.clone())
            .err()
            .expect("invalid arguments must fail");
        assert_eq!(error.kind(), kind, "{args:?}: {error}");
    }
}
