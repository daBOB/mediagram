//! Walking `root` at startup to find the chunks a previous run left behind.
//!
//! No single bad entry aborts this — a directory whose name is not a valid
//! set id (a stray `lost+found`), an unreadable entry, a chunk the wrong
//! length for what its set's `total` says — is skipped and logged instead.
//! Only `root` itself being unreadable is fatal, same as before.

use std::fs;
use std::io;
use std::path::Path;
use std::time::SystemTime;

use crate::rules;

use super::index::ChunkKey;

/// Every chunk file under `root` whose set id and length both check out.
/// Order is unspecified; [`super::index::Index::from_scan`] sorts it by
/// mtime.
pub(super) fn scan(root: &Path) -> io::Result<Vec<(ChunkKey, u64, SystemTime)>> {
    let mut found = Vec::new();
    for entry in fs::read_dir(root)? {
        let Some(entry) = log_err(entry, root) else {
            continue;
        };
        if entry.file_name() == ".tmp" {
            continue;
        }
        match entry.file_type() {
            Ok(t) if t.is_dir() => {}
            Ok(_) => continue,
            Err(err) => {
                warn(&entry.path(), &err);
                continue;
            }
        }
        let id = entry.file_name().to_string_lossy().into_owned();
        if !rules::valid_id(&id) {
            tracing::warn!(
                "scan: skipping {}, not a valid set id",
                entry.path().display()
            );
            continue;
        }
        scan_set(&entry.path(), &id, &mut found);
    }
    Ok(found)
}

/// Everything found under one set's directory, dropping — and deleting —
/// any chunk whose length does not fit its recorded `total`. A set with no
/// readable `total` is scanned without that check: there is nothing to
/// validate a length against, so every chunk is trusted as found.
fn scan_set(dir: &Path, id: &str, found: &mut Vec<(ChunkKey, u64, SystemTime)>) {
    let total = read_total(dir);
    let Ok(entries) = fs::read_dir(dir).inspect_err(|err| warn(dir, err)) else {
        return;
    };
    for entry in entries {
        let Some(entry) = log_err(entry, dir) else {
            continue;
        };
        let name = entry.file_name();
        let name = name.to_string_lossy();
        if name == "total" {
            continue;
        }
        let Ok(n) = name.parse::<u32>() else {
            continue;
        };
        let Ok(metadata) = entry.metadata().inspect_err(|err| warn(&entry.path(), err)) else {
            continue;
        };
        let len = metadata.len();
        if let Some(total) = total {
            if rules::check_length(n, len, total).is_err() {
                tracing::warn!(
                    "scan: dropping {} — {len} bytes does not fit a total of {total}",
                    entry.path().display()
                );
                let _ = fs::remove_file(entry.path());
                continue;
            }
        }
        found.push((
            (id.to_string(), n),
            len,
            metadata.modified().unwrap_or(SystemTime::UNIX_EPOCH),
        ));
    }
}

fn read_total(dir: &Path) -> Option<u64> {
    fs::read_to_string(dir.join("total"))
        .ok()?
        .trim()
        .parse()
        .ok()
}

fn log_err<T>(result: io::Result<T>, context: &Path) -> Option<T> {
    result.inspect_err(|err| warn(context, err)).ok()
}

fn warn(path: &Path, err: &io::Error) {
    tracing::warn!(
        "scan: skipping an unreadable entry in {}: {err}",
        path.display()
    );
}
