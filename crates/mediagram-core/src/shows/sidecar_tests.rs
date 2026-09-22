use mediagram_tmdb::details::TitleDetailsRow;
use mlib_spec::Kind;
use rusqlite::Connection;

use super::*;
use crate::shows::{read, upsert};

fn row(overview: &str) -> TitleDetailsRow {
    TitleDetailsRow {
        kind: Kind::Movie,
        id: 550,
        lang: "en-US".into(),
        overview: Some(overview.into()),
        tagline: None, genres: None, rating: None, network: None, status: None,
        first_air: None, last_air: None, total_seasons: None, total_episodes: None,
    }
}

/// Every object a database holds, as SQLite itself records it: one row per
/// table and index, carrying the statement that would rebuild it — including
/// the columns `ALTER TABLE` appended, which a single hand-written `CREATE`
/// spells differently even when it ends up with the same ones.
fn schema_of(conn: &Connection) -> Vec<(String, String)> {
    let mut objects: Vec<(String, String)> = conn
        .prepare("SELECT name, COALESCE(sql, '') FROM sqlite_master")
        .unwrap()
        .query_map([], |row| Ok((row.get(0)?, row.get(1)?)))
        .unwrap()
        .collect::<rusqlite::Result<_>>()
        .unwrap();
    objects.sort();
    objects
}

/// The schema is written in one place. A column added to the shared
/// migrations reaches this database too, or the two silently disagree about
/// what a row holds.
///
/// Compared against a database built from those migrations rather than
/// against a list of column names kept here by hand: a hand-written `CREATE
/// TABLE shows(...)` covering the same columns is the second copy of the DDL
/// this exists to forbid, and is also the obvious shortcut past the
/// `ALTER TABLE` replay problem — a list of names would wave it through.
#[test]
fn the_sidecar_is_built_from_the_shared_migrations() {
    let dir = tempfile::tempdir().unwrap();
    let sidecar = open_or_create(&dir.path().join("details.db")).unwrap();

    let reference = Connection::open_in_memory().unwrap();
    for statement in schema::migrations_up_to(schema::SCHEMA_VERSION) {
        reference.execute(statement, []).unwrap();
    }

    let held = schema_of(&sidecar);
    assert!(held.iter().any(|(name, _)| name == "shows"), "no shows table at all");
    assert_eq!(held, schema_of(&reference));
}

/// Asking twice replaces rather than duplicates: a later fetch is a
/// correction, not a second opinion.
#[test]
fn a_second_fetch_of_one_title_replaces_the_first() {
    let dir = tempfile::tempdir().unwrap();
    let conn = open_or_create(&dir.path().join("details.db")).unwrap();

    upsert(&conn, &row("first")).unwrap();
    upsert(&conn, &row("second")).unwrap();

    let row = read(&conn, "tmdb-movie-550").unwrap().unwrap();
    assert_eq!(row.overview.as_deref(), Some("second"));
}

/// Reopening a sidecar that is already at this build's schema applies no
/// statement a second time: SQLite has no `ADD COLUMN IF NOT EXISTS`, so a
/// replayed migration list would fail and take the store with it.
#[test]
fn reopening_an_existing_sidecar_replays_no_migration() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("details.db");

    drop(open_or_create(&path).unwrap());

    assert!(open_or_create(&path).is_ok());
}

/// A migration that fails partway leaves the file exactly as it was.
///
/// A group can be several statements — v6 adds two columns — and without one
/// transaction around them the first would stick while the recorded version
/// stayed behind. Every later open would then replay that `ALTER TABLE` and
/// fail on "duplicate column name" for good, so the device quietly stops
/// recording anything it fetches until it is set up again. The half-upgraded
/// file is built by hand here, because the device it happens on is one that
/// was killed between two statements.
#[test]
fn a_migration_that_fails_partway_leaves_the_file_as_it_was() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("details.db");

    // A v5 sidecar that already holds the second of v6's two columns, so
    // v6's first statement succeeds and its second cannot.
    let conn = Connection::open(&path).unwrap();
    for statement in schema::migrations_up_to(5) {
        conn.execute(statement, []).unwrap();
    }
    conn.execute("ALTER TABLE shows ADD COLUMN total_episodes INTEGER", []).unwrap();
    conn.pragma_update(None, "user_version", 5).unwrap();
    drop(conn);

    assert!(open_or_create(&path).is_err());

    let conn = Connection::open(&path).unwrap();
    assert_eq!(
        recorded_version(&conn).unwrap(),
        Recorded::UserVersion(5),
        "a failed migration advanced the recorded version",
    );
    assert!(
        !schema_of(&conn).iter().any(|(_, sql)| sql.contains("total_seasons")),
        "a failed migration left one of its columns behind",
    );
}

/// A sidecar written before the version moved into `meta` recorded it in
/// `PRAGMA user_version`; opening one takes that as where it is, applies
/// nothing twice, and records the version where the index keeps its own.
#[test]
fn an_older_sidecar_moves_its_version_into_meta() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("details.db");
    let conn = Connection::open(&path).unwrap();
    for statement in schema::migrations_up_to(schema::SCHEMA_VERSION) {
        conn.execute(statement, []).unwrap();
    }
    conn.pragma_update(None, "user_version", schema::SCHEMA_VERSION).unwrap();
    drop(conn);

    let conn = open_or_create(&path).unwrap();
    assert_eq!(recorded_version(&conn).unwrap(), Recorded::Meta(schema::SCHEMA_VERSION));
}
