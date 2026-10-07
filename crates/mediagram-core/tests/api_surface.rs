//! The narrow surface Kotlin calls through UniFFI. No network calls here:
//! these exercise only what a caller can observe without ever reaching
//! Telegram — a fresh install's authorization state, and lookups against a
//! catalog that either has never been refreshed or was built by hand.

use std::path::Path;

use mediagram_core::state::profiles::ProfileOutcome::{
    Done, Invalid, NameTaken, NotAllowed, NotFound, NotSynced, Wait, WrongPin,
};
use rusqlite::Connection;

mod state_seed;

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

/// A catalog exists, and the set asked for is not in it: `NotFound` for that
/// id, while a set the catalog does hold is still read.
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

/// The player's own way to resolve a saved id: one row, not the whole
/// catalog searched afterwards for it.
#[tokio::test]
async fn media_set_finds_the_row_a_saved_id_names() {
    let dir = tempfile::tempdir().unwrap();
    seed_one_set(dir.path(), "01SETHELD00000000000000001");

    let found = core(dir.path())
        .media_set("01SETHELD00000000000000001".into())
        .await
        .unwrap();
    assert_eq!(found.unwrap().set_id, "01SETHELD00000000000000001");
}

#[tokio::test]
async fn media_set_answers_none_for_an_id_the_catalog_does_not_hold() {
    let dir = tempfile::tempdir().unwrap();
    seed_one_set(dir.path(), "01SETHELD00000000000000001");

    assert_eq!(
        core(dir.path()).media_set("nosuchset".into()).await.unwrap(),
        None
    );
}

/// The same "nothing installed is an empty answer, not a failure" rule
/// `listing_sets_before_any_refresh_is_empty_not_an_error` is held to above.
#[tokio::test]
async fn media_set_answers_none_before_any_refresh() {
    let dir = tempfile::tempdir().unwrap();
    assert_eq!(
        core(dir.path()).media_set("anything".into()).await.unwrap(),
        None
    );
}

#[tokio::test]
async fn watch_state_is_profile_scoped_except_kids_and_survives_reopening() {
    let dir = tempfile::tempdir().unwrap();
    let player = core(dir.path());
    assert!(player.clone().profiles().await.is_empty());
    assert_eq!(player.clone().chosen_profile().await, None);
    let made = state_seed::household(&player, dir.path(), &["André", "Bea"], &[]).await;
    let (andre, bea) = (made[0].clone(), made[1].clone());
    assert!(player.clone().choose_profile(andre.id.clone()).await);
    assert!(!player.clone().choose_profile("missing".into()).await);

    player
        .clone()
        .set_progress(
            andre.id.clone(),
            "shared".into(),
            120.0,
            Some(1000.0),
            "2026-10-03".into(),
        )
        .await;
    player
        .clone()
        .set_progress(
            bea.id.clone(),
            "shared".into(),
            300.0,
            None,
            "2026-10-03".into(),
        )
        .await;
    player
        .clone()
        .set_progress(
            andre.id.clone(),
            "finished".into(),
            90.0,
            Some(100.0),
            "2026-10-03".into(),
        )
        .await;
    player
        .clone()
        .set_progress(
            bea.id.clone(),
            "finished".into(),
            7.0,
            Some(100.0),
            "2026-10-03".into(),
        )
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
    player.clone().set_kids("family".into(), Some(12)).await;
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
    reopened.clone().set_kids("family".into(), None).await;
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
    let made = state_seed::household(&player, dir.path(), &["Owner", "Other"], &[]).await;
    let (owner, other) = (made[0].id.clone(), made[1].id.clone());
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
    // Every profile call answers `Invalid`, the outcome that means nothing
    // happened, rather than throwing.
    let p = || player.clone();
    let pin = || String::from("1234");
    let id = || String::from("viewer");
    assert_eq!(p().create_first_admin("Viewer".into(), pin()).await, Invalid);
    assert_eq!(p().create_grown_up(id(), pin(), "Bea".into(), pin()).await, Invalid);
    assert_eq!(p().create_kid(id(), pin(), "Mia".into(), 6).await, Invalid);
    assert_eq!(p().delete_profile(id(), pin(), id()).await, Invalid);
    assert_eq!(p().unlock_profile(id(), pin()).await, Invalid);
    assert_eq!(p().claim_admin(id(), pin()).await, Invalid);
    assert_eq!(p().set_pin(id(), pin(), id(), pin()).await, Invalid);
    assert_eq!(p().set_kids_age(id(), pin(), id(), 6).await, Invalid);
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
        .set_progress(
            "viewer".into(),
            "set".into(),
            10.0,
            None,
            "2026-10-03".into(),
        )
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
    player.clone().set_kids("set".into(), Some(12)).await;
    assert_eq!(
        player.clone().snapshot("viewer".into()).await,
        Default::default()
    );

    std::fs::remove_dir(obstruction).unwrap();
    let viewer = state_seed::household(&player, dir.path(), &["Viewer"], &[]).await.remove(0);
    assert_eq!(
        player.clone().profiles().await,
        std::slice::from_ref(&viewer)
    );
    assert_eq!(player.snapshot(viewer.id).await, Default::default());
}

async fn named(
    player: &std::sync::Arc<mediagram_core::api::Core>,
    name: &str,
) -> mediagram_core::state::profiles::Profile {
    let listed = player.clone().profiles().await;
    listed.into_iter().find(|p| p.name == name).unwrap()
}

#[tokio::test]
async fn managing_profiles_answers_one_outcome_per_reason_across_the_boundary() {
    let dir = tempfile::tempdir().unwrap();
    let player = core(dir.path());
    let p = || player.clone();

    // Not one sync round taken in yet: this player has not heard who the
    // household already is.
    assert!(!p().has_synced_once().await);
    assert_eq!(p().create_first_admin("André".into(), "1234".into()).await, NotSynced);
    state_seed::first_round_found_nobody(&player, dir.path()).await;
    assert!(p().has_synced_once().await);

    assert_eq!(p().create_first_admin("  ".into(), "1234".into()).await, Invalid);
    assert_eq!(p().create_first_admin("André".into(), "1234".into()).await, Done);
    assert_eq!(p().create_first_admin("Bea".into(), "1111".into()).await, NotAllowed);
    let andre = named(&player, "André").await;
    assert!(andre.admin && andre.has_pin && !andre.kids);
    assert_eq!((andre.kids_age, andre.parent_id.as_deref()), (None, None));
    let andre = andre.id;
    let pin = || String::from("1234");

    assert_eq!(p().claim_admin(andre.clone(), "0000".into()).await, NotAllowed);
    assert_eq!(p().create_kid(andre.clone(), pin(), "Mia".into(), 7).await, Invalid);
    assert_eq!(p().create_kid(andre.clone(), pin(), " andré".into(), 6).await, NameTaken);
    assert_eq!(p().create_kid(andre.clone(), "0000".into(), "Mia".into(), 6).await, WrongPin);
    assert_eq!(p().create_kid(andre.clone(), pin(), "Mia".into(), 6).await, Done);
    assert_eq!(p().delete_profile(andre.clone(), pin(), andre.clone()).await, NotAllowed);
    assert_eq!(p().delete_profile(andre.clone(), pin(), "nobody".into()).await, NotFound);

    let mia = named(&player, "Mia").await;
    assert_eq!(
        (mia.kids, mia.kids_age, mia.parent_id.as_deref()),
        (true, Some(6), Some(andre.as_str()))
    );
    assert!(!mia.admin && !mia.has_pin);
    assert_eq!(p().create_kid(mia.id.clone(), String::new(), "Ben".into(), 6).await, NotAllowed);
    assert_eq!(p().set_kids_age(andre.clone(), pin(), mia.id.clone(), 12).await, Done);
    assert_eq!(named(&player, "Mia").await.kids_age, Some(12));
    assert_eq!(p().unlock_profile(mia.id.clone(), String::new()).await, Done);

    let bea = ("Bea".to_string(), "1111".to_string());
    assert_eq!(p().create_grown_up(andre.clone(), pin(), bea.0, bea.1).await, Done);
    let bea = named(&player, "Bea").await;
    assert!(bea.has_pin && !bea.admin && !bea.kids);
    assert_eq!(p().set_pin(andre.clone(), pin(), bea.id.clone(), "2222".into()).await, Done);
    assert_eq!(p().unlock_profile(bea.id.clone(), "1111".into()).await, WrongPin);
    assert_eq!(p().unlock_profile(bea.id.clone(), "2222".into()).await, Done);
    assert_eq!(p().create_kid(bea.id.clone(), "2222".into(), "Leo".into(), 12).await, Done);

    // A grown-up leaves with the kids it is the parent of; the admin's stay.
    assert_eq!(p().delete_profile(andre.clone(), pin(), bea.id).await, Done);
    let names: Vec<String> = p().profiles().await.into_iter().map(|x| x.name).collect();
    assert_eq!(names, ["André", "Mia"]);
}

#[tokio::test]
async fn five_wrong_pins_make_that_profile_wait_across_calls() {
    let dir = tempfile::tempdir().unwrap();
    let player = core(dir.path());
    let made = state_seed::household(&player, dir.path(), &["André", "Bea"], &[]).await;
    let (andre, bea) = (made[0].id.clone(), made[1].id.clone());
    for _ in 0..5 {
        assert_eq!(player.clone().unlock_profile(andre.clone(), "0000".into()).await, WrongPin);
    }
    let Wait { seconds } = player.clone().unlock_profile(andre, state_seed::PIN.into()).await else {
        panic!("the right PIN still waits");
    };
    assert!((59..=60).contains(&seconds), "{seconds}");
    let bea_opens = player.unlock_profile(bea, state_seed::PIN.into()).await;
    assert_eq!(bea_opens, Done, "another profile's PIN is still compared");
}

/// A grown-up that another device later calls a kid keeps its claim and PIN
/// in their columns; the profile the surface hands out never says a kid is
/// the admin or has a PIN, since a kid opens freely.
#[tokio::test]
async fn a_kid_is_never_said_to_be_the_admin_or_to_hold_a_pin() {
    let dir = tempfile::tempdir().unwrap();
    let player = core(dir.path());
    state_seed::household(&player, dir.path(), &["André"], &[]).await;
    Connection::open(dir.path().join("state.db"))
        .unwrap()
        .execute("UPDATE profiles SET kids = 1", [])
        .unwrap();
    let andre = named(&player, "André").await;
    assert_eq!(
        (andre.kids, andre.admin, andre.has_pin, andre.kids_age),
        (true, false, false, Some(12))
    );
}

#[tokio::test]
async fn a_kids_mark_says_from_six_or_from_twelve_in_the_snapshot() {
    let dir = tempfile::tempdir().unwrap();
    let player = core(dir.path());
    let viewer = state_seed::household(&player, dir.path(), &["Viewer"], &[]).await.remove(0).id;
    player.clone().set_kids("six".into(), Some(6)).await;
    player.clone().set_kids("twelve".into(), Some(12)).await;
    let snapshot = player.clone().snapshot(viewer.clone()).await;
    let mut kids = snapshot.kids.clone();
    kids.sort();
    assert_eq!(kids, ["six", "twelve"]);
    assert_eq!(snapshot.kids_from_six, ["six"]);
    player.clone().set_kids("six".into(), Some(12)).await;
    assert!(player.clone().snapshot(viewer.clone()).await.kids_from_six.is_empty());
    player.clone().set_kids("six".into(), None).await;
    assert_eq!(player.snapshot(viewer).await.kids, ["twelve"]);
}
