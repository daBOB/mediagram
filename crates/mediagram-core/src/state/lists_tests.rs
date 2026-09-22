use super::*;
use crate::state::StateDb;
use crate::state::profiles;

fn profile(db: &StateDb) -> String {
    db.with(|conn| profiles::create(conn, "André")).unwrap().unwrap().id
}

#[test]
fn a_new_collection_is_empty() {
    let dir = tempfile::tempdir().unwrap();
    let db = StateDb::new(dir.path().to_path_buf());
    let id = profile(&db);
    let list = db.with(|conn| create(conn, &id, "Favourites")).unwrap().unwrap();
    assert_eq!(list.items, Vec::<String>::new());
}

#[test]
fn adding_a_title_twice_holds_it_once() {
    let dir = tempfile::tempdir().unwrap();
    let db = StateDb::new(dir.path().to_path_buf());
    let id = profile(&db);
    let list = db.with(|conn| create(conn, &id, "Favourites")).unwrap().unwrap();
    db.with(|conn| set_in_collection(conn, &id, &list.id, "01A", true)).unwrap();
    db.with(|conn| set_in_collection(conn, &id, &list.id, "01A", true)).unwrap();
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
    let theirs = db.with(|conn| profiles::create(conn, "Robin")).unwrap().unwrap().id;
    let list = db.with(|conn| create(conn, &mine, "Favourites")).unwrap().unwrap();

    assert!(!db.with(|conn| set_in_collection(conn, &theirs, &list.id, "01A", true)).unwrap());
    assert!(!db.with(|conn| delete(conn, &theirs, &list.id)).unwrap());
}

#[test]
fn deleting_a_collection_drops_its_items_too() {
    let dir = tempfile::tempdir().unwrap();
    let db = StateDb::new(dir.path().to_path_buf());
    let id = profile(&db);
    let list = db.with(|conn| create(conn, &id, "Favourites")).unwrap().unwrap();
    db.with(|conn| set_in_collection(conn, &id, &list.id, "01A", true)).unwrap();

    assert!(db.with(|conn| delete(conn, &id, &list.id)).unwrap());
    assert_eq!(db.with(|conn| collections_for(conn, &id)).unwrap(), Vec::new());
}
