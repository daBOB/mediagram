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
