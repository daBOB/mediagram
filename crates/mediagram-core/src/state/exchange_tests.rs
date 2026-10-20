use super::*;
use crate::state::StateDb;
use crate::state::editors_choice::{editors_choice, set_editors_choice};
use crate::state::lists;
use crate::state::merge::{MergedProfile, merge_states};
use crate::state::record::{NameKeys, ProgressRow, UnwatchedRow, WatchedRow, parse_record};

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
fn importing_an_empty_profile_counts_its_creation_only_once() {
    let (_dir, db) = db();
    let mut merged = MergedState {
        profiles: vec![MergedProfile {
            name: "robin".into(),
            display_name: "Robin".into(),
            ..Default::default()
        }],
        ..Default::default()
    };

    assert_eq!(db.with(|conn| import_merged(conn, &merged)), Some(1));
    merged.profiles[0].name = "  ROBIN  ".into();
    assert_eq!(db.with(|conn| import_merged(conn, &merged)), Some(0));
    let stored = db.with(profiles::list).unwrap();
    assert_eq!(stored.len(), 1);
    assert_eq!(stored[0].name, "Robin");
}

/// A device that never heard about `02B` must not lose the position it does
/// know about: importing a merge that only mentions `02B` may not touch
/// `01A`.
#[test]
fn import_never_deletes_a_row_the_merge_did_not_mention() {
    let (_dir, db) = db();
    let id = profile(&db);
    db.with(|conn| rows::set_progress(conn, &id, "01A", 100.0, None))
        .unwrap();

    let merged = MergedState {
        profiles: vec![MergedProfile {
            name: "andré".into(),
            display_name: "André".into(),
            progress: vec![ProgressRow {
                set_id: "02B".into(),
                at: 5.0,
                duration: None,
                updated_at: 1.0,
            }],
            watched: vec![],
            ..Default::default()
        }],
        ..Default::default()
    };
    db.with(|conn| import_merged(conn, &merged)).unwrap();

    let positions = db.with(|conn| rows::progress_for(conn, &id)).unwrap();
    assert!(
        positions.iter().any(|row| row.set_id == "01A"),
        "an untouched row must survive an import"
    );
}

/// The tombstone rule: a completion deletes a position no newer than it,
/// even one the completion's own row never mentions by timestamp.
#[test]
fn a_completion_deletes_a_position_no_newer_than_it() {
    let (_dir, db) = db();
    let id = profile(&db);
    db.with(|conn| rows::set_progress(conn, &id, "01A", 100.0, None))
        .unwrap();

    let merged = MergedState {
        profiles: vec![MergedProfile {
            name: "andré".into(),
            display_name: "André".into(),
            progress: vec![],
            watched: vec![WatchedRow {
                set_id: "01A".into(),
                updated_at: 9_999_999_999_999.0,
            }],
            ..Default::default()
        }],
        ..Default::default()
    };
    db.with(|conn| import_merged(conn, &merged)).unwrap();

    assert_eq!(
        db.with(|conn| rows::progress_for(conn, &id)).unwrap(),
        Vec::new()
    );
    assert_eq!(
        db.with(|conn| rows::watched_for(conn, &id)).unwrap().len(),
        1
    );
}

/// An `UnwatchedRow` removal is not itself a tombstone for progress —
/// `last_finished_at` is: a position made since that completion (a genuine
/// rewatch) survives, unlike under a live completion
/// (`a_completion_deletes_a_position_no_newer_than_it`).
#[test]
fn a_removal_leaves_a_rewatch_position_alone() {
    let (_dir, db) = db();
    let id = profile(&db);
    db.with(|conn| rows::set_progress(conn, &id, "01A", 300.0, None))
        .unwrap();

    let merged = MergedState {
        profiles: vec![MergedProfile {
            name: "andré".into(),
            display_name: "André".into(),
            progress: vec![],
            watched: vec![],
            unwatched: vec![UnwatchedRow {
                set_id: "01A".into(),
                updated_at: 9_999_999_999_999.0,
                last_finished_at: 1.0,
            }],
            ..Default::default()
        }],
        ..Default::default()
    };
    db.with(|conn| import_merged(conn, &merged)).unwrap();

    assert_eq!(
        db.with(|conn| rows::progress_for(conn, &id)).unwrap()[0].at,
        300.0
    );
    assert_eq!(db.with(|conn| rows::watched_for(conn, &id)).unwrap(), Vec::new());
}

/// A viewer named in the merge but never seen on this device gets a local
/// profile, spelled the way the merge's `displayName` says — never the
/// normalised identity.
#[test]
fn an_unknown_viewer_is_created_from_the_display_name() {
    let (_dir, db) = db();
    let merged = MergedState {
        profiles: vec![MergedProfile {
            name: "robin".into(),
            display_name: "Robin".into(),
            progress: vec![ProgressRow {
                set_id: "01A".into(),
                at: 5.0,
                duration: None,
                updated_at: 1.0,
            }],
            watched: vec![],
            ..Default::default()
        }],
        ..Default::default()
    };
    db.with(|conn| import_merged(conn, &merged)).unwrap();

    let names: Vec<String> = db
        .with(profiles::list)
        .unwrap()
        .into_iter()
        .map(|p| p.name)
        .collect();
    assert_eq!(names, vec!["Robin".to_string()]);
}

/// `state.db` sits beside `catalog/`, never inside it, so a refresh that
/// replaces the catalog wholesale must leave it alone.
#[test]
fn a_catalog_refresh_does_not_touch_state_db() {
    let (dir, db) = db();
    let id = profile(&db);
    db.with(|conn| rows::set_progress(conn, &id, "01A", 100.0, None))
        .unwrap();

    let catalog_root = dir.path().join("catalog");
    let incoming = catalog_root.join("incoming");
    std::fs::create_dir_all(&incoming).unwrap();
    crate::versions::install_staged(&catalog_root, &incoming, "v-1").unwrap();

    let positions = db.with(|conn| rows::progress_for(conn, &id)).unwrap();
    assert_eq!(
        positions.len(),
        1,
        "a catalog refresh must not touch state.db"
    );
}

/// Stamps no clock wrote, as a peer's JSON text carries them: 2^53, the
/// largest i64, and a double far past both.
const PAST_SAFE: [&str; 3] = ["9007199254740992", "9223372036854775807", "1e300"];

/// One sync round the way `sync` runs it: this device's own document, built
/// fresh, merged with a peer's parsed off the wire, and taken back in.
/// `rows` is the peer document's members after its header.
fn sync_in(db: &StateDb, rows: &str) {
    let peer = format!(r#"{{"format":1,"device":"phone","writtenAt":1,{rows}}}"#);
    db.with(|conn| {
        let own = export_record(conn, "laptop")?;
        let records: Vec<_> = std::iter::once(own).chain(parse_record(&peer)).collect();
        import_merged(conn, &merge_states(&records))
    })
    .expect("the sync round imports");
}

/// The peer's rows for André.
fn andre(rows: &str) -> String {
    format!(r#""profiles":[{{"name":"André",{rows}}}]"#)
}

/// A peer's row synced in before and after an edit this device makes. Taken
/// in, a row stamped past 2^53 − 1 outranks that edit and every later one,
/// on every device the row reaches.
fn edit_between_rounds<T>(
    db: &StateDb,
    peer: &str,
    edit: impl FnOnce(&Connection) -> rusqlite::Result<T>,
) {
    sync_in(db, peer);
    db.with(edit).expect("the edit is written");
    sync_in(db, peer);
}

#[test]
fn a_peer_position_past_the_safe_range_cannot_outrank_a_later_one() {
    for stamp in PAST_SAFE {
        let (_dir, db) = db();
        let id = profile(&db);
        let peer = andre(&format!(
            r#""progress":[{{"setId":"01A","at":5,"updatedAt":{stamp}}}]"#
        ));
        edit_between_rounds(&db, &peer, |conn| {
            rows::set_progress(conn, &id, "01A", 900.0, None)
        });
        let at: Vec<_> = db
            .with(|conn| rows::progress_for(conn, &id))
            .unwrap()
            .into_iter()
            .map(|row| row.at)
            .collect();
        assert_eq!(at, vec![900.0], "{stamp}: the later position stands");
    }
}

#[test]
fn a_peer_watchlist_mark_past_the_safe_range_cannot_outrank_a_later_removal() {
    for stamp in PAST_SAFE {
        let (_dir, db) = db();
        let id = profile(&db);
        db.with(|conn| rows::set_watchlisted(conn, &id, "01A", true))
            .unwrap();
        let peer = andre(&format!(
            r#""watchlist":[{{"setId":"01A","updatedAt":{stamp}}}]"#
        ));
        edit_between_rounds(&db, &peer, |conn| {
            rows::set_watchlisted(conn, &id, "01A", false)
        });
        let listed = db.with(|conn| rows::watchlist_for(conn, &id)).unwrap();
        assert_eq!(
            listed,
            Vec::<String>::new(),
            "{stamp}: the later removal stands"
        );
    }
}

#[test]
fn a_peer_collection_past_the_safe_range_cannot_outrank_a_later_rename() {
    for stamp in PAST_SAFE {
        let (_dir, db) = db();
        let id = profile(&db);
        let list = db
            .with(|conn| lists::create(conn, &id, "Films"))
            .unwrap()
            .unwrap();
        let peer = andre(&format!(
            r#""collections":[{{"id":"{}","name":"Stolen","items":["01A"],"updatedAt":{stamp}}}]"#,
            list.id
        ));
        edit_between_rounds(&db, &peer, |conn| {
            lists::rename(conn, &id, &list.id, "Mine")
        });
        let held: Vec<_> = db
            .with(|conn| lists::collections_for(conn, &id))
            .unwrap()
            .into_iter()
            .map(|list| (list.name, list.items))
            .collect();
        assert_eq!(
            held,
            vec![("Mine".to_string(), vec![])],
            "{stamp}: the later rename stands, and the peer's items do not come with it"
        );
    }
}

#[test]
fn a_peer_kids_mark_past_the_safe_range_cannot_outrank_a_later_removal() {
    for stamp in PAST_SAFE {
        let (_dir, db) = db();
        db.with(|conn| rows::set_kids(conn, "01A", Some(12))).unwrap();
        let peer = format!(r#""kids":[{{"setId":"01A","updatedAt":{stamp}}}]"#);
        edit_between_rounds(&db, &peer, |conn| rows::set_kids(conn, "01A", None));
        let kids = db.with(rows::kids).unwrap();
        assert_eq!(
            kids,
            Vec::<String>::new(),
            "{stamp}: the later removal stands"
        );
    }
}

#[test]
fn a_peer_editors_choice_past_the_safe_range_cannot_outrank_a_later_pick() {
    for stamp in PAST_SAFE {
        let (_dir, db) = db();
        let peer = format!(r#""editorsChoice":[{{"setId":"01B","updatedAt":{stamp}}}]"#);
        edit_between_rounds(&db, &peer, |conn| set_editors_choice(conn, "01A", true));
        assert_eq!(
            db.with(editors_choice).unwrap(),
            Some("01A".to_string()),
            "{stamp}: the later pick leads"
        );
    }
}

/// A device's own store, opened directly so a test sees an import's error.
struct Device {
    _dir: tempfile::TempDir,
    conn: Connection,
    name: &'static str,
}

fn device(name: &'static str) -> Device {
    let dir = tempfile::tempdir().unwrap();
    let conn = crate::state::open::open(dir.path()).unwrap();
    Device { _dir: dir, conn, name }
}

/// A device's document as the channel carries it.
fn wire(device: &Device) -> SyncRecord {
    let text = serde_json::to_string(&export_record(&device.conn, device.name).unwrap()).unwrap();
    parse_record(&text).unwrap()
}

/// One round on `on`: its own document and `from`'s, merged and taken back in.
fn round(on: &Device, from: &Device) -> u64 {
    import_merged(&on.conn, &merge_states(&[wire(on), wire(from)])).unwrap()
}

/// A document as a build from before renames reads it: without the two keys.
fn older(mut record: SyncRecord) -> SyncRecord {
    for profile in &mut record.profiles {
        profile.names = NameKeys::default();
    }
    record
}

/// The laptop and the TV, both knowing Oma — her position and her list, made
/// on the laptop and taken in by the TV — and then the laptop renames her
/// Omi. Both devices, then the laptop's id for her and the TV's.
fn renamed_on_the_laptop() -> (Device, Device, String, String) {
    let (laptop, tv) = (device("laptop"), device("tv"));
    let oma = profiles::create(&laptop.conn, "Oma", false).unwrap().unwrap().id;
    rows::set_progress(&laptop.conn, &oma, "01A", 60.0, Some(1200.0)).unwrap();
    let list = lists::create(&laptop.conn, &oma, "Favs").unwrap().unwrap();
    assert!(lists::set_in_collection(&laptop.conn, &oma, &list.id, "01A", true).unwrap());
    assert!(round(&tv, &laptop) > 0);
    let tv_oma = profiles::list(&tv.conn).unwrap().remove(0).id;
    laptop
        .conn
        .execute(
            r#"UPDATE profiles SET name = 'Omi', renamed_at = 2000, former_names = '["oma"]' WHERE id = ?1"#,
            [&oma],
        )
        .unwrap();
    (laptop, tv, oma, tv_oma)
}

fn ids_and_names(conn: &Connection) -> Vec<(String, String)> {
    profiles::list(conn).unwrap().into_iter().map(|one| (one.id, one.name)).collect()
}

fn renamed_omi() -> NameKeys {
    NameKeys { renamed_at: 2000.0, former_names: vec!["oma".into()] }
}

/// Every row of every table, to show an import left them all as they were.
fn everything(conn: &Connection) -> Vec<String> {
    let mut tables = conn.prepare("SELECT name FROM sqlite_master WHERE type = 'table' ORDER BY name").unwrap();
    let tables: Vec<String> = tables.query_map([], |row| row.get(0)).unwrap().map(Result::unwrap).collect();
    let mut out = Vec::new();
    for table in tables {
        let mut select = conn.prepare(&format!("SELECT * FROM \"{table}\"")).unwrap();
        let width = select.column_count();
        let found = select
            .query_map([], |row| {
                (0..width).map(|at| row.get::<_, rusqlite::types::Value>(at)).collect::<rusqlite::Result<Vec<_>>>()
            })
            .unwrap();
        out.extend(found.map(|one| format!("{table}: {:?}", one.unwrap())));
    }
    out
}

#[test]
fn two_devices_agree_on_a_rename_and_a_second_round_changes_nothing() {
    let (laptop, tv, _, tv_oma) = renamed_on_the_laptop();
    assert!(round(&tv, &laptop) > 0);

    assert_eq!(ids_and_names(&tv.conn), [(tv_oma.clone(), "Omi".to_string())]);
    let progress: Vec<(String, f64)> =
        rows::progress_for(&tv.conn, &tv_oma).unwrap().into_iter().map(|row| (row.set_id, row.at)).collect();
    assert_eq!(progress, [("01A".to_string(), 60.0)]);
    let kept: Vec<(String, Vec<String>)> =
        lists::collections_for(&tv.conn, &tv_oma).unwrap().into_iter().map(|one| (one.name, one.items)).collect();
    assert_eq!(kept, [("Favs".to_string(), vec!["01A".to_string()])]);
    let exported = export_record(&tv.conn, "tv").unwrap().profiles.remove(0);
    assert_eq!((exported.name.as_str(), exported.names), ("Omi", renamed_omi()));

    assert_eq!(round(&tv, &laptop), 0);
    assert_eq!(round(&laptop, &tv), 0);
}

/// And makes no profile under the old name.
#[test]
fn an_older_builds_document_under_the_old_name_is_filed_under_the_new_one() {
    let (laptop, tv, oma, tv_oma) = renamed_on_the_laptop();
    // The TV never took the rename in, and goes on watching as Oma.
    rows::set_progress(&tv.conn, &tv_oma, "01B", 30.0, Some(1200.0)).unwrap();

    let merged = merge_states(&[wire(&laptop), older(wire(&tv))]);
    assert!(import_merged(&laptop.conn, &merged).unwrap() > 0);

    assert_eq!(ids_and_names(&laptop.conn), [(oma.clone(), "Omi".to_string())]);
    let mut progress: Vec<String> =
        rows::progress_for(&laptop.conn, &oma).unwrap().into_iter().map(|row| row.set_id).collect();
    progress.sort();
    assert_eq!(progress, ["01A", "01B"]);
    assert_eq!(export_record(&laptop.conn, "laptop").unwrap().profiles[0].names, renamed_omi());
}

#[test]
fn an_older_builds_own_merge_shows_both_names() {
    let (laptop, tv, _, _) = renamed_on_the_laptop();
    let merged = merge_states(&[older(wire(&tv)), older(wire(&laptop))]);
    let mut names: Vec<String> = merged.profiles.into_iter().map(|one| one.name).collect();
    names.sort();
    assert_eq!(names, ["oma", "omi"]);
}

/// Why the merge and import ship, and reach every device, before anything
/// can rename: the new name's row would take in a list id the old one holds.
/// The TV keeps every row, takes nothing in and publishes nothing until it
/// updates.
#[test]
fn an_older_build_holding_the_persons_list_cannot_take_the_new_name_in() {
    let (laptop, tv, _, _) = renamed_on_the_laptop();
    let before = everything(&tv.conn);

    let merged = merge_states(&[older(wire(&tv)), older(wire(&laptop))]);
    let error = import_merged(&tv.conn, &merged).unwrap_err();

    assert!(error.to_string().contains("UNIQUE constraint failed: collections.id"), "{error}");
    assert_eq!(everything(&tv.conn), before);
    let names: Vec<String> = profiles::list(&tv.conn).unwrap().into_iter().map(|one| one.name).collect();
    assert_eq!(names, ["Oma"]);
}
