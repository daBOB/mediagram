//! The on-device bundle cache: `<data_dir>/subtitles/<sha>.json.gz`, one
//! small file per set. Size-capped rather than pruned on install — an index
//! without the v13 tables (a pre-v13 push) must not wipe bundles already
//! held, the way a refresh clears other staged directories.

use std::collections::HashMap;
use std::path::{Path, PathBuf};
use std::sync::{Arc, LazyLock, Mutex, PoisonError};
use std::time::SystemTime;

use mlib_spec::subtitle_bundle::{self, Bundle};
use tokio::sync::Mutex as AsyncMutex;

use crate::api::Core;
use crate::catalog_subtitles::BundleRef;

#[path = "subtitles_cache_download.rs"]
mod download;
use download::fetch_into;

/// The whole cache's own budget — roughly the library's every bundle at
/// once, so eviction is rare in practice.
const MAX_SUBTITLE_CACHE_BYTES: u64 = 64 * 1024 * 1024;

/// Per-sha locks, so two callers wanting the same bundle at once — a preload
/// and a course hold, say — fetch it once between them rather than twice.
/// One table process-wide rather than one per `Core`: there is one `Core`
/// per running app, and a shared table costs nothing a second bundle ever
/// notices.
// ponytail: global lock table, move to a per-Core field if a test ever
// needs two live Cores not to share it.
#[derive(Default)]
pub(in crate::api) struct Locks(Mutex<HashMap<String, Arc<AsyncMutex<()>>>>);

static LOCKS: LazyLock<Locks> = LazyLock::new(Locks::default);

impl Locks {
    pub(super) fn get(&self, sha: &str) -> Arc<AsyncMutex<()>> {
        self.0
            .lock()
            .unwrap_or_else(PoisonError::into_inner)
            .entry(sha.to_string())
            .or_insert_with(|| Arc::new(AsyncMutex::new(())))
            .clone()
    }
}

pub(super) fn dir(core: &Core) -> PathBuf {
    core.data_dir.join("subtitles")
}

pub(super) fn cached_path(core: &Core, sha: &str) -> PathBuf {
    dir(core).join(format!("{sha}.json.gz"))
}

/// The bundle `bundle` names, from the cache once it decodes, else fetched
/// and cached. `None` for anything that goes wrong — a bad sha shape, an
/// oversize bundle, a download or decode failure — each logged, none of it
/// a reason to fail a caller trying to play or preload something else.
pub(in crate::api) async fn fetch(core: &Core, set_id: &str, bundle: &BundleRef) -> Option<Bundle> {
    if bundle.bytes > subtitle_bundle::MAX_COMPRESSED_BYTES as u64
        || !subtitle_bundle::valid_sha256(&bundle.sha256)
    {
        tracing::warn!(
            set_id,
            bytes = bundle.bytes,
            "a subtitle bundle's own record is not one this build will fetch"
        );
        return None;
    }
    let _held = LOCKS.get(&bundle.sha256).lock_owned().await;

    let path = cached_path(core, &bundle.sha256);
    if let Some(cached) = read_cached(&path) {
        return Some(cached);
    }
    if let Err(err) = fetch_into(core, bundle, &path).await {
        tracing::warn!(set_id, error = %err, "a subtitle bundle could not be downloaded");
        return None;
    }
    trim_to_budget(core, MAX_SUBTITLE_CACHE_BYTES);
    match read_cached(&path) {
        Some(cached) => Some(cached),
        None => {
            tracing::warn!(
                set_id,
                "a freshly downloaded subtitle bundle would not decode"
            );
            let _ = std::fs::remove_file(&path);
            None
        }
    }
}

/// A cached file's decoded bundle, touching its mtime on a hit so the LRU
/// order reflects use. `None` for anything missing or corrupt — the caller
/// treats both the same, as a miss to refetch.
pub(super) fn read_cached(path: &Path) -> Option<Bundle> {
    let bytes = std::fs::read(path).ok()?;
    let decoded = subtitle_bundle::decode(&bytes).ok()?;
    if let Ok(file) = std::fs::File::open(path) {
        let _ = file.set_modified(SystemTime::now());
    }
    Some(decoded)
}

/// Evicts the least recently used cached bundles until the directory is back
/// under `budget` (always [`MAX_SUBTITLE_CACHE_BYTES`] outside a test).
/// Runs only after a write grows it — never on install, so a channel a step
/// behind never wipes bundles this device already fetched.
pub(super) fn trim_to_budget(core: &Core, budget: u64) {
    let Ok(read_dir) = std::fs::read_dir(dir(core)) else {
        return;
    };
    let mut held: Vec<(PathBuf, u64, SystemTime)> = read_dir
        .flatten()
        .filter(|entry| entry.path().extension().is_some_and(|ext| ext != "tmp"))
        .filter_map(|entry| {
            let meta = entry.metadata().ok()?;
            meta.is_file().then(|| {
                (
                    entry.path(),
                    meta.len(),
                    meta.modified().unwrap_or(SystemTime::UNIX_EPOCH),
                )
            })
        })
        .collect();

    let mut total: u64 = held.iter().map(|(_, len, _)| len).sum();
    if total <= budget {
        return;
    }
    held.sort_by_key(|(_, _, mtime)| *mtime);
    for (path, len, _) in held {
        if total <= budget {
            break;
        }
        if std::fs::remove_file(&path).is_ok() {
            total = total.saturating_sub(len);
        }
    }
}

#[cfg(test)]
#[path = "subtitles_cache_tests.rs"]
mod tests;
