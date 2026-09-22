use mlib_spec::Kind;

use crate::api::refresh::install_staged;

use super::*;

fn core_at(dir: &std::path::Path) -> std::sync::Arc<Core> {
    Core::new(dir.display().to_string(), 1, "test-hash".into())
}

/// A row as a fetch would record it. `ShowRow` carries no source of its own
/// — `upsert` writes the one the index writes — and names the kind with the
/// provider's own enum rather than with text.
fn described(kind: Kind, id: u64, overview: &str) -> ShowRow {
    ShowRow {
        kind,
        id,
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

/// The sidecar sits beside the version directories, not inside one. A
/// refresh replaces a version wholesale, and anything kept within it is
/// deleted every time the app asks the channel for the index.
#[test]
fn the_sidecar_survives_the_refreshes_that_follow_it() {
    let data = tempfile::tempdir().unwrap();
    let core = core_at(data.path());
    std::fs::create_dir_all(store::dir(&core).join("v-1")).unwrap();

    let conn = open_or_create(&core).unwrap();
    upsert(&conn, &described(Kind::Movie, 550, "Ein Kellner in Seifenblasen")).unwrap();
    drop(conn);

    // A real refresh rather than a stand-in for one. `install_staged` is
    // both destructive passes at once: it renames the staged directory over
    // the version, repoints `current`, and then sweeps every other version
    // and any leftover staging directory. Removing the old `v-…` directory
    // by hand would exercise neither, and would miss a sidecar kept
    // somewhere the rename or the sweep reaches — `incoming` above all,
    // which is a real place to put a file and one no refresh leaves
    // standing.
    let incoming = store::dir(&core).join("incoming");
    std::fs::create_dir_all(&incoming).unwrap();
    install_staged(&core, &incoming, "v-2").unwrap();

    let conn = open_or_create(&core).unwrap();
    assert!(read(&conn, "tmdb-movie-550").unwrap().is_some());
}

/// Forgetting the library forgets what was learned about it. The rows name
/// the previous account's titles, and a sign-out that left them behind would
/// be a sign-out in name only. Stated as a containment rather than by
/// deleting, because the delete itself is the caller's.
#[test]
fn the_sidecar_is_deleted_along_with_the_library_it_describes() {
    let data = tempfile::tempdir().unwrap();
    let core = core_at(data.path());

    assert!(
        details_db(&core).starts_with(store::dir(&core)),
        "descriptions at {} would survive being signed out",
        details_db(&core).display(),
    );
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
    let data = tempfile::tempdir().unwrap();
    let core = core_at(data.path());
    let sidecar = open_or_create(&core).unwrap();

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
    let data = tempfile::tempdir().unwrap();
    let core = core_at(data.path());
    let conn = open_or_create(&core).unwrap();

    upsert(&conn, &described(Kind::Movie, 550, "first")).unwrap();
    upsert(&conn, &described(Kind::Movie, 550, "second")).unwrap();

    let row = read(&conn, "tmdb-movie-550").unwrap().unwrap();
    assert_eq!(row.overview.as_deref(), Some("second"));
}

/// Reopening a sidecar that is already at this build's schema applies no
/// statement a second time: SQLite has no `ADD COLUMN IF NOT EXISTS`, so a
/// replayed migration list would fail and take the store with it.
#[test]
fn reopening_an_existing_sidecar_replays_no_migration() {
    let data = tempfile::tempdir().unwrap();
    let core = core_at(data.path());

    drop(open_or_create(&core).unwrap());

    assert!(open_or_create(&core).is_ok());
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
    let data = tempfile::tempdir().unwrap();
    let core = core_at(data.path());
    std::fs::create_dir_all(store::dir(&core)).unwrap();

    // A v5 sidecar that already holds the second of v6's two columns, so
    // v6's first statement succeeds and its second cannot.
    let conn = Connection::open(details_db(&core)).unwrap();
    for statement in schema::migrations_up_to(5) {
        conn.execute(statement, []).unwrap();
    }
    conn.execute("ALTER TABLE shows ADD COLUMN total_episodes INTEGER", []).unwrap();
    conn.pragma_update(None, "user_version", 5).unwrap();
    drop(conn);

    assert!(open_or_create(&core).is_err());

    let conn = Connection::open(details_db(&core)).unwrap();
    let at: i64 = conn.pragma_query_value(None, "user_version", |row| row.get(0)).unwrap();
    assert_eq!(at, 5, "a failed migration advanced the recorded version");
    assert!(
        !schema_of(&conn).iter().any(|(_, sql)| sql.contains("total_seasons")),
        "a failed migration left one of its columns behind",
    );
}

/// A key that begins with `tmdb-` and still carries an injection payload in
/// its `kind` is refused against the sidecar too, not only against the index
/// where the same test already stands.
///
/// The row is planted under the exact malicious `kind` — with raw SQL, since
/// `upsert` takes a `Kind` and could not write one — so a pass proves the key
/// never reached this query rather than merely matching nothing in it.
#[test]
fn an_injection_payload_is_refused_against_the_sidecar_too() {
    let data = tempfile::tempdir().unwrap();
    let core = core_at(data.path());

    let kind = "movie'; DROP TABLE shows;";
    let conn = open_or_create(&core).unwrap();
    conn.execute(
        "INSERT INTO shows(source, kind, id, lang, overview)
         VALUES ('tmdb', ?1, 550, 'en-US', 'should never be read')",
        rusqlite::params![kind],
    )
    .unwrap();
    drop(conn);

    assert!(show_info(&core, format!("tmdb-{kind}-550")).is_none());
}
