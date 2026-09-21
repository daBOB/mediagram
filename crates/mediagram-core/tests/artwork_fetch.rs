//! Fetching artwork, without reaching TMDB. The client is a trait, so these
//! drive a stub: CI has no API key, and a test that needed one would be a
//! test that does not run.

use std::path::Path;

use mediagram_core::api::artwork::{fetch_into, plan_fetch, verify_then_fetch};
use mediagram_core::api::{Core, CoreError};
use mediagram_core::dto::PosterReport;
use rusqlite::Connection;

use mediagram_tmdb::tmdb_client::TmdbApi;

/// Answers the details payload for any id, so `resolve_posters` finds a path.
struct StubApi {
    poster_path: Option<String>,
}

impl TmdbApi for StubApi {
    async fn get_json(&self, _path: &str, _query: &[(&str, String)]) -> anyhow::Result<serde_json::Value> {
        Ok(serde_json::json!({ "id": 1, "poster_path": self.poster_path }))
    }
}

/// Answers every request the way TMDB answers a credential it will not
/// accept, in the wording `TmdbClient::get_json` builds for one.
struct RejectingApi;

impl TmdbApi for RejectingApi {
    async fn get_json(&self, path: &str, _query: &[(&str, String)]) -> anyhow::Result<serde_json::Value> {
        anyhow::bail!(
            r#"tmdb request to {path} failed with 401 Unauthorized: {{"status_code":7,"status_message":"Invalid API key."}}"#
        )
    }
}

/// Builds `<dir>/catalog/current/library.db` directly — bypassing
/// `refresh_catalog`, which needs the network — with one row per
/// `(kind, tmdb id)` pair, following the fixture style in `api_surface.rs`.
fn catalog_with_kinds(dir: &Path, kinds: &[(&str, Option<i64>)]) {
    let current = dir.join("catalog").join("current");
    std::fs::create_dir_all(&current).unwrap();
    let conn = Connection::open(current.join("library.db")).unwrap();
    for stmt in mlib_spec::schema::migrations_up_to(mlib_spec::schema::SCHEMA_VERSION) {
        conn.execute(stmt, []).unwrap();
    }
    for (i, (kind, tmdb)) in kinds.iter().enumerate() {
        let set_id = format!("01SETFIXTURE{i:015}");
        conn.execute(
            "INSERT INTO sets(set_id, kind, tmdb, container, total, part_count, status, created_at, spec_version)
             VALUES (?1, ?2, ?3, 'mkv', 0, 0, 'complete', ?4, 1)",
            rusqlite::params![set_id, kind, tmdb, i as i64],
        )
        .unwrap();
    }
}

fn core_at(dir: &Path) -> std::sync::Arc<Core> {
    Core::new(dir.display().to_string(), 1, "test-hash".into())
}

/// Drops a poster already on disk where a fetch would write one, so a test
/// can exercise "already held" without a network download. The directory
/// comes from [`plan_fetch`] rather than being spelled out here: a hand-built
/// path is a second opinion about where artwork lives, and the first time
/// these two disagreed the posters were being deleted on every refresh.
fn write_existing_poster(dir: &Path, key: &str) {
    let posters = plan_fetch(&core_at(dir)).unwrap().artwork_dir;
    std::fs::create_dir_all(&posters).unwrap();
    std::fs::write(posters.join(format!("{key}.jpg")), b"stub").unwrap();
}

/// Plans a fetch over the catalog the test just seeded, then calls
/// `fetch_into` directly — the internal half, never the public
/// `fetch_posters`, which constructs a real client from a key this test does
/// not have.
async fn fetch_with(dir: &Path, api: StubApi) -> PosterReport {
    // `reqwest::Client::new()` panics with no crypto provider installed:
    // this crate builds with `rustls-no-provider`, so nothing pulls one in
    // automatically. The real entry point installs one inside `http::client`;
    // this test has no access to that private function, so it installs the
    // same provider directly. Installing twice (a second test in this run)
    // is not an error — whichever provider got there first is fine.
    let _ = rustls::crypto::ring::default_provider().install_default();

    let plan = plan_fetch(&core_at(dir)).expect("the seeded catalog is readable");
    fetch_into(&api, &offline_client(), &plan.artwork_dir, &plan.titles, plan.without_id).await
}

/// A client that can never reach the real network: TMDB itself is stubbed,
/// but the poster CDN is not, and these tests must not depend on this
/// machine having egress. Pinning `image.tmdb.org` to a closed local port
/// fails a download the same way a real network problem would — landing in
/// `failed`, not `fetched` — deterministically and without a real request
/// ever leaving the process.
fn offline_client() -> reqwest::Client {
    reqwest::Client::builder()
        .resolve("image.tmdb.org", "127.0.0.1:1".parse().unwrap())
        .build()
        .unwrap()
}

/// A key the provider rejects stays rejected however warm the disk cache
/// is.
///
/// The cache answers a repeated resolve, which is what it is for. It must
/// never answer for the credential: the cache keys on the endpoint and the
/// query, and the key appears in neither, so a rotated or mistyped key
/// asked through it would be validated against a file the *previous* key
/// paid for. The run that followed would then serve every title out of that
/// same cache and report a library entirely "already held" — a screen full
/// of zeroes a viewer would retry forever, which is the one outcome the
/// check exists to prevent.
#[tokio::test]
async fn a_rejected_key_is_still_rejected_after_a_successful_run() {
    let dir = tempfile::tempdir().unwrap();
    catalog_with_kinds(dir.path(), &[("movie", Some(11225))]);
    let plan = plan_fetch(&core_at(dir.path())).unwrap();
    let (artwork, titles) = (plan.artwork_dir, plan.titles);
    let _ = rustls::crypto::ring::default_provider().install_default();

    // A first run with a key the provider accepts, which is what leaves a
    // warm cache behind.
    verify_then_fetch(
        StubApi { poster_path: Some("/a.jpg".into()) },
        &offline_client(),
        &artwork,
        "en-US",
        &titles,
        0,
    )
    .await
    .expect("a key the provider accepts fetches");

    // The same device afterwards, with a key the provider will not take.
    let err = verify_then_fetch(RejectingApi, &offline_client(), &artwork, "en-US", &titles, 0)
        .await
        .expect_err("a rejected key must not be verified out of the cache");

    assert!(matches!(err, CoreError::NotAuthorized(_)), "reported as {err:?}");
}

#[tokio::test]
async fn a_title_with_no_provider_id_is_counted_rather_than_failed() {
    // A course has no TMDB entry at all, and a library of them must not
    // report a failure for every one.
    let dir = tempfile::tempdir().unwrap();
    catalog_with_kinds(dir.path(), &[("tut", None), ("movie", Some(11225))]);

    let report = fetch_with(dir.path(), StubApi { poster_path: Some("/a.jpg".into()) }).await;

    assert_eq!(report.no_provider_id, 1);
}

#[tokio::test]
async fn artwork_already_on_disk_is_not_fetched_again() {
    let dir = tempfile::tempdir().unwrap();
    catalog_with_kinds(dir.path(), &[("movie", Some(11225))]);
    write_existing_poster(dir.path(), "tmdb-movie-11225");

    let report = fetch_with(dir.path(), StubApi { poster_path: Some("/a.jpg".into()) }).await;

    assert_eq!(report.already_held, 1);
    assert_eq!(report.fetched, 0);
}

/// Every episode of a series shares one poster key, so a season of eight is
/// one download rather than eight.
#[tokio::test]
async fn a_series_is_one_poster_however_many_episodes_it_has() {
    let dir = tempfile::tempdir().unwrap();
    catalog_with_kinds(dir.path(), &[("ep", Some(1399)), ("ep", Some(1399)), ("ep", Some(1399))]);

    let report = fetch_with(dir.path(), StubApi { poster_path: Some("/a.jpg".into()) }).await;

    assert_eq!(report.fetched + report.failed, 1);
}

/// A provider that answers without a path is not an error; that title simply
/// has no artwork.
#[tokio::test]
async fn a_title_the_provider_has_no_art_for_is_not_a_failure() {
    let dir = tempfile::tempdir().unwrap();
    catalog_with_kinds(dir.path(), &[("movie", Some(11225))]);

    let report = fetch_with(dir.path(), StubApi { poster_path: None }).await;

    assert_eq!(report.failed, 0);
    assert_eq!(report.fetched, 0);
}
