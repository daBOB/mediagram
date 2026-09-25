use tempfile::tempdir;

use super::*;

fn open(budget: u64) -> (tempfile::TempDir, ChunkStore) {
    let dir = tempdir().expect("temp dir");
    let store = ChunkStore::open(dir.path().to_path_buf(), budget).expect("open");
    (dir, store)
}

#[test]
fn eviction_removes_the_oldest_first_to_stay_within_budget() {
    let chunk = rules::CHUNK;
    // Budget for two chunks; a third put must evict the oldest.
    let (_dir, store) = open(chunk * 2);
    let body = vec![0u8; chunk as usize];

    // Ordering here does not depend on wall-clock resolution: the index
    // breaks mtime ties with a monotonic sequence, so back-to-back calls
    // are ordered correctly even within the same clock tick.
    store.put("set1", 0, chunk * 3, &body).unwrap();
    store.put("set1", 1, chunk * 3, &body).unwrap();
    store.put("set1", 2, chunk * 3, &body).unwrap();

    assert_eq!(store.status().chunks, 2);
    assert_eq!(store.status().held_bytes, chunk * 2);
    assert_eq!(
        store.get("set1", 0).unwrap(),
        None,
        "the oldest was evicted"
    );
    assert!(store.get("set1", 1).unwrap().is_some());
    assert!(store.get("set1", 2).unwrap().is_some());
}

#[test]
fn a_get_refreshes_lru_order_so_it_survives_the_next_eviction() {
    let chunk = rules::CHUNK;
    let (_dir, store) = open(chunk * 2);
    let body = vec![0u8; chunk as usize];

    store.put("set1", 0, chunk * 3, &body).unwrap();
    store.put("set1", 1, chunk * 3, &body).unwrap();

    // Touch chunk 0 so it is no longer the oldest.
    store.get("set1", 0).unwrap();

    store.put("set1", 2, chunk * 3, &body).unwrap();

    // Chunk 1 was the least recently used at the time of the third put.
    assert_eq!(store.get("set1", 1).unwrap(), None, "chunk 1 was evicted");
    assert!(store.get("set1", 0).unwrap().is_some());
    assert!(store.get("set1", 2).unwrap().is_some());
}

#[test]
fn reopening_rebuilds_held_bytes_and_order_from_disk() {
    let chunk = rules::CHUNK;
    let dir = tempdir().expect("temp dir");
    let body = vec![0u8; chunk as usize];
    let total = chunk * 4;
    // The Linux VFS caches "now" for a file's mtime for a few milliseconds
    // rather than reading the clock on every write, so two writes close
    // enough together can land on the same mtime — invisible within one
    // process (the index's sequence number breaks the tie), but a restart
    // has only the mtime to go on. A short sleep between the writes this
    // test needs told apart keeps it a test of ordering, not of that
    // caching's granularity.
    let settle = std::time::Duration::from_millis(20);
    {
        let store = ChunkStore::open(dir.path().to_path_buf(), chunk * 10).expect("open");
        store.put("set1", 0, total, &body).unwrap();
        std::thread::sleep(settle);
        store.put("set1", 1, total, &body).unwrap();
        std::thread::sleep(settle);
        store.put("set1", 2, total, &body).unwrap();
        std::thread::sleep(settle);
        // Touch the oldest chunk last, so it is the most recently used one
        // when this run ends, even though it was the first one written.
        store.get("set1", 0).unwrap();
    }

    let reopened = ChunkStore::open(dir.path().to_path_buf(), chunk * 3).expect("reopen");
    assert_eq!(reopened.status().chunks, 3);
    assert_eq!(reopened.status().held_bytes, chunk * 3);

    // A restart carries no in-memory sequence, only each file's mtime — the
    // scan must have preserved the GET's refresh, or this evicts chunk 0
    // (oldest by write order) instead of chunk 1 (oldest by last use).
    reopened.put("set1", 3, total, &body).unwrap();

    assert_eq!(
        reopened.get("set1", 1).unwrap(),
        None,
        "chunk 1 was the least recently used, restart or not"
    );
    assert!(reopened.get("set1", 0).unwrap().is_some());
    assert!(reopened.get("set1", 2).unwrap().is_some());
    assert!(reopened.get("set1", 3).unwrap().is_some());
}

#[test]
fn head_reports_the_length_without_returning_the_body() {
    let (_dir, store) = open(1 << 30);
    let body = b"hello world";
    store.put("set1", 0, body.len() as u64, body).unwrap();
    assert_eq!(store.head("set1", 0).unwrap(), Some(body.len() as u64));
}

/// Left behind, a stale `total` from before every one of a set's chunks
/// was evicted would 409 a PUT for a set the store, by every other
/// measure, no longer holds anything of.
#[test]
fn evicting_a_sets_last_chunk_clears_its_total_too() {
    let chunk = rules::CHUNK;
    let (dir, store) = open(chunk);
    let body = vec![0u8; chunk as usize];

    store.put("set1", 0, chunk, &body).unwrap();
    assert!(dir.path().join("set1").join("total").exists());

    // A second set's chunk pushes "set1" out entirely (budget for one).
    store.put("set2", 0, chunk, &body).unwrap();

    assert_eq!(store.get("set1", 0).unwrap(), None);
    assert!(
        !dir.path().join("set1").exists(),
        "the emptied set's directory, total included, should be gone"
    );

    // A fresh PUT of "set1" with a different total is accepted as a first
    // write, not rejected as a mismatch against the cleared-out total.
    let outcome = store
        .put("set1", 0, 999, b"x".repeat(999).as_slice())
        .unwrap();
    assert!(!matches!(outcome, PutOutcome::TotalMismatch { .. }));
}

#[test]
fn status_reports_the_configured_budget() {
    let (_dir, store) = open(42);
    assert_eq!(store.status().budget_bytes, 42);
}
