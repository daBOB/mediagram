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

#[test]
fn a_game_blob_is_replaced_by_a_newer_write_and_found_again_after_reopening() {
    let dir = tempdir().unwrap();
    let store = BlobStore::open(dir.path().to_path_buf(), 1 << 20).unwrap();
    let list = "preisfrage/articles.json";
    assert_eq!(
        store.put(BlobKind::Game, list, b"[1]").unwrap(),
        BlobPut::Created
    );
    assert_eq!(
        store.put(BlobKind::Game, list, b"[2,2]").unwrap(),
        BlobPut::Replaced
    );
    store
        .put(BlobKind::Game, "preisfrage/photos-B0ABC12345.jpg", &[9; 7])
        .unwrap();
    assert!(
        dir.path()
            .join("blobs/game/preisfrage/articles.json")
            .is_file()
    );
    drop(store);

    let reopened = BlobStore::open(dir.path().to_path_buf(), 1 << 20).unwrap();
    assert_eq!(reopened.status().0, 5 + 7);
    assert_eq!(
        reopened.get(BlobKind::Game, list).unwrap(),
        Some(b"[2,2]".to_vec())
    );
}

#[test]
fn game_blobs_share_the_budget_and_go_oldest_first() {
    let dir = tempdir().unwrap();
    let store = BlobStore::open(dir.path().to_path_buf(), 20).unwrap();
    store
        .put(BlobKind::Game, "preisfrage/a.jpg", &[1; 10])
        .unwrap();
    store.put(BlobKind::Art, "w92/b.jpg", &[2; 10]).unwrap();
    store
        .put(BlobKind::Game, "preisfrage/c.png", &[3; 10])
        .unwrap();

    assert_eq!(
        store.head(BlobKind::Game, "preisfrage/a.jpg").unwrap(),
        None
    );
    assert_eq!(store.head(BlobKind::Art, "w92/b.jpg").unwrap(), Some(10));
    assert_eq!(
        store.head(BlobKind::Game, "preisfrage/c.png").unwrap(),
        Some(10)
    );
}

#[test]
fn game_names_are_a_lowercase_game_and_a_flat_file() {
    assert!(valid_game("preisfrage") && valid_game(&"a".repeat(24)));
    for bad in ["", "Preisfrage", "preis-frage", "..", &"a".repeat(25)] {
        assert!(!valid_game(bad), "{bad:?}");
    }
    assert!(valid_game_file("articles.json") && valid_game_file("photos-B0ABC12345.jpg"));
    assert!(valid_game_file("x_1.png") && valid_game_file(&format!("{}.jpg", "a".repeat(80))));
    for bad in [
        "articles",
        "a.gif",
        "../x.json",
        "a/b.json",
        ".json",
        "a..json",
        &format!("{}.jpg", "a".repeat(81)),
    ] {
        assert!(!valid_game_file(bad), "{bad:?}");
    }
    let store_dir = tempdir().unwrap();
    let store = BlobStore::open(store_dir.path().to_path_buf(), 1 << 20).unwrap();
    assert!(
        store
            .put(BlobKind::Game, "preisfrage/../x.json", b"{}")
            .is_err()
    );
    assert!(store.put(BlobKind::Game, "articles.json", b"{}").is_err());
}
