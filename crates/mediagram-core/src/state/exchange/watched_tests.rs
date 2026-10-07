use super::*;
use crate::state::StateDb;
use crate::state::exchange::{export_record, import_merged};
use crate::state::merge::merge_states;
use crate::state::record::parse_record;
use crate::state::rows;

fn db_with_profile() -> (tempfile::TempDir, StateDb, String) {
    let dir = tempfile::tempdir().unwrap();
    let db = StateDb::new(dir.path().to_path_buf());
    let id = db
        .with(|conn| crate::state::profiles::create(conn, "André", false))
        .unwrap()
        .unwrap()
        .id;
    (dir, db, id)
}

#[test]
fn a_live_mark_and_its_removal_export_under_different_keys() {
    let (_dir, db, id) = db_with_profile();
    db.with(|conn| rows::set_watched(conn, &id, "01A", true))
        .unwrap();
    db.with(|conn| rows::set_watched(conn, &id, "01B", true))
        .unwrap();
    db.with(|conn| rows::set_watched(conn, &id, "01B", false))
        .unwrap();

    let (watched, unwatched) = db.with(|conn| export_watched(conn, &id)).unwrap();
    assert_eq!(
        watched.iter().map(|r| r.set_id.as_str()).collect::<Vec<_>>(),
        vec!["01A"]
    );
    assert_eq!(unwatched.len(), 1);
    assert_eq!(unwatched[0].set_id, "01B");
    assert!(unwatched[0].last_finished_at > 0.0);
    assert!(unwatched[0].updated_at > unwatched[0].last_finished_at);
}

/// A removal older than what this device already holds live must not
/// overwrite it — the merge already decided the live mark was newer.
#[test]
fn an_older_removal_does_not_overwrite_a_newer_live_mark() {
    let (_dir, db, id) = db_with_profile();
    db.with(|conn| rows::set_watched(conn, &id, "01A", true))
        .unwrap();
    let finished_at = db
        .with(|conn| rows::watched_for(conn, &id))
        .unwrap()[0]
        .finished_at;

    db.with(|conn| {
        import_unwatched(
            conn,
            &id,
            &[UnwatchedRow {
                set_id: "01A".into(),
                updated_at: (finished_at - 100) as f64,
                last_finished_at: (finished_at - 200) as f64,
            }],
        )
    })
    .unwrap();

    assert_eq!(
        db.with(|conn| rows::watched_for(conn, &id))
            .unwrap()
            .iter()
            .map(|r| r.set_id.as_str())
            .collect::<Vec<_>>(),
        vec!["01A"]
    );
}

/// A removal newer than what this device holds live replaces it outright.
#[test]
fn a_newer_removal_replaces_a_live_mark() {
    let (_dir, db, id) = db_with_profile();
    db.with(|conn| rows::set_watched(conn, &id, "01A", true))
        .unwrap();
    let finished_at = db
        .with(|conn| rows::watched_for(conn, &id))
        .unwrap()[0]
        .finished_at;

    db.with(|conn| {
        import_unwatched(
            conn,
            &id,
            &[UnwatchedRow {
                set_id: "01A".into(),
                updated_at: (finished_at + 1000) as f64,
                last_finished_at: finished_at as f64,
            }],
        )
    })
    .unwrap();

    assert_eq!(db.with(|conn| rows::watched_for(conn, &id)).unwrap(), Vec::new());
}

/// An unwatched row for a title this device has never heard of still
/// materialises a tombstone, the same way an unmet viewer still gets a
/// local profile.
#[test]
fn an_unwatched_row_for_an_unknown_title_still_creates_a_tombstone() {
    let (_dir, db, id) = db_with_profile();
    let changed = db
        .with(|conn| {
            import_unwatched(
                conn,
                &id,
                &[UnwatchedRow {
                    set_id: "01A".into(),
                    updated_at: 2000.0,
                    last_finished_at: 1000.0,
                }],
            )
        })
        .unwrap();
    assert_eq!(changed, 1);
    assert_eq!(db.with(|conn| rows::watched_for(conn, &id)).unwrap(), Vec::new());
}

/// A removal exactly as new as a local live mark still applies:
/// `merge::watched::reconcile` gives that tie to the removal, and import
/// must agree with it rather than favouring whichever fact happened to be
/// standing already (R2).
#[test]
fn a_removal_tied_exactly_with_a_local_live_mark_still_applies() {
    let (_dir, db, id) = db_with_profile();
    db.with(|conn| rows::set_watched(conn, &id, "01A", true))
        .unwrap();
    let finished_at = db
        .with(|conn| rows::watched_for(conn, &id))
        .unwrap()[0]
        .finished_at;

    db.with(|conn| {
        import_unwatched(
            conn,
            &id,
            &[UnwatchedRow {
                set_id: "01A".into(),
                updated_at: finished_at as f64,
                last_finished_at: finished_at as f64,
            }],
        )
    })
    .unwrap();

    assert_eq!(db.with(|conn| rows::watched_for(conn, &id)).unwrap(), Vec::new());
}

/// Re-marking watched always beats a future-dated removal, even one this
/// device only knows about through an import carrying another device's
/// (ahead-running) clock (R1).
#[test]
fn remarking_watched_always_beats_a_future_dated_removal() {
    let (_dir, db, id) = db_with_profile();
    let far_future = (crate::state::profiles::now_ms() + 100_000) as f64;

    db.with(|conn| {
        import_unwatched(
            conn,
            &id,
            &[UnwatchedRow {
                set_id: "01A".into(),
                updated_at: far_future,
                last_finished_at: far_future - 1000.0,
            }],
        )
    })
    .unwrap();
    assert_eq!(db.with(|conn| rows::watched_for(conn, &id)).unwrap(), Vec::new());

    db.with(|conn| rows::set_watched(conn, &id, "01A", true))
        .unwrap();
    let (watched, _) = db.with(|conn| export_watched(conn, &id)).unwrap();
    assert_eq!(watched.len(), 1);
    assert!(watched[0].updated_at > far_future);
}

/// 2^53, one past it, and the largest i64: stamps no clock wrote. The first
/// two are past what the web holds exactly; the last saturates `as i64` and
/// leaves nothing for an own write's `+ 1` to step past.
const PAST_SAFE: [&str; 3] = ["9007199254740992", "9007199254740993", "9223372036854775807"];

/// One sync round the way `sync` runs it: this device's own document, built
/// fresh, merged with a peer's parsed off the wire, and taken back in.
fn sync_in(db: &StateDb, watched: &str, unwatched: &str) {
    let peer = format!(
        r#"{{"format":1,"device":"phone","writtenAt":1,"profiles":[{{"name":"André","progress":[],"watched":[{watched}],"unwatched":[{unwatched}]}}]}}"#
    );
    db.with(|conn| {
        let own = export_record(conn, "laptop")?;
        let records: Vec<_> = std::iter::once(own).chain(parse_record(&peer)).collect();
        import_merged(conn, &merge_states(&records))
    })
    .expect("the sync round imports");
}

/// Whether the round trip through `db.with` wrote this device's document,
/// with the error kept rather than folded into `None`.
fn exported(db: &StateDb) -> rusqlite::Result<crate::state::record::SyncRecord> {
    db.with(|conn| Ok(export_record(conn, "laptop"))).unwrap()
}

/// What SQLite holds in each `watched` row, `finished_at/removed_at`.
fn kinds(db: &StateDb) -> Vec<String> {
    db.with(|conn| {
        let mut stmt = conn.prepare(
            "SELECT typeof(finished_at) || '/' || typeof(removed_at) FROM watched ORDER BY set_id",
        )?;
        stmt.query_map([], |row| row.get(0))?.collect()
    })
    .unwrap()
}

/// A peer's stamp past 2^53 − 1 is dropped at the boundary: taken in, a live
/// mark at it would outrank this device's real removal for good, and a
/// removal that finished at it would tombstone every position this title
/// ever gets again.
#[test]
fn a_peer_stamp_past_the_safe_range_is_not_taken_in() {
    for stamp in PAST_SAFE {
        let (_dir, db, id) = db_with_profile();
        db.with(|conn| rows::set_watched(conn, &id, "01A", true))
            .unwrap();
        db.with(|conn| rows::set_watched(conn, &id, "01A", false))
            .unwrap();
        db.with(|conn| rows::set_progress(conn, &id, "01C", 600.0, None))
            .unwrap();

        sync_in(
            &db,
            &format!(r#"{{"setId":"01A","updatedAt":{stamp}}}"#),
            &format!(
                r#"{{"setId":"01B","updatedAt":{stamp},"lastFinishedAt":1789000000000}},
                   {{"setId":"01C","updatedAt":1789000000000,"lastFinishedAt":{stamp}}}"#
            ),
        );

        let watched = db.with(|conn| rows::watched_for(conn, &id)).unwrap();
        assert_eq!(watched, vec![], "{stamp}: the real removal of 01A stands");
        let progress = db.with(|conn| rows::progress_for(conn, &id)).unwrap();
        assert_eq!(progress.len(), 1, "{stamp}: the position on 01C survives");
        let (_, unwatched) = db.with(|conn| export_watched(conn, &id)).unwrap();
        let titles: Vec<_> = unwatched.iter().map(|r| r.set_id.as_str()).collect();
        assert_eq!(titles, vec!["01A"], "{stamp}: no peer removal is taken in");
    }
}

/// The overflow end to end: a peer's mark at a stamp no clock wrote, then
/// this device un-marking and re-marking it. Every round after must still
/// write this device's document and read its list.
#[test]
fn a_peer_stamp_past_the_safe_range_cannot_stop_this_devices_document() {
    for stamp in PAST_SAFE {
        let (_dir, db, id) = db_with_profile();
        sync_in(
            &db,
            &format!(r#"{{"setId":"01A","updatedAt":{stamp}}}"#),
            "",
        );
        for finished in [false, true] {
            db.with(|conn| rows::set_watched(conn, &id, "01A", finished))
                .unwrap();
            exported(&db).unwrap_or_else(|err| panic!("{stamp}, finished {finished}: {err}"));
            db.with(|conn| Ok(rows::watched_for(conn, &id)))
                .unwrap()
                .unwrap_or_else(|err| panic!("{stamp}, finished {finished}: {err}"));
        }
    }
}

/// A row an older build already saturated — before such stamps were refused
/// on parse — must not overflow this device's own `+ 1`: the sum is a real
/// in SQLite, and every integer read of it fails.
#[test]
fn own_writes_on_a_row_already_at_the_largest_integer_stay_integers() {
    let (_dir, db, id) = db_with_profile();
    db.with(|conn| rows::set_watched(conn, &id, "01A", true))
        .unwrap();
    db.with(|conn| conn.execute("UPDATE watched SET finished_at = ?1", [i64::MAX]))
        .unwrap();

    db.with(|conn| rows::set_watched(conn, &id, "01A", false))
        .unwrap();
    assert_eq!(kinds(&db), vec!["integer/integer"]);
    db.with(|conn| rows::set_watched(conn, &id, "01A", true))
        .unwrap();
    assert_eq!(kinds(&db), vec!["integer/null"]);

    exported(&db).expect("the document is still written");
    let watched = db.with(|conn| rows::watched_for(conn, &id)).unwrap();
    assert_eq!(watched.len(), 1, "the re-mark is live");
}

/// A real that an older build's overflowing `+ 1` already left in the table
/// must not stop the document, nor the list of what this profile finished.
#[test]
fn a_row_already_holding_a_real_stamp_still_exports_and_lists() {
    let (_dir, db, id) = db_with_profile();
    db.with(|conn| {
        conn.execute(
            "INSERT INTO watched(profile_id, set_id, finished_at, removed_at)
               VALUES (?1, '01A', 1e19, NULL), (?1, '01B', 1789000000000, 1e19)",
            [&id],
        )
    })
    .unwrap();
    assert_eq!(kinds(&db), vec!["real/null", "integer/real"]);

    exported(&db).expect("the document is still written");
    let watched = db.with(|conn| Ok(rows::watched_for(conn, &id))).unwrap();
    let titles: Vec<_> = watched
        .expect("the list still reads")
        .into_iter()
        .map(|r| r.set_id)
        .collect();
    assert_eq!(titles, vec!["01A"]);
}
