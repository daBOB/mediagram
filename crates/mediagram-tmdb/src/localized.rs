//! Asking TMDB in one language on every request.

use anyhow::Result;
use serde_json::Value;

use crate::tmdb_client::TmdbApi;

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

impl<A: TmdbApi> Localized<crate::disk_cache::DiskCachedApi<A>> {
    /// Asks again for cached answers older than `age`; see
    /// [`DiskCachedApi::with_max_age`](crate::disk_cache::DiskCachedApi::with_max_age).
    #[must_use]
    pub fn refreshing(mut self, age: std::time::Duration) -> Self {
        self.inner = self.inner.with_max_age(age);
        self
    }
}
