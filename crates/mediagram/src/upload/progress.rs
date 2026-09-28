//! How far the upload in progress has got, for another process to read.
//!
//! Deliberately not in `library.db`. This changes every second or two and is
//! worthless once the run ends, where the index is the durable record of what
//! the library is; writing progress into it would put churn into the one file
//! that must survive, and would have a reader contending with the writer for
//! the same database while an upload is the thing under way.
//!
//! So it is one small file per upload, rewritten whole, and read as a hint.
//! Per upload because `upload_slots` lets several run at once: one shared
//! file had them overwrite each other, and the first to finish cleared it. A reader
//! must treat a stale one as nothing: the process that wrote it can die
//! without clearing it, and a number that stopped moving an hour ago says
//! nothing about now.

use std::path::{Path, PathBuf};
use std::sync::Arc;
use std::sync::atomic::{AtomicU64, Ordering};
use std::time::Duration;

use serde::{Deserialize, Serialize};

/// The single shared file of versions before `upload_slots`; still read, as
/// an older process may be running beside a newer one.
pub fn path_in(data_dir: &Path) -> PathBuf {
    data_dir.join("upload-progress.json")
}

/// This set's own file.
pub fn path_for(data_dir: &Path, set_id: &str) -> PathBuf {
    data_dir.join(format!("upload-progress-{set_id}.json"))
}

/// How often the file is rewritten. Often enough that a reader watching it
/// sees movement, rarely enough that it is nothing next to the bytes going
/// over the wire.
const INTERVAL: Duration = Duration::from_secs(2);

/// Past this, a reader treats the file as saying nothing: whatever wrote it
/// is gone, or wedged, and either way the number is not about now.
pub const STALE_AFTER_SECONDS: i64 = 30;

/// What the uploader last managed of the part it is on.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
pub struct Progress {
    pub set_id: String,
    /// The part being sent, counting from zero as the index does.
    pub part: u32,
    pub parts: u32,
    /// Bytes of this part handed to the transport so far.
    pub bytes_sent: u64,
    /// Bytes of this part in total.
    pub part_bytes: u64,
    /// Bytes of the whole set already done, not counting this part.
    pub bytes_done: u64,
    pub set_bytes: u64,
    pub updated_at: i64,
}

impl Progress {
    /// Whether this was written recently enough to mean anything.
    pub fn is_fresh(&self, now: i64) -> bool {
        now.saturating_sub(self.updated_at) <= STALE_AFTER_SECONDS
    }

    /// Bytes of the whole set sent, as far as anyone knows.
    pub fn set_bytes_sent(&self) -> u64 {
        self.bytes_done.saturating_add(self.bytes_sent)
    }
}

/// Every upload's file, the shared legacy one included.
///
/// A malformed file is treated as absent rather than as an error: it is a
/// hint, and a half-written one must not stop a reader reporting everything
/// else it knows.
pub fn read_all(data_dir: &Path) -> Vec<Progress> {
    let Ok(entries) = std::fs::read_dir(data_dir) else {
        return Vec::new();
    };
    entries
        .flatten()
        .filter(|e| {
            let name = e.file_name();
            let name = name.to_string_lossy();
            name.starts_with("upload-progress") && name.ends_with(".json")
        })
        .filter_map(|e| std::fs::read_to_string(e.path()).ok())
        .filter_map(|text| serde_json::from_str(&text).ok())
        .collect()
}

/// Removes this set's file, when it is no longer being uploaded.
pub fn clear(data_dir: &Path, set_id: &str) {
    let _ = std::fs::remove_file(path_for(data_dir, set_id));
}

/// Rewrites the file while `counter` moves, until the returned handle drops.
///
/// The counter is updated by the reader feeding the transport, which happens
/// inside somebody else's poll loop; writing from there would put file I/O
/// on the path every chunk takes. A task on a timer keeps the two apart.
pub struct Reporter {
    task: tokio::task::JoinHandle<()>,
}

impl Reporter {
    pub fn start(data_dir: &Path, counter: Arc<AtomicU64>, shape: Progress) -> Reporter {
        let path = path_for(data_dir, &shape.set_id);
        let task = tokio::spawn(async move {
            let mut ticker = tokio::time::interval(INTERVAL);
            loop {
                ticker.tick().await;
                let current = Progress {
                    bytes_sent: counter.load(Ordering::Relaxed),
                    updated_at: crate::clock::now_unix(),
                    ..shape.clone()
                };
                let Ok(text) = serde_json::to_string(&current) else {
                    continue;
                };
                // A failed write is not worth a word: the upload is the
                // point, and this only describes it.
                let _ = tokio::fs::write(&path, text).await;
            }
        });
        Reporter { task }
    }
}

impl Drop for Reporter {
    fn drop(&mut self) {
        self.task.abort();
    }
}
