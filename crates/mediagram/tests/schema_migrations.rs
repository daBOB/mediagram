//! Migrations are applied by version, not replayed wholesale. The original
//! list was re-executed on every open and relied on every statement being
//! `CREATE ... IF NOT EXISTS`, which cannot express adding a column.

use mediagram::index::db;

fn schema_version(conn: &rusqlite::Connection) -> i64 {
    db::get_meta(conn, "schema_version")
        .unwrap()
        .unwrap()
        .parse()
        .unwrap()
}

fn has_column(conn: &rusqlite::Connection, table: &str, column: &str) -> bool {
    let mut stmt = conn
        .prepare(&format!("PRAGMA table_info({table})"))
        .unwrap();
    let names: Vec<String> = stmt
        .query_map([], |r| r.get::<_, String>(1))
        .unwrap()
        .map(|r| r.unwrap())
        .collect();
    names.iter().any(|n| n == column)
}

#[test]
fn a_fresh_database_reaches_the_current_version() {
    let dir = tempfile::tempdir().unwrap();
    let conn = db::open(dir.path()).unwrap();
    assert_eq!(schema_version(&conn), mlib_spec::schema::SCHEMA_VERSION);
    assert!(has_column(&conn, "sets", "chap"));
}

/// The case the old runner could not survive: opening twice must not try to
/// add a column that is already there.
#[test]
fn opening_twice_is_a_no_op() {
    let dir = tempfile::tempdir().unwrap();
    let first = db::open(dir.path()).unwrap();
    drop(first);
    let second = db::open(dir.path()).unwrap();
    assert_eq!(schema_version(&second), mlib_spec::schema::SCHEMA_VERSION);
}

/// A database created before the column existed must gain it, keeping its
/// rows, which is what an upgrade on a real machine looks like.
#[test]
fn a_database_at_version_one_is_upgraded_in_place() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("library.db");
    {
        // Build a v1 database by hand: the original statements, version 1.
        let conn = mediagram::index::sqlite_init::open(&path).unwrap();
        for statement in mlib_spec::schema::migrations_up_to(1) {
            conn.execute(statement, []).unwrap();
        }
        conn.execute(
            "INSERT INTO meta(key, value) VALUES('schema_version', '1')",
            [],
        )
        .unwrap();
        conn.execute(
            "INSERT INTO sets(set_id, kind, container, total, part_count, created_at, spec_version)
             VALUES('01OLDSET0000000000000001', 'movie', 'mkv', 10, 1, 1700000000, 2)",
            [],
        )
        .unwrap();
        assert!(!has_column(&conn, "sets", "chap"), "v1 has no chap column");
    }

    let conn = db::open(dir.path()).unwrap();

    assert!(has_column(&conn, "sets", "chap"), "upgrade adds the column");
    assert_eq!(schema_version(&conn), mlib_spec::schema::SCHEMA_VERSION);
    let kept: i64 = conn
        .query_row("SELECT COUNT(*) FROM sets", [], |r| r.get(0))
        .unwrap();
    assert_eq!(kept, 1, "an upgrade keeps existing rows");
}

#[test]
fn an_upgraded_database_matches_a_fresh_one() {
    let fresh_dir = tempfile::tempdir().unwrap();
    let fresh = db::open(fresh_dir.path()).unwrap();
    let mut fresh_cols: Vec<String> = column_names(&fresh, "sets");

    let old_dir = tempfile::tempdir().unwrap();
    {
        let conn = mediagram::index::sqlite_init::open(old_dir.path().join("library.db")).unwrap();
        for statement in mlib_spec::schema::migrations_up_to(1) {
            conn.execute(statement, []).unwrap();
        }
        conn.execute(
            "INSERT INTO meta(key, value) VALUES('schema_version', '1')",
            [],
        )
        .unwrap();
    }
    let upgraded = db::open(old_dir.path()).unwrap();
    let mut upgraded_cols: Vec<String> = column_names(&upgraded, "sets");

    fresh_cols.sort();
    upgraded_cols.sort();
    assert_eq!(fresh_cols, upgraded_cols, "one definition of the schema");
}

fn column_names(conn: &rusqlite::Connection, table: &str) -> Vec<String> {
    let mut stmt = conn
        .prepare(&format!("PRAGMA table_info({table})"))
        .unwrap();
    stmt.query_map([], |r| r.get::<_, String>(1))
        .unwrap()
        .map(|r| r.unwrap())
        .collect()
}

fn has_table(conn: &rusqlite::Connection, table: &str) -> bool {
    conn.query_row(
        "SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name=?1",
        [table],
        |r| r.get::<_, i64>(0),
    )
    .unwrap()
        > 0
}

/// A v11 database — the layout before `categories` existed — must gain the
/// table on open, keeping its rows, which is what an upgrade on a real
/// uploading machine looks like.
#[test]
fn a_database_at_v11_gains_the_categories_table_on_upgrade() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("library.db");
    {
        let conn = mediagram::index::sqlite_init::open(&path).unwrap();
        for statement in mlib_spec::schema::migrations_up_to(11) {
            conn.execute(statement, []).unwrap();
        }
        conn.execute(
            "INSERT INTO meta(key, value) VALUES('schema_version', '11')",
            [],
        )
        .unwrap();
        assert!(!has_table(&conn, "categories"), "v11 has no categories table");
    }

    let upgraded = db::open(dir.path()).unwrap();

    assert!(has_table(&upgraded, "categories"), "upgrade adds the table");
    assert_eq!(schema_version(&upgraded), mlib_spec::schema::SCHEMA_VERSION);

    let fresh_dir = tempfile::tempdir().unwrap();
    let fresh = db::open(fresh_dir.path()).unwrap();
    let mut fresh_cols = column_names(&fresh, "categories");
    let mut upgraded_cols = column_names(&upgraded, "categories");
    fresh_cols.sort();
    upgraded_cols.sort();
    assert_eq!(fresh_cols, upgraded_cols, "one definition of the schema");
}

/// A v12 database — the layout before the subtitle tables existed — must
/// gain both on open, keeping its rows, which is what an upgrade on a real
/// uploading machine looks like.
#[test]
fn a_database_at_v12_gains_the_subtitle_tables_on_upgrade() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join("library.db");
    {
        let conn = mediagram::index::sqlite_init::open(&path).unwrap();
        for statement in mlib_spec::schema::migrations_up_to(12) {
            conn.execute(statement, []).unwrap();
        }
        conn.execute(
            "INSERT INTO meta(key, value) VALUES('schema_version', '12')",
            [],
        )
        .unwrap();
        conn.execute(
            "INSERT INTO sets(set_id, kind, container, total, part_count, created_at, spec_version)
             VALUES('01OLDSET0000000000000002', 'movie', 'mkv', 10, 1, 1700000000, 4)",
            [],
        )
        .unwrap();
        assert!(!has_table(&conn, "subtitle_files"), "v12 has no subtitle_files table");
        assert!(!has_table(&conn, "subtitle_tracks"), "v12 has no subtitle_tracks table");
    }

    let upgraded = db::open(dir.path()).unwrap();

    assert!(has_table(&upgraded, "subtitle_files"), "upgrade adds subtitle_files");
    assert!(has_table(&upgraded, "subtitle_tracks"), "upgrade adds subtitle_tracks");
    assert_eq!(schema_version(&upgraded), mlib_spec::schema::SCHEMA_VERSION);
    let kept: i64 = upgraded
        .query_row("SELECT COUNT(*) FROM sets", [], |r| r.get(0))
        .unwrap();
    assert_eq!(kept, 1, "an upgrade keeps existing rows");

    let fresh_dir = tempfile::tempdir().unwrap();
    let fresh = db::open(fresh_dir.path()).unwrap();
    for table in ["subtitle_files", "subtitle_tracks"] {
        let mut fresh_cols = column_names(&fresh, table);
        let mut upgraded_cols = column_names(&upgraded, table);
        fresh_cols.sort();
        upgraded_cols.sort();
        assert_eq!(fresh_cols, upgraded_cols, "one definition of the schema");
    }
}
