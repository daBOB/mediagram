//! What a run actually costs the provider, counted rather than asserted in
//! a comment.
//!
//! A run walks its titles twice — once for artwork, once for descriptions —
//! and the only thing that makes that acceptable is the disk cache
//! `verify_then_fetch` wraps the client in: both walks ask
//! `mediagram_tmdb::details::details` for the same path and the same query,
//! so the second is served off disk. Nothing else in the suite can tell the
//! difference. Take `DiskCachedApi` out of that composition and every test
//! still passes while every title silently costs two requests.

// Only part of the harness is wanted here; `artwork_fetch.rs` compiles this
// same module and uses the rest of it.
#[allow(dead_code)]
mod fetch_stub;

use std::sync::Arc;
use std::sync::atomic::{AtomicUsize, Ordering};

use fetch_stub::{catalog_with_kinds, core_at, install_crypto_provider, offline_client};

use mediagram_core::api::artwork::{plan_fetch, verify_then_fetch};
use mediagram_tmdb::tmdb_client::TmdbApi;

/// Answers like the ordinary stub and counts every request that actually
/// reaches it.
///
/// The counter is shared rather than read back through `Localized::inner`,
/// because what has to be counted sits two wrappers down — `Localized` over
/// `DiskCachedApi` over this — and `DiskCachedApi` exposes no inner of its
/// own. A handle the test keeps needs neither.
struct CountingApi {
    asked: Arc<AtomicUsize>,
}

impl TmdbApi for CountingApi {
    async fn get_json(
        &self,
        path: &str,
        _query: &[(&str, String)],
    ) -> anyhow::Result<serde_json::Value> {
        self.asked.fetch_add(1, Ordering::SeqCst);
        let id: u64 =
            path.rsplit('/').next().and_then(|tail| tail.parse().ok()).unwrap_or_default();
        Ok(serde_json::json!({ "id": id, "poster_path": "/a.jpg" }))
    }
}

/// A title is asked about once, however many answers are taken out of it.
///
/// Driven through `verify_then_fetch`, which is where the composition is
/// built, so this fails if the cache is ever dropped from it — not through
/// a cache a test assembled for itself, which would prove only that
/// `DiskCachedApi` works.
#[tokio::test]
async fn a_title_is_asked_about_once_however_many_answers_are_taken_from_it() {
    let dir = tempfile::tempdir().unwrap();
    catalog_with_kinds(dir.path(), &[("movie", Some(550)), ("ep", Some(1399))]);
    install_crypto_provider();
    let core = core_at(dir.path());
    let plan = plan_fetch(&core, "en-US").unwrap();
    let asked = Arc::new(AtomicUsize::new(0));

    verify_then_fetch(
        &core,
        CountingApi { asked: Arc::clone(&asked) },
        &offline_client(),
        &plan.artwork_dir,
        &plan.language,
        &plan.titles,
        plan.without_id,
    )
    .await
    .expect("a stub that answers accepts the key");

    // One `/authentication` — deliberately off the cache, so it is always a
    // real request — and one apiece for `/movie/550` and `/tv/1399`, shared
    // between the walk that reads a poster path out of the payload and the
    // walk that reads a description out of the same one.
    assert_eq!(asked.load(Ordering::SeqCst), 3, "a title was asked about more than once");
}

/// Every episode of a series is one title, so a season is one request and
/// not one per row — the arithmetic a library of a few hundred episodes
/// depends on, and the reason the walk is over titles rather than sets.
#[tokio::test]
async fn a_series_costs_one_request_however_many_episodes_it_has() {
    let dir = tempfile::tempdir().unwrap();
    catalog_with_kinds(dir.path(), &[("ep", Some(1399)); 8]);
    install_crypto_provider();
    let core = core_at(dir.path());
    let plan = plan_fetch(&core, "en-US").unwrap();
    let asked = Arc::new(AtomicUsize::new(0));

    verify_then_fetch(
        &core,
        CountingApi { asked: Arc::clone(&asked) },
        &offline_client(),
        &plan.artwork_dir,
        &plan.language,
        &plan.titles,
        plan.without_id,
    )
    .await
    .expect("a stub that answers accepts the key");

    assert_eq!(asked.load(Ordering::SeqCst), 2, "one key check and one /tv/1399");
}
