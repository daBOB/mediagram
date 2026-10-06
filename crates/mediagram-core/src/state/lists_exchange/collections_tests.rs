use super::*;
use crate::state::{StateDb, lists, profiles};

fn db() -> (tempfile::TempDir, StateDb) {
    let dir = tempfile::tempdir().unwrap();
    let db = StateDb::new(dir.path().to_path_buf());
    (dir, db)
}

fn profile(db: &StateDb, name: &str) -> String {
    db.with(|conn| profiles::create(conn, name, false))
        .unwrap()
        .unwrap()
        .id
}

fn row(id: &str, name: &str, items: &[&str], updated_at: f64, removed: bool) -> CollectionRow {
    CollectionRow {
        id: id.into(),
        name: name.into(),
        items: items.iter().map(|item| item.to_string()).collect(),
        updated_at,
        removed,
    }
}

/// The whole-list rule: a newer import replaces name and membership
/// together, and does not merge item by item with what was there.
#[test]
fn importing_a_newer_collection_replaces_name_and_items_together() {
    let (_dir, db) = db();
    let id = profile(&db, "André");
    let list = db
        .with(|conn| lists::create(conn, &id, "Sunday"))
        .unwrap()
        .unwrap();
    db.with(|conn| lists::set_in_collection(conn, &id, &list.id, "01A", true))
        .unwrap();

    let newer = CollectionRow {
        id: list.id.clone(),
        name: "Sunday night".into(),
        items: vec!["01B".into()],
        updated_at: 9_999_999_999_999.0,
        removed: false,
    };
    db.with(|conn| import_collections(conn, &id, &[newer]))
        .unwrap();

    let held = &db.with(|conn| lists::collections_for(conn, &id)).unwrap()[0];
    assert_eq!(held.name, "Sunday night");
    assert_eq!(held.items, vec!["01B".to_string()]);
}

/// A collection this device has never made arrives whole from a merge and
/// keeps the id it was given — not a freshly minted one.
#[test]
fn importing_an_unknown_collection_creates_it_under_the_same_id() {
    let (_dir, db) = db();
    let id = profile(&db, "André");
    let theirs = CollectionRow {
        id: "c-from-elsewhere".into(),
        name: "Theirs".into(),
        items: vec!["01A".into()],
        updated_at: 1000.0,
        removed: false,
    };
    db.with(|conn| import_collections(conn, &id, &[theirs]))
        .unwrap();

    let held = db.with(|conn| lists::collections_for(conn, &id)).unwrap();
    assert_eq!(held[0].id, "c-from-elsewhere");
}

#[test]
fn importing_a_collection_tombstone_removes_it_from_snapshot() {
    let (_dir, db) = db();
    let id = profile(&db, "André");
    let list = db
        .with(|conn| lists::create(conn, &id, "Weg"))
        .unwrap()
        .unwrap();

    let gone = CollectionRow {
        id: list.id,
        name: "Weg".into(),
        items: vec![],
        updated_at: 9_999_999_999_999.0,
        removed: true,
    };
    db.with(|conn| import_collections(conn, &id, &[gone]))
        .unwrap();

    assert_eq!(
        db.with(|conn| lists::collections_for(conn, &id)).unwrap(),
        Vec::new()
    );
}

/// A list goes out whole, its titles in the order it was built, a deleted
/// one as a tombstone stamped when it was deleted — and only this
/// profile's lists.
#[test]
fn export_carries_each_list_whole_in_built_order_with_its_tombstones() {
    let (_dir, db) = db();
    let id = profile(&db, "André");
    let other = profile(&db, "Bea");
    let list = db
        .with(|conn| lists::create(conn, &id, "Sunday"))
        .unwrap()
        .unwrap();
    for set_id in ["01B", "01A"] {
        db.with(|conn| lists::set_in_collection(conn, &id, &list.id, set_id, true))
            .unwrap();
    }
    let gone = db
        .with(|conn| lists::create(conn, &id, "Gone"))
        .unwrap()
        .unwrap();
    db.with(|conn| lists::delete(conn, &id, &gone.id)).unwrap();
    db.with(|conn| lists::create(conn, &other, "Hers")).unwrap();

    let mut out = db.with(|conn| export_collections(conn, &id)).unwrap();
    out.sort_by(|a, b| a.name.cmp(&b.name));
    assert_eq!(out.len(), 2);
    assert_eq!((out[0].name.as_str(), out[0].removed), ("Gone", true));
    assert_eq!((out[1].name.as_str(), out[1].removed), ("Sunday", false));
    assert_eq!(out[1].items, ["01B", "01A"]);
    let removed_at: i64 = db
        .with(|conn| {
            conn.query_row(
                "SELECT removed_at FROM collections WHERE id = ?1",
                [&gone.id],
                |r| r.get(0),
            )
        })
        .unwrap();
    assert_eq!(out[0].updated_at, removed_at as f64);
}

/// The standing row wins a tie: an import is corrective, and a copy of the
/// same moment is not news.
#[test]
fn an_older_or_equal_import_changes_nothing() {
    let (_dir, db) = db();
    let id = profile(&db, "André");
    let standing = row("c1", "Mine", &["01A"], 2000.0, false);
    assert_eq!(
        db.with(|conn| import_collections(conn, &id, std::slice::from_ref(&standing)))
            .unwrap(),
        1
    );

    let stale = [
        row("c1", "Older", &["01B"], 1999.0, false),
        row("c1", "Same moment", &[], 2000.0, true),
    ];
    assert_eq!(
        db.with(|conn| import_collections(conn, &id, &stale))
            .unwrap(),
        0
    );
    assert_eq!(
        db.with(|conn| export_collections(conn, &id)).unwrap(),
        [standing]
    );
}

/// Keeping the incoming id means an older live copy still circulating
/// cannot bring a deleted list back.
#[test]
fn an_older_live_copy_cannot_recreate_a_deleted_list() {
    let (_dir, db) = db();
    let id = profile(&db, "André");
    let gone = row("c1", "Weg", &["01A"], 3000.0, true);
    db.with(|conn| import_collections(conn, &id, &[gone]))
        .unwrap();

    let echo = row("c1", "Weg", &["01A"], 2000.0, false);
    assert_eq!(
        db.with(|conn| import_collections(conn, &id, &[echo]))
            .unwrap(),
        0
    );
    assert!(
        db.with(|conn| lists::collections_for(conn, &id))
            .unwrap()
            .is_empty()
    );
    assert!(db.with(|conn| export_collections(conn, &id)).unwrap()[0].removed);
}

/// A stranger's list naming a title twice keeps it once, where it first
/// appeared.
#[test]
fn a_title_named_twice_is_held_once_at_its_first_place() {
    let (_dir, db) = db();
    let id = profile(&db, "André");
    let theirs = row("c1", "Twice", &["01A", "01B", "01A", "01C"], 1000.0, false);
    db.with(|conn| import_collections(conn, &id, &[theirs]))
        .unwrap();
    let held = db.with(|conn| export_collections(conn, &id)).unwrap();
    assert_eq!(held[0].items, ["01A", "01B", "01C"]);
}

#[test]
fn an_exported_list_imports_unchanged_into_another_store() {
    let (_dir, here) = db();
    let id = profile(&here, "André");
    let list = here
        .with(|conn| lists::create(conn, &id, "Sunday"))
        .unwrap()
        .unwrap();
    here.with(|conn| lists::set_in_collection(conn, &id, &list.id, "01A", true))
        .unwrap();
    let out = here.with(|conn| export_collections(conn, &id)).unwrap();

    let (_elsewhere_dir, elsewhere) = db();
    let theirs = profile(&elsewhere, "André");
    assert_eq!(
        elsewhere
            .with(|conn| import_collections(conn, &theirs, &out))
            .unwrap(),
        1
    );
    assert_eq!(
        elsewhere
            .with(|conn| export_collections(conn, &theirs))
            .unwrap(),
        out
    );
}
