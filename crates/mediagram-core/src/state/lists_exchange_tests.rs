use super::*;
use crate::state::StateDb;
use crate::state::record::CollectionRow;
use crate::state::{lists, profiles, rows};

fn db() -> (tempfile::TempDir, StateDb) {
    let dir = tempfile::tempdir().unwrap();
    let db = StateDb::new(dir.path().to_path_buf());
    (dir, db)
}

fn profile(db: &StateDb) -> String {
    db.with(|conn| profiles::create(conn, "André", false))
        .unwrap()
        .unwrap()
        .id
}

#[test]
fn a_local_removal_exports_as_a_tombstone_row() {
    let (_dir, db) = db();
    let id = profile(&db);
    db.with(|conn| rows::set_watchlisted(conn, &id, "01A", true))
        .unwrap();
    db.with(|conn| rows::set_watchlisted(conn, &id, "01A", false))
        .unwrap();

    let exported = db.with(|conn| export_watchlist(conn, &id)).unwrap();
    assert_eq!(
        exported,
        vec![ListRow {
            set_id: "01A".into(),
            updated_at: exported[0].updated_at,
            removed: true,
            age: None,
        }]
    );
}

/// A newer live row from another device beats an older local tombstone —
/// the re-add case, seen from the importing side.
#[test]
fn importing_a_newer_live_row_revives_a_local_tombstone() {
    let (_dir, db) = db();
    let id = profile(&db);
    db.with(|conn| rows::set_watchlisted(conn, &id, "01A", true))
        .unwrap();
    db.with(|conn| rows::set_watchlisted(conn, &id, "01A", false))
        .unwrap();

    let revive = ListRow {
        set_id: "01A".into(),
        updated_at: 9_999_999_999_999.0,
        removed: false,
        age: None,
    };
    db.with(|conn| import_watchlist(conn, &id, &[revive]))
        .unwrap();

    assert_eq!(
        db.with(|conn| rows::watchlist_for(conn, &id)).unwrap(),
        vec!["01A".to_string()]
    );
}

/// An older tombstone must not undo a local live row that is newer.
#[test]
fn importing_an_older_tombstone_does_not_remove_a_newer_local_row() {
    let (_dir, db) = db();
    let id = profile(&db);
    db.with(|conn| rows::set_watchlisted(conn, &id, "01A", true))
        .unwrap();

    let stale = ListRow {
        set_id: "01A".into(),
        updated_at: 1.0,
        removed: true,
        age: None,
    };
    db.with(|conn| import_watchlist(conn, &id, &[stale]))
        .unwrap();

    assert_eq!(
        db.with(|conn| rows::watchlist_for(conn, &id)).unwrap(),
        vec!["01A".to_string()]
    );
}

#[test]
fn kids_import_is_not_scoped_to_a_profile() {
    let (_dir, db) = db();
    let mark = ListRow {
        set_id: "01K".into(),
        updated_at: 1000.0,
        removed: false,
        age: None,
    };
    db.with(|conn| import_kids(conn, &[mark])).unwrap();
    assert_eq!(db.with(rows::kids).unwrap(), vec!["01K".to_string()]);
}

/// The whole-list rule: a newer import replaces name and membership
/// together, and does not merge item by item with what was there.
#[test]
fn importing_a_newer_collection_replaces_name_and_items_together() {
    let (_dir, db) = db();
    let id = profile(&db);
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
    let id = profile(&db);
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
    let id = profile(&db);
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

fn kids_row(updated_at: f64, removed: bool, age: Option<u8>) -> ListRow {
    ListRow { set_id: "x".into(), updated_at, removed, age }
}

/// `(marked_at, removed_at, age)` as stored.
fn stored_mark(db: &StateDb) -> (i64, Option<i64>, Option<i64>) {
    db.with(|c| {
        c.query_row("SELECT marked_at, removed_at, age FROM kids WHERE set_id = 'x'", [], |r| {
            Ok((r.get(0)?, r.get(1)?, r.get(2)?))
        })
    })
    .unwrap()
}

#[test]
fn a_kids_marks_age_travels_out_and_back_in() {
    let (_dir, db) = db();
    db.with(|c| rows::set_kids(c, "six", Some(6))).unwrap();
    db.with(|c| rows::set_kids(c, "twelve", Some(12))).unwrap();
    let mut out = db.with(export_kids).unwrap();
    out.sort_by(|a, b| a.set_id.cmp(&b.set_id));
    assert_eq!(out.iter().map(|r| r.age).collect::<Vec<_>>(), [Some(6), None]);

    let (_other_dir, other) = self::db();
    assert_eq!(other.with(|c| import_kids(c, &out)).unwrap(), 2);
    assert_eq!(other.with(rows::kids_from_six).unwrap(), ["six"]);
    assert_eq!(other.with(|c| import_kids(c, &out)).unwrap(), 0, "a second round is quiet");
}

#[test]
fn a_newer_row_moves_a_mark_from_twelve_to_six_and_an_older_one_does_not_move_it_back() {
    let (_dir, db) = db();
    db.with(|c| import_kids(c, &[kids_row(10.0, false, None)])).unwrap();
    db.with(|c| import_kids(c, &[kids_row(20.0, false, Some(6))])).unwrap();
    assert_eq!(db.with(rows::kids_from_six).unwrap(), ["x"]);
    assert_eq!(db.with(|c| import_kids(c, &[kids_row(15.0, false, None)])).unwrap(), 0);
    assert_eq!(db.with(rows::kids_from_six).unwrap(), ["x"]);
}

/// At the same moment the merge has already chosen between the two, so a
/// row that differs here — another age, or removed where this is live — is
/// the merge's choice and is taken.
#[test]
fn a_tie_that_differs_takes_the_merges_row() {
    let (_dir, db) = db();
    db.with(|c| import_kids(c, &[kids_row(10.0, false, None)])).unwrap();
    assert_eq!(db.with(|c| import_kids(c, &[kids_row(10.0, false, None)])).unwrap(), 0);
    assert_eq!(db.with(|c| import_kids(c, &[kids_row(10.0, false, Some(6))])).unwrap(), 1);
    assert_eq!(db.with(rows::kids_from_six).unwrap(), ["x"]);
    assert_eq!(db.with(|c| import_kids(c, &[kids_row(10.0, true, None)])).unwrap(), 1);
    assert!(db.with(rows::kids).unwrap().is_empty());
}

/// A removal says nothing about an age. A tombstone's column may still hold
/// the age the mark had while live — a build from before ages leaves it
/// there — and it is neither sent nor compared; one this device takes in is
/// written without one.
#[test]
fn a_tombstone_carries_no_age_out_or_in() {
    let (_dir, db) = db();
    db.with(|c| c.execute("INSERT INTO kids(set_id, marked_at, removed_at, age) VALUES ('x', 1, 2, 6)", []))
        .unwrap();
    let out = db.with(export_kids).unwrap();
    assert_eq!(out, [kids_row(2.0, true, None)]);
    assert_eq!(db.with(|c| import_kids(c, &out)).unwrap(), 0);

    db.with(|c| import_kids(c, &[kids_row(3.0, false, Some(6))])).unwrap();
    db.with(|c| import_kids(c, &[kids_row(4.0, true, None)])).unwrap();
    assert_eq!(stored_mark(&db), (3, Some(4), None));
}
