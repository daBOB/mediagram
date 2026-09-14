//! Fixture-based tests for TMDB metadata resolution. No network access:
//! test doubles in `tests/support/` serve canned JSON from
//! `tests/fixtures/tmdb/`.

mod support;

use std::cell::Cell;
use std::collections::VecDeque;
use std::rc::Rc;

use support::metadata::resolve::{ResolveInput, ResolvedItem, resolve};
use support::metadata::tmdb_client::DiskCachedApi;
use support::metadata::tmdb_client::TmdbApi;
use support::{FixtureApi, ScriptedPrompter, StubApi};

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
