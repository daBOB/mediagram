//! Caches the folded corpus `search::rank::Corpus::build` produces, so a
//! query re-folds the catalog only when a refresh has actually swapped the
//! version underneath it — the amortisation `search::rank`'s module doc
//! promises, and the reason a search does not cost what folding five
//! hundred summaries costs on every keystroke.

use std::path::{Path, PathBuf};
use std::sync::Arc;

use crate::catalog::SearchableSet;
use crate::search::rank::Corpus;

/// The corpus built for whichever catalog version was current last time,
/// and which version that was.
///
/// `pub(in crate::api)`, not `pub(super)`: `Core` holds one of these
/// directly (`api/mod.rs`), two levels up from where this is declared.
#[derive(Default)]
pub(in crate::api) struct SearchCache(Option<(Option<PathBuf>, Arc<Corpus>)>);

impl SearchCache {
    /// The corpus for `version`, reused as-is when `version` still matches
    /// whichever one was cached last, rebuilt from `sets` otherwise.
    pub(in crate::api) fn get_or_build(&mut self, version: Option<PathBuf>, sets: &[SearchableSet]) -> Arc<Corpus> {
        if let Some((cached_version, corpus)) = &self.0 {
            if *cached_version == version {
                return Arc::clone(corpus);
            }
        }
        let corpus = Arc::new(Corpus::build(sets));
        self.0 = Some((version, Arc::clone(&corpus)));
        corpus
    }
}

/// Which catalog version is installed right now, resolving the `current`
/// symlink the way `store::facts`'s `published_at` does — the symlink's own
/// path never changes across a refresh, only what it points at, so the
/// path alone would cache a corpus forever and never notice a new one
/// installed under it. `None` when nothing is installed, or the link
/// cannot be resolved; either way, a cache key nothing else will ever equal
/// by accident.
pub(super) fn version_key(current_dir: &Path) -> Option<PathBuf> {
    std::fs::canonicalize(current_dir).ok()
}

#[cfg(test)]
#[path = "cache_tests.rs"]
mod tests;
