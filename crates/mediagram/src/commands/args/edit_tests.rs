use super::*;
use crate::edit::anime::AnimeChoice;
use crate::edit::plan::Clearable;
use clap::Parser;
use clap::error::ErrorKind;
use mlib_spec::Kind;

/// `mediagram edit`'s arguments, parsed by clap exactly as the CLI parses
/// them, without the binary's own command enum.
#[derive(Parser)]
struct Edit {
    #[command(flatten)]
    args: EditArgs,
}

fn parse(args: &[&str]) -> Result<EditArgs, clap::Error> {
    Edit::try_parse_from(std::iter::once("edit").chain(args.iter().copied())).map(|e| e.args)
}

fn refused(args: &[&str]) -> ErrorKind {
    match parse(args) {
        Ok(_) => panic!("{args:?} should be refused"),
        Err(err) => err.kind(),
    }
}

/// The flags every field correction is made with.
const FIELD_EDITS: [&[&str]; 11] = [
    &["--refresh"],
    &["--kind", "ep"],
    &["--tmdb", "1399"],
    &["--clear", "year"],
    &["--title", "Winter"],
    &["--show", "Thrones"],
    &["--year", "2011"],
    &["--season", "1"],
    &["--episode", "2"],
    &["--chap", "Intro"],
    &["--path", "a/b"],
];

#[test]
fn each_field_correction_reaches_its_field() {
    let all: Vec<&str> = std::iter::once("set-a")
        .chain(FIELD_EDITS.concat())
        .collect();
    let args = parse(&all).unwrap();

    assert_eq!(args.set_id, "set-a");
    assert!(args.refresh);
    assert_eq!(args.kind, Some(Kind::Ep));
    assert_eq!(args.tmdb, Some(1399));
    assert_eq!(args.clear, [Clearable::Year]);
    assert_eq!(args.title.as_deref(), Some("Winter"));
    assert_eq!(args.show.as_deref(), Some("Thrones"));
    assert_eq!(args.year, Some(2011));
    assert_eq!(args.season, Some(1));
    assert_eq!(args.episode, Some(2));
    assert_eq!(args.chap.as_deref(), Some("Intro"));
    assert_eq!(args.path.as_deref(), Some("a/b"));
    assert!(!args.dry_run);
}

/// Fields to empty can be listed with commas, repeated, or both.
#[test]
fn clear_takes_a_comma_separated_list_and_repeats() {
    let args = parse(&["set-a", "--clear", "show,year", "--clear", "path"]).unwrap();

    assert_eq!(
        args.clear,
        [Clearable::Show, Clearable::Year, Clearable::Path]
    );
}

/// A space after a comma has always been accepted, and typing the flag must
/// not start refusing it.
#[test]
fn clear_trims_each_listed_field() {
    let args = parse(&["set-a", "--clear", "show, chap"]).unwrap();

    assert_eq!(args.clear, [Clearable::Show, Clearable::Chap]);
}

/// An unknown field or a kind a set cannot be moved to is refused while the
/// arguments are read, before the index is opened.
#[test]
fn an_unknown_field_or_kind_is_refused_by_the_parser() {
    assert_eq!(
        refused(&["set-a", "--clear", "bogus"]),
        ErrorKind::ValueValidation
    );
    assert_eq!(
        refused(&["set-a", "--kind", "doc"]),
        ErrorKind::ValueValidation
    );
}

/// The set to correct is not optional, and numbers out of their range are
/// refused before anything is looked up.
#[test]
fn a_missing_set_or_an_impossible_number_is_refused() {
    assert_eq!(refused(&[]), ErrorKind::MissingRequiredArgument);
    assert_eq!(
        refused(&["set-a", "--year", "70000"]),
        ErrorKind::ValueValidation
    );
    assert_eq!(
        refused(&["set-a", "--tmdb", "1.5"]),
        ErrorKind::ValueValidation
    );
    assert_eq!(
        refused(&["set-a", "--season", "one"]),
        ErrorKind::ValueValidation
    );
}

/// `--anime`, `--category` and `--clear-category` each write one index row
/// and nothing else, so none of them combines with a field correction —
/// any of them.
#[test]
fn index_only_edits_refuse_every_field_correction() {
    for index_only in [
        &["--anime", "yes"][..],
        &["--category", "Trading"],
        &["--clear-category"],
    ] {
        for field in FIELD_EDITS {
            let args = [&["set-a"][..], index_only, field].concat();
            assert_eq!(refused(&args), ErrorKind::ArgumentConflict, "{args:?}");
        }
    }
}

/// What an index-only edit would do can still be asked first.
#[test]
fn index_only_edits_still_take_a_dry_run() {
    let anime = parse(&["set-a", "--anime", "auto", "--dry-run"]).unwrap();
    assert_eq!(anime.anime, Some(AnimeChoice::Auto));
    assert!(anime.dry_run);

    let category = parse(&["set-a", "--category", "Trading", "--dry-run"]).unwrap();
    assert_eq!(category.category.as_deref(), Some("Trading"));
    assert!(category.dry_run);

    let cleared = parse(&["set-a", "--clear-category", "--dry-run"]).unwrap();
    assert!(cleared.clear_category);
    assert!(cleared.dry_run);
}
