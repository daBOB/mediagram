use std::sync::Arc;

use tempfile::tempdir;

use super::*;

fn key(c: char) -> String {
    c.to_string().repeat(64)
}

#[test]
fn blobs_evict_least_recent_within_their_own_budget() {
    let dir = tempdir().unwrap();
    let store = BlobStore::open(dir.path().to_path_buf(), 20).unwrap();
    store.put(BlobKind::Tmdb, &key('a'), &[1; 10]).unwrap();
    store.put(BlobKind::Tmdb, &key('b'), &[2; 10]).unwrap();
    // Reading `a` makes `b` the least recent.
    store.get(BlobKind::Tmdb, &key('a')).unwrap().unwrap();
    store.put(BlobKind::Art, "w342/c.jpg", &[3; 10]).unwrap();

    assert_eq!(store.head(BlobKind::Tmdb, &key('a')).unwrap(), Some(10));
    assert_eq!(store.head(BlobKind::Tmdb, &key('b')).unwrap(), None);
    assert_eq!(store.head(BlobKind::Art, "w342/c.jpg").unwrap(), Some(10));
    assert_eq!(store.status(), (20, 20));
}

#[test]
fn concurrent_art_puts_leave_one_whole_file() {
    let dir = tempdir().unwrap();
    let store = Arc::new(BlobStore::open(dir.path().to_path_buf(), 1 << 20).unwrap());
    let body = vec![7u8; 100_000];
    let handles: Vec<_> = (0..2)
        .map(|_| {
            let (store, body) = (store.clone(), body.clone());
            std::thread::spawn(move || store.put(BlobKind::Art, "original/x.png", &body))
        })
        .collect();
    let outcomes: Vec<_> = handles
        .into_iter()
        .map(|h| h.join().unwrap().unwrap())
        .collect();

    assert!(outcomes.contains(&BlobPut::Created));
    let file = dir.path().join("blobs/art/original/x.png");
    assert_eq!(std::fs::metadata(file).unwrap().len(), body.len() as u64);
    assert_eq!(store.status().0, body.len() as u64);
}

#[test]
fn scan_at_open_restores_held_bytes() {
    let dir = tempdir().unwrap();
    let store = BlobStore::open(dir.path().to_path_buf(), 1 << 20).unwrap();
    store.put(BlobKind::Tmdb, &key('a'), &[1; 30]).unwrap();
    store.put(BlobKind::Art, "h90/p_q-1.svg", &[2; 12]).unwrap();
    drop(store);

    let reopened = BlobStore::open(dir.path().to_path_buf(), 1 << 20).unwrap();
    assert_eq!(reopened.status().0, 42);
    assert_eq!(
        reopened.get(BlobKind::Art, "h90/p_q-1.svg").unwrap(),
        Some(vec![2; 12])
    );
}

#[test]
fn names_follow_the_published_shapes() {
    assert!(valid_art_size("w92") && valid_art_size("h1234") && valid_art_size("original"));
    assert!(!valid_art_size("w9") && !valid_art_size("w12345") && !valid_art_size("big"));
    assert!(
        valid_art_name("a-b_C9.jpg") && !valid_art_name("a.gif") && !valid_art_name("../x.jpg")
    );
    assert!(!valid_art_name(&format!("{}.jpg", "a".repeat(65))));
    assert!(valid_tmdb_key(&key('f')) && !valid_tmdb_key(&key('F')) && !valid_tmdb_key("ab"));
}

#[test]
fn a_read_blob_outlives_a_later_written_one_after_reopening() {
    let dir = tempdir().unwrap();
    let store = BlobStore::open(dir.path().to_path_buf(), 20).unwrap();
    store.put(BlobKind::Tmdb, &key('a'), &[1; 10]).unwrap();
    std::thread::sleep(std::time::Duration::from_millis(20));
    store.put(BlobKind::Tmdb, &key('b'), &[2; 10]).unwrap();
    std::thread::sleep(std::time::Duration::from_millis(20));
    store.get(BlobKind::Tmdb, &key('a')).unwrap().unwrap();
    drop(store);

    let reopened = BlobStore::open(dir.path().to_path_buf(), 20).unwrap();
    reopened.put(BlobKind::Tmdb, &key('c'), &[3; 10]).unwrap();
    assert_eq!(reopened.head(BlobKind::Tmdb, &key('a')).unwrap(), Some(10));
    assert_eq!(reopened.head(BlobKind::Tmdb, &key('b')).unwrap(), None);
}
