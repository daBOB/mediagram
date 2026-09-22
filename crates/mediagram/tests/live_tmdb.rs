//! The configured TMDB credential, against the real API.
//!
//! Ignored by default: it needs a key and a network. Run it after changing
//! the credential or the client:
//!
//!     MEDIAGRAM_LIVE=1 cargo test -p mediagram --test live_tmdb -- --ignored --nocapture
//!
//! It exists because the two credentials TMDB issues are sent differently and
//! the wrong one answers 401 with nothing to say why. Nothing here prints the
//! key.

use mediagram::config;
use mediagram_tmdb::tmdb_client::{Credential, TmdbApi, TmdbClient, classify};

fn live() -> bool {
    std::env::var("MEDIAGRAM_LIVE").as_deref() == Ok("1")
}

#[tokio::test]
#[ignore = "hits the real TMDB API; run with MEDIAGRAM_LIVE=1 --ignored"]
async fn the_configured_credential_resolves_a_real_show() {
    if !live() {
        eprintln!("skipping: set MEDIAGRAM_LIVE=1 to run");
        return;
    }
    let cfg = config::load(None).expect("a config to load");
    let key = cfg.tmdb_key.expect("tmdb_key to be set");
    eprintln!("credential form: {:?}", classify(&key));

    // Through `with_cache`, which is what `add` uses: it is the wrapper that
    // asks for the configured language, so building the client bare would
    // test a path nothing runs.
    let cache = tempfile::tempdir().expect("a cache dir");
    let client = TmdbClient::with_cache(mediagram_core::http::client().unwrap(), &key, cache.path(), &cfg.tmdb_language);
    eprintln!("language: {}", cfg.tmdb_language);
    let found = client
        .get_json(
            "/search/tv",
            &[("query", "Spartacus House of Ashur".to_string())],
        )
        .await
        .expect("TMDB should answer; a 401 here means the credential was sent the wrong way");

    let results = found["results"].as_array().expect("results");
    assert!(!results.is_empty(), "the show should be findable");
    eprintln!(
        "first hit: {} ({})",
        results[0]["name"], results[0]["first_air_date"]
    );
}

/// The episode titles the uploader would write into captions.
#[tokio::test]
#[ignore = "hits the real TMDB API; run with MEDIAGRAM_LIVE=1 --ignored"]
async fn a_seasons_episodes_come_back_named() {
    if !live() {
        eprintln!("skipping: set MEDIAGRAM_LIVE=1 to run");
        return;
    }
    let cfg = config::load(None).expect("a config to load");
    let cache = tempfile::tempdir().expect("a cache dir");
    let client = TmdbClient::with_cache(
        mediagram_core::http::client().unwrap(),
        &cfg.tmdb_key.clone().expect("tmdb_key to be set"),
        cache.path(),
        &cfg.tmdb_language,
    );
    eprintln!("language: {}", cfg.tmdb_language);

    let season = client
        .get_json("/tv/240459/season/1", &[])
        .await
        .expect("the season should resolve");

    let episodes = season["episodes"].as_array().expect("episodes");
    assert!(!episodes.is_empty());
    for episode in episodes.iter().take(3) {
        eprintln!(
            "  S{}E{} {}",
            episode["season_number"], episode["episode_number"], episode["name"]
        );
    }
}

/// A key that is not a JWT must still go the old way, or every existing
/// install breaks the moment this ships.
#[test]
fn a_v3_key_is_still_classified_as_a_query_parameter() {
    assert_eq!(
        classify("0123456789abcdef0123456789abcdef"),
        Credential::QueryParam
    );
}
