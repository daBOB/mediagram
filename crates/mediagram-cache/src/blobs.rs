//! Small answers and images (TMDB JSON, posters) kept beside the video
//! chunks but under their own budget: evicting a blob never touches a chunk
//! and the reverse, so a burst of poster downloads cannot push a film out.
//!
//! Layout under `{root}/blobs`: `tmdb/{key[0..2]}/{key}` and
//! `art/{size}/{name}`. Every write is staged in `.tmp` and renamed into
//! place, so a reader sees a file whole or not at all. The index lives in
//! memory behind one lock and is rebuilt by a scan at open, oldest mtime
//! first; the lock is held across the rename so index and disk never
//! disagree about which of two racing writers won.

use std::fs;
use std::io;
use std::path::{Path, PathBuf};
use std::sync::{Mutex, MutexGuard};
use std::time::SystemTime;

use crate::store::publish::temp_name;

mod index;
mod names;
use index::Index;
use names::relative_path;
pub use names::{valid_art_name, valid_art_size, valid_tmdb_key};

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum BlobKind {
    /// JSON answers: a newer write replaces the older one.
    Tmdb,
    /// Images: the first write stands.
    Art,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum BlobPut {
    Created,
    Replaced,
    AlreadyHeld,
}

pub struct BlobStore {
    root: PathBuf,
    budget: u64,
    index: Mutex<Index>,
}

impl BlobStore {
    /// Scans `{root}/blobs` for what an earlier run left and sweeps its
    /// staging directory; it is created if absent.
    pub fn open(root: PathBuf, budget: u64) -> io::Result<BlobStore> {
        let root = root.join("blobs");
        let _ = fs::remove_dir_all(root.join(".tmp"));
        fs::create_dir_all(root.join(".tmp"))?;
        let mut found = Vec::new();
        for top in ["tmdb", "art"] {
            for dir in subdirs(&root.join(top)) {
                for file in fs::read_dir(&dir)?.flatten() {
                    let Ok(meta) = file.metadata() else { continue };
                    let rel = file
                        .path()
                        .strip_prefix(&root)
                        .ok()
                        .map(|p| p.to_string_lossy().into_owned());
                    if let (true, Some(rel)) = (meta.is_file(), rel) {
                        found.push((
                            meta.modified().unwrap_or(SystemTime::UNIX_EPOCH),
                            rel,
                            meta.len(),
                        ));
                    }
                }
            }
        }
        found.sort();
        let mut index = Index::default();
        for (_, rel, len) in found {
            index.insert(&rel, len);
        }
        let store = BlobStore {
            root,
            budget,
            index: Mutex::new(index),
        };
        store.evict_over_budget(&mut store.lock());
        Ok(store)
    }

    fn lock(&self) -> MutexGuard<'_, Index> {
        self.index.lock().unwrap_or_else(|e| e.into_inner())
    }

    fn evict_over_budget(&self, index: &mut Index) {
        while index.held > self.budget {
            let Some(rel) = index.pop_oldest() else { break };
            let _ = fs::remove_file(self.root.join(rel));
        }
    }

    /// The bytes held under `name`, counting the read as a use. A name that
    /// is not valid for `kind` is simply not held.
    pub fn get(&self, kind: BlobKind, name: &str) -> io::Result<Option<Vec<u8>>> {
        let Some(rel) = relative_path(kind, name) else {
            return Ok(None);
        };
        if !self.lock().touch(&rel) {
            return Ok(None);
        }
        match fs::read(self.root.join(rel)) {
            Ok(bytes) => Ok(Some(bytes)),
            Err(e) if e.kind() == io::ErrorKind::NotFound => Ok(None),
            Err(e) => Err(e),
        }
    }

    /// The length held under `name`, without counting it as a use.
    pub fn head(&self, kind: BlobKind, name: &str) -> io::Result<Option<u64>> {
        let Some(rel) = relative_path(kind, name) else {
            return Ok(None);
        };
        Ok(self.lock().entries.get(&rel).map(|e| e.0))
    }

    /// Stores `body`. A body larger than the whole budget is refused with
    /// `InvalidInput` before anything is evicted for it.
    pub fn put(&self, kind: BlobKind, name: &str, body: &[u8]) -> io::Result<BlobPut> {
        let rel = relative_path(kind, name)
            .ok_or_else(|| io::Error::new(io::ErrorKind::InvalidInput, "invalid blob name"))?;
        if body.len() as u64 > self.budget {
            return Err(io::Error::new(
                io::ErrorKind::InvalidInput,
                "larger than the blob budget",
            ));
        }
        let tmp = self.root.join(".tmp").join(temp_name());
        fs::write(&tmp, body)?;
        let dest = self.root.join(&rel);
        let mut index = self.lock();
        let held = index.entries.contains_key(&rel);
        if held && kind == BlobKind::Art {
            let _ = fs::remove_file(&tmp);
            index.touch(&rel);
            return Ok(BlobPut::AlreadyHeld);
        }
        let placed = dest
            .parent()
            .map_or(Ok(()), fs::create_dir_all)
            .and_then(|()| fs::rename(&tmp, &dest));
        if let Err(e) = placed {
            let _ = fs::remove_file(&tmp);
            return Err(e);
        }
        index.insert(&rel, body.len() as u64);
        self.evict_over_budget(&mut index);
        Ok(if held {
            BlobPut::Replaced
        } else {
            BlobPut::Created
        })
    }

    /// `(held bytes, budget)`.
    pub fn status(&self) -> (u64, u64) {
        (self.lock().held, self.budget)
    }
}

fn subdirs(dir: &Path) -> Vec<PathBuf> {
    fs::read_dir(dir)
        .into_iter()
        .flatten()
        .flatten()
        .filter(|e| e.file_type().is_ok_and(|t| t.is_dir()))
        .map(|e| e.path())
        .collect()
}

#[cfg(test)]
#[path = "blobs_tests.rs"]
mod tests;
