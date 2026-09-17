//! HTTP access to TMDB plus an on-disk cache; kept generic behind `TmdbApi`
//! so `resolve` and its tests never need a live network connection.

use std::path::{Path, PathBuf};
use std::time::Duration;

use anyhow::{Context, Result, bail};
use serde_json::Value;
use sha2::{Digest, Sha256};

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

/// Asks TMDB for one language, on every request.
///
/// TMDB answers in English unless told otherwise, so a German library gets
/// "Forsaken" where the file says "Verlassen" — TMDB has both, the client
/// simply never asked.
///
/// This wraps the cache rather than sitting inside the client, and the order
/// matters: the cache keys on the query it is handed, so the language has to
/// be in that query. Added behind the cache, two languages would share one
/// entry and the second caller would be served the first's answer.
pub struct Localized<A> {
    inner: A,
    language: String,
}

impl<A> Localized<A> {
    pub fn new(inner: A, language: impl Into<String>) -> Self {
        Self {
            inner,
            language: language.into(),
        }
    }

    /// The wrapped API. Used by tests to see what was actually asked.
    pub fn inner(&self) -> &A {
        &self.inner
    }
}

impl<A: TmdbApi> TmdbApi for Localized<A> {
    async fn get_json(&self, path: &str, query: &[(&str, String)]) -> Result<Value> {
        // A caller that names its own language means it.
        if query.iter().any(|(key, _)| *key == "language") {
            return self.inner.get_json(path, query).await;
        }
        let mut with_language = query.to_vec();
        with_language.push(("language", self.language.clone()));
        self.inner.get_json(path, &with_language).await
    }
}

/// Direct HTTP access to the TMDB v3 API. Accepts either credential TMDB
/// issues and sends it the way that one requires. Retries on HTTP 429,
/// honoring `Retry-After` up to `MAX_RETRIES` times before giving up.
pub struct TmdbClient {
    http: reqwest::Client,
    api_key: String,
    credential: Credential,
}

impl TmdbClient {
    pub fn new(api_key: impl Into<String>) -> Self {
        let api_key = api_key.into().trim().to_string();
        Self {
            http: reqwest::Client::new(),
            credential: classify(&api_key),
            api_key,
        }
    }

    /// A real client, cached on disk and asking for one language, ready to
    /// pass to `resolve`.
    pub fn with_cache(
        api_key: &str,
        cache_dir: &Path,
        language: &str,
    ) -> Localized<DiskCachedApi<TmdbClient>> {
        Localized::new(
            DiskCachedApi::new(TmdbClient::new(api_key), cache_dir),
            language,
        )
    }
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
            let body: Value = resp
                .json()
                .await
                .map_err(reqwest::Error::without_url)
                .with_context(|| format!("tmdb response for {path} was not JSON"))?;
            if !status.is_success() {
                bail!("tmdb request to {path} failed with {status}: {body}");
            }
            return Ok(body);
        }
    }
}

/// Wraps any `TmdbApi` with a disk cache keyed by sha256(path + sorted
/// query), so repeated resolves of the same file never re-hit the network.
pub struct DiskCachedApi<A> {
    inner: A,
    cache_dir: PathBuf,
}

impl<A: TmdbApi> DiskCachedApi<A> {
    pub fn new(inner: A, cache_dir: impl Into<PathBuf>) -> Self {
        Self {
            inner,
            cache_dir: cache_dir.into().join("tmdb-cache"),
        }
    }

    fn cache_key(path: &str, query: &[(&str, String)]) -> String {
        let mut pairs: Vec<(&str, &str)> = query.iter().map(|(k, v)| (*k, v.as_str())).collect();
        pairs.sort_unstable();
        let mut buf = path.to_string();
        for (key, value) in pairs {
            buf.push('\u{1f}');
            buf.push_str(key);
            buf.push('=');
            buf.push_str(value);
        }
        let mut hasher = Sha256::new();
        hasher.update(buf.as_bytes());
        hex::encode(hasher.finalize())
    }
}

impl<A: TmdbApi> TmdbApi for DiskCachedApi<A> {
    async fn get_json(&self, path: &str, query: &[(&str, String)]) -> Result<Value> {
        let file = self
            .cache_dir
            .join(format!("{}.json", Self::cache_key(path, query)));
        if let Ok(bytes) = std::fs::read(&file) {
            if let Ok(value) = serde_json::from_slice(&bytes) {
                return Ok(value);
            }
        }

        let value = self.inner.get_json(path, query).await?;
        let empty_page = value
            .get("results")
            .and_then(|r| r.as_array())
            .is_some_and(|a| a.is_empty());
        if empty_page {
            return Ok(value);
        }
        std::fs::create_dir_all(&self.cache_dir)
            .with_context(|| format!("creating tmdb cache dir {}", self.cache_dir.display()))?;
        std::fs::write(&file, serde_json::to_vec(&value)?)
            .with_context(|| format!("writing tmdb cache file {}", file.display()))?;
        Ok(value)
    }
}
