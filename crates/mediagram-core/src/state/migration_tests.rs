use super::*;

fn populated_v1(path: &Path) -> Connection {
    let conn = Connection::open(path).unwrap();
    for statement in schema::migrations_up_to(1) {
        conn.execute(statement, []).unwrap();
    }
    conn.execute_batch(
        "INSERT INTO profiles VALUES ('viewer', 'André', 11);
         INSERT INTO progress VALUES ('viewer', 'playing', 42.5, 900.0, 22);
         INSERT INTO watched VALUES ('viewer', 'finished', 33);
         INSERT INTO watchlist VALUES ('viewer', 'later', 44);
         INSERT INTO kids VALUES ('family', 55);
         INSERT INTO collections VALUES ('list-a', 'viewer', 'Weekend', 66);
         INSERT INTO collections VALUES ('list-b', 'viewer', 'Favorites', 77);
         INSERT INTO collection_items VALUES ('list-a', 'second', 2);
         INSERT INTO collection_items VALUES ('list-a', 'first', 1);
         INSERT INTO state_meta VALUES ('chosen_profile', 'viewer');",
    )
    .unwrap();
    conn.pragma_update(None, "user_version", 1).unwrap();
    conn
}

fn assert_kept_rows(conn: &Connection) {
    let viewers = profiles::list(conn).unwrap();
    assert_eq!(viewers.len(), 1);
    assert_eq!((&*viewers[0].id, &*viewers[0].name), ("viewer", "André"));
    assert_eq!(profiles::chosen(conn).unwrap(), Some("viewer".into()));
    for (query, expected) in [
        ("SELECT created_at FROM profiles WHERE id = 'viewer'", 11),
        ("SELECT added_at FROM watchlist WHERE set_id = 'later'", 44),
        ("SELECT marked_at FROM kids WHERE set_id = 'family'", 55),
    ] {
        let timestamp: i64 = conn.query_row(query, [], |row| row.get(0)).unwrap();
        assert_eq!(timestamp, expected);
    }
    assert_eq!(
        rows::progress_for(conn, "viewer").unwrap(),
        [rows::ProgressRow {
            set_id: "playing".into(),
            at: 42.5,
            duration: Some(900.0),
            updated_at: 22,
        }]
    );
    assert_eq!(
        rows::watched_for(conn, "viewer").unwrap(),
        [rows::WatchedRow {
            set_id: "finished".into(),
            finished_at: 33,
        }]
    );
}

fn assert_migrated(conn: &Connection) {
    assert_kept_rows(conn);
    assert_eq!(rows::watchlist_for(conn, "viewer").unwrap(), ["later"]);
    assert_eq!(rows::kids(conn).unwrap(), ["family"]);
    let collections = lists::collections_for(conn, "viewer").unwrap();
    assert_eq!(
        collections,
        [
            lists::ListRow {
                id: "list-a".into(),
                name: "Weekend".into(),
                items: vec!["first".into(), "second".into()],
            },
            lists::ListRow {
                id: "list-b".into(),
                name: "Favorites".into(),
                items: vec![],
            }
        ]
    );
    for table in ["watchlist", "kids", "collections"] {
        let live: i64 = conn
            .query_row(
                &format!("SELECT COUNT(*) FROM {table} WHERE removed_at IS NOT NULL"),
                [],
                |row| row.get(0),
            )
            .unwrap();
        assert_eq!(live, 0, "existing {table} rows must remain live");
    }
    let mut query = conn
        .prepare("SELECT created_at, updated_at FROM collections ORDER BY id")
        .unwrap();
    let times: Vec<(i64, i64)> = query
        .query_map([], |row| Ok((row.get(0)?, row.get(1)?)))
        .unwrap()
        .collect::<Result<_, _>>()
        .unwrap();
    assert_eq!(times, [(66, 66), (77, 77)]);
    let version: i64 = conn
        .pragma_query_value(None, "user_version", |row| row.get(0))
        .unwrap();
    assert_eq!(version, schema::VERSION);
}

#[test]
fn populated_v1_rows_and_collection_order_survive_migration_and_reopening() {
    let dir = tempfile::tempdir().unwrap();
    let conn = populated_v1(&dir.path().join(STATE_FILE));
    migrate(&conn).unwrap();
    assert_migrated(&conn);
    drop(conn);
    assert_migrated(&open(dir.path()).unwrap());
}

fn columns(conn: &Connection, table: &str) -> Vec<String> {
    conn.prepare(&format!("PRAGMA table_info({table})"))
        .unwrap()
        .query_map([], |row| row.get(1))
        .unwrap()
        .collect::<Result<_, _>>()
        .unwrap()
}

#[test]
fn a_later_migration_conflict_rolls_back_earlier_schema_changes_and_version() {
    let dir = tempfile::tempdir().unwrap();
    let path = dir.path().join(STATE_FILE);
    let conn = populated_v1(&path);
    conn.execute("ALTER TABLE kids ADD COLUMN removed_at INTEGER", [])
        .unwrap();
    let before: Vec<_> = ["watchlist", "kids", "collections"]
        .map(|table| columns(&conn, table))
        .into();

    assert!(migrate(&conn).is_err());
    assert!(
        conn.is_autocommit(),
        "the failed migration must close its transaction"
    );
    drop(conn);

    let conn = Connection::open(path).unwrap();
    let version: i64 = conn
        .pragma_query_value(None, "user_version", |row| row.get(0))
        .unwrap();
    assert_eq!(version, 1);
    let after: Vec<_> = ["watchlist", "kids", "collections"]
        .map(|table| columns(&conn, table))
        .into();
    assert_eq!(
        after, before,
        "the earlier watchlist ALTER cannot survive the failure"
    );
    assert_kept_rows(&conn);
    conn.execute("ALTER TABLE kids DROP COLUMN removed_at", [])
        .unwrap();
    migrate(&conn).unwrap();
    assert_migrated(&conn);
}
