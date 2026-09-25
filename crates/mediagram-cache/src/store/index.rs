//! The in-memory LRU bookkeeping: which chunks are held, in what order, and
//! how many bytes they cost. No filesystem access happens here — the caller
//! in `store.rs` owns the chunk files and only tells this module what it
//! did.

use std::collections::{BTreeMap, HashMap};
use std::time::SystemTime;

/// `(mtime, a tie-breaking sequence)`. The sequence exists so that two
/// touches in the same process — a put right after another, on a
/// filesystem whose mtime resolution is coarser than the gap between
/// them — still order correctly; only a fresh scan at startup relies on
/// mtime alone, because the sequence does not survive a restart.
pub(super) type OrderKey = (SystemTime, u64);
pub(super) type ChunkKey = (String, u32);

struct Meta {
    size: u64,
    order_key: OrderKey,
}

pub(super) struct Index {
    order: BTreeMap<OrderKey, ChunkKey>,
    meta: HashMap<ChunkKey, Meta>,
    held_bytes: u64,
    seq: u64,
}

impl Index {
    /// Builds the index from a directory scan. `found` need not be sorted:
    /// this assigns the tie-breaking sequence in mtime order, so two chunks
    /// with the same mtime still come out ordered by which was genuinely
    /// written first.
    pub(super) fn from_scan(mut found: Vec<(ChunkKey, u64, SystemTime)>) -> Self {
        found.sort_by_key(|(_, _, mtime)| *mtime);

        let mut order = BTreeMap::new();
        let mut meta = HashMap::new();
        let mut held_bytes = 0u64;
        for (seq, (key, size, mtime)) in found.into_iter().enumerate() {
            let order_key = (mtime, seq as u64);
            order.insert(order_key, key.clone());
            held_bytes += size;
            meta.insert(key, Meta { size, order_key });
        }
        let seq = meta.len() as u64;
        Self {
            order,
            meta,
            held_bytes,
            seq,
        }
    }

    fn next_order_key(&mut self, mtime: SystemTime) -> OrderKey {
        let seq = self.seq;
        self.seq += 1;
        (mtime, seq)
    }

    /// A chunk this process just wrote. Usually brand new, but a chunk
    /// deleted by hand (outside the store) and then re-PUT is still a key
    /// this index already has a stale entry for — replace it rather than
    /// add to it, so neither `held_bytes` double-counts the old size nor
    /// the old entry's order key is left orphaned in `order` with nothing
    /// in `meta` to match it.
    pub(super) fn insert(&mut self, key: ChunkKey, size: u64, mtime: SystemTime) {
        if let Some(old) = self.meta.get(&key) {
            self.order.remove(&old.order_key);
            self.held_bytes = self.held_bytes.saturating_sub(old.size);
        }
        let order_key = self.next_order_key(mtime);
        self.order.insert(order_key, key.clone());
        self.held_bytes += size;
        self.meta.insert(key, Meta { size, order_key });
    }

    /// A GET: the chunk was already known, so only its order moves. A
    /// no-op if the key is not present — a GET that read the file
    /// successfully can still race an eviction that drops the entry before
    /// this runs under the lock, and manufacturing one here would count it
    /// in `chunk_count` with nothing added to `held_bytes`, setting up a
    /// later `forget` of it to subtract a size that was never added.
    pub(super) fn refresh(&mut self, key: &ChunkKey, size: u64, mtime: SystemTime) {
        let Some(meta) = self.meta.get(key) else {
            return;
        };
        self.order.remove(&meta.order_key);
        let order_key = self.next_order_key(mtime);
        self.order.insert(order_key, key.clone());
        self.meta.insert(key.clone(), Meta { size, order_key });
    }

    /// The chunk's file vanished behind this store's back.
    pub(super) fn forget(&mut self, key: &ChunkKey) {
        if let Some(meta) = self.meta.remove(key) {
            self.order.remove(&meta.order_key);
            self.held_bytes = self.held_bytes.saturating_sub(meta.size);
        }
    }

    /// Drops the oldest entries until `held_bytes <= budget`, returning
    /// which chunks the caller must now delete from disk.
    pub(super) fn evict_over_budget(&mut self, budget: u64) -> Vec<ChunkKey> {
        let mut evicted = Vec::new();
        while self.held_bytes > budget {
            let Some((&order_key, key)) = self.order.iter().next() else {
                break;
            };
            let key = key.clone();
            self.order.remove(&order_key);
            if let Some(meta) = self.meta.remove(&key) {
                self.held_bytes = self.held_bytes.saturating_sub(meta.size);
            }
            evicted.push(key);
        }
        evicted
    }

    pub(super) fn held_bytes(&self) -> u64 {
        self.held_bytes
    }

    pub(super) fn chunk_count(&self) -> u64 {
        self.meta.len() as u64
    }
}

#[cfg(test)]
#[path = "index_tests.rs"]
mod tests;
