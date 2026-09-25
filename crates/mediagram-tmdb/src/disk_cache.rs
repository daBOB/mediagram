//! A disk cache in front of any [`TmdbApi`]. Populated responses are reused;
//! empty search pages are fetched again so a later search can find new titles.

use std::path::PathBuf;
use std::time::Duration;

use anyhow::{Context, Result};
use serde_json::Value;
use sha2::{Digest, Sha256};

use crate::tmdb_client::TmdbApi;

/// Wraps any `TmdbApi` with a disk cache keyed by sha256(path + sorted
/// query). Empty search pages are deliberately not cached and are retried.
pub struct DiskCachedApi<A> {
    inner: A,
    cache_dir: PathBuf,
    /// Entries older than this are asked for again. `None`, the default,
    /// keeps every entry for good: what `add` looked up stays answerable
    /// with no key and no network.
    max_age: Option<Duration>,
}

impl<A: TmdbApi> DiskCachedApi<A> {
    pub fn new(inner: A, cache_dir: impl Into<PathBuf>) -> Self {
        Self {
            inner,
            cache_dir: cache_dir.into().join("tmdb-cache"),
            max_age: None,
        }
    }

    /// Asks again for any entry older than `age`. A refresh that fails keeps
    /// serving the old entry: stale provider data beats none.
    #[must_use]
    pub fn with_max_age(mut self, age: Duration) -> Self {
        self.max_age = Some(age);
        self
    }

    /// Whether the entry at `file` is young enough to answer without asking.
    fn fresh(&self, file: &std::path::Path) -> bool {
        let Some(max_age) = self.max_age else {
            return true;
        };
        std::fs::metadata(file)
            .and_then(|meta| meta.modified())
            .ok()
            .and_then(|modified| modified.elapsed().ok())
            .is_some_and(|age| age < max_age)
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
        let cached: Option<Value> = std::fs::read(&file)
            .ok()
            .and_then(|bytes| serde_json::from_slice(&bytes).ok());
        if let Some(value) = &cached
            && self.fresh(&file)
        {
            return Ok(value.clone());
        }

        let value = match (self.inner.get_json(path, query).await, cached) {
            (Ok(value), _) => value,
            (Err(err), Some(stale)) => {
                tracing::warn!(path, error = %err, "keeping a stale TMDB entry; the refresh failed");
                return Ok(stale);
            }
            (Err(err), None) => return Err(err),
        };
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
