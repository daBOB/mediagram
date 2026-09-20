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

/// Builds the client this crate uses for every outbound HTTPS request.
pub(super) fn client() -> Result<reqwest::Client, CoreError> {
    // Installing twice (a second `Core`, or a second call in tests) is not
    // an error worth surfacing: whichever provider got there first is fine.
    let _ = rustls::crypto::ring::default_provider().install_default();

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
    /// depended on. Needs network egress; there is no way to prove a real
    /// handshake without one.
    #[tokio::test]
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
