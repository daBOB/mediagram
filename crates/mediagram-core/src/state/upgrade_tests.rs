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
        let preferences = schema::migrations_up_to(schema::VERSION)
            .into_iter()
            .find(|statement| statement.contains("TABLE IF NOT EXISTS preferences"))
            .unwrap();
        conn.execute(preferences, []).unwrap();
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

/// A store from before viewing stats gains both stats tables, empty, keeps
/// every row it held, and drops a profile's stats with the profile.
#[test]
fn a_store_from_before_stats_gains_empty_stats_tables() {
    let dir = tempfile::tempdir().unwrap();
    {
        let conn = Connection::open(dir.path().join(STATE_FILE)).unwrap();
        for statement in schema::migrations_up_to(6) {
            conn.execute(statement, []).unwrap();
        }
        conn.pragma_update(None, "user_version", 6i64).unwrap();
        conn.execute("INSERT INTO profiles(id, name, created_at) VALUES ('p1', 'André', 0)", []).unwrap();
        conn.execute(
            "INSERT INTO progress(profile_id, set_id, at_seconds, duration, updated_at)
               VALUES ('p1', 'set1', 12.5, 90.0, 0)",
            [],
        )
        .unwrap();
    }

    let db = StateDb::new(dir.path().to_path_buf());

    assert_eq!(db.with(|conn| rows::progress_for(conn, "p1")).unwrap().len(), 1);
    let count = |table: &str| -> i64 {
        db.with(|conn| conn.query_row(&format!("SELECT COUNT(*) FROM {table}"), [], |row| row.get(0)))
            .unwrap()
    };
    assert_eq!((count("stats_titles"), count("stats_days")), (0, 0));
    db.with(|conn| {
        conn.execute(
            "INSERT INTO stats_days(profile_id, day, device, seconds, updated_at)
               VALUES ('p1', '2026-10-03', 'phone', 60.0, 1)",
            [],
        )?;
        profiles::delete(conn, "p1")
    })
    .unwrap();
    assert_eq!(count("stats_days"), 0, "a profile's stats go with it");
}

/// A store from before profile roles gains their columns: every existing
/// kid is FSK 12 dated 0 (the one limit there was, older than any choice),
/// every existing mark stays "from 12", and nobody is an admin, has a
/// parent or holds a PIN.
#[test]
fn a_store_from_before_roles_keeps_every_kid_and_mark_at_twelve() {
    let dir = tempfile::tempdir().unwrap();
    {
        let conn = Connection::open(dir.path().join(STATE_FILE)).unwrap();
        for statement in schema::migrations_up_to(7) {
            conn.execute(statement, []).unwrap();
        }
        conn.pragma_update(None, "user_version", 7i64).unwrap();
        conn.execute_batch(
            "INSERT INTO profiles(id, name, created_at, kids) VALUES ('p1', 'André', 0, 0), ('p2', 'Mia', 1, 1);
             INSERT INTO kids(set_id, marked_at) VALUES ('family', 5);",
        )
        .unwrap();
    }

    let conn = open(dir.path()).unwrap();

    let row = |id: &str| -> (Option<i64>, i64, bool) {
        conn.query_row(
            "SELECT kids_age, kids_age_updated_at,
                    parent_id IS NULL AND admin_claimed_at IS NULL AND pin_hash IS NULL
                      AND pin_salt IS NULL AND pin_updated_at = 0
               FROM profiles WHERE id = ?1",
            [id],
            |r| Ok((r.get(0)?, r.get(1)?, r.get(2)?)),
        )
        .unwrap()
    };
    assert_eq!(row("p1"), (None, 0, true));
    assert_eq!(row("p2"), (Some(12), 0, true));
    let age: Option<i64> = conn
        .query_row("SELECT age FROM kids WHERE set_id = 'family'", [], |r| r.get(0))
        .unwrap();
    assert_eq!(age, None);
    let version: i64 = conn.pragma_query_value(None, "user_version", |r| r.get(0)).unwrap();
    assert_eq!(version, 8);
}
