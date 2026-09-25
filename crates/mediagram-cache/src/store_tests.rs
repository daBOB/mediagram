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
fn concurrent_puts_of_the_same_key_never_interleave() {
    let dir = tempdir().expect("temp dir");
    let store =
        std::sync::Arc::new(ChunkStore::open(dir.path().to_path_buf(), 1 << 30).expect("open"));
    let total = rules::CHUNK;
    let a = vec![b'a'; total as usize];
    let b = vec![b'b'; total as usize];

    let store_a = store.clone();
    let a_body = a.clone();
    let t1 = std::thread::spawn(move || store_a.put("set1", 0, total, &a_body));
    let store_b = store.clone();
    let b_body = b.clone();
    let t2 = std::thread::spawn(move || store_b.put("set1", 0, total, &b_body));

    let r1 = t1.join().unwrap().unwrap();
    let r2 = t2.join().unwrap().unwrap();

    // One wins (Created), the other is told it already exists.
    let outcomes = [r1, r2];
    assert_eq!(
        outcomes
            .iter()
            .filter(|o| **o == PutOutcome::Created)
            .count(),
        1
    );
    assert_eq!(
        outcomes
            .iter()
            .filter(|o| **o == PutOutcome::AlreadyHeld)
            .count(),
        1
    );

    // Whichever won, the stored body is intact: entirely `a` or entirely
    // `b`, never a mix of the two.
    let stored = store.get("set1", 0).unwrap().unwrap();
    assert!(stored == a || stored == b);
}

#[test]
fn concurrent_first_puts_with_different_totals_give_one_created_and_one_mismatch() {
    let dir = tempdir().expect("temp dir");
    let store =
        std::sync::Arc::new(ChunkStore::open(dir.path().to_path_buf(), 1 << 30).expect("open"));

    let store_a = store.clone();
    let t1 = std::thread::spawn(move || store_a.put("set1", 0, 100, b"x".repeat(100).as_slice()));
    let store_b = store.clone();
    let t2 = std::thread::spawn(move || store_b.put("set1", 0, 200, b"y".repeat(100).as_slice()));

    let r1 = t1.join().unwrap().unwrap();
    let r2 = t2.join().unwrap().unwrap();
    let outcomes = [r1, r2];

    assert_eq!(
        outcomes
            .iter()
            .filter(|o| **o == PutOutcome::Created)
            .count(),
        1
    );
    assert_eq!(
        outcomes
            .iter()
            .filter(|o| matches!(o, PutOutcome::TotalMismatch { .. }))
            .count(),
        1
    );
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

#[test]
fn head_and_get_of_an_unknown_chunk_are_none() {
    let (_dir, store) = open(1 << 30);
    assert_eq!(store.get("set1", 0).unwrap(), None);
    assert_eq!(store.head("set1", 0).unwrap(), None);
}
