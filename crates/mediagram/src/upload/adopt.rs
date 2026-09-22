//! Adoption scan: finds parts that were already posted to the channel but never
//! recorded locally (a crash between `send_message` and `mark_done`), so resume
//! never uploads a part twice.

use std::collections::HashMap;

use crate::index::rescan::Seen;

/// An existing channel message already carrying one of our parts, found by
/// scanning recent history instead of re-uploading.
pub struct Adopted {
    pub message_id: i64,
    pub doc_id: i64,
    pub sha256: String,
}

/// Matches recent channel messages against `set_id` by parsing their
/// caption, keyed by part index so each pending part can be looked up once.
pub fn adoption_map(set_id: &str, recent: &[Seen]) -> HashMap<u32, Adopted> {
    let mut map = HashMap::new();
    for seen in recent {
        if !mlib_spec::caption_codec::is_mlib(&seen.caption) {
            continue;
        }
        let Ok(caption) = mlib_spec::parse(&seen.caption) else {
            continue;
        };
        if caption.set != set_id {
            continue;
        }
        let Some(doc_id) = seen.doc_id else {
            continue;
        };
        map.entry(caption.part.i).or_insert(Adopted {
            message_id: seen.message_id,
            doc_id,
            sha256: caption.part.sha256,
        });
    }
    map
}
