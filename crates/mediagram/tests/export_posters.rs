//! Poster resolution for the package export. Posters come from the TMDB
//! responses already cached on disk, so a normal export needs no API key and
//! no network: the paths are in payloads `add` fetched when it resolved the
//! title.

use mediagram::export::posters::{PosterRef, poster_url, resolve_posters};
use mediagram::metadata::tmdb_client::TmdbApi;
use mlib_spec::Kind;

use anyhow::{Result, bail};
use serde_json::{Value, json};

/// Serves canned payloads by request path, and records what was asked for.
#[derive(Default)]
struct FakeApi {
    payloads: Vec<(String, Value)>,
    failing: Vec<String>,
}

impl FakeApi {
    fn with(mut self, path: &str, value: Value) -> Self {
        self.payloads.push((path.to_string(), value));
        self
    }
    fn failing(mut self, path: &str) -> Self {
        self.failing.push(path.to_string());
        self
    }
}

impl TmdbApi for FakeApi {
    async fn get_json(&self, path: &str, _query: &[(&str, String)]) -> Result<Value> {
        if self.failing.iter().any(|p| p == path) {
            bail!("tmdb is unreachable");
        }
        match self.payloads.iter().find(|(p, _)| p == path) {
            Some((_, v)) => Ok(v.clone()),
            None => bail!("no payload for {path}"),
        }
    }
}

#[test]
fn a_poster_url_uses_the_tmdb_image_cdn_at_a_television_sized_width() {
    let url = poster_url("/abc123.jpg");
    assert_eq!(url, "https://image.tmdb.org/t/p/w342/abc123.jpg");
}

#[tokio::test]
async fn posters_are_keyed_by_kind_so_movie_and_show_ids_cannot_collide() {
    let api = FakeApi::default()
        .with(
            "/movie/550",
            json!({"id": 550, "poster_path": "/movie550.jpg"}),
        )
        .with("/tv/550", json!({"id": 550, "poster_path": "/tv550.jpg"}));

    let found = resolve_posters(&api, &[(Kind::Movie, 550), (Kind::Ep, 550)]).await;

    assert_eq!(
        found,
        vec![
            PosterRef {
                key: "tmdb-movie-550".into(),
                path: "/movie550.jpg".into()
            },
            PosterRef {
                key: "tmdb-tv-550".into(),
                path: "/tv550.jpg".into()
            },
        ]
    );
}

/// TMDB being unreachable, rate limited, or refusing an unauthenticated
/// request costs that title its poster and nothing more. The export still
/// produces a package.
#[tokio::test]
async fn a_failing_title_loses_its_poster_without_failing_the_export() {
    let api = FakeApi::default()
        .with("/movie/1", json!({"id": 1, "poster_path": "/one.jpg"}))
        .failing("/movie/2")
        .with("/movie/3", json!({"id": 3, "poster_path": "/three.jpg"}));

    let found = resolve_posters(
        &api,
        &[(Kind::Movie, 1), (Kind::Movie, 2), (Kind::Movie, 3)],
    )
    .await;

    let keys: Vec<&str> = found.iter().map(|p| p.key.as_str()).collect();
    assert_eq!(keys, ["tmdb-movie-1", "tmdb-movie-3"]);
}

#[tokio::test]
async fn a_title_with_no_poster_recorded_is_skipped() {
    let api = FakeApi::default()
        .with("/movie/1", json!({"id": 1}))
        .with("/movie/2", json!({"id": 2, "poster_path": null}))
        .with("/movie/3", json!({"id": 3, "poster_path": "/three.jpg"}));

    let found = resolve_posters(
        &api,
        &[(Kind::Movie, 1), (Kind::Movie, 2), (Kind::Movie, 3)],
    )
    .await;

    assert_eq!(found.len(), 1);
    assert_eq!(found[0].key, "tmdb-movie-3");
}

/// A poster path is remote data that becomes part of a URL and a file name,
/// so anything that is not a plain image path is refused.
#[tokio::test]
async fn a_hostile_poster_path_is_refused() {
    let api = FakeApi::default()
        .with(
            "/movie/1",
            json!({"id": 1, "poster_path": "/../../etc/passwd"}),
        )
        .with(
            "/movie/2",
            json!({"id": 2, "poster_path": "no-leading-slash.jpg"}),
        )
        .with("/movie/3", json!({"id": 3, "poster_path": "/ok.jpg"}));

    let found = resolve_posters(
        &api,
        &[(Kind::Movie, 1), (Kind::Movie, 2), (Kind::Movie, 3)],
    )
    .await;

    assert_eq!(
        found.len(),
        1,
        "only the well-formed path survives: {found:?}"
    );
    assert_eq!(found[0].path, "/ok.jpg");
}

#[tokio::test]
async fn duplicate_ids_are_requested_once() {
    let api = FakeApi::default().with("/movie/7", json!({"id": 7, "poster_path": "/seven.jpg"}));

    let found = resolve_posters(&api, &[(Kind::Movie, 7), (Kind::Movie, 7)]).await;

    assert_eq!(found.len(), 1);
}

#[tokio::test]
async fn no_titles_means_no_requests_and_no_posters() {
    let api = FakeApi::default();
    assert!(resolve_posters(&api, &[]).await.is_empty());
}
