//! Which blobs are held and in what order they were last used.

use std::collections::{BTreeMap, HashMap};

#[derive(Default)]
pub(super) struct Index {
    /// relative path -> (length, recency tick)
    pub(super) entries: HashMap<String, (u64, u64)>,
    /// recency tick -> relative path; the first entry is the next to evict.
    order: BTreeMap<u64, String>,
    pub(super) held: u64,
    tick: u64,
}

impl Index {
    pub(super) fn touch(&mut self, rel: &str) -> bool {
        let Some(entry) = self.entries.get_mut(rel) else {
            return false;
        };
        self.order.remove(&entry.1);
        self.tick += 1;
        entry.1 = self.tick;
        self.order.insert(self.tick, rel.to_string());
        true
    }

    pub(super) fn insert(&mut self, rel: &str, len: u64) {
        if let Some((old, tick)) = self.entries.remove(rel) {
            self.held -= old;
            self.order.remove(&tick);
        }
        self.tick += 1;
        self.entries.insert(rel.to_string(), (len, self.tick));
        self.order.insert(self.tick, rel.to_string());
        self.held += len;
    }

    pub(super) fn pop_oldest(&mut self) -> Option<String> {
        let (_, rel) = self.order.pop_first()?;
        let (len, _) = self.entries.remove(&rel)?;
        self.held -= len;
        Some(rel)
    }
}
