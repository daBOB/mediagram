//! Posters: resolving which artwork a title has, and filling a directory
//! with it. Paths come from the TMDB responses already cached on disk, so a
//! normal run needs no API key and no network — `add` cached those payloads
//! when it resolved each title.

use mediagram_tmdb::poster_files::{already_held, download_into};
use mediagram_tmdb::posters::{
    PosterRef, poster_url, resolve_posters,
};
use mediagram_tmdb::tmdb_client::TmdbApi;
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

/// A series brings its seasons' artwork with it, from the same payload, and
/// a season TMDB has no artwork for — or unsafe artwork — is left out.
#[tokio::test]
async fn a_series_yields_a_poster_per_season_that_has_one() {
    let api = FakeApi::default().with(
        "/tv/7",
        json!({"id": 7, "poster_path": "/show.jpg", "seasons": [
            {"season_number": 0, "poster_path": "/specials.jpg"},
            {"season_number": 1, "poster_path": "/s1.jpg"},
            {"season_number": 2},
            {"season_number": 3, "poster_path": "/../etc/passwd"}
        ]}),
    );

    let found = resolve_posters(&api, &[(Kind::Ep, 7)]).await;

    let keys: Vec<&str> = found.iter().map(|p| p.key.as_str()).collect();
    assert_eq!(keys, ["tmdb-tv-7", "tmdb-tv-7-s0", "tmdb-tv-7-s1"]);
    assert_eq!(found[2].path, "/s1.jpg");
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

// Filling a directory. The download itself talks to TMDB's CDN and is
// exercised by the export path; what is worth pinning here is everything
// that decides whether a request happens at all, which is where a local
// poster directory differs from a staged one.

#[tokio::test]
async fn no_posters_means_no_directory() {
    let tmp = tempfile::tempdir().unwrap();
    let dir = tmp.path().join("posters");

    let written = download_into(&mediagram_core::http::client().unwrap(), &[], &dir)
        .await
        .unwrap();

    assert!(written.is_empty());
    assert!(
        !dir.exists(),
        "a library with nothing to illustrate leaves no empty directory behind"
    );
}

/// The key becomes a file name, so a malformed one is refused before it can
/// name anything. Nothing about it reaches the filesystem.
#[tokio::test]
async fn a_malformed_key_is_refused_without_a_request() {
    let tmp = tempfile::tempdir().unwrap();
    let dir = tmp.path().join("posters");
    let refs = vec![PosterRef {
        key: "../../etc/passwd".into(),
        path: "/whatever.jpg".into(),
    }];

    let written = download_into(&mediagram_core::http::client().unwrap(), &refs, &dir)
        .await
        .unwrap();

    assert!(written.is_empty());
    assert_eq!(
        std::fs::read_dir(&dir).unwrap().count(),
        0,
        "a rejected key writes nothing"
    );
}

/// Re-running after the library grows must not refetch what is already held,
/// or the ordinary case becomes the slow one.
#[tokio::test]
async fn a_poster_already_on_disk_is_kept_and_not_requested_again() {
    let tmp = tempfile::tempdir().unwrap();
    let dir = tmp.path().join("posters");
    std::fs::create_dir_all(&dir).unwrap();
    std::fs::write(dir.join("tmdb-movie-550.jpg"), b"held").unwrap();

    let refs = vec![PosterRef {
        key: "tmdb-movie-550".into(),
        path: "/five-fifty.jpg".into(),
    }];
    assert_eq!(already_held(&refs, &dir), 1);

    // The bytes are the proof: had the skip failed, the CDN response would
    // have replaced this placeholder with a real image.
    let written = download_into(&mediagram_core::http::client().unwrap(), &refs, &dir)
        .await
        .unwrap();

    assert_eq!(written, vec!["tmdb-movie-550".to_string()]);
    assert_eq!(
        std::fs::read(dir.join("tmdb-movie-550.jpg")).unwrap(),
        b"held"
    );
}

#[test]
fn nothing_is_held_in_a_directory_that_does_not_exist_yet() {
    let tmp = tempfile::tempdir().unwrap();
    let refs = vec![PosterRef {
        key: "tmdb-tv-1396".into(),
        path: "/breaking.jpg".into(),
    }];

    assert_eq!(already_held(&refs, &tmp.path().join("posters")), 0);
}
