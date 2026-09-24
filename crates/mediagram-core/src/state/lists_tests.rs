use super::*;
use crate::state::StateDb;
use crate::state::profiles;

fn profile(db: &StateDb) -> String {
    db.with(|conn| profiles::create(conn, "André", false))
        .unwrap()
        .unwrap()
        .id
}

#[test]
fn a_new_collection_is_empty() {
    let dir = tempfile::tempdir().unwrap();
    let db = StateDb::new(dir.path().to_path_buf());
    let id = profile(&db);
    let list = db
        .with(|conn| create(conn, &id, "Favourites"))
        .unwrap()
        .unwrap();
    assert_eq!(list.items, Vec::<String>::new());
}

#[test]
fn a_failed_collection_lookup_is_not_reported_as_an_absent_collection() {
    let conn = rusqlite::Connection::open_in_memory().unwrap();
    // An unavailable schema is a database error, not a missing collection.
    assert!(set_in_collection(&conn, "viewer", "collection", "01A", true).is_err());
}

#[test]
fn adding_a_title_twice_holds_it_once() {
    let dir = tempfile::tempdir().unwrap();
    let db = StateDb::new(dir.path().to_path_buf());
    let id = profile(&db);
    let list = db
        .with(|conn| create(conn, &id, "Favourites"))
        .unwrap()
        .unwrap();
    db.with(|conn| set_in_collection(conn, &id, &list.id, "01A", true))
        .unwrap();
    db.with(|conn| set_in_collection(conn, &id, &list.id, "01A", true))
        .unwrap();
    let lists = db.with(|conn| collections_for(conn, &id)).unwrap();
    assert_eq!(lists[0].items, vec!["01A".to_string()]);
}

/// A list belonging to a different profile must not be reachable through
/// this one's calls.
#[test]
fn a_list_is_scoped_to_the_profile_that_made_it() {
    let dir = tempfile::tempdir().unwrap();
    let db = StateDb::new(dir.path().to_path_buf());
    let mine = profile(&db);
    let theirs = db
        .with(|conn| profiles::create(conn, "Robin", false))
        .unwrap()
        .unwrap()
        .id;
    let list = db
        .with(|conn| create(conn, &mine, "Favourites"))
        .unwrap()
        .unwrap();

    assert!(
        !db.with(|conn| set_in_collection(conn, &theirs, &list.id, "01A", true))
            .unwrap()
    );
    assert!(!db.with(|conn| delete(conn, &theirs, &list.id)).unwrap());
}

#[test]
fn deleting_a_collection_takes_it_off_the_list_of_lists() {
    let dir = tempfile::tempdir().unwrap();
    let db = StateDb::new(dir.path().to_path_buf());
    let id = profile(&db);
    let list = db
        .with(|conn| create(conn, &id, "Favourites"))
        .unwrap()
        .unwrap();
    db.with(|conn| set_in_collection(conn, &id, &list.id, "01A", true))
        .unwrap();

    assert!(db.with(|conn| delete(conn, &id, &list.id)).unwrap());
    assert_eq!(
        db.with(|conn| collections_for(conn, &id)).unwrap(),
        Vec::new()
    );
}

/// A deleted list is a tombstoned row, not a gone one — checked rather than
/// assumed: a stale row here would let a title be added back to a list
/// nobody can see.
#[test]
fn a_deleted_collection_cannot_be_added_to_again() {
    let dir = tempfile::tempdir().unwrap();
    let db = StateDb::new(dir.path().to_path_buf());
    let id = profile(&db);
    let list = db.with(|conn| create(conn, &id, "Weg")).unwrap().unwrap();
    db.with(|conn| delete(conn, &id, &list.id)).unwrap();

    assert!(
        !db.with(|conn| set_in_collection(conn, &id, &list.id, "01B", true))
            .unwrap()
    );
}
