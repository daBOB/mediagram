//! `Core::achievements` end to end: profiles and marks written through the
//! calls Kotlin makes, day rows as a sync import leaves them, a catalog
//! built by hand, and the answer read back across the same boundary.

use std::path::Path;
use std::sync::Arc;

use mediagram_core::api::Core;
use mediagram_core::state::stats::achievements::Achievements;
use rusqlite::{Connection, params};

mod state_seed;

const FILM: &str = "01HEAT";
const TODAY: &str = "2026-10-03";

fn core(dir: &Path) -> Arc<Core> {
    Core::new(
        dir.display().to_string(),
        1,
        "test-hash".into(),
        "test-device".into(),
    )
}

/// `<dir>/catalog/current/library.db` with one playable film, described as Crime and Drama.
fn seed_catalog(dir: &Path) {
    let current = dir.join("catalog").join("current");
    std::fs::create_dir_all(&current).unwrap();
    let conn = Connection::open(current.join("library.db")).unwrap();
    for stmt in mlib_spec::schema::migrations_up_to(mlib_spec::schema::SCHEMA_VERSION) {
        conn.execute(stmt, []).unwrap();
    }
    conn.execute(
        "INSERT INTO sets(set_id, kind, title, tmdb, container, total, part_count, status, created_at, spec_version)
         VALUES (?1, 'movie', 'Heat', 949, 'mkv', 0, 0, 'complete', 0, 1)",
        [FILM],
    )
    .unwrap();
    conn.execute(
        "INSERT INTO shows(source, kind, id, genres) VALUES ('tmdb', 'movie', 949, 'Crime, Drama')",
        [],
    )
    .unwrap();
}

/// Eight days of two hours each from the television, as a sync round leaves them.
fn seed_days(dir: &Path, profile_id: &str) {
    let conn = Connection::open(dir.join("state.db")).unwrap();
    for day in 1..=8 {
        conn.execute(
            "INSERT INTO stats_days(profile_id, day, device, seconds, updated_at) VALUES (?1, ?2, 'tv-1', 7200, 1)",
            params![profile_id, format!("2026-09-{day:02}")],
        )
        .unwrap();
    }
}

fn ids(answer: &Achievements) -> Vec<&str> {
    answer
        .earned
        .iter()
        .map(|a| a.id.as_str())
        .chain(answer.next.iter().map(|a| a.id.as_str()))
        .collect()
}

#[tokio::test]
async fn a_kids_profile_earns_the_film_but_never_hours_streaks_or_binges() {
    let dir = tempfile::tempdir().unwrap();
    seed_catalog(dir.path());
    let core = core(dir.path());
    let made = state_seed::household(&core, dir.path(), &["Grown"], &["Kid"]).await;
    let (grown, kid) = (made[0].clone(), made[1].clone());
    for profile in [&kid, &grown] {
        core.clone()
            .set_watched(profile.id.clone(), FILM.into(), true)
            .await;
        seed_days(dir.path(), &profile.id);
    }

    let kids = core
        .clone()
        .achievements(kid.id.clone(), TODAY.into(), 120)
        .await;
    assert!(ids(&kids).contains(&"films-1"), "{kids:?}");
    assert!(
        !ids(&kids).iter().any(|id| ["hours-", "streak-", "binge-"]
            .iter()
            .any(|p| id.starts_with(p))),
        "{kids:?}"
    );

    let grown = core
        .clone()
        .achievements(grown.id.clone(), TODAY.into(), 120)
        .await;
    for id in ["films-1", "hours-10", "streak-7"] {
        assert!(grown.earned.iter().any(|a| a.id == id), "{id} in {grown:?}");
    }
    // Two genres from the catalog's own row for the film.
    assert!(
        grown.next.iter().any(|a| a.id == "genres-5" && a.have == 2),
        "{grown:?}"
    );
}

#[tokio::test]
async fn a_profile_this_device_does_not_hold_has_earned_nothing() {
    let dir = tempfile::tempdir().unwrap();
    let answer = core(dir.path())
        .achievements("nobody".into(), TODAY.into(), 120)
        .await;
    assert_eq!(answer, Achievements::default());
}

#[tokio::test]
async fn with_no_catalog_installed_the_hours_still_count() {
    let dir = tempfile::tempdir().unwrap();
    let core = core(dir.path());
    let grown = state_seed::household(&core, dir.path(), &["Grown"], &[]).await.remove(0);
    seed_days(dir.path(), &grown.id);
    let answer = core
        .clone()
        .achievements(grown.id.clone(), TODAY.into(), 120)
        .await;
    assert!(
        answer.earned.iter().any(|a| a.id == "hours-10"),
        "{answer:?}"
    );
}

/// A finish stamp a sync round saturated to the largest i64 must not panic the
/// date arithmetic — an episode's finish is read as a local day — and the rest
/// of the answer must still come back.
#[tokio::test]
async fn a_corrupt_synced_finish_stamp_cannot_overflow_the_achievement_dates() {
    let dir = tempfile::tempdir().unwrap();
    seed_catalog(dir.path());
    let core = core(dir.path());
    let grown = state_seed::household(&core, dir.path(), &["Grown"], &[]).await.remove(0);
    Connection::open(dir.path().join("catalog").join("current").join("library.db"))
        .unwrap()
        .execute(
            "INSERT INTO sets(set_id, kind, title, show, container, total, part_count, status, created_at, spec_version)
             VALUES ('01EPISODE', 'ep', 'Pilot', 'Wire', 'mkv', 0, 0, 'complete', 0, 1)",
            [],
        )
        .unwrap();
    for set in [FILM, "01EPISODE"] {
        core.clone()
            .set_watched(grown.id.clone(), set.into(), true)
            .await;
    }
    seed_days(dir.path(), &grown.id);
    Connection::open(dir.path().join("state.db"))
        .unwrap()
        .execute("UPDATE watched SET finished_at = ?1", params![i64::MAX])
        .unwrap();
    let answer = core
        .clone()
        .achievements(grown.id.clone(), TODAY.into(), 120)
        .await;
    assert!(
        answer.earned.iter().any(|a| a.id == "films-1"),
        "{answer:?}"
    );
    assert!(
        answer.earned.iter().any(|a| a.id == "hours-10"),
        "{answer:?}"
    );
}
