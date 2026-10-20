use crate::state::exchange::{export_record, import_merged};
use crate::state::households::{self, Household};
use crate::state::merge::{MergedProfile, MergedState, merge_states};
use crate::state::record::{
    HouseholdEntry, HouseholdAdmin, HouseholdName, ProfileHousehold, ProfileRoles, SyncRecord, parse_record,
};
use crate::state::{StateDb, profiles};

fn db() -> (tempfile::TempDir, StateDb) {
    let dir = tempfile::tempdir().unwrap();
    let db = StateDb::new(dir.path().to_path_buf());
    (dir, db)
}

fn make(db: &StateDb, name: &str, kids: bool) -> String {
    db.with(|c| profiles::create(c, name, kids)).unwrap().unwrap().id
}

fn sql(db: &StateDb, statements: &[&str]) {
    for statement in statements {
        db.with(|c| c.execute_batch(statement)).unwrap();
    }
}

/// The stored households but Home, removed ones too:
/// id, name, name stamp, admin, admin stamp, removed.
type Stored = (String, String, i64, Option<String>, i64, i64);

fn rows(db: &StateDb) -> Vec<Stored> {
    db.with(|c| {
        let mut stmt = c.prepare(
            "SELECT id, name, name_updated_at, admin_id, admin_updated_at, removed
               FROM households WHERE id <> 'home' ORDER BY id",
        )?;
        let all = stmt.query_map([], |r| {
            Ok((r.get(0)?, r.get(1)?, r.get(2)?, r.get(3)?, r.get(4)?, r.get(5)?))
        })?;
        all.collect()
    })
    .unwrap()
}

fn home_name(db: &StateDb) -> String {
    db.with(|c| c.query_row("SELECT name FROM households WHERE id = 'home'", [], |r| r.get(0))).unwrap()
}

fn live(db: &StateDb) -> Vec<Household> {
    db.with(|c| households::live(c, &profiles::list(c)?)).unwrap()
}

fn record(db: &StateDb, device: &str) -> SyncRecord {
    db.with(|c| export_record(c, device)).unwrap()
}

fn name(text: &str, updated_at: f64) -> Option<HouseholdName> {
    Some(HouseholdName { text: text.into(), updated_at })
}

fn entry(id: &str) -> HouseholdEntry {
    HouseholdEntry { id: id.into(), name: None, admin: None, removed: false }
}

fn viewer(name: &str, household: Option<(&str, f64)>) -> MergedProfile {
    MergedProfile {
        name: name.to_lowercase(),
        display_name: name.into(),
        roles: ProfileRoles {
            household: household.map(|(id, updated_at)| ProfileHousehold { id: id.into(), updated_at }),
            ..Default::default()
        },
        ..Default::default()
    }
}

fn import(db: &StateDb, households: Vec<HouseholdEntry>, profiles: Vec<MergedProfile>) -> u64 {
    let merged = MergedState { profiles, households, ..Default::default() };
    db.with(|c| import_merged(c, &merged)).unwrap()
}

#[test]
fn a_household_nobody_changed_is_not_said() {
    let (_dir, db) = db();
    make(&db, "André", false);
    make(&db, "Mia", true);
    let said = record(&db, "laptop");
    assert!(said.households.is_empty());
    assert!(said.profiles.iter().all(|one| one.roles.household.is_none()));
    assert!(!serde_json::to_string(&said).unwrap().contains("household"));
}

#[test]
fn a_renamed_home_is_said_with_its_name_alone() {
    let (_dir, db) = db();
    make(&db, "André", false);
    sql(&db, &["UPDATE households SET name = 'Borck', name_updated_at = 1000 WHERE id = 'home'"]);
    let home = HouseholdEntry { name: name("Borck", 1000.0), ..entry("home") };
    assert_eq!(record(&db, "laptop").households, vec![home]);
}

#[test]
fn an_admin_whose_profile_is_not_here_is_not_said() {
    let (_dir, db) = db();
    let me = make(&db, "André", false);
    sql(&db, &[
        "INSERT INTO households(id, name, name_updated_at, admin_id, admin_updated_at) VALUES ('b', 'Oma', 1000, 'gone', 1100)",
        // Nothing left to say once the admin is not: the row is not written at all.
        "INSERT INTO households(id, name, admin_id, admin_updated_at) VALUES ('c', 'Flat', 'gone', 1100)",
        &format!("INSERT INTO households(id, name, admin_id, admin_updated_at) VALUES ('d', 'Mine', '{me}', 1200)"),
        "INSERT INTO households(id, name, removed) VALUES ('e', 'Gone', 1)",
    ]);
    let admin = Some(HouseholdAdmin { name: "André".into(), updated_at: 1200.0 });
    assert_eq!(
        record(&db, "laptop").households,
        vec![
            HouseholdEntry { name: name("Oma", 1000.0), ..entry("b") },
            HouseholdEntry { admin, ..entry("d") },
            HouseholdEntry { removed: true, ..entry("e") },
        ]
    );
}

#[test]
fn a_profiles_household_is_said_once_it_is_dated() {
    let (_dir, db) = db();
    let maja = make(&db, "Maja", false);
    make(&db, "Tim", false);
    // As stored, a removed household too: only the view reads it as Home.
    sql(&db, &[
        "INSERT INTO households(id, name, removed) VALUES ('b', 'Oma', 1)",
        &format!("UPDATE profiles SET household_id = 'b', household_updated_at = 1500 WHERE id = '{maja}'"),
    ]);
    let said = record(&db, "laptop");
    let of = |who: &str| said.profiles.iter().find(|one| one.name == who).unwrap().roles.household.clone();
    assert_eq!(of("Maja"), Some(ProfileHousehold { id: "b".into(), updated_at: 1500.0 }));
    assert_eq!(of("Tim"), None);
}

#[test]
fn a_household_heard_from_another_device_is_made_here_its_admin_found_by_name() {
    let (_dir, db) = db();
    let oma = make(&db, "Oma", false);
    let heard = vec![
        HouseholdEntry {
            name: name("Oma", 1000.0),
            admin: Some(HouseholdAdmin { name: "oma".into(), updated_at: 1100.0 }),
            ..entry("b")
        },
        // No name to make it with: not made.
        HouseholdEntry { admin: Some(HouseholdAdmin { name: "oma".into(), updated_at: 1100.0 }), ..entry("c") },
    ];
    // Made, its admin, Oma moved in.
    assert_eq!(import(&db, heard, vec![viewer("Oma", Some(("b", 1200.0)))]), 3);
    assert_eq!(rows(&db), vec![("b".into(), "Oma".into(), 1000, Some(oma.clone()), 1100, 0)]);
    assert_eq!(live(&db).iter().find(|one| one.id == "b").unwrap().admin_id, Some(oma));
}

#[test]
fn a_removal_is_taken_and_never_undone() {
    let (_dir, db) = db();
    sql(&db, &["INSERT INTO households(id, name, name_updated_at) VALUES ('b', 'Oma', 1000)"]);
    let gone = HouseholdEntry { removed: true, ..entry("b") };
    assert_eq!(import(&db, vec![gone], vec![]), 1);
    let alive = HouseholdEntry { name: name("Oma", 1000.0), ..entry("b") };
    assert_eq!(import(&db, vec![alive], vec![]), 0);
    assert_eq!(rows(&db)[0].5, 1);
    // Home is never removed, whatever a hand-built merge says.
    assert_eq!(import(&db, vec![HouseholdEntry { removed: true, ..entry("home") }], vec![]), 0);
    assert!(live(&db).iter().any(|one| one.id == "home"));
}

#[test]
fn a_newer_local_rename_survives_an_older_merge() {
    let (_dir, db) = db();
    sql(&db, &["UPDATE households SET name = 'Borck', name_updated_at = 2000 WHERE id = 'home'"]);
    let said = |text: &str, at: f64| vec![HouseholdEntry { name: name(text, at), ..entry("home") }];
    assert_eq!(import(&db, said("Old", 1000.0), vec![]), 0);
    assert_eq!(home_name(&db), "Borck");
    // As new and different is the merge's winner by device id.
    assert_eq!(import(&db, said("Tv", 2000.0), vec![]), 1);
    assert_eq!(home_name(&db), "Tv");
}

#[test]
fn a_profiles_newer_household_is_taken() {
    let (_dir, db) = db();
    let mia = make(&db, "Mia", false);
    sql(&db, &[&format!("UPDATE profiles SET household_id = 'c', household_updated_at = 1000 WHERE id = '{mia}'")]);
    let moved = |id: &str, at: f64| import(&db, vec![], vec![viewer("Mia", Some((id, at)))]);
    assert_eq!(moved("home", 900.0), 0);
    assert_eq!(moved("b", 2000.0), 1);
    assert_eq!(moved("b", 2000.0), 0);
    let said = record(&db, "x").profiles[0].roles.household.clone();
    assert_eq!(said, Some(ProfileHousehold { id: "b".into(), updated_at: 2000.0 }));
}

#[test]
fn a_document_from_an_older_build_changes_no_household() {
    let (_dir, db) = db();
    let maja = make(&db, "Maja", false);
    sql(&db, &[
        &format!("INSERT INTO households(id, name, name_updated_at, admin_id, admin_updated_at) VALUES ('b', 'Oma', 1000, '{maja}', 1000)"),
        &format!("UPDATE profiles SET household_id = 'b', household_updated_at = 1000 WHERE id = '{maja}'"),
    ]);
    let before = rows(&db);
    let old = parse_record(r#"{"format":1,"device":"old-tv","writtenAt":0,"profiles":[{"name":"Maja"}]}"#).unwrap();
    assert!(old.households.is_empty() && old.profiles[0].roles.household.is_none());
    let merged = merge_states(&[record(&db, "laptop"), old]);
    assert_eq!(db.with(|c| import_merged(c, &merged)).unwrap(), 0);
    assert_eq!(rows(&db), before);
    assert_eq!(db.with(profiles::list).unwrap()[0].household, "b");
}

#[test]
fn two_devices_agree_on_households_and_a_second_round_changes_nothing() {
    let ((_l, laptop), (_t, tv)) = (db(), db());
    let oma = make(&laptop, "Oma", false);
    sql(&laptop, &[
        "UPDATE households SET name = 'Borck', name_updated_at = 900 WHERE id = 'home'",
        &format!("INSERT INTO households(id, name, name_updated_at, admin_id, admin_updated_at) VALUES ('b', 'Oma', 1000, '{oma}', 1000)"),
        "INSERT INTO households(id, name, name_updated_at, removed) VALUES ('c', 'Flat', 1000, 1)",
        &format!("UPDATE profiles SET household_id = 'b', household_updated_at = 1000 WHERE id = '{oma}'"),
    ]);
    // Through the wire, as a document travels.
    let wire = |db: &StateDb, device: &str| parse_record(&serde_json::to_string(&record(db, device)).unwrap()).unwrap();
    let round = || {
        let merged = merge_states(&[wire(&tv, "tv"), wire(&laptop, "laptop")]);
        tv.with(|c| import_merged(c, &merged)).unwrap()
    };
    assert!(round() > 0);
    let names: Vec<_> = live(&tv).into_iter().map(|one| (one.id, one.name)).collect();
    assert_eq!(names, vec![("home".to_string(), "Borck".to_string()), ("b".into(), "Oma".into())]);
    let people = tv.with(profiles::list).unwrap();
    assert_eq!((people[0].name.as_str(), people[0].household.as_str(), people[0].admin), ("Oma", "b", true));
    assert_eq!(round(), 0);
    let alone = merge_states(&[wire(&laptop, "laptop")]);
    assert_eq!(laptop.with(|c| import_merged(c, &alone)).unwrap(), 0);
}
