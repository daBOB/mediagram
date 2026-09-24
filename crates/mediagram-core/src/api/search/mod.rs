//! `Core::search`: ranks the current catalog against a query the way the
//! web player's `/api/search` route does. `search::rank` does the actual
//! work; this reads the catalog, keeps [`cache::SearchCache`] fed, and
//! carries the result across the UniFFI boundary.

use std::sync::Arc;

use crate::catalog;
use crate::search::rank;

use super::{Core, CoreError, store};

pub(super) mod cache;

/// A query longer than this is not a search, it is an accident — a pasted
/// paragraph, or a barcode scanner firing into the wrong field.
const MAX_QUERY_LEN: usize = 200;

/// One hit: which set, which field earned it, and the words around a
/// summary match. Never title, path or any other field a caller already
/// holds — Kotlin already has the full `SetSummary` list from `list_sets`
/// and joins this back onto it by `set_id`.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct SearchHit {
    pub set_id: String,
    /// Which field this hit was found on: `"title"`, `"show"`, `"chap"`,
    /// `"path"` or `"summary"` — the same words the web's `/api/search`
    /// answers.
    pub matched: String,
    pub excerpt: Option<String>,
}

#[uniffi::export(async_runtime = "tokio")]
impl Core {
    /// The catalog's sets matching every word of `query`, best first.
    /// Empty for an empty query or a catalog that has not loaded yet —
    /// neither is an error, both are "nothing to show".
    pub async fn search(self: Arc<Self>, query: String) -> Vec<SearchHit> {
        // Cut before it ever reaches folding or a regex: a paragraph pasted
        // into the box should cost this call the same as a real query, not
        // a scan proportional to how much was pasted.
        let query: String = query.chars().take(MAX_QUERY_LEN).collect();
        self.blocking(move |core| run(core, &query)).await
    }
}

fn run(core: &Core, query: &str) -> Vec<SearchHit> {
    let conn = match store::open(core) {
        Ok(conn) => conn,
        // No catalog installed yet: nothing to search, and nothing wrong.
        Err(CoreError::NotFound(_)) => return Vec::new(),
        Err(err) => {
            tracing::warn!(error = %err, "the index could not be opened for a search");
            return Vec::new();
        }
    };
    let sets = match catalog::list_searchable(&conn) {
        Ok(sets) => sets,
        Err(err) => {
            tracing::warn!(error = %err, "the catalog could not be read for a search");
            return Vec::new();
        }
    };

    let version = cache::version_key(&store::current_dir(core));
    let started = std::time::Instant::now();
    let corpus = core
        .search_cache
        .lock()
        .unwrap_or_else(std::sync::PoisonError::into_inner)
        .get_or_build(version, &sets);
    let hits = rank::search(&corpus, query, &rank::collator());
    tracing::debug!(sets = sets.len(), hits = hits.len(), elapsed_ms = started.elapsed().as_millis(), "search ranked");

    hits.into_iter()
        .map(|hit| SearchHit { set_id: hit.set_id, matched: hit.matched.as_str().to_string(), excerpt: hit.excerpt })
        .collect()
}
