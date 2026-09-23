use super::*;
use crate::state::StateDb;
use crate::state::{lists, profiles, rows};

fn db() -> (tempfile::TempDir, StateDb) {
    let dir = tempfile::tempdir().unwrap();
    let db = StateDb::new(dir.path().to_path_buf());
    (dir, db)
}

fn profile(db: &StateDb) -> String {
    db.with(|conn| profiles::create(conn, "André")).unwrap().unwrap().id
}

#[test]
fn a_local_removal_exports_as_a_tombstone_row() {
    let (_dir, db) = db();
    let id = profile(&db);
    db.with(|conn| rows::set_watchlisted(conn, &id, "01A", true)).unwrap();
    db.with(|conn| rows::set_watchlisted(conn, &id, "01A", false)).unwrap();

    let exported = db.with(|conn| export_watchlist(conn, &id)).unwrap();
    assert_eq!(exported, vec![ListRow { set_id: "01A".into(), updated_at: exported[0].updated_at, removed: true }]);
}

/// A newer live row from another device beats an older local tombstone —
/// the re-add case, seen from the importing side.
#[test]
fn importing_a_newer_live_row_revives_a_local_tombstone() {
    let (_dir, db) = db();
    let id = profile(&db);
    db.with(|conn| rows::set_watchlisted(conn, &id, "01A", true)).unwrap();
    db.with(|conn| rows::set_watchlisted(conn, &id, "01A", false)).unwrap();

    let revive = ListRow { set_id: "01A".into(), updated_at: 9_999_999_999_999.0, removed: false };
    db.with(|conn| import_watchlist(conn, &id, &[revive])).unwrap();

    assert_eq!(db.with(|conn| rows::watchlist_for(conn, &id)).unwrap(), vec!["01A".to_string()]);
}

/// An older tombstone must not undo a local live row that is newer.
#[test]
fn importing_an_older_tombstone_does_not_remove_a_newer_local_row() {
    let (_dir, db) = db();
    let id = profile(&db);
    db.with(|conn| rows::set_watchlisted(conn, &id, "01A", true)).unwrap();

    let stale = ListRow { set_id: "01A".into(), updated_at: 1.0, removed: true };
    db.with(|conn| import_watchlist(conn, &id, &[stale])).unwrap();

    assert_eq!(db.with(|conn| rows::watchlist_for(conn, &id)).unwrap(), vec!["01A".to_string()]);
}

#[test]
fn kids_import_is_not_scoped_to_a_profile() {
    let (_dir, db) = db();
    let mark = ListRow { set_id: "01K".into(), updated_at: 1000.0, removed: false };
    db.with(|conn| import_kids(conn, &[mark])).unwrap();
    assert_eq!(db.with(rows::kids).unwrap(), vec!["01K".to_string()]);
}

/// The whole-list rule: a newer import replaces name and membership
/// together, and does not merge item by item with what was there.
#[test]
fn importing_a_newer_collection_replaces_name_and_items_together() {
    let (_dir, db) = db();
    let id = profile(&db);
    let list = db.with(|conn| lists::create(conn, &id, "Sunday")).unwrap().unwrap();
    db.with(|conn| lists::set_in_collection(conn, &id, &list.id, "01A", true)).unwrap();

    let newer = CollectionRow {
        id: list.id.clone(),
        name: "Sunday night".into(),
        items: vec!["01B".into()],
        updated_at: 9_999_999_999_999.0,
        removed: false,
    };
    db.with(|conn| import_collections(conn, &id, &[newer])).unwrap();

    let held = &db.with(|conn| lists::collections_for(conn, &id)).unwrap()[0];
    assert_eq!(held.name, "Sunday night");
    assert_eq!(held.items, vec!["01B".to_string()]);
}

/// A collection this device has never made arrives whole from a merge and
/// keeps the id it was given — not a freshly minted one.
#[test]
fn importing_an_unknown_collection_creates_it_under_the_same_id() {
    let (_dir, db) = db();
    let id = profile(&db);
    let theirs = CollectionRow {
        id: "c-from-elsewhere".into(),
        name: "Theirs".into(),
        items: vec!["01A".into()],
        updated_at: 1000.0,
        removed: false,
    };
    db.with(|conn| import_collections(conn, &id, &[theirs])).unwrap();

    let held = db.with(|conn| lists::collections_for(conn, &id)).unwrap();
    assert_eq!(held[0].id, "c-from-elsewhere");
}

#[test]
fn importing_a_collection_tombstone_removes_it_from_snapshot() {
    let (_dir, db) = db();
    let id = profile(&db);
    let list = db.with(|conn| lists::create(conn, &id, "Weg")).unwrap().unwrap();

    let gone =
        CollectionRow { id: list.id, name: "Weg".into(), items: vec![], updated_at: 9_999_999_999_999.0, removed: true };
    db.with(|conn| import_collections(conn, &id, &[gone])).unwrap();

    assert_eq!(db.with(|conn| lists::collections_for(conn, &id)).unwrap(), Vec::new());
}
