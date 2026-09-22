//! A disk cache in front of any [`TmdbApi`], so resolving the same file twice
//! never asks TMDB twice.

use std::path::PathBuf;

use anyhow::{Context, Result};
use serde_json::Value;
use sha2::{Digest, Sha256};

use crate::tmdb_client::TmdbApi;

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
