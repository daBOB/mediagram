//! The narrow surface Kotlin calls through UniFFI. No network calls here:
//! these exercise only what a caller can observe without ever reaching
//! Telegram — a fresh install's authorization state, and a lookup against a
//! catalog that has never been refreshed.

#[test]
fn a_fresh_core_is_not_authorized() {
    let dir = tempfile::tempdir().unwrap();
    let core = mediagram_core::api::Core::new(dir.path().display().to_string());
    assert!(!core.is_authorized());
}

#[test]
fn reading_an_unknown_set_is_not_found() {
    let dir = tempfile::tempdir().unwrap();
    let core = mediagram_core::api::Core::new(dir.path().display().to_string());
    let err = core.total_size("nosuchset".into()).unwrap_err();
    assert!(matches!(err, mediagram_core::api::CoreError::NotFound(_)));
}

#[test]
fn listing_sets_before_any_refresh_is_empty_not_an_error() {
    let dir = tempfile::tempdir().unwrap();
    let core = mediagram_core::api::Core::new(dir.path().display().to_string());
    assert_eq!(core.list_sets().unwrap(), Vec::new());
}

#[test]
fn a_poster_key_of_the_wrong_shape_is_never_looked_up() {
    let dir = tempfile::tempdir().unwrap();
    let core = mediagram_core::api::Core::new(dir.path().display().to_string());
    assert_eq!(core.poster_path("../escape".into()), None);
}
