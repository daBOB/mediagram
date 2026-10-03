use super::*;
use crate::state::exchange::import_merged;
use crate::state::merge::{MergedProfile, MergedState};
use crate::state::profiles::{self, now_ms};
use crate::state::record::{DayStatRow, ProgressRow, TitleStatRow};

const DAY: &str = "2026-10-03";

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

/// A position write of `01A`, `secs` seconds of wall time after `t0`.
fn play(db: &StateDb, id: &str, at: f64, t0: i64, secs: i64, day: &str) {
    db.set_progress_counted(id, "01A", at, Some(5400.0), day, t0 + secs * 1000)
        .unwrap();
}

/// This device's `01A` row: seconds, started, last watched, started over.
fn title(db: &StateDb, id: &str) -> (f64, i64, i64, Option<i64>) {
    db.with(|conn| {
        conn.query_row(
            "SELECT seconds, started_at, last_watched_at, again_at FROM stats_titles
               WHERE profile_id = ?1 AND set_id = '01A'",
            [id],
            |row| Ok((row.get(0)?, row.get(1)?, row.get(2)?, row.get(3)?)),
        )
    })
    .unwrap()
}

fn days(db: &StateDb, id: &str) -> Vec<(String, f64)> {
    db.with(|conn| {
        let mut stmt =
            conn.prepare("SELECT day, seconds FROM stats_days WHERE profile_id = ?1 ORDER BY day")?;
        let rows = stmt.query_map([id], |row| Ok((row.get(0)?, row.get(1)?)))?;
        rows.collect()
    })
    .unwrap()
}

#[test]
fn a_step_counts_the_smaller_advance_capped_and_nothing_backwards() {
    let prev = Tick {
        at: 100.0,
        wall_ms: 1_000_000,
    };
    for (at, now_ms, expect, why) in [
        (110.0, 1_010_000, 10.0, "steady playback"),
        (
            110.0,
            1_300_000,
            10.0,
            "a pause counts only the time after resuming",
        ),
        (
            700.0,
            1_010_000,
            10.0,
            "a seek forward counts the wall time it took",
        ),
        (120.0, 1_010_000, 10.0, "2× speed counts wall time"),
        (160.0, 1_060_000, 15.0, "a long gap is capped"),
        (50.0, 1_010_000, 0.0, "a seek back counts nothing"),
        (110.0, 1_000_000, 0.0, "no wall time passed"),
        (110.0, 990_000, 0.0, "the clock went back"),
        (f64::NAN, 1_010_000, 0.0, "a broken position counts nothing"),
    ] {
        assert_eq!(step_seconds(Some(&prev), at, now_ms), expect, "{why}");
    }
    assert_eq!(
        step_seconds(None, 110.0, 1_010_000),
        0.0,
        "a first write counts nothing"
    );
}

#[test]
fn only_a_finished_title_with_no_position_here_is_started_over() {
    assert!(again_now(false, true));
    assert!(!again_now(true, true) && !again_now(false, false) && !again_now(true, false));
}

#[test]
fn the_first_write_of_a_title_starts_it_and_counts_nothing() {
    let (_dir, db) = db();
    let id = profile(&db);
    let t0 = now_ms();
    play(&db, &id, 600.0, t0, 0, DAY);
    assert_eq!(title(&db, &id), (0.0, t0, t0, None));
    assert!(days(&db, &id).is_empty(), "no step, no day row");
    let device: String = db
        .with(|conn| conn.query_row("SELECT device FROM stats_titles", [], |row| row.get(0)))
        .unwrap();
    assert_eq!(
        Some(device),
        db.with(sync::device_id),
        "rows name the device that wrote them"
    );
}

#[test]
fn steady_playback_adds_wall_time_to_the_title_and_its_day() {
    let (_dir, db) = db();
    let id = profile(&db);
    let t0 = now_ms();
    for (at, secs) in [(0.0, 0), (10.0, 10), (20.0, 20)] {
        play(&db, &id, at, t0, secs, DAY);
    }
    assert_eq!(title(&db, &id), (20.0, t0, t0 + 20_000, None));
    assert_eq!(days(&db, &id), [(DAY.to_string(), 20.0)]);
    let position = db.with(|conn| rows::progress_for(conn, &id)).unwrap();
    assert_eq!(position[0].at, 20.0, "the position itself landed too");
}

#[test]
fn a_step_across_midnight_counts_on_the_day_of_the_write_that_ends_it() {
    let (_dir, db) = db();
    let id = profile(&db);
    let t0 = now_ms();
    play(&db, &id, 0.0, t0, 0, "2026-10-03");
    play(&db, &id, 10.0, t0, 10, "2026-10-04");
    assert_eq!(days(&db, &id), [("2026-10-04".to_string(), 10.0)]);
}

#[test]
fn a_restarted_process_counts_nothing_for_its_first_write() {
    let dir = tempfile::tempdir().unwrap();
    let t0 = now_ms();
    let id = {
        let db = StateDb::new(dir.path().to_path_buf());
        let id = profile(&db);
        play(&db, &id, 0.0, t0, 0, DAY);
        play(&db, &id, 10.0, t0, 10, DAY);
        id
    };
    let db = StateDb::new(dir.path().to_path_buf());
    play(&db, &id, 20.0, t0, 20, DAY);
    assert_eq!(
        title(&db, &id).0,
        10.0,
        "no tick survives a restart to measure from"
    );
    play(&db, &id, 30.0, t0, 30, DAY);
    assert_eq!(
        title(&db, &id),
        (20.0, t0, t0 + 30_000, None),
        "started once, not again"
    );
    assert_eq!(days(&db, &id), [(DAY.to_string(), 20.0)]);
}

#[test]
fn starting_a_finished_title_over_is_watched_again_once() {
    let (_dir, db) = db();
    let id = profile(&db);
    let t0 = now_ms();
    play(&db, &id, 0.0, t0, 0, DAY);
    play(&db, &id, 10.0, t0, 10, DAY);
    db.with(|conn| rows::set_watched(conn, &id, "01A", true))
        .unwrap();
    db.forget_tick(&id, "01A");
    play(&db, &id, 20.0, t0, 20, DAY);
    assert_eq!(
        title(&db, &id).0,
        10.0,
        "a finished title's next play is measured from nothing"
    );
    play(&db, &id, 30.0, t0, 30, DAY);
    let (seconds, started, _, again) = title(&db, &id);
    assert_eq!(
        (seconds, started),
        (20.0, t0),
        "started over, not started anew"
    );
    assert_eq!(
        again,
        Some(t0 + 20_000),
        "only the first write after finishing starts it over"
    );
}

/// Finished on another device (or before stats), never played here: its
/// first play here is already a start-over.
#[test]
fn a_title_finished_before_it_played_here_starts_as_watched_again() {
    let (_dir, db) = db();
    let id = profile(&db);
    db.with(|conn| rows::set_watched(conn, &id, "01A", true))
        .unwrap();
    let t0 = now_ms();
    play(&db, &id, 0.0, t0, 0, DAY);
    assert_eq!(title(&db, &id), (0.0, t0, t0, Some(t0)));
}

#[test]
fn a_title_taken_back_from_finished_is_not_watched_again() {
    let (_dir, db) = db();
    let id = profile(&db);
    db.with(|conn| rows::set_watched(conn, &id, "01A", true))
        .unwrap();
    db.with(|conn| rows::set_watched(conn, &id, "01A", false))
        .unwrap();
    play(&db, &id, 0.0, now_ms(), 0, DAY);
    assert_eq!(title(&db, &id).3, None);
}

/// One transaction: when the day row cannot be written, the position and
/// the title row are not either.
#[test]
fn a_position_and_its_watch_time_land_together_or_not_at_all() {
    let (_dir, db) = db();
    let id = profile(&db);
    let t0 = now_ms();
    play(&db, &id, 0.0, t0, 0, DAY);
    db.with(|conn| {
        conn.execute_batch(
            "CREATE TRIGGER refuse_day BEFORE INSERT ON stats_days
             BEGIN SELECT RAISE(ABORT, 'cannot count'); END;",
        )
    })
    .unwrap();

    assert!(
        db.set_progress_counted(&id, "01A", 10.0, None, DAY, t0 + 10_000)
            .is_none()
    );

    assert_eq!(
        db.with(|conn| rows::progress_for(conn, &id)).unwrap()[0].at,
        0.0
    );
    assert_eq!(title(&db, &id).0, 0.0);
}

/// Positions merged in from other devices are never this device's viewing:
/// importing one writes no stats row and leaves no tick to measure from.
#[test]
fn importing_a_merge_records_no_watch_time() {
    let (_dir, db) = db();
    let id = profile(&db);
    let t0 = now_ms();
    let position = ProgressRow {
        set_id: "01A".into(),
        at: 100.0,
        duration: None,
        updated_at: t0 as f64,
    };
    let merged = MergedState {
        profiles: vec![MergedProfile {
            name: "andré".into(),
            display_name: "André".into(),
            progress: vec![position],
            ..Default::default()
        }],
        ..Default::default()
    };
    db.with(|conn| import_merged(conn, &merged)).unwrap();
    let counted: i64 = db
        .with(|conn| conn.query_row("SELECT COUNT(*) FROM stats_titles", [], |row| row.get(0)))
        .unwrap();
    assert_eq!(counted, 0, "an imported position writes no stats row");

    play(&db, &id, 110.0, t0, 10, DAY);
    assert_eq!(
        title(&db, &id).0,
        0.0,
        "nor leaves a tick for the next own write"
    );
    assert!(days(&db, &id).is_empty());
}

/// This device's rows came back from a reinstall stamped from ahead of its
/// clock: its next writes still add to them and stamp past them, or the
/// next import would hand the older copy back.
#[test]
fn this_devices_stamps_never_move_backwards() {
    let (_dir, db) = db();
    let id = profile(&db);
    let t0 = now_ms();
    let own = db.with(sync::device_id).unwrap();
    let ahead = (t0 + 1_000_000) as f64;
    let merged = MergedState {
        profiles: vec![MergedProfile {
            name: "andré".into(),
            display_name: "André".into(),
            title_stats: vec![TitleStatRow {
                set_id: "01A".into(),
                device: own.clone(),
                started_at: t0 as f64,
                last_watched_at: ahead,
                seconds: 100.0,
                again_at: None,
                updated_at: ahead,
            }],
            day_stats: vec![DayStatRow {
                day: DAY.into(),
                device: own,
                seconds: 100.0,
                updated_at: ahead,
            }],
            ..Default::default()
        }],
        ..Default::default()
    };
    db.with(|conn| import_merged(conn, &merged)).unwrap();

    play(&db, &id, 0.0, t0, 0, DAY);
    play(&db, &id, 10.0, t0, 10, DAY);

    let stamps: (f64, f64, i64, i64) = db
        .with(|conn| {
            conn.query_row(
                "SELECT t.seconds, d.seconds, t.updated_at, d.updated_at
                   FROM stats_titles t JOIN stats_days d USING (profile_id, device)",
                [],
                |row| Ok((row.get(0)?, row.get(1)?, row.get(2)?, row.get(3)?)),
            )
        })
        .unwrap();
    assert_eq!(stamps, (110.0, 110.0, t0 + 1_000_002, t0 + 1_000_001));
}
