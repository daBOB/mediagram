use super::*;
use crate::api::test_support::index_at;

const TODAY: &str = "2026-10-03";

/// Eight days of two hours each, as a sync round from the television leaves them.
fn eight_long_evenings(core: &Core, profile_id: &str) {
    core.state_db
        .with(|conn| {
            for day in 1..=8 {
                conn.execute(
                    "INSERT INTO stats_days(profile_id, day, device, seconds, updated_at)
                       VALUES (?1, ?2, 'tv-1', 7200, 1)",
                    rusqlite::params![profile_id, format!("2026-09-{day:02}")],
                )?;
            }
            Ok(())
        })
        .unwrap();
}

/// A catalog that cannot be read is an empty library, not a failure: a
/// watched film it would have named earns nothing, but hours need no
/// catalog and still count.
#[tokio::test]
async fn an_unreadable_catalog_still_counts_the_hours() {
    let dir = tempfile::tempdir().unwrap();
    let current = dir.path().join("catalog").join("current");
    std::fs::create_dir_all(&current).unwrap();
    std::fs::write(current.join("library.db"), b"not a database").unwrap();
    let core = Core::at(dir.path());
    let grown = core.add_profile("Grown", false).id;
    core.clone()
        .set_watched(grown.clone(), "01HEAT".into(), true)
        .await;
    eight_long_evenings(&core, &grown);

    let (titles, collections) = installed_library(&core);
    let answer = core.achievements(grown, TODAY.into(), 120).await;

    assert!(titles.is_empty() && collections.is_empty());
    let earned: Vec<&str> = answer.earned.iter().map(|a| a.id.as_str()).collect();
    assert!(earned.contains(&"hours-10"), "{earned:?}");
    assert!(!earned.contains(&"films-1"), "{earned:?}");
}

#[tokio::test]
async fn an_unreadable_store_has_earned_nothing() {
    let dir = tempfile::tempdir().unwrap();
    std::fs::create_dir(dir.path().join("state.db")).unwrap();
    index_at(dir.path());
    let core = Core::at(dir.path());

    let answer = core.achievements("viewer".into(), TODAY.into(), 120).await;

    assert_eq!(answer, Achievements::default());
}
