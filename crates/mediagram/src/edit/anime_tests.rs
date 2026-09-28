use mlib_spec::caption::{Caption, Episode, Kind, Part};
use mlib_spec::ids::ProviderIds;

use super::*;
use crate::index::db;

fn caption(kind: Kind, tmdb: Option<u64>) -> Caption {
    Caption {
        t: kind,
        ids: ProviderIds {
            tmdb,
            tvdb: None,
            imdb: None,
        },
        cid: None,
        show: Some("Mila Superstar".into()),
        chap: None,
        path: None,
        title: Some("Episode 1".into()),
        year: Some(1976),
        s: Some(1),
        e: Some(Episode::Single(1)),
        abs: None,
        q: None,
        hdr: None,
        container: "mkv".into(),
        vcodec: None,
        acodec: None,
        alang: vec![],
        slang: vec![],
        dur: None,
        variant: None,
        set: "01ANIMESET00000000000001".into(),
        part: Part {
            i: 0,
            n: 1,
            off: 0,
            len: 0,
            sha256: String::new(),
        },
        total: 0,
    }
}

fn row(kind: Kind, tmdb: Option<u64>) -> SetRow {
    SetRow::from_caption(&caption(kind, tmdb), 1_700_000_000)
}

fn db() -> Connection {
    let dir = tempfile::tempdir().unwrap();
    db::open(dir.path()).unwrap()
}

#[test]
fn setting_yes_writes_an_override_keyed_to_the_series() {
    let conn = db();
    let row = row(Kind::Ep, Some(46348));

    run(&conn, &row, AnimeChoice::Yes, false).unwrap();

    assert_eq!(
        anime_overrides::get(&conn, Kind::Ep, 46348).unwrap(),
        Some(true)
    );
}

/// `auto` clears the override but leaves a row — a merge needs its `set_at`
/// to carry the clear to the other machine.
#[test]
fn setting_auto_clears_a_prior_override_but_leaves_the_row() {
    let conn = db();
    let row = row(Kind::Ep, Some(46348));
    run(&conn, &row, AnimeChoice::Yes, false).unwrap();

    run(&conn, &row, AnimeChoice::Auto, false).unwrap();

    assert_eq!(anime_overrides::get(&conn, Kind::Ep, 46348).unwrap(), None);
    let rows: i64 = conn
        .query_row("SELECT COUNT(*) FROM anime_overrides", [], |r| r.get(0))
        .unwrap();
    assert_eq!(rows, 1);
}

#[test]
fn dry_run_writes_nothing() {
    let conn = db();
    let row = row(Kind::Ep, Some(46348));

    run(&conn, &row, AnimeChoice::Yes, true).unwrap();

    assert_eq!(anime_overrides::get(&conn, Kind::Ep, 46348).unwrap(), None);
}

#[test]
fn a_set_with_no_tmdb_id_is_refused() {
    let conn = db();
    let row = row(Kind::Ep, None);

    let error = run(&conn, &row, AnimeChoice::Yes, false).unwrap_err();

    assert!(error.to_string().contains("has no TMDB id"));
}

#[test]
fn a_tutorial_is_refused() {
    let conn = db();
    let row = row(Kind::Tut, Some(1));

    let error = run(&conn, &row, AnimeChoice::Yes, false).unwrap_err();

    assert!(error.to_string().contains("only films and series can be anime"));
}

#[test]
fn asking_for_the_value_it_already_has_is_a_no_op() {
    let conn = db();
    let row = row(Kind::Ep, Some(46348));

    let error = run(&conn, &row, AnimeChoice::Auto, false).unwrap_err();

    assert!(error.to_string().contains("already says automatic"));
}
