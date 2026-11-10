//! Client for the household's LAN cache server: a TMDB answer fetched once is
//! read by every player. Reads are open; writes carry the shared token's
//! signature. The server is a convenience, never a dependency: after a
//! failure it is left alone for a while and the caller carries on as before.

use std::sync::Mutex;
use std::time::{Duration, Instant};

use mlib_spec::lan_signature::{SCHEME, sign};

use crate::disk_cache::cache_key;

/// A slow server must not stall a player that can reach TMDB itself.
const GET_TIMEOUT: Duration = Duration::from_secs(2);
/// How long a failed server is left alone.
const SKIP_FOR: Duration = Duration::from_secs(30);

pub struct HomeCache {
    http: reqwest::Client,
    url: String,
    token: Option<String>,
    skip_until: Mutex<Option<Instant>>,
}

impl HomeCache {
    pub fn new(http: reqwest::Client, url: impl Into<String>, token: Option<String>) -> Self {
        Self {
            http,
            url: url.into().trim_end_matches('/').to_string(),
            token,
            skip_until: Mutex::new(None),
        }
    }

    fn skipping(&self) -> bool {
        let mut until = self.skip_until.lock().unwrap_or_else(|e| e.into_inner());
        match *until {
            Some(t) if t > Instant::now() => true,
            Some(_) => {
                *until = None;
                false
            }
            None => false,
        }
    }

    fn fail(&self) {
        *self.skip_until.lock().unwrap_or_else(|e| e.into_inner()) =
            Some(Instant::now() + SKIP_FOR);
    }

    /// The stored bytes, or `None` for a miss, a refusal or an unreachable server.
    pub async fn get(&self, path: &str) -> Option<Vec<u8>> {
        if self.skipping() {
            return None;
        }
        let sent = self
            .http
            .get(format!("{}{path}", self.url))
            .timeout(GET_TIMEOUT)
            .send()
            .await;
        let resp = sent.inspect_err(|_| self.fail()).ok()?;
        if !resp.status().is_success() {
            return None;
        }
        resp.bytes()
            .await
            .inspect_err(|_| self.fail())
            .ok()
            .map(|b| b.to_vec())
    }

    /// Whether the server already holds `path`.
    pub async fn has(&self, path: &str) -> bool {
        if self.skipping() {
            return false;
        }
        let sent = self
            .http
            .head(format!("{}{path}", self.url))
            .timeout(GET_TIMEOUT)
            .send()
            .await;
        sent.inspect_err(|_| self.fail())
            .is_ok_and(|r| r.status().is_success())
    }

    /// Stores `body` at `path`. Without the token, or while the server is
    /// being skipped, nothing is sent; a failed upload is not worth reporting
    /// because the next player simply uploads it again.
    pub async fn put(&self, path: &str, body: &[u8]) {
        let Some(token) = &self.token else { return };
        if self.skipping() {
            return;
        }
        let sig = sign(token, "PUT", path, body.len() as u64, body);
        let sent = self
            .http
            .put(format!("{}{path}", self.url))
            .header("Authorization", format!("{SCHEME} {sig}"))
            .body(body.to_vec())
            .timeout(Duration::from_secs(30))
            .send()
            .await;
        let _ = sent.inspect_err(|_| self.fail());
    }
}

/// The server key of a TMDB request: the disk key, except that a language
/// like `de-DE` counts as `de`, since TMDB's answers differ little and the
/// household should share one entry.
pub fn tmdb_path(path: &str, query: &[(&str, String)]) -> String {
    let query: Vec<(&str, String)> = query
        .iter()
        .map(|(k, v)| {
            let v = if *k == "language" {
                v.split(['-', '_']).next().unwrap_or(v).to_ascii_lowercase()
            } else {
                v.clone()
            };
            (*k, v)
        })
        .collect();
    format!("/v1/tmdb/{}", cache_key(path, &query))
}

/// The server path of an image: its size and the file name of its TMDB path.
pub fn art_path(size: &str, tmdb_path: &str) -> String {
    let name = tmdb_path.rsplit('/').next().unwrap_or(tmdb_path);
    format!("/v1/art/{size}/{name}")
}

#[cfg(test)]
#[path = "home_cache_tests.rs"]
mod tests;
