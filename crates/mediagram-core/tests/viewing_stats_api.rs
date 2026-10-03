//! Viewing stats through the surface Kotlin calls: a position write records
//! watch time, finishing a title makes its next play a new viewing, and
//! `stats` reads it all back as one summary. Real clock, so the waits are
//! real too — short, and only ever compared loosely.

use std::sync::Arc;
use std::time::Duration;

use mediagram_core::api::Core;
use mediagram_core::state::stats::summary::HistoryKind;

const TODAY: &str = "2026-10-03";

fn core(dir: &std::path::Path) -> Arc<Core> {
    Core::new(
        dir.display().to_string(),
        1,
        "test-hash".into(),
        "test-device".into(),
    )
}

/// A position write a little after the last, as the player's save tick makes them.
async fn play(core: &Arc<Core>, profile: &str, at: f64) {
    tokio::time::sleep(Duration::from_millis(20)).await;
    core.clone()
        .set_progress(
            profile.into(),
            "01FILM".into(),
            at,
            Some(5400.0),
            TODAY.into(),
        )
        .await;
}

#[tokio::test]
async fn watching_finishing_and_starting_over_read_back_as_history() {
    let dir = tempfile::tempdir().unwrap();
    let core = core(dir.path());
    let viewer = core
        .clone()
        .create_profile("André".into(), false)
        .await
        .unwrap();

    play(&core, &viewer.id, 0.0).await;
    play(&core, &viewer.id, 10.0).await;
    let watched = core.clone().stats(viewer.id.clone(), TODAY.into()).await;
    assert!(
        watched.all_seconds > 0.0 && watched.all_seconds < 10.0,
        "wall time, not position: {}",
        watched.all_seconds
    );

    core.clone()
        .set_watched(viewer.id.clone(), "01FILM".into(), true)
        .await;
    play(&core, &viewer.id, 20.0).await;
    let after = core.clone().stats(viewer.id.clone(), TODAY.into()).await;

    assert_eq!(
        after.all_seconds, watched.all_seconds,
        "finishing forgets the last tick"
    );
    let kinds: Vec<HistoryKind> = after.history.iter().map(|line| line.kind).collect();
    assert_eq!(
        kinds,
        [
            HistoryKind::Again,
            HistoryKind::Finished,
            HistoryKind::Started
        ]
    );
    assert!(
        after
            .history
            .iter()
            .all(|line| line.set_id == "01FILM" && line.seconds == watched.all_seconds)
    );
    assert_eq!(
        (after.last30.len(), after.last30[29].day.as_str()),
        (30, TODAY)
    );
    assert_eq!(after.week_seconds, after.all_seconds);
}

#[tokio::test]
async fn a_profile_with_nothing_watched_reads_as_thirty_empty_days() {
    let dir = tempfile::tempdir().unwrap();
    let summary = core(dir.path()).stats("nobody".into(), TODAY.into()).await;
    assert_eq!((summary.all_seconds, summary.last30.len()), (0.0, 30));
    assert!(summary.history.is_empty());
    assert!(summary.last30.iter().all(|day| day.seconds == 0.0));
}

#[tokio::test]
async fn the_write_after_finishing_counts_nothing_however_long_the_gap() {
    let dir = tempfile::tempdir().unwrap();
    let core = core(dir.path());
    let viewer = core
        .clone()
        .create_profile("André".into(), false)
        .await
        .unwrap();

    play(&core, &viewer.id, 100.0).await;
    core.clone()
        .set_watched(viewer.id.clone(), "01FILM".into(), true)
        .await;
    let finished = core.clone().stats(viewer.id.clone(), TODAY.into()).await;
    // With the tick still remembered this write would count its 150 ms.
    tokio::time::sleep(Duration::from_millis(150)).await;
    play(&core, &viewer.id, 110.0).await;
    let after = core.clone().stats(viewer.id.clone(), TODAY.into()).await;

    assert_eq!(after.all_seconds, finished.all_seconds);
}
