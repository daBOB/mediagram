use super::StateDb;
use super::exchange::{export_record, import_merged};
use super::merge::{MergedProfile, MergedState};
use super::profiles;

fn db() -> (tempfile::TempDir, StateDb) {
    let dir = tempfile::tempdir().unwrap();
    let db = StateDb::new(dir.path().to_path_buf());
    (dir, db)
}

fn merged(name: &str, kids: bool) -> MergedState {
    MergedState {
        profiles: vec![MergedProfile {
            name: name.to_lowercase(),
            display_name: name.to_string(),
            kids,
            progress: vec![],
            watched: vec![],
            watchlist: vec![],
            collections: vec![],
        }],
        kids: vec![],
        editors_choice: vec![],
    }
}

#[test]
fn a_profile_is_kids_only_when_created_as_one() {
    let (_dir, db) = db();
    db.with(|c| profiles::create(c, "André", false)).unwrap();
    db.with(|c| profiles::create(c, "Mia", true)).unwrap();
    let listed = db.with(profiles::list).unwrap();
    let flags: Vec<(String, bool)> = listed.into_iter().map(|p| (p.name, p.kids)).collect();
    assert_eq!(flags, vec![("André".into(), false), ("Mia".into(), true)]);
}

#[test]
fn the_record_carries_kids_only_on_the_kids_profile() {
    let (_dir, db) = db();
    db.with(|c| profiles::create(c, "André", false)).unwrap();
    db.with(|c| profiles::create(c, "Mia", true)).unwrap();
    let record = db.with(|c| export_record(c, "phone")).unwrap();
    let written = serde_json::to_value(&record).unwrap();
    let profiles = written["profiles"].as_array().unwrap();
    let mia = profiles.iter().find(|p| p["name"] == "Mia").unwrap();
    let andre = profiles.iter().find(|p| p["name"] == "André").unwrap();
    assert_eq!(mia["kids"], serde_json::Value::Bool(true));
    assert!(andre.get("kids").is_none());
}

#[test]
fn an_import_upgrades_an_existing_profile_and_never_downgrades() {
    let (_dir, db) = db();
    db.with(|c| profiles::create(c, "Mia", false)).unwrap();
    assert_eq!(
        db.with(|c| import_merged(c, &merged("Mia", true))).unwrap(),
        1
    );
    assert!(db.with(profiles::list).unwrap()[0].kids);

    assert_eq!(
        db.with(|c| import_merged(c, &merged("Mia", false)))
            .unwrap(),
        0
    );
    assert!(db.with(profiles::list).unwrap()[0].kids);
}

#[test]
fn an_import_creates_an_unmet_viewer_with_the_flag() {
    let (_dir, db) = db();
    db.with(|c| import_merged(c, &merged("Ben", true))).unwrap();
    assert!(db.with(profiles::list).unwrap()[0].kids);
}
