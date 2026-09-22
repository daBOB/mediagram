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
