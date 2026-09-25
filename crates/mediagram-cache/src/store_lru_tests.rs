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
    let dir = tempdir().expect("temp dir");
    let body = b"hello world";
    {
        let store = ChunkStore::open(dir.path().to_path_buf(), 1 << 30).expect("open");
        store.put("set1", 0, body.len() as u64, body).unwrap();
    }
    let reopened = ChunkStore::open(dir.path().to_path_buf(), 1 << 30).expect("reopen");
    assert_eq!(reopened.status().chunks, 1);
    assert_eq!(reopened.status().held_bytes, body.len() as u64);
    assert_eq!(reopened.get("set1", 0).unwrap(), Some(body.to_vec()));
}

#[test]
fn head_reports_the_length_without_returning_the_body() {
    let (_dir, store) = open(1 << 30);
    let body = b"hello world";
    store.put("set1", 0, body.len() as u64, body).unwrap();
    assert_eq!(store.head("set1", 0).unwrap(), Some(body.len() as u64));
}

#[test]
fn status_reports_the_configured_budget() {
    let (_dir, store) = open(42);
    assert_eq!(store.status().budget_bytes, 42);
}
