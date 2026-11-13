//! A renamed profile on this device: what it writes about its renames, and
//! how it takes a merged rename in — the same row renamed, or two rows of one
//! person folded into one. The web's `state-profile-rename-import.test.ts`.

use rusqlite::{Connection, params};

use crate::state::exchange::{export_record, import_merged};
use crate::state::merge::{MergedProfile, MergedState, merge_states};
use crate::state::open::open;
use crate::state::record::{NameKeys, SyncRecord, normal_name, parse_record};
use crate::state::{lists, profiles, rows};

fn store() -> (tempfile::TempDir, Connection) {
    let dir = tempfile::tempdir().unwrap();
    let conn = open(dir.path()).unwrap();
    (dir, conn)
}

fn make(conn: &Connection, name: &str, kids: bool) -> String {
    profiles::create(conn, name, kids).unwrap().unwrap().id
}

fn keys(renamed_at: f64, former: &[&str]) -> NameKeys {
    NameKeys { renamed_at, former_names: former.iter().map(|name| name.to_string()).collect() }
}

/// A merge of one viewer, built by hand as the web's tests build theirs.
fn viewer(name: &str, names: NameKeys) -> MergedState {
    let profile = MergedProfile {
        name: normal_name(name).unwrap(),
        display_name: name.into(),
        names,
        ..Default::default()
    };
    MergedState { profiles: vec![profile], ..Default::default() }
}

/// Celine renamed to Celi on another device.
fn celi() -> MergedState {
    viewer("Celi", keys(5000.0, &["celine"]))
}

fn wire(conn: &Connection, device: &str) -> SyncRecord {
    let text = serde_json::to_string(&export_record(conn, device).unwrap()).unwrap();
    parse_record(&text).unwrap()
}

/// This device's own document merged with another device's, on which
/// Celine was renamed to Celi.
fn round(conn: &Connection) -> MergedState {
    let there = parse_record(
        r#"{"format":1,"device":"tv","writtenAt":1789000000000,
            "profiles":[{"name":"Celi","renamedAt":5000,"formerNames":["celine"]}]}"#,
    )
    .unwrap();
    merge_states(&[wire(conn, "laptop"), there])
}

fn import(conn: &Connection, merged: &MergedState) -> u64 {
    import_merged(conn, merged).unwrap()
}

fn names(conn: &Connection) -> Vec<(String, String)> {
    profiles::list(conn).unwrap().into_iter().map(|one| (one.id, one.name)).collect()
}

/// "Celine", the older row, and "Celi", a second row an older build made for
/// the same person.
fn two_rows() -> (tempfile::TempDir, Connection, String, String) {
    let (dir, conn) = store();
    let older = make(&conn, "Celine", false);
    let newer = make(&conn, "Celi", false);
    conn.execute("UPDATE profiles SET created_at = 1 WHERE id = ?1", [&older]).unwrap();
    conn.execute("UPDATE profiles SET created_at = 2 WHERE id = ?1", [&newer]).unwrap();
    (dir, conn, older, newer)
}

#[test]
fn a_store_nobody_renamed_exports_neither_key() {
    let (_dir, conn) = store();
    make(&conn, "André", false);
    make(&conn, "Mia", true);
    let text = serde_json::to_string(&export_record(&conn, "laptop").unwrap()).unwrap();
    assert!(!text.contains("renamedAt") && !text.contains("formerNames"), "{text}");
}

/// The same id, its history, the new stamp and former names.
#[test]
fn a_rename_taken_in_renames_the_row_in_place() {
    let (_dir, conn) = store();
    let celine = make(&conn, "Celine", false);
    rows::set_progress(&conn, &celine, "01A", 60.0, Some(1200.0)).unwrap();
    rows::set_watchlisted(&conn, &celine, "01B", true).unwrap();

    assert_eq!(import(&conn, &celi()), 1);

    assert_eq!(names(&conn), [(celine.clone(), "Celi".to_string())]);
    let progress: Vec<String> = rows::progress_for(&conn, &celine).unwrap().into_iter().map(|row| row.set_id).collect();
    assert_eq!(progress, ["01A"]);
    assert_eq!(rows::watchlist_for(&conn, &celine).unwrap(), ["01B"]);
    let exported = export_record(&conn, "laptop").unwrap().profiles.remove(0);
    assert_eq!((exported.name, exported.local_id), ("Celi".to_string(), Some(celine)));
    assert_eq!(exported.names, keys(5000.0, &["celine"]));
}

#[test]
fn a_second_import_of_the_same_rename_changes_nothing() {
    let (_dir, conn) = store();
    make(&conn, "Celine", false);
    assert_eq!(import(&conn, &celi()), 1);
    assert_eq!(import(&conn, &celi()), 0);
    // Nor does a round through the wire with the document that carried it.
    assert_eq!(import(&conn, &round(&conn)), 0);
}

#[test]
fn a_case_only_rename_takes_the_new_spelling() {
    let (_dir, conn) = store();
    let id = make(&conn, "celi", false);
    assert_eq!(import(&conn, &viewer("Celi", keys(5000.0, &[]))), 1);
    assert_eq!(names(&conn), [(id, "Celi".to_string())]);
}

#[test]
fn without_a_rename_this_device_keeps_its_own_spelling() {
    let (_dir, conn) = store();
    let id = make(&conn, "andré", false);
    assert_eq!(import(&conn, &viewer("André", NameKeys::default())), 0);
    assert_eq!(names(&conn), [(id, "andré".to_string())]);
}

#[test]
fn a_viewer_never_met_is_made_with_the_merged_stamp_and_former_names() {
    let (_dir, conn) = store();
    assert_eq!(import(&conn, &celi()), 1);
    let exported = export_record(&conn, "laptop").unwrap().profiles.remove(0);
    assert_eq!(exported.name, "Celi");
    assert_eq!(exported.names, keys(5000.0, &["celine"]));
}

/// The older row's id, and the newest of each row.
#[test]
fn two_rows_of_one_person_fold_into_the_older() {
    let (_dir, conn, older, newer) = two_rows();
    let position = |id: &str, set_id: &str, at: f64, updated_at: i64| {
        conn.execute(
            "INSERT INTO progress(profile_id, set_id, at_seconds, duration, updated_at) VALUES (?1, ?2, ?3, 1200, ?4)",
            params![id, set_id, at, updated_at],
        )
        .unwrap();
    };
    position(&older, "01A", 60.0, 1000);
    position(&older, "01B", 30.0, 1000);
    position(&newer, "01A", 300.0, 2000);
    position(&newer, "01C", 90.0, 2000);

    assert!(import(&conn, &round(&conn)) > 0);

    assert_eq!(names(&conn), [(older.clone(), "Celi".to_string())]);
    let mut progress: Vec<(String, f64)> =
        rows::progress_for(&conn, &older).unwrap().into_iter().map(|row| (row.set_id, row.at)).collect();
    progress.sort_by(|a, b| a.0.cmp(&b.0));
    assert_eq!(progress, [("01A".into(), 300.0), ("01B".into(), 30.0), ("01C".into(), 90.0)]);
    assert_eq!(import(&conn, &round(&conn)), 0);
}

#[test]
fn the_older_row_is_kept_though_it_holds_the_merged_name() {
    let (_dir, conn, _, newer) = two_rows();
    conn.execute("UPDATE profiles SET created_at = 0 WHERE id = ?1", [&newer]).unwrap();
    import(&conn, &celi());
    assert_eq!(names(&conn), [(newer, "Celi".to_string())]);
}

/// The smaller id even when it is the former name's row, which the merged
/// name's would precede if nothing sorted them.
#[test]
fn of_two_rows_made_at_once_the_smaller_id_is_kept() {
    let (_dir, conn, _, _) = two_rows();
    conn.execute_batch(
        "UPDATE profiles SET id = 'b', created_at = 1 WHERE name = 'Celi';
         UPDATE profiles SET id = 'a' WHERE name = 'Celine';",
    )
    .unwrap();
    import(&conn, &celi());
    assert_eq!(names(&conn), [("a".to_string(), "Celi".to_string())]);
}

/// Collections move whole, ids and all, so the merge's copy of one finds
/// it under the kept row rather than clashing with its id.
#[test]
fn a_fold_moves_the_folded_rows_lists_without_a_key_clash() {
    let (_dir, conn, older, newer) = two_rows();
    let list = lists::create(&conn, &newer, "Favs").unwrap().unwrap();
    assert!(lists::set_in_collection(&conn, &newer, &list.id, "01A", true).unwrap());
    rows::set_watchlisted(&conn, &newer, "01B", true).unwrap();

    // A merge that says nothing of the list: what the fold moved is all there is.
    assert_eq!(import(&conn, &celi()), 2);

    let kept: Vec<(String, String, Vec<String>)> =
        lists::collections_for(&conn, &older).unwrap().into_iter().map(|one| (one.id, one.name, one.items)).collect();
    assert_eq!(kept, [(list.id, "Favs".to_string(), vec!["01A".to_string()])]);
    assert_eq!(rows::watchlist_for(&conn, &older).unwrap(), ["01B"]);
    // The same list arriving under the kept row is the row it already has.
    assert_eq!(import(&conn, &round(&conn)), 0);
}

#[test]
fn a_fold_points_a_kids_parent_and_a_households_admin_at_the_kept_row() {
    let (_dir, conn, older, newer) = two_rows();
    let mia = make(&conn, "Mia", true);
    conn.execute("UPDATE profiles SET parent_id = ?2 WHERE id = ?1", [&mia, &newer]).unwrap();
    conn.execute(
        "INSERT INTO households(id, name, name_updated_at, admin_id, admin_updated_at) VALUES ('b', 'Flat', 1000, ?1, 1000)",
        [&newer],
    )
    .unwrap();

    import(&conn, &celi());

    let read = |query: &str| -> String { conn.query_row(query, [], |row| row.get(0)).unwrap() };
    assert_eq!(read(&format!("SELECT parent_id FROM profiles WHERE id = '{mia}'")), older);
    assert_eq!(read("SELECT admin_id FROM households WHERE id = 'b'"), older);
}

/// The web's browser remembers its own choice; here it is `state_meta`'s.
#[test]
fn a_fold_moves_the_chosen_profile_to_the_kept_row() {
    let (_dir, conn, older, newer) = two_rows();
    assert!(profiles::choose(&conn, &newer).unwrap());
    import(&conn, &celi());
    assert_eq!(profiles::chosen(&conn).unwrap(), Some(older));
}

#[test]
fn a_fold_leaves_the_roles_to_the_merge() {
    let hide = |conn: &Connection, id: &str| {
        conn.execute(
            "UPDATE profiles SET hidden_sections = '[\"anime\"]', hidden_sections_updated_at = 1000 WHERE id = ?1",
            [id],
        )
        .unwrap();
    };
    let hidden = |conn: &Connection| -> Vec<(String, Vec<String>)> {
        profiles::list(conn).unwrap().into_iter().map(|one| (one.id, one.hidden_sections)).collect()
    };

    // A merge that says nothing of them: nothing is copied from the folded row.
    let (_dir, conn, older, newer) = two_rows();
    hide(&conn, &newer);
    import(&conn, &celi());
    assert_eq!(hidden(&conn), [(older, Vec::<String>::new())]);

    // This device's own document was part of the merge, so the merge carries them.
    let (_again, conn, older, newer) = two_rows();
    hide(&conn, &newer);
    import(&conn, &round(&conn));
    assert_eq!(hidden(&conn), [(older, vec!["anime".to_string()])]);
    let at: i64 = conn.query_row("SELECT hidden_sections_updated_at FROM profiles", [], |row| row.get(0)).unwrap();
    assert_eq!(at, 1000);
}

/// A mark made under the folded row survives it, with its id, so a merge
/// that carries the same mark finds it under the kept row.
#[test]
fn a_fold_moves_the_folded_rows_scene_marks() {
    let (_dir, conn, older, newer) = two_rows();
    let id = crate::state::scenes::mark(&conn, &newer, "01A", 61.5).unwrap().unwrap();

    import(&conn, &celi());

    let kept = crate::state::scenes::live_for(&conn, &older).unwrap();
    assert_eq!(kept.iter().map(|m| m.id.as_str()).collect::<Vec<_>>(), [id.as_str()]);
}
