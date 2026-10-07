//! One set's status: a question about a set, never a use of it.

use std::io;

use super::{ChunkStore, total};

pub struct SetStatus {
    /// `None` when this store holds no `total` for the set: none of it has
    /// ever been PUT here, or every chunk it once held has since been
    /// evicted — eviction clears the recorded total along with a set's
    /// last chunk, the same as a set that was never written at all.
    pub total: Option<u64>,
    pub chunks_held: u64,
    pub bytes_held: u64,
}

impl ChunkStore {
    /// One set's status: its recorded `total`, if any, and how much of it
    /// this store currently holds. A question, not a use — unlike `get`,
    /// this touches no chunk's mtime and moves nothing in the LRU order, so
    /// polling it on its own can never change what eviction picks next.
    pub fn set_status(&self, id: &str) -> io::Result<SetStatus> {
        let total = total::read_valid(&self.total_path(id))?;
        let (chunks_held, bytes_held) = self.lock_index().set_totals(id);
        Ok(SetStatus {
            total,
            chunks_held,
            bytes_held,
        })
    }
}

#[cfg(test)]
#[path = "set_status_tests.rs"]
mod tests;
