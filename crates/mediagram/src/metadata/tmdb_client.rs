//! HTTP access to TMDB plus an on-disk cache; kept generic behind `TmdbApi`
//! so `resolve` and its tests never need a live network connection.
#![allow(dead_code)] // Consumed once `add` wires metadata resolution in.

use std::path::{Path, PathBuf};
use std::time::Duration;

use anyhow::{Context, Result, bail};
use serde_json::Value;
use sha2::{Digest, Sha256};

const BASE_URL: &str = "https://api.themoviedb.org/3";
const MAX_RETRIES: u32 = 3;

/// A single TMDB endpoint call, keyed by path + query params. Implemented by
/// the real HTTP client and by fixture/stub doubles in tests.
pub trait TmdbApi {
    async fn get_json(&self, path: &str, query: &[(&str, String)]) -> Result<Value>;
}

/// Direct HTTP access to the TMDB v3 API, authenticating via the `api_key`
/// query parameter. Retries on HTTP 429, honoring `Retry-After` up to
/// `MAX_RETRIES` times before giving up.
pub struct TmdbClient {
    http: reqwest::Client,
    api_key: String,
}

impl TmdbClient {
    pub fn new(api_key: impl Into<String>) -> Self {
        Self {
            http: reqwest::Client::new(),
            api_key: api_key.into(),
        }
    }

    /// A real client wrapped with the disk cache, ready to pass to `resolve`.
    pub fn with_cache(api_key: &str, cache_dir: &Path) -> DiskCachedApi<TmdbClient> {
        DiskCachedApi::new(TmdbClient::new(api_key), cache_dir)
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
            pairs.append_pair("api_key", &self.api_key);
            for (key, value) in query {
                pairs.append_pair(key, value);
            }
        }

        let mut retries = 0;
        loop {
            let resp = self
                .http
                .get(url.clone())
                .send()
                .await
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
        std::fs::create_dir_all(&self.cache_dir)
            .with_context(|| format!("creating tmdb cache dir {}", self.cache_dir.display()))?;
        std::fs::write(&file, serde_json::to_vec(&value)?)
            .with_context(|| format!("writing tmdb cache file {}", file.display()))?;
        Ok(value)
    }
}
