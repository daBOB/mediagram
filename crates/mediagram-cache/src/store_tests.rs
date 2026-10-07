use tempfile::tempdir;

use super::*;

fn open(budget: u64) -> (tempfile::TempDir, ChunkStore) {
    let dir = tempdir().expect("temp dir");
    let store = ChunkStore::open(dir.path().to_path_buf(), budget).expect("open");
    (dir, store)
}

#[test]
fn put_then_get_round_trips() {
    let (_dir, store) = open(1 << 30);
    let body = b"hello world";
    assert_eq!(
        store.put("set1", 0, body.len() as u64, body).unwrap(),
        PutOutcome::Created
    );
    assert_eq!(store.get("set1", 0).unwrap(), Some(body.to_vec()));
}

#[test]
fn a_second_put_of_the_same_chunk_is_a_no_op_not_an_overwrite() {
    let (_dir, store) = open(1 << 30);
    let total = 11;
    store.put("set1", 0, total, b"hello world").unwrap();
    let outcome = store.put("set1", 0, total, b"different!!").unwrap();
    assert_eq!(outcome, PutOutcome::AlreadyHeld);
    // The first body wins; the second is never written.
    assert_eq!(store.get("set1", 0).unwrap(), Some(b"hello world".to_vec()));
}

#[test]
fn a_put_against_an_already_recorded_different_total_is_rejected() {
    let (_dir, store) = open(1 << 30);
    store
        .put("set1", 0, 100, b"x".repeat(100).as_slice())
        .unwrap();
    let outcome = store
        .put("set1", 1, 999, b"y".repeat(50).as_slice())
        .unwrap();
    assert_eq!(outcome, PutOutcome::TotalMismatch { held: 100 });
}

#[test]
fn a_get_whose_file_vanished_is_404_and_drops_the_index_entry() {
    let (dir, store) = open(1 << 30);
    let body = b"hello world";
    store.put("set1", 0, body.len() as u64, body).unwrap();
    std::fs::remove_file(dir.path().join("set1").join("0")).unwrap();

    assert_eq!(store.get("set1", 0).unwrap(), None);
    // The index entry is gone too: status no longer counts its bytes.
    assert_eq!(store.status().chunks, 0);
    assert_eq!(store.status().held_bytes, 0);
}

#[test]
fn a_crash_mid_write_leaves_no_visible_chunk_and_is_swept_on_open() {
    let dir = tempdir().expect("temp dir");
    let tmp_dir = dir.path().join(".tmp");
    std::fs::create_dir_all(&tmp_dir).unwrap();
    std::fs::write(tmp_dir.join("leftover"), b"half-written").unwrap();

    let store = ChunkStore::open(dir.path().to_path_buf(), 1 << 30).expect("open");
    assert_eq!(store.status().chunks, 0);
    assert!(!tmp_dir.join("leftover").exists());
}

/// A panic anywhere while the index lock is held (a bug, a future
/// regression, an allocation failure) poisons the `Mutex`. The index's own
/// state is simple saturating integer counters, never a pointer or an
/// invariant that a torn mutation could leave unsafe to read, so recovering
/// the guard and carrying on is safe — and correct, since the alternative
/// is every request after the first panic failing forever on the same
/// store, for a reason with nothing to do with that request.
#[test]
fn a_poisoned_index_lock_does_not_wedge_the_store() {
    let (_dir, store) = open(1 << 30);
    store.put("set1", 0, 5, b"hello").unwrap();

    let poisoned = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
        let _guard = store.index.lock().unwrap();
        panic!("simulated panic while the index lock is held");
    }));
    assert!(poisoned.is_err());
    assert!(store.index.is_poisoned());

    // Every operation that takes the lock must still work.
    assert_eq!(store.status().chunks, 1);
    assert_eq!(store.get("set1", 0).unwrap(), Some(b"hello".to_vec()));
    assert_eq!(
        store.put("set2", 0, 10, b"world12345").unwrap(),
        PutOutcome::Created
    );
}

#[test]
fn head_and_get_of_an_unknown_chunk_are_none() {
    let (_dir, store) = open(1 << 30);
    assert_eq!(store.get("set1", 0).unwrap(), None);
    assert_eq!(store.head("set1", 0).unwrap(), None);
}
