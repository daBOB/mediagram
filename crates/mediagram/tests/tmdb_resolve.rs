//! Fixture-based tests for TMDB metadata resolution. No network access:
//! test doubles in `tests/support/` serve canned JSON from
//! `tests/fixtures/tmdb/`.

mod support;

use std::cell::Cell;
use std::collections::VecDeque;
use std::rc::Rc;

use anyhow::Result;
use serde_json::{Value, json};

use mediagram_tmdb::disk_cache::DiskCachedApi;
use mediagram_tmdb::tmdb_client::TmdbApi;
use support::tmdb::metadata::resolve::{ResolveInput, ResolvedItem, resolve};
use support::tmdb::{FixtureApi, ScriptedPrompter, StubApi};

use mlib_spec::{Episode, Kind, ProviderIds};

#[tokio::test]
async fn explicit_tmdb_id_never_prompts() {
    let api = FixtureApi::new(&[("/movie/603", "movie_603.json")]);
    let mut ui = ScriptedPrompter::default();
    let input = ResolveInput {
        file_name: "The Matrix (1999).mkv".into(),
        tmdb: Some(603),
        ..Default::default()
    };

    let item = resolve(&api, &input, &mut ui).await.expect("resolve");

    assert_eq!(item.kind, Kind::Movie);
    assert_eq!(item.ids.tmdb, Some(603));
    assert_eq!(item.ids.imdb.as_deref(), Some("tt0133093"));
    assert_eq!(item.title.as_deref(), Some("The Matrix"));
    assert_eq!(item.year, Some(1999));
    assert_eq!(ui.select_calls, 0);
    assert_eq!(api.call_count(), 1);
}

#[tokio::test]
async fn severance_search_auto_picks_and_fetches_episode_title() {
    let api = FixtureApi::new(&[
        ("/search/tv", "search_tv_severance.json"),
        ("/tv/95396", "tv_95396.json"),
        ("/tv/95396/season/2/episode/1", "tv_95396_s2e1.json"),
    ]);
    let mut ui = ScriptedPrompter::default();
    let input = ResolveInput {
        file_name: "Severance (2022) - s02e01.mkv".into(),
        ..Default::default()
    };

    let item = resolve(&api, &input, &mut ui).await.expect("resolve");

    assert_eq!(item.kind, Kind::Ep);
    assert_eq!(item.ids.tmdb, Some(95396));
    assert_eq!(item.ids.imdb.as_deref(), Some("tt11280740"));
    assert_eq!(item.ids.tvdb, Some(371980));
    assert_eq!(item.show.as_deref(), Some("Severance"));
    assert_eq!(item.title.as_deref(), Some("Hello, Ms. Cobel"));
    assert_eq!(item.season, Some(2));
    assert_eq!(item.episode, Some(Episode::Single(1)));
    assert_eq!(ui.select_calls, 0);
}

#[tokio::test]
async fn ambiguous_remake_prompts_exactly_once() {
    let api = FixtureApi::new(&[
        ("/search/movie", "search_movie_the_thing.json"),
        ("/movie/1091", "movie_1091.json"),
    ]);
    let mut ui = ScriptedPrompter {
        select_answers: VecDeque::from([0]),
        ..Default::default()
    };
    let input = ResolveInput {
        file_name: "The Thing.mkv".into(),
        ..Default::default()
    };

    let item = resolve(&api, &input, &mut ui).await.expect("resolve");

    assert_eq!(ui.select_calls, 1);
    assert_eq!(item.ids.tmdb, Some(1091));
    assert_eq!(item.year, Some(1982));
}

#[tokio::test]
async fn manual_flag_skips_tmdb_entirely() {
    let api = FixtureApi::new(&[]);
    let canned = ResolvedItem {
        kind: Kind::Movie,
        ids: ProviderIds::default(),
        title: Some("Hand Entered".into()),
        show: None,
        year: Some(2020),
        season: None,
        episode: None,
        abs: None,
    };
    let mut ui = ScriptedPrompter {
        manual_answer: Some(canned.clone()),
        ..Default::default()
    };
    let input = ResolveInput {
        file_name: "whatever.mkv".into(),
        manual: true,
        ..Default::default()
    };

    let item = resolve(&api, &input, &mut ui).await.expect("resolve");

    assert_eq!(item, canned);
    assert_eq!(api.call_count(), 0);
    assert_eq!(ui.select_calls, 0);
}

#[tokio::test]
async fn disk_cache_avoids_second_api_call() {
    let dir = tempfile::tempdir().expect("tempdir");
    let calls = Rc::new(Cell::new(0));
    let stub = StubApi {
        calls: calls.clone(),
        response: serde_json::json!({"id": 1}),
    };
    let cached = DiskCachedApi::new(stub, dir.path());

    let first = cached.get_json("/movie/1", &[]).await.expect("first call");
    let second = cached.get_json("/movie/1", &[]).await.expect("second call");

    assert_eq!(first, second);
    assert_eq!(
        calls.get(),
        1,
        "second identical request must be served from disk cache"
    );
}

#[tokio::test]
async fn zero_search_hits_suggests_manual_retry() {
    // A search with no hits fails outright and names --manual as the way
    // out, rather than falling back to it automatically.
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

    let mut ui = ScriptedPrompter::default();
    let api = EmptySearchApi;
    let input = ResolveInput {
        file_name: "UnknownFilm.mkv".into(),
        ..Default::default()
    };

    let err = resolve(&api, &input, &mut ui)
        .await
        .expect_err("zero hits must not silently succeed");

    assert!(err.to_string().contains("--manual"));
}

#[tokio::test]
async fn explicit_tvdb_flag_overrides_external_ids() {
    // --tvdb is authoritative: v1 never queries TVDB directly, so whatever
    // the caller supplied wins over anything TMDB's external_ids returned.
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

    let mut ui = ScriptedPrompter::default();
    let api = MovieFixtureApi;
    let input = ResolveInput {
        file_name: "test.mkv".into(),
        tmdb: Some(603),
        tvdb: Some(9999),
        ..Default::default()
    };

    let item = resolve(&api, &input, &mut ui).await.expect("resolve");

    assert_eq!(item.ids.tvdb, Some(9999));
}

#[tokio::test]
async fn explicit_tmdb_id_with_season_episode_fetches_episode_title() {
    // An explicit tmdb id carrying season/episode skips search entirely but
    // still fetches the episode's title.
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

    let mut ui = ScriptedPrompter::default();
    let api = TvShowFixtureApi;
    let input = ResolveInput {
        file_name: "Severance.mkv".into(),
        tmdb: Some(95396),
        season: Some(2),
        episode: Some(1),
        ..Default::default()
    };

    let item = resolve(&api, &input, &mut ui).await.expect("resolve");

    assert_eq!(item.season, Some(2));
    assert_eq!(item.title.as_deref(), Some("Hello, Ms. Cobel"));
}

#[tokio::test]
async fn absolute_episode_number_is_stored_verbatim() {
    // --abs is carried into the result even when TMDB has nothing to say
    // about it.
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

    let mut ui = ScriptedPrompter::default();
    let api = MinimalApi;
    let input = ResolveInput {
        file_name: "anime.mkv".into(),
        tmdb: Some(1),
        abs: Some(42),
        ..Default::default()
    };

    let item = resolve(&api, &input, &mut ui).await.expect("resolve");

    assert_eq!(item.abs, Some(42));
}

#[tokio::test]
async fn explicit_imdb_id_resolves_via_find_endpoint() {
    // --imdb looks the id up via /find?external_source=imdb_id, then fetches
    // full details for the tmdb id that comes back.
    struct ImdbLookupApi;

    impl TmdbApi for ImdbLookupApi {
        async fn get_json(&self, path: &str, query: &[(&str, String)]) -> Result<Value> {
            if path.contains("/find") {
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

    let mut ui = ScriptedPrompter::default();
    let api = ImdbLookupApi;
    let input = ResolveInput {
        file_name: "matrix.mkv".into(),
        imdb: Some("tt0133093".into()),
        ..Default::default()
    };

    let item = resolve(&api, &input, &mut ui).await.expect("resolve");

    assert_eq!(item.ids.tmdb, Some(603));
    assert_eq!(item.title.as_deref(), Some("The Matrix"));
}
