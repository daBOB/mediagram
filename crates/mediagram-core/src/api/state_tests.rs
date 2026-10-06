use super::*;

/// One pick for the household, whoever made it: every profile's snapshot
/// carries the same one, a new pick retires the last, and unpinning — of
/// any title — leaves none.
#[tokio::test]
async fn the_editors_choice_is_one_household_pick_in_every_snapshot() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let andre = core.add_profile("André", false).id;
    let mia = core.add_profile("Mia", true).id;

    core.clone().set_editors_choice("first".into(), true).await;
    core.clone().set_editors_choice("second".into(), true).await;

    assert_eq!(
        core.clone().editors_choice().await.as_deref(),
        Some("second")
    );
    for profile in [andre.clone(), mia] {
        let snapshot = core.clone().snapshot(profile).await;
        assert_eq!(snapshot.editors_choice.as_deref(), Some("second"));
    }
    core.clone().set_editors_choice("first".into(), false).await;
    assert_eq!(core.clone().editors_choice().await, None);
    assert_eq!(core.snapshot(andre).await.editors_choice, None);
}

#[tokio::test]
async fn an_unreadable_store_has_no_editors_choice_and_pinning_one_is_harmless() {
    let dir = tempfile::tempdir().unwrap();
    std::fs::create_dir(dir.path().join("state.db")).unwrap();
    let core = Core::at(dir.path());

    core.clone().set_editors_choice("pick".into(), true).await;

    assert_eq!(core.editors_choice().await, None);
}
