use super::*;

const SHOW: &str = "tmdb-tv-1396";

#[tokio::test]
async fn a_choice_is_kept_for_its_own_profile_until_forgotten() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let andre = core.add_profile("André", false).id;
    let bea = core.add_profile("Bea", false).id;
    let choose = |value: Option<&str>| {
        let value = value.map(str::to_owned);
        core.clone()
            .set_preference(andre.clone(), SHOW.into(), "audio".into(), value)
    };

    assert!(choose(Some("de")).await);

    let held = core.clone().preferences(andre.clone()).await;
    let expected = preferences::PreferenceRow {
        scope: SHOW.into(),
        name: "audio".into(),
        value: "de".into(),
    };
    assert_eq!(held, [expected]);
    assert!(core.clone().preferences(bea).await.is_empty());
    assert!(choose(None).await);
    assert!(core.clone().preferences(andre).await.is_empty());
}

#[tokio::test]
async fn a_choice_with_nowhere_to_be_filed_is_refused() {
    let dir = tempfile::tempdir().unwrap();
    let core = Core::at(dir.path());
    let andre = core.add_profile("André", false).id;
    let set = |scope: &str, name: &str| {
        core.clone()
            .set_preference(andre.clone(), scope.into(), name.into(), Some("de".into()))
    };

    assert!(!set("  ", "audio").await);
    assert!(!set(SHOW, "").await);

    assert!(core.clone().preferences(andre).await.is_empty());
}

#[tokio::test]
async fn an_unreadable_store_remembers_nothing_and_says_so() {
    let dir = tempfile::tempdir().unwrap();
    std::fs::create_dir(dir.path().join("state.db")).unwrap();
    let core = Core::at(dir.path());

    let stored = core
        .clone()
        .set_preference(
            "viewer".into(),
            SHOW.into(),
            "audio".into(),
            Some("de".into()),
        )
        .await;

    assert!(!stored);
    assert!(core.preferences("viewer".into()).await.is_empty());
}
