//! The Telegram document behind each part of the set being played.

/// Which Telegram document each part of one set is, resolved once.
///
/// Resolving costs a round trip, and a set is read a few hundred times
/// while it plays — every one of them for the same handful of parts. The
/// document a message holds is fixed, but the handle carries a file
/// reference Telegram expires; `read` evicts a part whose reference is
/// refused and resolves it again, so a long pause does not strand a set.
///
/// Held for one set, because that is what a player reads. Opening another
/// replaces it, which is what keeps this from growing with every set ever
/// played.
#[derive(Default)]
pub(super) struct DocumentCache {
    set_id: String,
    by_message: std::collections::HashMap<i64, grammers_client::media::Document>,
}

impl DocumentCache {
    pub(super) fn get(&self, set_id: &str, message_id: i64) -> Option<grammers_client::media::Document> {
        if self.set_id != set_id {
            return None;
        }
        self.by_message.get(&message_id).cloned()
    }

    pub(super) fn evict(&mut self, set_id: &str, message_id: i64) {
        if self.set_id == set_id {
            self.by_message.remove(&message_id);
        }
    }

    pub(super) fn put(&mut self, set_id: &str, message_id: i64, document: grammers_client::media::Document) {
        if self.set_id != set_id {
            self.set_id = set_id.to_string();
            self.by_message.clear();
        }
        self.by_message.insert(message_id, document);
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    /// The emptiest document the bindings will build. This cache stores
    /// whatever it is handed and never looks inside, so what it holds does
    /// not matter here — only which key answers with it.
    fn document() -> grammers_client::media::Document {
        use grammers_tl_types as tl;
        grammers_client::media::Document::from_raw_media(tl::types::MessageMediaDocument {
            nopremium: false,
            spoiler: false,
            video: false,
            round: false,
            voice: false,
            document: None,
            alt_documents: None,
            video_cover: None,
            video_timestamp: None,
            ttl_seconds: None,
        })
    }

    /// The cache exists because resolving a part is a round trip and a
    /// playing set asks for the same handful of parts a few hundred times.
    #[test]
    fn a_part_resolved_once_is_answered_from_memory_after() {
        let mut cache = DocumentCache::default();
        cache.put("set-a", 100, document());

        assert!(cache.get("set-a", 100).is_some());
    }

    #[test]
    fn a_part_never_resolved_is_not_guessed_at() {
        let mut cache = DocumentCache::default();
        cache.put("set-a", 100, document());

        assert!(cache.get("set-a", 999).is_none());
    }

    /// Two sets can hold the same message id only by accident, and
    /// answering one set's read with another's document would serve the
    /// wrong film's bytes. Opening a set is also what bounds this: without
    /// it the map would grow with every set ever played.
    #[test]
    fn opening_another_set_forgets_the_one_before_it() {
        let mut cache = DocumentCache::default();
        cache.put("set-a", 100, document());

        cache.put("set-b", 200, document());

        assert!(cache.get("set-a", 100).is_none(), "one set's parts must not answer another's");
        assert!(cache.get("set-b", 200).is_some());
    }

    /// A handle held across a long pause outlives its file reference; once
    /// Telegram refuses it, the cache must stop answering with it or every
    /// read of the set fails until another set is opened.
    #[test]
    fn an_evicted_part_is_resolved_again_and_its_neighbours_are_kept() {
        let mut cache = DocumentCache::default();
        cache.put("set-a", 100, document());
        cache.put("set-a", 101, document());

        cache.evict("set-a", 100);
        cache.evict("set-b", 101);

        assert!(cache.get("set-a", 100).is_none());
        assert!(cache.get("set-a", 101).is_some(), "forgetting another set's part touches nothing");
    }
}
