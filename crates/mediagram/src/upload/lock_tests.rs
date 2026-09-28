//! The upload lock across processes, with one slot and with several.

use super::*;

#[tokio::test]
async fn a_second_holder_waits_and_is_told_it_is_waiting() {
    let dir = tempfile::tempdir().unwrap();
    let held = acquire(dir.path(), || panic!("nothing else holds it"))
        .await
        .unwrap();
    assert!(is_held(dir.path()));

    // Nobody can take it while it is held, and letting go frees it.
    let file = open(&path_in(dir.path())).unwrap();
    assert!(!try_lock(&file).unwrap());
    drop(held);
    assert!(!is_held(dir.path()));
    assert!(try_lock(&file).unwrap());
}

#[tokio::test]
async fn two_slots_take_two_uploads_and_a_third_waits() {
    let dir = tempfile::tempdir().unwrap();
    let first = acquire_slot(dir.path(), 2, || panic!("slot free"))
        .await
        .unwrap();
    let second = acquire_slot(dir.path(), 2, || panic!("slot free"))
        .await
        .unwrap();
    assert!(all_held(dir.path(), 2));
    // Slot 0 is the historic file, so an older binary shares it.
    assert!(is_held(dir.path()));

    let third = tokio::time::timeout(
        std::time::Duration::from_millis(300),
        acquire_slot(dir.path(), 2, || {}),
    )
    .await;
    assert!(third.is_err(), "a third upload must wait while two run");

    drop(first);
    assert!(!all_held(dir.path(), 2));
    drop(second);
}

#[tokio::test]
async fn one_slot_behaves_as_before() {
    let dir = tempfile::tempdir().unwrap();
    let lock = acquire_slot(dir.path(), 1, || panic!("free"))
        .await
        .unwrap();
    assert!(all_held(dir.path(), 1) && is_held(dir.path()));
    drop(lock);
}

#[tokio::test]
async fn waiting_is_only_reported_when_there_is_a_wait() {
    let dir = tempfile::tempdir().unwrap();
    let mut said = false;
    let lock = acquire(dir.path(), || said = true).await.unwrap();
    assert!(!said);
    drop(lock);
}
