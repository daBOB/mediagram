//! HTTP access to TMDB plus an on-disk cache; kept generic behind `TmdbApi`
//! so `resolve` and its tests never need a live network connection.
//!
//! This crate does not own transport: `TmdbClient` takes an already-built
//! `reqwest::Client` rather than constructing one of its own. Every program
//! that links it builds that client with `mediagram_core::api::http`, on the
//! one TLS stack chosen there; a client built here would have to guess.

use std::path::Path;
use std::time::Duration;

use anyhow::{Context, Result};
use serde_json::Value;

use crate::disk_cache::DiskCachedApi;
use crate::localized::Localized;

const BASE_URL: &str = "https://api.themoviedb.org/3";
const MAX_RETRIES: u32 = 3;

/// A single TMDB endpoint call, keyed by path + query params. Implemented by
/// the real HTTP client and by fixture/stub doubles in tests.
// Consumed via `impl TmdbApi` (static dispatch), so `Send` bounds on the future are not needed.
#[allow(async_fn_in_trait)]
pub trait TmdbApi {
    async fn get_json(&self, path: &str, query: &[(&str, String)]) -> Result<Value>;
}

/// How TMDB wants a credential presented.
///
/// The settings page offers two, and people paste whichever they see first.
/// A v3 API key is 32 hex characters and goes in the `api_key` query
/// parameter; a v4 "API Read Access Token" is a JWT and goes in an
/// `Authorization: Bearer` header. Sent the wrong way, a read token answers
/// 401 with nothing to say why.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Credential {
    QueryParam,
    Bearer,
}

/// Works out which kind of credential this is.
///
/// Anything unrecognised is treated as a v3 key, which is what this client
/// always did: a wrong guess there fails loudly with a 401 rather than
/// quietly doing something else.
pub fn classify(key: &str) -> Credential {
    let key = key.trim();
    let looks_like_a_jwt =
        key.starts_with("eyJ") && key.split('.').filter(|part| !part.is_empty()).count() == 3;
    if looks_like_a_jwt {
        Credential::Bearer
    } else {
        Credential::QueryParam
    }
}

/// Direct HTTP access to the TMDB v3 API. Accepts either credential TMDB
/// issues and sends it the way that one requires. Retries on HTTP 429,
/// honoring `Retry-After` up to `MAX_RETRIES` times before giving up.
///
/// Takes its `reqwest::Client` rather than building one — see this module's
/// own doc comment for why. A constructor that built its own transport is
/// exactly the footgun being removed here, so there is no no-client variant
/// left to reach for.
pub struct TmdbClient {
    http: reqwest::Client,
    api_key: String,
    credential: Credential,
}

impl TmdbClient {
    pub fn new(http: reqwest::Client, api_key: impl Into<String>) -> Self {
        let api_key = api_key.into().trim().to_string();
        Self {
            http,
            credential: classify(&api_key),
            api_key,
        }
    }

    /// Cached on disk and asking for one language, ready to pass to
    /// `resolve`. `http` is the caller's own client — see this module's
    /// doc comment for why this crate never builds one of its own.
    pub fn with_cache(
        http: reqwest::Client,
        api_key: &str,
        cache_dir: &Path,
        language: &str,
    ) -> Localized<DiskCachedApi<TmdbClient>> {
        Localized::new(
            DiskCachedApi::new(TmdbClient::new(http, api_key), cache_dir),
            language,
        )
    }
}

/// TMDB answered, with a status other than success. A caller that has to
/// tell a rejected key (401) from anything else matches on `status`, not on
/// how the message happens to be worded.
#[derive(Debug, thiserror::Error)]
#[error("tmdb request to {path} failed with {status}: {body}")]
pub struct HttpStatus {
    pub path: String,
    pub status: u16,
    pub body: String,
}

impl TmdbApi for TmdbClient {
    async fn get_json(&self, path: &str, query: &[(&str, String)]) -> Result<Value> {
        // Built by hand (not `RequestBuilder::query`) since the crate's `query`
        // feature isn't enabled; `reqwest::Url` re-exports the `url` crate.
        let mut url = reqwest::Url::parse(&format!("{BASE_URL}{path}"))
            .with_context(|| format!("invalid tmdb path {path}"))?;
        {
            let mut pairs = url.query_pairs_mut();
            if self.credential == Credential::QueryParam {
                pairs.append_pair("api_key", &self.api_key);
            }
            for (key, value) in query {
                pairs.append_pair(key, value);
            }
        }

        let mut retries = 0;
        loop {
            let mut request = self.http.get(url.clone());
            if self.credential == Credential::Bearer {
                // Never in the URL: a bearer token in a query string reaches
                // logs, proxies and error messages.
                request = request.bearer_auth(&self.api_key);
            }
            let resp = request
                .send()
                .await
                .map_err(reqwest::Error::without_url) // the URL carries the api key
                .with_context(|| format!("tmdb request to {path} failed"))?;

            if resp.status() == reqwest::StatusCode::TOO_MANY_REQUESTS && retries < MAX_RETRIES {
                let wait_secs = resp
                    .headers()
                    .get(reqwest::header::RETRY_AFTER)
                    .and_then(|h| h.to_str().ok())
                    .and_then(|s| s.parse::<u64>().ok())
                    .unwrap_or(1);
                retries += 1;
                tokio::time::sleep(Duration::from_secs(wait_secs)).await;
                continue;
            }

            let status = resp.status();
            if !status.is_success() {
                // Read as text: an error page from a proxy or an outage is
                // not JSON, and must still say which status it was.
                let body = resp.text().await.unwrap_or_default();
                return Err(HttpStatus {
                    path: path.to_string(),
                    status: status.as_u16(),
                    body,
                }
                .into());
            }
            let body: Value = resp
                .json()
                .await
                .map_err(reqwest::Error::without_url)
                .with_context(|| format!("tmdb response for {path} was not JSON"))?;
            return Ok(body);
        }
    }
}

