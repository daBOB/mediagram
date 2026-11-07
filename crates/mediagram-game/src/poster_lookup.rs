//! Textless poster files, which the host does not hold itself: the core
//! supplies a lookup from a title's key to a file it has cached or fetches.

use std::future::Future;
use std::path::PathBuf;
use std::pin::Pin;
use std::sync::Arc;
use std::time::Duration;

/// Maps a `GameTitle.key` to a textless poster file, `None` when there is none.
pub type PosterLookup =
    Arc<dyn Fn(String) -> Pin<Box<dyn Future<Output = Option<PathBuf>> + Send>> + Send + Sync>;

/// A cached poster answers at once; one still being fetched would leave the
/// round's first steps blank, which costs more than the textless image is
/// worth, so the normal poster is served instead.
pub(crate) const LOOKUP_WAIT: Duration = Duration::from_millis(300);

pub(crate) async fn textless_file(lookup: &PosterLookup, key: &str) -> Option<PathBuf> {
    tokio::time::timeout(LOOKUP_WAIT, lookup(key.to_string())).await.ok().flatten()
}
