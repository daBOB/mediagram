use mlib_spec::Kind;

use super::*;
use crate::index::db;

fn db() -> Connection {
    let dir = tempfile::tempdir().unwrap();
    db::open(dir.path()).unwrap()
}

#[test]
fn a_title_nobody_set_an_override_for_reads_as_automatic() {
    assert_eq!(get(&db(), Kind::Ep, 46348).unwrap(), None);
}

#[test]
fn setting_yes_is_read_back_as_yes() {
    let conn = db();
    set(&conn, Kind::Ep, 46348, Some(true), 1_700_000_000).unwrap();
    assert_eq!(get(&conn, Kind::Ep, 46348).unwrap(), Some(true));
}

/// Clearing to automatic keeps the row rather than deleting it — a merge
/// needs a timestamp to carry the clear to the other machine.
#[test]
fn setting_auto_keeps_the_row_but_reads_back_as_automatic() {
    let conn = db();
    set(&conn, Kind::Ep, 46348, Some(true), 1_700_000_000).unwrap();
    set(&conn, Kind::Ep, 46348, None, 1_700_000_100).unwrap();

    assert_eq!(get(&conn, Kind::Ep, 46348).unwrap(), None);
    let rows: i64 = conn
        .query_row("SELECT COUNT(*) FROM anime_overrides", [], |row| {
            row.get(0)
        })
        .unwrap();
    assert_eq!(rows, 1, "a cleared override stays a row, not a deletion");
}

/// A film and a series can share a TMDB id; the key is `(source, kind, id)`,
/// the same as `shows`.
#[test]
fn a_film_and_a_series_sharing_an_id_hold_separate_overrides() {
    let conn = db();
    set(&conn, Kind::Movie, 550, Some(true), 1_700_000_000).unwrap();
    set(&conn, Kind::Ep, 550, Some(false), 1_700_000_000).unwrap();

    assert_eq!(get(&conn, Kind::Movie, 550).unwrap(), Some(true));
    assert_eq!(get(&conn, Kind::Ep, 550).unwrap(), Some(false));
}

#[test]
fn setting_it_again_replaces_rather_than_duplicates() {
    let conn = db();
    set(&conn, Kind::Ep, 46348, Some(true), 1_700_000_000).unwrap();
    set(&conn, Kind::Ep, 46348, Some(false), 1_700_000_100).unwrap();

    assert_eq!(get(&conn, Kind::Ep, 46348).unwrap(), Some(false));
    let rows: i64 = conn
        .query_row("SELECT COUNT(*) FROM anime_overrides", [], |row| {
            row.get(0)
        })
        .unwrap();
    assert_eq!(rows, 1);
}
