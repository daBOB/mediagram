//! The one HTTP client this crate builds, deliberately not reqwest's default
//! TLS setup.
//!
//! reqwest's `rustls` feature routes certificate verification through
//! `rustls-platform-verifier`, which on Android calls into a Kotlin
//! companion object over JNI and panics ("expect rustls-platform-verifier to
//! be initialized") without it. This crate has no such component, so this
//! module builds a plain `rustls::ClientConfig` — a webpki verifier over the
//! Mozilla root set, the same trust anchors every browser ships — and hands
//! it to reqwest as a preconfigured backend, bypassing the platform
//! verifier entirely rather than trying to satisfy it.

use std::sync::Arc;

use super::CoreError;

/// Installs this crate's rustls crypto provider, once per process.
///
/// `rustls-no-provider` is enabled precisely so none is chosen for us — see
/// this module's own doc comment — which means every `reqwest::Client` this
/// crate builds needs one already installed, or it panics at construction
/// time, before a single byte goes over the wire. `client()` below calls
/// this itself, so a caller that only ever builds a client through `client()`
/// never has to call this directly — `mediagram-tmdb`'s `TmdbClient` no
/// longer builds a client of its own to catch out; it takes the one
/// `client()` returns. This is exported anyway for anything that builds a
/// `reqwest::Client` some other way and needs the same provider ahead of it.
/// Installing twice (a second `Core`, a second call in tests, or both this
/// and `client()` in the same request) is not an error worth surfacing:
/// whichever provider got there first is fine.
pub(super) fn install_provider() {
    let _ = rustls::crypto::ring::default_provider().install_default();
}

/// Builds the client this crate uses for every outbound HTTPS request.
pub(super) fn client() -> Result<reqwest::Client, CoreError> {
    install_provider();

    let mut roots = rustls::RootCertStore::empty();
    roots.extend(webpki_roots::TLS_SERVER_ROOTS.iter().cloned());

    let provider = Arc::new(rustls::crypto::ring::default_provider());
    let tls = rustls::ClientConfig::builder_with_provider(provider)
        .with_safe_default_protocol_versions()
        .map_err(|err| CoreError::Network(format!("building the TLS client failed: {err}")))?
        .with_root_certificates(roots)
        .with_no_client_auth();

    reqwest::Client::builder()
        .tls_backend_preconfigured(tls)
        .build()
        .map_err(|err| CoreError::Network(format!("building the HTTP client failed: {err}")))
}

#[cfg(test)]
mod tests {
    use super::*;

    /// The whole point of building our own TLS config: it must actually
    /// complete a handshake, not merely compile and link. `example.com` is
    /// IANA-reserved for exactly this — stable, TLS-terminating, meant to be
    /// depended on. Needs network egress, so it is not part of the default
    /// run: `cargo test -p mediagram-core --lib -- --ignored the_client`.
    ///
    /// This proves webpki-plus-ring completes a handshake on whatever host
    /// runs it — nothing more. It says nothing about the platform verifier
    /// this client is built to avoid: on Linux that verifier is reachable
    /// and would also pass here, so a green run of this test is not by
    /// itself evidence that Android's panic is avoided, only that the
    /// replacement path is not itself broken.
    #[tokio::test]
    #[ignore = "needs network egress; run with --ignored"]
    async fn the_client_completes_a_real_tls_handshake() {
        let client = client().expect("the client builds");
        let response = client
            .get("https://example.com")
            .send()
            .await
            .expect("the TLS handshake and request complete");
        assert!(response.status().is_success());
    }
}
