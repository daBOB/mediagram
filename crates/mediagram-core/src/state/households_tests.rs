use super::*;
use crate::state::{StateDb, profiles};

fn db() -> (tempfile::TempDir, StateDb) {
    let dir = tempfile::tempdir().unwrap();
    let db = StateDb::new(dir.path().to_path_buf());
    (dir, db)
}

fn sql(db: &StateDb, statements: &str) {
    db.with(|conn| conn.execute_batch(statements)).unwrap();
}

fn placed(db: &StateDb) -> Vec<(String, String, bool, bool)> {
    db.with(profiles::list).unwrap().into_iter().map(|p| (p.id, p.household, p.owner, p.admin)).collect()
}

#[test]
fn a_kid_is_listed_in_its_parents_household() {
    let (_dir, db) = db();
    sql(
        &db,
        "INSERT INTO households(id, name) VALUES ('h2', 'Oma');
         INSERT INTO profiles(id, name, created_at, admin_claimed_at) VALUES ('o', 'owner', 1, 5);
         INSERT INTO profiles(id, name, created_at, household_id) VALUES ('g', 'grown', 2, 'h2');
         INSERT INTO profiles(id, name, created_at, kids, kids_age, parent_id) VALUES ('k', 'kid', 3, 1, 12, 'g');",
    );

    let list = placed(&db);

    assert_eq!(list[2], ("k".into(), "h2".into(), false, false), "the kid's own field says home");
    assert_eq!(list[0], ("o".into(), "home".into(), true, true));
}

#[test]
fn a_profile_whose_household_is_gone_is_listed_in_home() {
    let (_dir, db) = db();
    sql(
        &db,
        "INSERT INTO households(id, name, removed) VALUES ('h2', 'Oma', 1);
         INSERT INTO profiles(id, name, created_at, household_id) VALUES ('a', 'a', 1, 'h2');
         INSERT INTO profiles(id, name, created_at, household_id) VALUES ('b', 'b', 2, 'never-existed');",
    );

    let homes: Vec<String> = placed(&db).into_iter().map(|p| p.1).collect();
    let stored: String = db
        .with(|conn| conn.query_row("SELECT household_id FROM profiles WHERE id = 'a'", [], |r| r.get(0)))
        .unwrap();

    assert_eq!(homes, ["home", "home"]);
    assert_eq!(stored, "h2", "the record keeps where it was");
}

#[test]
fn households_are_listed_live_by_name_with_who_runs_them() {
    let (_dir, db) = db();
    sql(
        &db,
        "INSERT INTO households(id, name, admin_id) VALUES ('h1', 'Zoe', 'g');
         INSERT INTO households(id, name) VALUES ('h2', 'ÄRGER'), ('h3', 'Gone'), ('h4', 'zoe');
         UPDATE households SET removed = 1 WHERE id = 'h3';
         UPDATE households SET removed = 1 WHERE id = 'home';
         INSERT INTO profiles(id, name, created_at, admin_claimed_at) VALUES ('o', 'owner', 1, 5);
         INSERT INTO profiles(id, name, created_at, household_id) VALUES ('g', 'grown', 2, 'h1');",
    );

    let live = db
        .with(|conn| {
            let profiles = profiles::list(conn)?;
            live(conn, &profiles)
        })
        .unwrap();

    let summary: Vec<(&str, Option<&str>)> = live.iter().map(|h| (h.id.as_str(), h.admin_id.as_deref())).collect();
    // home, zoe (h1, h4 by id), then the accent, which sorts after ASCII.
    assert_eq!(summary, [("home", Some("o")), ("h1", Some("g")), ("h4", None), ("h2", None)]);
}
