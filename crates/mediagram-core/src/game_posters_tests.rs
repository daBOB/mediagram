use mediagram_tmdb::tmdb_client::TmdbApi;

use super::*;

/// Fails the test if TMDB is asked anything.
struct NoRequests;

impl TmdbApi for NoRequests {
    async fn get_json(&self, path: &str, _: &[(&str, String)]) -> anyhow::Result<serde_json::Value> {
        panic!("unexpected request to {path}");
    }
}

#[tokio::test]
async fn cached_textless_poster_needs_no_request() {
    let dir = tempfile::tempdir().unwrap();
    let file = dir.path().join("tmdb-movie-550.jpg");
    std::fs::write(&file, b"jpg").unwrap();
    let http = crate::http::client().unwrap();
    assert_eq!(textless_poster(&NoRequests, &http, dir.path(), "tmdb-movie-550").await, Some(file));
}

#[tokio::test]
async fn a_known_miss_is_not_asked_again() {
    let dir = tempfile::tempdir().unwrap();
    std::fs::write(dir.path().join("tmdb-tv-4608.none"), b"").unwrap();
    let http = crate::http::client().unwrap();
    assert_eq!(textless_poster(&NoRequests, &http, dir.path(), "tmdb-tv-4608").await, None);
}

#[tokio::test]
async fn a_key_without_a_provider_title_makes_no_request() {
    let dir = tempfile::tempdir().unwrap();
    let http = crate::http::client().unwrap();
    assert_eq!(textless_poster(&NoRequests, &http, dir.path(), "local-poster").await, None);
}

#[tokio::test]
async fn concurrent_lookups_for_one_title_fetch_once() {
    use std::sync::atomic::{AtomicUsize, Ordering};

    let dir = tempfile::tempdir().unwrap();
    let fetches = Arc::new(AtomicUsize::new(0));
    // Behaves like `textless_poster`: cache first, then a slow fetch.
    let fake: PosterLookup = {
        let (dir, fetches) = (dir.path().to_path_buf(), fetches.clone());
        Arc::new(move |key| {
            let (dir, fetches) = (dir.clone(), fetches.clone());
            Box::pin(async move {
                let file = dir.join(format!("{key}.jpg"));
                if !file.exists() {
                    fetches.fetch_add(1, Ordering::SeqCst);
                    tokio::time::sleep(std::time::Duration::from_millis(50)).await;
                    std::fs::write(&file, b"jpg").unwrap();
                }
                Some(file)
            })
        })
    };
    let lookup = one_at_a_time(fake);
    let (a, b) = tokio::join!(lookup("film".into()), lookup("film".into()));
    assert_eq!(fetches.load(Ordering::SeqCst), 1);
    assert_eq!(a, b);
    assert!(a.is_some());
}

#[test]
fn first_poster_without_a_language_is_chosen() {
    let images = serde_json::json!({"posters": [
        {"file_path": "/de.jpg", "iso_639_1": "de"},
        {"file_path": "/blank.jpg", "iso_639_1": null},
        {"file_path": "/blank2.jpg", "iso_639_1": null},
    ]});
    assert_eq!(first_textless(&images).as_deref(), Some("/blank.jpg"));
    assert_eq!(first_textless(&serde_json::json!({"posters": []})), None);
}
