//! The one harness the fetch tests drive, so there is never a second
//! opinion about what a stubbed provider answers or where a run's output
//! lands.
//!
//! The client is a trait, so nothing here reaches TMDB: CI has no API key,
//! and a test that needed one would be a test that does not run. The poster
//! CDN is stubbed differently — see [`offline_client`] — because it is not
//! behind that trait.

use std::path::Path;

use mediagram_core::api::artwork::{plan_fetch, verify_then_fetch};
use mediagram_core::api::fetch::fetch_into;
use mediagram_core::api::{Core, CoreError};
use mediagram_core::dto::FetchReport;
use rusqlite::Connection;

use mediagram_tmdb::tmdb_client::TmdbApi;

/// Answers a details payload, which is where both halves of a run get their
/// answer: `resolve_posters` reads a path out of it and the description walk
/// reads the rest.
#[derive(Default)]
pub struct StubApi {
    poster_path: Option<String>,
    /// The one provider id this stub will answer for. `None` answers for
    /// anything, which is what most of these tests want.
    only: Option<u64>,
}

impl StubApi {
    pub fn with_poster(path: &str) -> Self {
        StubApi { poster_path: Some(path.into()), only: None }
    }

    /// Answers for one title and refuses every other, the way the provider
    /// answers for a title it holds and 404s for one it does not.
    ///
    /// No poster path at all, so the only thing a title can lose here is its
    /// description: the CDN is unreachable from a test, so a resolved path
    /// would fail its download too and a test about descriptions would be
    /// counting artwork.
    pub fn answering_only(id: u64) -> Self {
        StubApi { poster_path: None, only: Some(id) }
    }
}

impl TmdbApi for StubApi {
    async fn get_json(
        &self,
        path: &str,
        _query: &[(&str, String)],
    ) -> anyhow::Result<serde_json::Value> {
        let asked: Option<u64> = path.rsplit('/').next().and_then(|tail| tail.parse().ok());
        if let Some(only) = self.only
            && asked != Some(only)
        {
            anyhow::bail!("tmdb request to {path} failed with 404 Not Found: {{}}");
        }
        // The id is echoed rather than fixed: a recorded row is keyed by the
        // id in the payload, and a stub that answered `1` for everything
        // would file every title under one key.
        Ok(serde_json::json!({ "id": asked.unwrap_or_default(), "poster_path": self.poster_path }))
    }
}

/// Answers every request the way TMDB answers a credential it will not
/// accept, in the wording `TmdbClient::get_json` builds for one.
pub struct RejectingApi;

impl TmdbApi for RejectingApi {
    async fn get_json(
        &self,
        path: &str,
        _query: &[(&str, String)],
    ) -> anyhow::Result<serde_json::Value> {
        anyhow::bail!(
            r#"tmdb request to {path} failed with 401 Unauthorized: {{"status_code":7,"status_message":"Invalid API key."}}"#
        )
    }
}

/// Builds `<dir>/catalog/current/library.db` directly — bypassing
/// `refresh_catalog`, which needs the network — with one row per
/// `(kind, tmdb id)` pair, following the fixture style in `api_surface.rs`.
pub fn catalog_with_kinds(dir: &Path, kinds: &[(&str, Option<i64>)]) {
    let rows: Vec<_> = kinds.iter().map(|(kind, tmdb)| (*kind, *tmdb, None)).collect();
    catalog_with_sets(dir, &rows);
}

/// The same, for sets that belong to a collection: a course's lessons or a
/// series' episodes, which are one title between them however many rows the
/// index holds. `show` is `None` for a collection the index never named,
/// which a shelf still draws as one card.
pub fn catalog_with_collection(dir: &Path, kind: &str, show: Option<&str>, sets: usize) {
    let rows: Vec<_> = (0..sets).map(|_| (kind, None, show)).collect();
    catalog_with_sets(dir, &rows);
}

fn catalog_with_sets(dir: &Path, rows: &[(&str, Option<i64>, Option<&str>)]) {
    let current = dir.join("catalog").join("current");
    std::fs::create_dir_all(&current).unwrap();
    let conn = Connection::open(current.join("library.db")).unwrap();
    for stmt in mlib_spec::schema::migrations_up_to(mlib_spec::schema::SCHEMA_VERSION) {
        conn.execute(stmt, []).unwrap();
    }
    for (i, (kind, tmdb, show)) in rows.iter().enumerate() {
        let set_id = format!("01SETFIXTURE{i:015}");
        conn.execute(
            "INSERT INTO sets(set_id, kind, tmdb, show, container, total, part_count, status, created_at, spec_version)
             VALUES (?1, ?2, ?3, ?4, 'mkv', 0, 0, 'complete', ?5, 1)",
            rusqlite::params![set_id, kind, tmdb, show, i as i64],
        )
        .unwrap();
    }
}

pub fn core_at(dir: &Path) -> std::sync::Arc<Core> {
    Core::new(dir.display().to_string(), 1, "test-hash".into())
}

/// Drops a poster already on disk where a fetch would write one, so a test
/// can exercise "already held" without a network download. The directory
/// comes from [`plan_fetch`] rather than being spelled out here: a hand-built
/// path is a second opinion about where artwork lives, and the first time
/// these two disagreed the posters were being deleted on every refresh.
pub fn write_existing_poster(dir: &Path, key: &str) {
    let posters = plan_fetch(&core_at(dir), "en-US").unwrap().artwork_dir;
    std::fs::create_dir_all(&posters).unwrap();
    std::fs::write(posters.join(format!("{key}.jpg")), b"stub").unwrap();
}

/// Plans a fetch over the catalog the test just seeded, then calls
/// `fetch_into` directly — the internal half, never the public
/// `fetch_missing`, which constructs a real client from a key this test does
/// not have.
pub async fn fetch_with(dir: &Path, api: StubApi) -> FetchReport {
    install_crypto_provider();
    let core = core_at(dir);
    let plan = plan_fetch(&core, "en-US").expect("the seeded catalog is readable");
    fetch_into(
        &core,
        &api,
        &offline_client(),
        &plan.artwork_dir,
        &plan.language,
        &plan.titles,
        plan.without_id,
    )
    .await
}

/// The same run, against a provider that will not take the key — through
/// `verify_then_fetch`, because the check being in front of everything else
/// is the thing under test.
pub async fn fetch_rejecting(dir: &Path) -> Result<FetchReport, CoreError> {
    install_crypto_provider();
    let core = core_at(dir);
    let plan = plan_fetch(&core, "en-US").expect("the seeded catalog is readable");
    verify_then_fetch(
        &core,
        RejectingApi,
        &offline_client(),
        &plan.artwork_dir,
        &plan.language,
        &plan.titles,
        plan.without_id,
    )
    .await
}

/// `reqwest::Client::new()` panics with no crypto provider installed: this
/// crate builds with `rustls-no-provider`, so nothing pulls one in
/// automatically. The real entry point installs one inside `http::client`;
/// these tests have no access to that private function, so they install the
/// same provider directly. Installing twice (a second test in this run) is
/// not an error — whichever provider got there first is fine.
pub fn install_crypto_provider() {
    let _ = rustls::crypto::ring::default_provider().install_default();
}

/// A client that can never reach the real network: TMDB itself is stubbed,
/// but the poster CDN is not, and these tests must not depend on this
/// machine having egress. Pinning `image.tmdb.org` to a closed local port
/// fails a download the same way a real network problem would — landing in
/// `failed`, not `posters_fetched` — deterministically and without a real
/// request ever leaving the process.
pub fn offline_client() -> reqwest::Client {
    reqwest::Client::builder()
        .resolve("image.tmdb.org", "127.0.0.1:1".parse().unwrap())
        .build()
        .unwrap()
}
