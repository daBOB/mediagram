//! Evicting down to budget, and cleaning up after it.

use std::fs;
use std::path::{Path, PathBuf};

use super::index::Index;

fn set_dir(root: &Path, id: &str) -> PathBuf {
    root.join(id)
}

fn chunk_path(root: &Path, id: &str, n: u32) -> PathBuf {
    set_dir(root, id).join(n.to_string())
}

/// Evicts down to budget, then — for any set that eviction just took down
/// to zero chunks — removes its now-meaningless `total` marker and
/// directory too. Left behind, a stale `total` would 409 a PUT for a set
/// this store no longer holds a single byte of.
pub(super) fn evict_over_budget(root: &Path, budget: u64, index: &mut Index) {
    for (id, n) in index.evict_over_budget(budget) {
        let _ = fs::remove_file(chunk_path(root, &id, n));
        remove_set_dir_if_empty(root, &id);
    }
}

fn remove_set_dir_if_empty(root: &Path, id: &str) {
    let dir = set_dir(root, id);
    let has_chunk = fs::read_dir(&dir).into_iter().flatten().any(|entry| {
        entry.ok().is_some_and(|entry| {
            let name = entry.file_name();
            name != "total" && name.to_string_lossy().parse::<u32>().is_ok()
        })
    });
    if !has_chunk {
        let _ = fs::remove_dir_all(&dir);
    }
}
