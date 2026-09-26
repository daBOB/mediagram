//! The narrow surface Kotlin calls through UniFFI. No network calls here:
//! these exercise only what a caller can observe without ever reaching
//! Telegram — a fresh install's authorization state, and lookups against a
//! catalog that either has never been refreshed or was built by hand.

use std::path::Path;

use rusqlite::Connection;

fn core(dir: &Path) -> std::sync::Arc<mediagram_core::api::Core> {
    mediagram_core::api::Core::new(
        dir.display().to_string(),
        1,
        "test-hash".into(),
        "test-device".into(),
    )
}

/// Builds `<dir>/catalog/current/library.db` directly — bypassing
/// `refresh_catalog`, which needs the network — with one set inserted, so a
/// lookup for a *different* id exercises "found the catalog, did not find
/// this set" rather than "found no catalog at all".
fn seed_one_set(dir: &Path, set_id: &str) {
    let current = dir.join("catalog").join("current");
    std::fs::create_dir_all(&current).unwrap();
    let conn = Connection::open(current.join("library.db")).unwrap();
    for stmt in mlib_spec::schema::migrations_up_to(mlib_spec::schema::SCHEMA_VERSION) {
        conn.execute(stmt, []).unwrap();
    }
    conn.execute(
        "INSERT INTO sets(set_id, kind, container, total, part_count, status, created_at, spec_version)
         VALUES (?1, 'movie', 'mkv', 0, 0, 'complete', 0, 1)",
        [set_id],
    )
    .unwrap();
}

#[test]
fn a_fresh_core_is_not_authorized() {
    let dir = tempfile::tempdir().unwrap();
    assert!(!core(dir.path()).is_authorized());
}

#[tokio::test]
async fn reading_an_unknown_set_with_no_catalog_at_all_is_not_found() {
    let dir = tempfile::tempdir().unwrap();
    let err = core(dir.path())
        .total_size("nosuchset".into())
        .await
        .unwrap_err();
    assert!(matches!(err, mediagram_core::api::CoreError::NotFound(_)));
}

/// The case the earlier version of this test claimed to cover but never
/// reached: a catalog exists, and the set asked for still is not in it.
#[tokio::test]
async fn reading_a_set_that_is_not_in_an_existing_catalog_is_not_found() {
    let dir = tempfile::tempdir().unwrap();
    seed_one_set(dir.path(), "01SETHELD00000000000000001");

    let err = core(dir.path())
        .total_size("01NOSUCHSET0000000000000001".into())
        .await
        .unwrap_err();
    assert!(matches!(err, mediagram_core::api::CoreError::NotFound(_)));
    // And the one that *is* there is found, so "not found" really is about
    // this id and not a broken catalog.
    assert_eq!(
        core(dir.path())
            .total_size("01SETHELD00000000000000001".into())
            .await
            .unwrap(),
        0
    );
}

#[tokio::test]
async fn listing_sets_before_any_refresh_is_empty_not_an_error() {
    let dir = tempfile::tempdir().unwrap();
    assert_eq!(core(dir.path()).list_sets().await.unwrap(), Vec::new());
}

/// Before any refresh there is no `credits`/`franchises` table to even open
/// — every departments lookup answers "nothing" rather than an error, the
/// same rule an unrefreshed `list_sets` is held to above.
#[tokio::test]
async fn the_departments_surface_is_empty_not_an_error_before_any_refresh() {
    let dir = tempfile::tempdir().unwrap();
    let player = core(dir.path());
    assert_eq!(
        player.clone().title_credits("tmdb-movie-550".into()).await,
        mediagram_core::dto::TitleCreditsRecord::default()
    );
    assert_eq!(player.clone().person(1).await, None);
    assert!(player.clone().franchises().await.is_empty());
    assert!(player.clone().search_people("anna".into()).await.is_empty());
    assert_eq!(player.fetch_portrait(1).await, None);
}

#[test]
fn a_poster_key_of_the_wrong_shape_is_never_looked_up() {
    let dir = tempfile::tempdir().unwrap();
    assert_eq!(core(dir.path()).poster_path("../escape".into()), None);
}

#[tokio::test]
async fn watch_state_is_profile_scoped_except_kids_and_survives_reopening() {
    let dir = tempfile::tempdir().unwrap();
    let player = core(dir.path());
    assert!(player.clone().profiles().await.is_empty());
    assert_eq!(player.clone().chosen_profile().await, None);
    let andre = player
        .clone()
        .create_profile("André".into(), false)
        .await
        .unwrap();
    let bea = player
        .clone()
        .create_profile("Bea".into(), false)
        .await
        .unwrap();
    assert!(player.clone().choose_profile(andre.id.clone()).await);
    assert!(!player.clone().choose_profile("missing".into()).await);

    player
        .clone()
        .set_progress(andre.id.clone(), "shared".into(), 120.0, Some(1000.0))
        .await;
    player
        .clone()
        .set_progress(bea.id.clone(), "shared".into(), 300.0, None)
        .await;
    player
        .clone()
        .set_progress(andre.id.clone(), "finished".into(), 90.0, Some(100.0))
        .await;
    player
        .clone()
        .set_progress(bea.id.clone(), "finished".into(), 7.0, Some(100.0))
        .await;
    player
        .clone()
        .set_watched(andre.id.clone(), "finished".into(), true)
        .await;
    player
        .clone()
        .set_watchlisted(andre.id.clone(), "later-a".into(), true)
        .await;
    player
        .clone()
        .set_watchlisted(bea.id.clone(), "later-b".into(), true)
        .await;
    player.clone().set_kids("family".into(), true).await;
    let collection = player
        .clone()
        .create_collection(andre.id.clone(), "Weekend".into())
        .await
        .unwrap();
    for set in ["second", "first"] {
        assert!(
            player
                .clone()
                .set_in_collection(andre.id.clone(), collection.id.clone(), set.into(), true)
                .await
        );
    }

    let first = player.clone().snapshot(andre.id.clone()).await;
    let second = player.clone().snapshot(bea.id.clone()).await;
    assert_eq!(
        first.progress.len(),
        1,
        "finishing clears only this viewer's resume point"
    );
    assert_eq!(first.progress[0].set_id, "shared");
    assert_eq!(first.progress[0].at, 120.0);
    assert_eq!(first.progress[0].duration, Some(1000.0));
    assert_eq!(first.watched.len(), 1);
    assert_eq!(first.watched[0].set_id, "finished");
    assert_eq!(first.watchlist, ["later-a"]);
    assert_eq!(first.kids, ["family"]);
    assert_eq!(first.collections.len(), 1);
    assert_eq!(first.collections[0].id, collection.id);
    assert_eq!(first.collections[0].items, ["second", "first"]);
    assert_eq!(second.progress.len(), 2);
    let other_position = second
        .progress
        .iter()
        .find(|row| row.set_id == "shared")
        .unwrap();
    assert_eq!((other_position.at, other_position.duration), (300.0, None));
    assert!(second.watched.is_empty());
    assert_eq!(second.watchlist, ["later-b"]);
    assert_eq!(second.kids, first.kids);
    assert!(second.collections.is_empty());
    assert_eq!(
        player.clone().chosen_profile().await,
        Some(andre.id.clone())
    );
    drop(player);

    let reopened = core(dir.path());
    let profiles = reopened.clone().profiles().await;
    assert_eq!(profiles.len(), 2);
    assert!(profiles.contains(&andre) && profiles.contains(&bea));
    assert_eq!(
        reopened.clone().chosen_profile().await,
        Some(andre.id.clone())
    );
    assert_eq!(reopened.clone().snapshot(andre.id.clone()).await, first);
    assert_eq!(reopened.clone().snapshot(bea.id.clone()).await, second);
    reopened
        .clone()
        .clear_progress(andre.id.clone(), "shared".into())
        .await;
    reopened
        .clone()
        .set_watched(andre.id.clone(), "finished".into(), false)
        .await;
    reopened
        .clone()
        .set_watchlisted(andre.id.clone(), "later-a".into(), false)
        .await;
    reopened.clone().set_kids("family".into(), false).await;
    let cleared = reopened.clone().snapshot(andre.id).await;
    assert!(
        cleared.progress.is_empty() && cleared.watched.is_empty() && cleared.watchlist.is_empty()
    );
    let other = reopened.snapshot(bea.id).await;
    assert_eq!(other.progress, second.progress);
    assert_eq!(other.watchlist, second.watchlist);
    assert!(cleared.kids.is_empty() && other.kids.is_empty());
}

#[tokio::test]
async fn a_collection_can_only_be_changed_by_its_owner() {
    let dir = tempfile::tempdir().unwrap();
    let player = core(dir.path());
    let owner = player
        .clone()
        .create_profile("Owner".into(), false)
        .await
        .unwrap()
        .id;
    let other = player
        .clone()
        .create_profile("Other".into(), false)
        .await
        .unwrap()
        .id;
    let collection = player
        .clone()
        .create_collection(owner.clone(), "Original".into())
        .await
        .unwrap();
    assert!(
        player
            .clone()
            .set_in_collection(owner.clone(), collection.id.clone(), "held".into(), true)
            .await
    );
    let before = player.clone().snapshot(owner.clone()).await;

    assert!(
        !player
            .clone()
            .rename_collection(other.clone(), collection.id.clone(), "Stolen".into())
            .await
    );
    assert!(
        !player
            .clone()
            .delete_collection(other.clone(), collection.id.clone())
            .await
    );
    for included in [false, true] {
        assert!(
            !player
                .clone()
                .set_in_collection(
                    other.clone(),
                    collection.id.clone(),
                    "held".into(),
                    included
                )
                .await
        );
    }
    assert_eq!(player.clone().snapshot(owner.clone()).await, before);
    assert!(player.clone().snapshot(other).await.collections.is_empty());

    assert!(
        player
            .clone()
            .rename_collection(owner.clone(), collection.id.clone(), "Renamed".into())
            .await
    );
    assert!(
        player
            .clone()
            .set_in_collection(owner.clone(), collection.id.clone(), "held".into(), false)
            .await
    );
    let renamed = player.clone().snapshot(owner.clone()).await;
    assert_eq!(renamed.collections[0].name, "Renamed");
    assert!(renamed.collections[0].items.is_empty());
    assert!(
        player
            .clone()
            .delete_collection(owner.clone(), collection.id.clone())
            .await
    );
    assert!(
        !player
            .clone()
            .set_in_collection(owner.clone(), collection.id, "new".into(), true)
            .await
    );
    drop(player);
    assert!(
        core(dir.path())
            .snapshot(owner)
            .await
            .collections
            .is_empty()
    );
}

#[tokio::test]
async fn unavailable_state_storage_returns_safe_defaults_and_can_be_retried() {
    let dir = tempfile::tempdir().unwrap();
    let obstruction = dir.path().join("state.db");
    std::fs::create_dir(&obstruction).unwrap();
    let player = core(dir.path());
    assert!(player.clone().profiles().await.is_empty());
    assert_eq!(player.clone().chosen_profile().await, None);
    assert_eq!(
        player.clone().create_profile("Viewer".into(), false).await,
        None
    );
    assert!(!player.clone().choose_profile("viewer".into()).await);
    assert_eq!(
        player.clone().snapshot("viewer".into()).await,
        Default::default()
    );
    assert_eq!(
        player
            .clone()
            .create_collection("viewer".into(), "Weekend".into())
            .await,
        None
    );
    assert!(
        !player
            .clone()
            .rename_collection("viewer".into(), "list".into(), "New".into())
            .await
    );
    assert!(
        !player
            .clone()
            .delete_collection("viewer".into(), "list".into())
            .await
    );
    assert!(
        !player
            .clone()
            .set_in_collection("viewer".into(), "list".into(), "set".into(), true)
            .await
    );
    player
        .clone()
        .set_progress("viewer".into(), "set".into(), 10.0, None)
        .await;
    player
        .clone()
        .clear_progress("viewer".into(), "set".into())
        .await;
    player
        .clone()
        .set_watched("viewer".into(), "set".into(), true)
        .await;
    player
        .clone()
        .set_watchlisted("viewer".into(), "set".into(), true)
        .await;
    player.clone().set_kids("set".into(), true).await;
    assert_eq!(
        player.clone().snapshot("viewer".into()).await,
        Default::default()
    );

    std::fs::remove_dir(obstruction).unwrap();
    let viewer = player
        .clone()
        .create_profile("Viewer".into(), false)
        .await
        .unwrap();
    assert_eq!(
        player.clone().profiles().await,
        std::slice::from_ref(&viewer)
    );
    assert_eq!(player.snapshot(viewer.id).await, Default::default());
}
