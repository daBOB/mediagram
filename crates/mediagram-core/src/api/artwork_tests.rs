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

/// `TmdbClient::new` — inside a crate this one does not control — builds a
/// bare `reqwest::Client` at construction time and panics immediately with
/// no crypto provider installed, before a request is ever sent. `cargo
/// test -p mediagram-core` (what this runs under, and what
/// `scripts/build-android-core.sh` actually cross-compiles) has no wider
/// workspace crate pulling a provider in for free the way a whole-workspace
/// `cargo test --all` does, so this is the scope that would have shown the
/// panic. No network is reached: the panic happens before any request, so
/// proving construction alone succeeds once `install_provider` has run is
/// the whole test.
#[test]
fn the_real_tmdb_client_builds_once_the_provider_is_installed() {
    http::install_provider();
    let _ = TmdbClient::with_cache("fake-key", Path::new("/tmp"), "en-US");
}
