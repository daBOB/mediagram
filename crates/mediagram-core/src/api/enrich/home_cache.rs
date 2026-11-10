//! `Core::set_home_cache`: the household's cache server, which Kotlin finds
//! and pairs with. TMDB answers and artwork are asked of it before TMDB, and a
//! device with no TMDB key asks only it.

use std::sync::Arc;

use mediagram_tmdb::home_cache::HomeCache;

use crate::api::Core;

#[uniffi::export]
impl Core {
    /// Names the home cache server and the pairing token that lets this
    /// device add to it; a `None` url forgets the server. Kotlin calls this on
    /// every change, so a server it has lost is never waited on.
    pub fn set_home_cache(&self, url: Option<String>, token: Option<String>) {
        let home = url.filter(|url| !url.is_empty()).and_then(|url| {
            let http = crate::http::client()
                .inspect_err(|err| tracing::warn!(error = %err, "the home cache server cannot be asked"))
                .ok()?;
            Some(Arc::new(HomeCache::new(http, url, token.filter(|t| !t.is_empty()))))
        });
        *self.home_cache.lock().unwrap_or_else(std::sync::PoisonError::into_inner) = home;
    }
}

impl Core {
    /// The server [`Core::set_home_cache`] last named, if any.
    pub(in crate::api) fn home_cache(&self) -> Option<Arc<HomeCache>> {
        self.home_cache.lock().unwrap_or_else(std::sync::PoisonError::into_inner).clone()
    }
}
