//! Fetching artwork, without reaching TMDB. The client is a trait, so these
//! drive a stub: CI has no API key, and a test that needed one would be a
//! test that does not run.

use std::path::Path;

use mediagram_core::api::artwork::{fetch_into, split_titles};
use mediagram_core::catalog::{PlayableSet, list_playable};
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

/// Drops a poster already on disk at the key a title would resolve to, so a
/// test can exercise "already held" without a network download.
fn write_existing_poster(dir: &Path, key: &str) {
    let posters = dir.join("catalog").join("current").join("posters");
    std::fs::create_dir_all(&posters).unwrap();
    std::fs::write(posters.join(format!("{key}.jpg")), b"stub").unwrap();
}

/// Reads the catalog `fetch_with` just seeded, splits it into what
/// `fetch_into` wants using the same [`split_titles`] production code
/// drives, and calls `fetch_into` directly — the internal half, never the
/// public `fetch_posters`, which constructs a real client from a key this
/// test does not have.
async fn fetch_with(dir: &Path, api: StubApi) -> PosterReport {
    // `reqwest::Client::new()` panics with no crypto provider installed:
    // this crate builds with `rustls-no-provider`, so nothing pulls one in
    // automatically. The real entry point installs one inside `http::client`;
    // this test has no access to that private function, so it installs the
    // same provider directly. Installing twice (a second test in this run)
    // is not an error — whichever provider got there first is fine.
    let _ = rustls::crypto::ring::default_provider().install_default();

    let conn = Connection::open(dir.join("catalog").join("current").join("library.db")).unwrap();
    let sets: Vec<PlayableSet> = list_playable(&conn).unwrap();
    let (titles, without_id) = split_titles(&sets);

    let posters_dir = dir.join("catalog").join("current").join("posters");
    fetch_into(&api, &offline_client(), &posters_dir, &titles, without_id).await
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
