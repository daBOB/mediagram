use super::*;
use clap::Parser;
use clap::error::ErrorKind;

/// One of these argument structs, parsed by clap exactly as the CLI parses
/// it, without the binary's own command enum.
#[derive(Parser)]
struct Parsed<A: Args> {
    #[command(flatten)]
    args: A,
}

fn parse<A: Args>(args: &[&str]) -> Result<A, clap::Error> {
    Parsed::<A>::try_parse_from(std::iter::once("cmd").chain(args.iter().copied())).map(|p| p.args)
}

/// Nothing but the folder: every option off, so a bare `add-course` neither
/// renames, skips a push nor files the course anywhere.
#[test]
fn a_bare_course_folder_takes_every_default() {
    let args: AddCourseArgs = parse(&["Rust Course"]).unwrap();

    assert_eq!(args.dir, PathBuf::from("Rust Course"));
    assert_eq!(args.course, None);
    assert_eq!(args.cid, None);
    assert_eq!(args.variant, None);
    assert_eq!(args.category, None);
    assert!(!args.dry_run && !args.no_push && !args.no_remux);
}

#[test]
fn every_course_option_reaches_its_field() {
    let args: AddCourseArgs = parse(&[
        "dir",
        "--course",
        "Rust",
        "--cid",
        "rust",
        "--variant",
        "de",
        "--category",
        "Trading",
        "--dry-run",
        "--no-push",
        "--no-remux",
    ])
    .unwrap();

    assert_eq!(args.course.as_deref(), Some("Rust"));
    assert_eq!(args.cid.as_deref(), Some("rust"));
    assert_eq!(args.variant.as_deref(), Some("de"));
    assert_eq!(args.category.as_deref(), Some("Trading"));
    assert!(args.dry_run && args.no_push && args.no_remux);
}

#[test]
fn every_documentary_option_reaches_its_field() {
    let bare: AddDocuArgs = parse(&["Terra X"]).unwrap();
    assert_eq!(bare.path, PathBuf::from("Terra X"));
    assert_eq!(bare.title, None);
    assert!(!bare.dry_run && !bare.no_push && !bare.no_remux);

    let args: AddDocuArgs = parse(&[
        "film.mp4",
        "--title",
        "Reef",
        "--cid",
        "reef",
        "--variant",
        "en",
        "--category",
        "Nature",
        "--dry-run",
        "--no-push",
        "--no-remux",
    ])
    .unwrap();
    assert_eq!(args.title.as_deref(), Some("Reef"));
    assert_eq!(args.cid.as_deref(), Some("reef"));
    assert_eq!(args.variant.as_deref(), Some("en"));
    assert_eq!(args.category.as_deref(), Some("Nature"));
    assert!(args.dry_run && args.no_push && args.no_remux);
}

/// Clearing and storing in one run is how art is replaced, so the parser
/// takes both; refusing a run that does neither is the command's to do.
#[test]
fn artwork_takes_new_files_and_a_clear_together() {
    let args: ArtworkArgs = parse(&[
        "The Matrix",
        "--poster",
        "p.jpg",
        "--backdrop",
        "b.png",
        "--clear",
    ])
    .unwrap();

    assert_eq!(args.target, "The Matrix");
    assert_eq!(args.poster, Some(PathBuf::from("p.jpg")));
    assert_eq!(args.backdrop, Some(PathBuf::from("b.png")));
    assert!(args.clear);
}

/// What to upload, or which title the art is for, is never optional.
#[test]
fn each_command_requires_what_it_acts_on() {
    let missing = |err: clap::Error| err.kind() == ErrorKind::MissingRequiredArgument;
    assert!(parse::<AddCourseArgs>(&[]).is_err_and(missing));
    assert!(parse::<AddDocuArgs>(&[]).is_err_and(missing));
    assert!(parse::<ArtworkArgs>(&[]).is_err_and(missing));
}
