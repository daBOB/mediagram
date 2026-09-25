//! Files written by older builds, opened by this one.

use super::*;

/// A real device may already hold a v2 file — profiles, progress, and
/// the rest, but no `preferences` table yet. Gaining it must not disturb
/// what is already there.
#[test]
fn an_older_store_upgrades_to_preferences_with_its_rows_intact() {
    let dir = tempfile::tempdir().unwrap();
    {
        let conn = Connection::open(dir.path().join(STATE_FILE)).unwrap();
        for statement in schema::migrations_up_to(2) {
            conn.execute(statement, []).unwrap();
        }
        conn.pragma_update(None, "user_version", 2i64).unwrap();
        conn.execute("INSERT INTO profiles(id, name, created_at) VALUES ('p1', 'André', 0)", []).unwrap();
        conn.execute(
            "INSERT INTO progress(profile_id, set_id, at_seconds, duration, updated_at)
               VALUES ('p1', 'set1', 12.5, 90.0, 0)",
            [],
        )
        .unwrap();
    }

    let db = StateDb::new(dir.path().to_path_buf());

    let names: Vec<String> = db.with(profiles::list).unwrap().into_iter().map(|p| p.name).collect();
    assert_eq!(names, vec!["André".to_string()]);
    assert_eq!(db.with(|conn| rows::progress_for(conn, "p1")).unwrap().len(), 1);
    assert!(db.with(|conn| preferences::set(conn, "p1", "show:x", "audio", Some("en"))).unwrap());
}

/// A file an earlier build left at version 3 with `preferences` and no
/// `kids` column opens with both, and keeps its rows.
#[test]
fn a_version_three_file_without_kids_gains_the_column_on_open() {
    let dir = tempfile::tempdir().unwrap();
    {
        let conn = Connection::open(dir.path().join(STATE_FILE)).unwrap();
        for statement in schema::migrations_up_to(2) {
            conn.execute(statement, []).unwrap();
        }
        conn.execute(schema::migrations_up_to(schema::VERSION).last().unwrap(), []).unwrap();
        conn.pragma_update(None, "user_version", 3i64).unwrap();
        conn.execute("INSERT INTO profiles(id, name, created_at) VALUES ('p1', 'André', 0)", []).unwrap();
    }

    let conn = open(dir.path()).unwrap();

    let has_kids: bool = conn
        .prepare("SELECT 1 FROM pragma_table_info('profiles') WHERE name = 'kids'")
        .unwrap()
        .exists([])
        .unwrap();
    assert!(has_kids);
    let names: Vec<String> = profiles::list(&conn).unwrap().into_iter().map(|p| p.name).collect();
    assert_eq!(names, vec!["André".to_string()]);
}
