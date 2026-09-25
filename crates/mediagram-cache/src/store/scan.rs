//! Walking `root` at startup to find the chunks a previous run left behind.

use std::fs;
use std::io;
use std::path::Path;
use std::time::SystemTime;

use super::index::ChunkKey;

/// Every chunk file under `root`, skipping `.tmp` and each set's `total`
/// marker. Order is unspecified; [`super::index::Index::from_scan`] sorts
/// it by mtime.
pub(super) fn scan(root: &Path) -> io::Result<Vec<(ChunkKey, u64, SystemTime)>> {
    let mut found = Vec::new();
    for entry in fs::read_dir(root)? {
        let entry = entry?;
        if entry.file_name() == ".tmp" || !entry.file_type()?.is_dir() {
            continue;
        }
        let id = entry.file_name().to_string_lossy().into_owned();
        for chunk_entry in fs::read_dir(entry.path())? {
            let chunk_entry = chunk_entry?;
            let name = chunk_entry.file_name();
            let name = name.to_string_lossy();
            if name == "total" {
                continue;
            }
            let Ok(n) = name.parse::<u32>() else {
                continue;
            };
            let metadata = chunk_entry.metadata()?;
            found.push((
                (id.clone(), n),
                metadata.len(),
                metadata.modified().unwrap_or(SystemTime::UNIX_EPOCH),
            ));
        }
    }
    Ok(found)
}
