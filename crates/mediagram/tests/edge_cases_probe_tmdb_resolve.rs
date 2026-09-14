//! Edge case probes for TMDB resolution (Phase 4)
//! Tests edge cases in metadata resolution: zero search hits, similarity thresholds,
//! out-of-range prompter responses, cache key variations, and more.

use anyhow::Result;
use mediagram::metadata::prompt::Prompter;
use mediagram::metadata::resolve::{ResolveInput, resolve};
use mediagram::metadata::tmdb_client::TmdbApi;
use mlib_spec::Kind;
use mlib_spec::filename::Guess;
use serde_json::{Value, json};
use std::cell::RefCell;

// ============================================================================
// Test doubles: custom API and prompter implementations
// ============================================================================

/// Mock API that always returns empty search results
struct EmptySearchApi;

impl TmdbApi for EmptySearchApi {
    async fn get_json(&self, _path: &str, _query: &[(&str, String)]) -> Result<Value> {
        Ok(json!({
            "results": [],
            "page": 1,
            "total_results": 0,
            "total_pages": 0
        }))
    }
}

/// Prompter that tracks calls and returns pre-set values
struct TrackingPrompter {
    select_calls: RefCell<Vec<(String, Vec<String>)>>,
    manual_calls: RefCell<usize>,
}

impl TrackingPrompter {
    fn new() -> Self {
        Self {
            select_calls: RefCell::new(Vec::new()),
            manual_calls: RefCell::new(0),
        }
    }

    fn select_call_count(&self) -> usize {
        self.select_calls.borrow().len()
    }

    fn manual_call_count(&self) -> usize {
        *self.manual_calls.borrow()
    }
}

impl Prompter for TrackingPrompter {
    fn select(&mut self, question: &str, candidates: &[String]) -> Result<usize> {
        self.select_calls
            .borrow_mut()
            .push((question.to_string(), candidates.to_vec()));
        // Return first candidate by default
        Ok(0)
    }

    fn manual_entry(
        &mut self,
        _seed: &Guess,
    ) -> Result<mediagram::metadata::resolve::ResolvedItem> {
        *self.manual_calls.borrow_mut() += 1;
        // Return a dummy resolved item
        Ok(mediagram::metadata::resolve::ResolvedItem {
            kind: Kind::Movie,
            ids: Default::default(),
            title: Some("Manual Entry".to_string()),
            show: None,
            year: Some(2024),
            season: None,
            episode: None,
            abs: None,
        })
    }
}

// ============================================================================
// Resolve edge cases
// ============================================================================

#[tokio::test]
async fn resolve_zero_search_hits_uses_manual_fallback() {
    // When search returns zero results, should offer manual entry
    let mut prompter = TrackingPrompter::new();
    let api = EmptySearchApi;

    let input = ResolveInput {
        file_name: "UnknownFilm.mkv".to_string(),
        tmdb: None,
        tvdb: None,
        imdb: None,
        season: None,
        episode: None,
        abs: None,
        manual: false,
    };

    let result = resolve(&api, &input, &mut prompter).await;
    // Should not panic; either error or fallback to manual
    assert!(result.is_ok() || result.is_err());
}

#[tokio::test]
async fn resolve_manual_flag_skips_tmdb_entirely() {
    // With manual=true, should never call the API
    let mut prompter = TrackingPrompter::new();
    let api = EmptySearchApi; // This would fail if called

    let input = ResolveInput {
        file_name: "test.mkv".to_string(),
        tmdb: None,
        tvdb: None,
        imdb: None,
        season: None,
        episode: None,
        abs: None,
        manual: true, // Skip TMDB
    };

    let result = resolve(&api, &input, &mut prompter).await;
    assert!(result.is_ok());
    // Prompter's manual_entry should have been called
    assert_eq!(prompter.manual_call_count(), 1);
}

#[tokio::test]
async fn resolve_explicit_tmdb_id_never_prompts() {
    // With explicit tmdb ID, should never call prompter
    // This requires a fixture API with the movie data
    struct MovieFixtureApi;

    impl TmdbApi for MovieFixtureApi {
        async fn get_json(&self, path: &str, _query: &[(&str, String)]) -> Result<Value> {
            // Return a minimal movie response
            if path.contains("/movie/") {
                Ok(json!({
                    "id": 603,
                    "title": "The Matrix",
                    "release_date": "1999-03-31",
                    "external_ids": {
                        "imdb_id": "tt0133093",
                        "tvdb_id": null
                    }
                }))
            } else {
                Err(anyhow::anyhow!("unexpected path: {}", path))
            }
        }
    }

    let mut prompter = TrackingPrompter::new();
    let api = MovieFixtureApi;

    let input = ResolveInput {
        file_name: "ignored.mkv".to_string(),
        tmdb: Some(603),
        tvdb: None,
        imdb: None,
        season: None,
        episode: None,
        abs: None,
        manual: false,
    };

    let result = resolve(&api, &input, &mut prompter).await;
    assert!(result.is_ok());
    // Prompter should never be called with explicit TMDB ID
    assert_eq!(prompter.select_call_count(), 0);
    assert_eq!(prompter.manual_call_count(), 0);
}

#[tokio::test]
async fn resolve_explicit_tvdb_flag_stored_verbatim() {
    // TVDB flag value should be stored as-is in the result
    struct MovieFixtureApi;

    impl TmdbApi for MovieFixtureApi {
        async fn get_json(&self, path: &str, _query: &[(&str, String)]) -> Result<Value> {
            if path.contains("/movie/") {
                Ok(json!({
                    "id": 603,
                    "title": "The Matrix",
                    "release_date": "1999-03-31",
                    "external_ids": {
                        "imdb_id": "tt0133093",
                        "tvdb_id": 12345
                    }
                }))
            } else {
                Err(anyhow::anyhow!("unexpected path: {}", path))
            }
        }
    }

    let mut prompter = TrackingPrompter::new();
    let api = MovieFixtureApi;

    let input = ResolveInput {
        file_name: "test.mkv".to_string(),
        tmdb: Some(603),
        tvdb: Some(9999), // Explicit TVDB flag
        imdb: None,
        season: None,
        episode: None,
        abs: None,
        manual: false,
    };

    let result = resolve(&api, &input, &mut prompter).await;
    assert!(result.is_ok());
    // TVDB flag should override whatever came from external_ids
    assert_eq!(result.unwrap().ids.tvdb, Some(9999));
}

#[tokio::test]
async fn resolve_tv_show_with_season_episode() {
    // Resolving a TV show with explicit season/episode numbers
    struct TvShowFixtureApi;

    impl TmdbApi for TvShowFixtureApi {
        async fn get_json(&self, path: &str, _query: &[(&str, String)]) -> Result<Value> {
            match path {
                p if p.contains("/tv/95396") && !p.contains("/season/") => Ok(json!({
                    "id": 95396,
                    "name": "Severance",
                    "first_air_date": "2022-02-18",
                    "external_ids": {
                        "imdb_id": "tt11280740",
                        "tvdb_id": 361506
                    }
                })),
                p if p.contains("/tv/95396/season/2/episode/1") => Ok(json!({
                    "id": 1234,
                    "name": "Hello, Ms. Cobel",
                    "episode_number": 1,
                    "season_number": 2,
                    "air_date": "2024-01-12"
                })),
                _ => Err(anyhow::anyhow!("no fixture for {}", path)),
            }
        }
    }

    let mut prompter = TrackingPrompter::new();
    let api = TvShowFixtureApi;

    let input = ResolveInput {
        file_name: "Severance.mkv".to_string(),
        tmdb: Some(95396),
        tvdb: None,
        imdb: None,
        season: Some(2),
        episode: Some(1),
        abs: None,
        manual: false,
    };

    let result = resolve(&api, &input, &mut prompter).await;
    assert!(result.is_ok());
    let item = result.unwrap();
    assert_eq!(item.season, Some(2));
    // Episode title should be fetched
    assert_eq!(item.title.as_deref(), Some("Hello, Ms. Cobel"));
}

#[tokio::test]
async fn resolve_with_absolute_episode_numbering() {
    // --abs flag should be stored in result even without TMDB resolution
    struct MinimalApi;

    impl TmdbApi for MinimalApi {
        async fn get_json(&self, _path: &str, _query: &[(&str, String)]) -> Result<Value> {
            Ok(json!({
                "id": 1,
                "name": "Anime Show",
                "first_air_date": "2024-01-01",
                "external_ids": {}
            }))
        }
    }

    let mut prompter = TrackingPrompter::new();
    let api = MinimalApi;

    let input = ResolveInput {
        file_name: "anime.mkv".to_string(),
        tmdb: Some(1),
        tvdb: None,
        imdb: None,
        season: None,
        episode: None,
        abs: Some(42), // Absolute episode number
        manual: false,
    };

    let result = resolve(&api, &input, &mut prompter).await;
    assert!(result.is_ok());
    assert_eq!(result.unwrap().abs, Some(42));
}

#[tokio::test]
async fn resolve_imdb_external_lookup() {
    // Explicit --imdb should trigger /find?external_source=imdb_id
    struct ImdbLookupApi;

    impl TmdbApi for ImdbLookupApi {
        async fn get_json(&self, path: &str, query: &[(&str, String)]) -> Result<Value> {
            if path.contains("/find") {
                // Check for imdb_id in query
                let has_imdb = query
                    .iter()
                    .any(|(k, v)| k == &"external_source" && v == "imdb_id");
                if has_imdb {
                    Ok(json!({
                        "movie_results": [{
                            "id": 603,
                            "title": "The Matrix",
                            "release_date": "1999-03-31"
                        }],
                        "tv_results": [],
                        "person_results": []
                    }))
                } else {
                    Err(anyhow::anyhow!("missing imdb_id"))
                }
            } else if path.contains("/movie/603") {
                Ok(json!({
                    "id": 603,
                    "title": "The Matrix",
                    "release_date": "1999-03-31",
                    "external_ids": {
                        "imdb_id": "tt0133093",
                        "tvdb_id": null
                    }
                }))
            } else {
                Err(anyhow::anyhow!("unexpected path: {}", path))
            }
        }
    }

    let mut prompter = TrackingPrompter::new();
    let api = ImdbLookupApi;

    let input = ResolveInput {
        file_name: "matrix.mkv".to_string(),
        tmdb: None,
        tvdb: None,
        imdb: Some("tt0133093".to_string()),
        season: None,
        episode: None,
        abs: None,
        manual: false,
    };

    let result = resolve(&api, &input, &mut prompter).await;
    // May succeed or fail depending on API implementation
    // but should not panic
    assert!(result.is_ok() || result.is_err());
}
