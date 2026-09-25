//! The chunk store: files on disk, and an in-memory LRU index
//! ([`index`]) rebuilt from them at startup.
//!
//! An id's shape is validated by the caller ([`crate::rules::valid_id`])
//! before it ever reaches here — this module trusts it enough to join it
//! onto a path.

mod index;
mod scan;
mod total;

use std::fs;
use std::io::{self, Read};
use std::path::PathBuf;
use std::sync::Mutex;
use std::time::SystemTime;

use index::Index;

pub struct ChunkStore {
    root: PathBuf,
    budget: u64,
    index: Mutex<Index>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum PutOutcome {
    /// This process wrote the chunk; nobody had it before.
    Created,
    /// The chunk was already held, with the same total: the write was
    /// silently ignored, first-write-wins.
    AlreadyHeld,
    /// The set already has a different total on record.
    TotalMismatch { held: u64 },
}

pub struct Status {
    pub held_bytes: u64,
    pub budget_bytes: u64,
    pub chunks: u64,
}

impl ChunkStore {
    /// Scans `root` for chunks already on disk and sweeps any temp file a
    /// previous run left mid-write. `root` is created if it does not exist.
    pub fn open(root: PathBuf, budget: u64) -> io::Result<Self> {
        fs::create_dir_all(&root)?;
        let tmp = root.join(".tmp");
        if tmp.exists() {
            fs::remove_dir_all(&tmp)?;
        }
        fs::create_dir_all(&tmp)?;

        let found = scan::scan(&root)?;
        Ok(Self {
            root,
            budget,
            index: Mutex::new(Index::from_scan(found)),
        })
    }

    fn set_dir(&self, id: &str) -> PathBuf {
        self.root.join(id)
    }

    fn chunk_path(&self, id: &str, n: u32) -> PathBuf {
        self.set_dir(id).join(n.to_string())
    }

    fn total_path(&self, id: &str) -> PathBuf {
        self.set_dir(id).join("total")
    }

    /// Writes `body` to a name unique to this call, then publishes it with
    /// a no-overwrite link: the loser of a race sees "exists" and leaves
    /// the winner's bytes untouched, never interleaved with its own.
    pub fn put(&self, id: &str, n: u32, total: u64, body: &[u8]) -> io::Result<PutOutcome> {
        fs::create_dir_all(self.set_dir(id))?;
        if let Some(held) = total::pair(&self.total_path(id), total)? {
            if held != total {
                return Ok(PutOutcome::TotalMismatch { held });
            }
        }

        let tmp_path = self.root.join(".tmp").join(temp_name());
        fs::write(&tmp_path, body)?;
        let chunk_path = self.chunk_path(id, n);
        let publish = fs::hard_link(&tmp_path, &chunk_path);
        let _ = fs::remove_file(&tmp_path);

        match publish {
            Ok(()) => {
                let mtime = fs::metadata(&chunk_path)?
                    .modified()
                    .unwrap_or_else(|_| SystemTime::now());
                let mut index = self.index.lock().expect("chunk index lock");
                index.insert((id.to_string(), n), body.len() as u64, mtime);
                self.evict_over_budget(&mut index);
                Ok(PutOutcome::Created)
            }
            Err(e) if e.kind() == io::ErrorKind::AlreadyExists => Ok(PutOutcome::AlreadyHeld),
            Err(e) => Err(e),
        }
    }

    fn evict_over_budget(&self, index: &mut Index) {
        for (id, n) in index.evict_over_budget(self.budget) {
            let _ = fs::remove_file(self.chunk_path(&id, n));
        }
    }

    /// Reads a chunk's bytes and touches its mtime, so the LRU order
    /// survives a restart: a chunk fetched a minute ago is not the one
    /// evicted first just because the process was restarted since.
    pub fn get(&self, id: &str, n: u32) -> io::Result<Option<Vec<u8>>> {
        let mut file = match fs::File::open(self.chunk_path(id, n)) {
            Ok(f) => f,
            Err(e) if e.kind() == io::ErrorKind::NotFound => {
                self.index
                    .lock()
                    .expect("chunk index lock")
                    .forget(&(id.to_string(), n));
                return Ok(None);
            }
            Err(e) => return Err(e),
        };
        let mut bytes = Vec::new();
        file.read_to_end(&mut bytes)?;
        let now = SystemTime::now();
        let _ = file.set_modified(now);
        drop(file);
        self.index.lock().expect("chunk index lock").refresh(
            &(id.to_string(), n),
            bytes.len() as u64,
            now,
        );
        Ok(Some(bytes))
    }

    /// A chunk's length without reading its body. Does not touch mtime:
    /// only a GET counts as use for eviction purposes.
    pub fn head(&self, id: &str, n: u32) -> io::Result<Option<u64>> {
        match fs::metadata(self.chunk_path(id, n)) {
            Ok(m) => Ok(Some(m.len())),
            Err(e) if e.kind() == io::ErrorKind::NotFound => {
                self.index
                    .lock()
                    .expect("chunk index lock")
                    .forget(&(id.to_string(), n));
                Ok(None)
            }
            Err(e) => Err(e),
        }
    }

    pub fn status(&self) -> Status {
        let index = self.index.lock().expect("chunk index lock");
        Status {
            held_bytes: index.held_bytes(),
            budget_bytes: self.budget,
            chunks: index.chunk_count(),
        }
    }
}

/// A name unique enough that two concurrent PUTs of the same chunk never
/// stage into the same temp file.
fn temp_name() -> String {
    let mut buf = [0u8; 16];
    getrandom::fill(&mut buf).expect("the OS random source is available");
    hex::encode(buf)
}

#[cfg(test)]
#[path = "store_tests.rs"]
mod tests;

#[cfg(test)]
#[path = "store_lru_tests.rs"]
mod lru_tests;

#[cfg(test)]
use crate::rules;
