use super::*;

/// Builds the same `anyhow::Error` shape
/// `mediagram_tmdb::tmdb_client::TmdbClient::get_json` bails with, so this
/// stays coupled to that crate's actual wording rather than to a guess
/// about it.
fn tmdb_style_error(path: &str, status: reqwest::StatusCode, body: &str) -> anyhow::Error {
    anyhow::anyhow!("tmdb request to {path} failed with {status}: {body}")
}

#[test]
fn a_401_response_is_recognised_as_a_rejected_key() {
    let err = tmdb_style_error(
        "/authentication",
        reqwest::StatusCode::UNAUTHORIZED,
        r#"{"status_code":7,"status_message":"Invalid API key.","success":false}"#,
    );
    assert!(rejected_the_key(&err));
}

/// A title the provider does not have is a different failure than a key it
/// will not accept, and must not be folded into the same one.
#[test]
fn a_404_response_is_not_mistaken_for_a_rejected_key() {
    let err = tmdb_style_error(
        "/movie/999999999",
        reqwest::StatusCode::NOT_FOUND,
        r#"{"status_code":34,"status_message":"The resource could not be found.","success":false}"#,
    );
    assert!(!rejected_the_key(&err));
}

/// A transport failure never reaches the `{status}` branch at all — the
/// message this crate's client builds for one carries no status number.
#[test]
fn a_transport_failure_is_not_mistaken_for_a_rejected_key() {
    let err = anyhow::anyhow!("tmdb request to /authentication failed");
    assert!(!rejected_the_key(&err));
}

/// `TmdbClient::new` used to build a bare `reqwest::Client` of its own at
/// construction time, inside a crate this one does not control — and it
/// panicked immediately with no crypto provider installed, before a
/// request was ever sent. `cargo test -p mediagram-core` (what this runs
/// under, and what `scripts/build-android-core.sh` actually cross-compiles)
/// has no wider workspace crate pulling a provider in for free the way a
/// whole-workspace `cargo test --all` does, so this is the scope that would
/// have shown the panic.
///
/// `TmdbClient` no longer builds a client of its own — it takes the one
/// `http::client()` builds, which installs the provider itself — so this
/// now proves the injected-client path construction succeeds end to end,
/// with no separate `install_provider` call of this test's own needed
/// first. No network is reached: `with_cache` only constructs.
#[test]
fn the_real_tmdb_client_builds_from_this_crates_own_client() {
    let client = http::client().expect("this crate's own client builds");
    let _ = TmdbClient::with_cache(client, "fake-key", Path::new("/tmp"), "en-US");
}
