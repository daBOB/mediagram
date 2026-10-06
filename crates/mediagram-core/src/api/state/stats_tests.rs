use super::*;

const TODAY: &str = "2026-10-03";

/// What a sync round leaves behind: one device's watch time on one day.
fn watched_on(core: &Core, profile_id: &str, device: &str, seconds: f64) {
    core.state_db
        .with(|conn| {
            conn.execute(
                "INSERT INTO stats_days(profile_id, day, device, seconds, updated_at)
                   VALUES (?1, ?2, ?3, ?4, 1)",
                rusqlite::params![profile_id, TODAY, device, seconds],
            )
        })
        .unwrap();
}

#[tokio::test]
async fn every_devices_watch_time_adds_up_in_one_summary() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let viewer = core.add_profile("André", false).id;
    let other = core.add_profile("Bea", false).id;
    watched_on(&core, &viewer, "living-room-tv", 3600.0);
    watched_on(&core, &viewer, "phone", 1800.0);
    watched_on(&core, &other, "phone", 900.0);

    let summary = core.stats(viewer, TODAY.into()).await;

    assert_eq!(summary.all_seconds, 5400.0);
    let today = summary.last30.last().unwrap();
    assert_eq!((today.day.as_str(), today.seconds), (TODAY, 5400.0));
}

/// Nothing watched reads as thirty empty days; a store that cannot be read
/// answers no days at all rather than an error.
#[tokio::test]
async fn an_unreadable_store_answers_the_empty_summary() {
    let dir = tempfile::tempdir().unwrap();
    std::fs::create_dir(dir.path().join("state.db")).unwrap();
    let core = Core::at(dir.path());

    let summary = core.stats("viewer".into(), TODAY.into()).await;

    assert_eq!(summary, summary::StatsSummary::default());
}
