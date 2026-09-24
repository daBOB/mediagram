use super::*;

#[test]
fn opens_creates_schema_and_records_version() {
    let dir = tempfile::tempdir().unwrap();
    let conn = open(dir.path()).unwrap();
    assert!(dir.path().join("library.db").exists());
    let version = get_meta(&conn, "schema_version").unwrap();
    assert_eq!(version, Some(mlib_spec::schema::SCHEMA_VERSION.to_string()));
}

#[test]
fn meta_roundtrip_and_delete() {
    let dir = tempfile::tempdir().unwrap();
    let conn = open(dir.path()).unwrap();
    assert_eq!(get_meta(&conn, "source:x").unwrap(), None);
    set_meta(&conn, "source:x", "/tmp/a.mkv").unwrap();
    assert_eq!(
        get_meta(&conn, "source:x").unwrap(),
        Some("/tmp/a.mkv".to_string())
    );
    set_meta(&conn, "source:x", "/tmp/b.mkv").unwrap();
    assert_eq!(
        get_meta(&conn, "source:x").unwrap(),
        Some("/tmp/b.mkv".to_string())
    );
    delete_meta(&conn, "source:x").unwrap();
    assert_eq!(get_meta(&conn, "source:x").unwrap(), None);
}

/// The player only reads. Opening read-only is what keeps a serving
/// process from checkpointing, and so from rewriting, the uploader's
/// index underneath it.
#[test]
fn read_only_open_sees_committed_rows_and_refuses_writes() {
    let dir = tempfile::tempdir().unwrap();
    let writable = open(dir.path()).unwrap();
    set_meta(&writable, "schema_version_probe", "1").unwrap();

    let reader = open_read_only(dir.path(), "read").unwrap();
    assert_eq!(
        get_meta(&reader, "schema_version_probe").unwrap(),
        Some("1".to_string())
    );
    assert!(set_meta(&reader, "schema_version_probe", "2").is_err());
}

#[test]
fn read_only_open_refuses_a_database_that_is_not_there() {
    let dir = tempfile::tempdir().unwrap();
    assert!(open_read_only(dir.path(), "read").is_err());
}

#[test]
fn reopen_is_idempotent() {
    let dir = tempfile::tempdir().unwrap();
    open(dir.path()).unwrap();
    // Migrations run again on an existing database without error.
    open(dir.path()).unwrap();
}

#[test]
fn malformed_schema_versions_are_refused_before_migration() {
    let dir = tempfile::tempdir().unwrap();
    let conn = super::super::sqlite_init::open(index_path(dir.path())).unwrap();
    conn.execute_batch("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        .unwrap();
    set_meta(&conn, "schema_version", "broken").unwrap();

    let error = open(dir.path()).unwrap_err();

    assert!(format!("{error:#}").contains("invalid schema version"));
    assert_eq!(
        get_meta(&conn, "schema_version").unwrap().as_deref(),
        Some("broken")
    );
    assert_eq!(
        conn.query_row(
            "SELECT count(*) FROM sqlite_schema WHERE name = 'sets'",
            [],
            |row| row.get::<_, i64>(0)
        )
        .unwrap(),
        0
    );
}

#[test]
fn unreadable_schema_versions_keep_the_query_error() {
    let dir = tempfile::tempdir().unwrap();
    let conn = super::super::sqlite_init::open(index_path(dir.path())).unwrap();
    conn.execute_batch("CREATE TABLE meta (key TEXT PRIMARY KEY)")
        .unwrap();

    let error = open(dir.path()).unwrap_err();

    assert!(format!("{error:#}").contains("reading meta key schema_version"));
    assert_eq!(
        conn.query_row(
            "SELECT count(*) FROM sqlite_schema WHERE name = 'sets'",
            [],
            |row| row.get::<_, i64>(0)
        )
        .unwrap(),
        0
    );
}

#[test]
fn an_absent_schema_version_bootstraps_the_database() {
    let dir = tempfile::tempdir().unwrap();
    let conn = super::super::sqlite_init::open(index_path(dir.path())).unwrap();
    conn.execute_batch("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        .unwrap();

    open(dir.path()).unwrap();

    assert_eq!(
        get_meta(&conn, "schema_version").unwrap(),
        Some(mlib_spec::schema::SCHEMA_VERSION.to_string())
    );
}
