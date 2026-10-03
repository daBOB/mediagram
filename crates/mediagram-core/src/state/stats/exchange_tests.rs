use super::*;
use crate::state::StateDb;
use crate::state::exchange::{export_record, import_merged};
use crate::state::merge::{MergedProfile, MergedState};
use crate::state::stats::summary;
use crate::state::{profiles, rows, sync};

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

fn title(set_id: &str, device: &str, seconds: f64, updated_at: f64) -> TitleStatRow {
    TitleStatRow {
        set_id: set_id.into(),
        device: device.into(),
        started_at: 1.0,
        last_watched_at: updated_at,
        seconds,
        again_at: None,
        updated_at,
    }
}

fn day(device: &str, seconds: f64, updated_at: f64) -> DayStatRow {
    DayStatRow {
        day: "2026-10-03".into(),
        device: device.into(),
        seconds,
        updated_at,
    }
}

/// `import_merged` of one viewer carrying only these stats rows.
fn import(db: &StateDb, title_stats: Vec<TitleStatRow>, day_stats: Vec<DayStatRow>) -> u64 {
    let merged = MergedState {
        profiles: vec![MergedProfile {
            name: "andré".into(),
            display_name: "André".into(),
            title_stats,
            day_stats,
            ..Default::default()
        }],
        ..Default::default()
    };
    db.with(|conn| import_merged(conn, &merged)).unwrap()
}

#[test]
fn a_merged_row_is_taken_only_when_newer_or_missing() {
    let (_dir, db) = db();
    let id = profile(&db);
    let rows = |seconds, at| {
        (
            vec![title("01A", "phone", seconds, at)],
            vec![day("phone", seconds, at)],
        )
    };

    let (titles, days) = rows(300.0, 20.0);
    assert_eq!(import(&db, titles, days), 2);
    let (titles, days) = rows(300.0, 20.0);
    assert_eq!(
        import(&db, titles, days),
        0,
        "the same rows again change nothing"
    );
    let (titles, days) = rows(100.0, 10.0);
    assert_eq!(import(&db, titles, days), 0, "an older copy never wins");
    let (titles, days) = rows(600.0, 30.0);
    assert_eq!(import(&db, titles, days), 2);

    assert_eq!(
        db.with(|conn| export(conn, &id)).unwrap(),
        rows(600.0, 30.0)
    );
}

/// Every device's rows go out, not only this one's, and always in the same
/// order, so an unchanged store sends an unchanged document.
#[test]
fn export_carries_every_devices_rows_in_a_fixed_order() {
    let (_dir, db) = db();
    let id = profile(&db);
    import(
        &db,
        vec![
            title("01B", "phone", 1.0, 5.0),
            title("01A", "tv", 2.0, 5.0),
            title("01A", "phone", 3.0, 5.0),
        ],
        vec![day("tv", 4.0, 5.0), day("phone", 5.0, 5.0)],
    );

    let (titles, days) = db.with(|conn| export(conn, &id)).unwrap();
    let keys: Vec<(&str, &str)> = titles
        .iter()
        .map(|row| (row.set_id.as_str(), row.device.as_str()))
        .collect();
    assert_eq!(keys, [("01A", "phone"), ("01A", "tv"), ("01B", "phone")]);
    assert_eq!(
        days.iter()
            .map(|row| row.device.as_str())
            .collect::<Vec<_>>(),
        ["phone", "tv"]
    );

    let record = db.with(|conn| export_record(conn, "laptop")).unwrap();
    assert_eq!(record.profiles[0].title_stats, titles);
    assert_eq!(record.profiles[0].day_stats, days);
}

/// A reinstall that kept its device id gets its own minutes back.
#[test]
fn this_devices_own_newer_row_comes_back_too() {
    let (_dir, db) = db();
    let id = profile(&db);
    let own = db.with(sync::device_id).unwrap();
    import(&db, vec![title("01A", &own, 900.0, 20.0)], vec![]);
    assert_eq!(
        db.with(|conn| export(conn, &id)).unwrap().0[0].seconds,
        900.0
    );
}

/// Every document a device without stats writes stays byte-identical.
#[test]
fn a_store_without_stats_exports_a_document_without_the_keys() {
    let (_dir, db) = db();
    profile(&db);
    let record = db.with(|conn| export_record(conn, "laptop")).unwrap();
    let body = serde_json::to_string(&record).unwrap();
    assert!(
        !body.contains("titleStats") && !body.contains("dayStats"),
        "{body}"
    );
}

/// A stamp past the integer range — one a store took in before such stamps
/// were refused on parse — must not stop this device's documents: the next
/// own write pushes it further, past what an integer column read can hold.
#[test]
fn an_own_row_stamped_past_the_integer_range_still_exports_after_a_write() {
    let (_dir, db) = db();
    let id = profile(&db);
    let own = db.with(sync::device_id).unwrap();
    import(
        &db,
        vec![title("01A", &own, 900.0, 1e19)],
        vec![day(&own, 300.0, 1e19)],
    );
    db.set_progress_counted(&id, "01A", 10.0, None, "2026-10-03", 1_000)
        .unwrap();
    db.set_progress_counted(&id, "01A", 20.0, None, "2026-10-03", 11_000)
        .unwrap();

    let record = db.with(|conn| export_record(conn, "laptop"));
    assert!(record.is_some(), "the document is still written");
    let (titles, days) = db.with(|conn| export(conn, &id)).unwrap();
    assert_eq!(titles[0].seconds, 910.0);
    assert_eq!(days[0].seconds, 310.0);
    let watched = db.with(|conn| rows::watched_for(conn, &id)).unwrap();
    let stats = summary::summarize(&summary::SummaryInput {
        today: "2026-10-03".into(),
        titles,
        days,
        watched,
    });
    assert_ne!(stats.history, vec![], "the stats page still has its history");
}
