//! The Telegram document behind each part of the set being read.

use std::collections::HashMap;
use std::sync::{Mutex, PoisonError};

use anyhow::Result;
use grammers_client::Client;
use grammers_client::media::Document;
use grammers_session::types::PeerRef;

use super::stream::part_document;
use crate::catalog::PartLocation;

/// Which Telegram document each part of one set is, resolved once.
///
/// Resolving costs a round trip, and a set is read a few hundred times while
/// it plays, every time for the same handful of parts. The document a message
/// holds is fixed, but the handle carries a file reference Telegram expires,
/// so a refused part is evicted and resolved again.
///
/// Held for one set, because that is what a reader reads: a read that names
/// other parts drops the ones it does not, which is what keeps this from
/// growing with every set ever played. Keyed by channel and message, since a
/// message id alone means nothing outside its channel.
#[derive(Default)]
pub struct PartDocuments {
    held: Mutex<HashMap<(i64, i64), Document>>,
}

fn key(location: &PartLocation) -> (i64, i64) {
    (location.chat_id, location.message_id)
}

impl PartDocuments {
    /// Forgets every part `locations` does not name.
    pub fn keep_only(&self, locations: &[PartLocation]) {
        let wanted: Vec<_> = locations.iter().map(key).collect();
        self.lock().retain(|held, _| wanted.contains(held));
    }

    /// The part's document, from memory when it has been resolved before.
    pub async fn resolve(
        &self,
        client: &Client,
        channel: PeerRef,
        location: &PartLocation,
    ) -> Result<Document> {
        if let Some(held) = self.get(location) {
            return Ok(held);
        }
        let document = part_document(client, channel, location.message_id).await?;
        self.put(location, document.clone());
        Ok(document)
    }

    /// Drops a part whose file reference Telegram refused.
    pub fn evict(&self, location: &PartLocation) {
        self.lock().remove(&key(location));
    }

    fn get(&self, location: &PartLocation) -> Option<Document> {
        self.lock().get(&key(location)).cloned()
    }

    fn put(&self, location: &PartLocation, document: Document) {
        self.lock().insert(key(location), document);
    }

    // Never held across an await, and every write leaves the map whole, so a
    // panic elsewhere cannot leave it in a state worth refusing.
    fn lock(&self) -> std::sync::MutexGuard<'_, HashMap<(i64, i64), Document>> {
        self.held.lock().unwrap_or_else(PoisonError::into_inner)
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

    fn at(chat_id: i64, message_id: i64) -> PartLocation {
        PartLocation {
            span: crate::range::PartSpan {
                idx: 0,
                off: 0,
                len: 1,
            },
            chat_id,
            message_id,
        }
    }

    /// The cache exists because resolving a part is a round trip and a
    /// playing set asks for the same handful of parts a few hundred times.
    #[test]
    fn a_part_resolved_once_is_answered_from_memory_after() {
        let cache = PartDocuments::default();
        cache.put(&at(-1, 100), document());

        assert!(cache.get(&at(-1, 100)).is_some());
        assert!(
            cache.get(&at(-1, 999)).is_none(),
            "a part never resolved is not guessed at"
        );
    }

    /// The same message id in another channel is another message, and
    /// answering with its document would serve the wrong film's bytes.
    #[test]
    fn a_message_id_is_only_meaningful_in_its_channel() {
        let cache = PartDocuments::default();
        cache.put(&at(-1, 100), document());

        assert!(cache.get(&at(-2, 100)).is_none());
    }

    /// Reading another set is what bounds this: without it the map would
    /// grow with every set ever played.
    #[test]
    fn reading_other_parts_forgets_the_ones_before() {
        let cache = PartDocuments::default();
        cache.put(&at(-1, 100), document());
        cache.put(&at(-1, 101), document());

        cache.keep_only(&[at(-1, 101), at(-1, 200)]);

        assert!(cache.get(&at(-1, 100)).is_none());
        assert!(cache.get(&at(-1, 101)).is_some());
    }

    /// A handle held across a long pause outlives its file reference; once
    /// Telegram refuses it, the cache must stop answering with it or every
    /// read of the set fails until another set is opened.
    #[test]
    fn an_evicted_part_is_resolved_again_and_its_neighbours_are_kept() {
        let cache = PartDocuments::default();
        cache.put(&at(-1, 100), document());
        cache.put(&at(-1, 101), document());

        cache.evict(&at(-1, 100));

        assert!(cache.get(&at(-1, 100)).is_none());
        assert!(cache.get(&at(-1, 101)).is_some());
    }
}
