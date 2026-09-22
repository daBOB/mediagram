//! What a removal will destroy.
//!
//! Stated in full before anything is touched, because a set id is not
//! something anyone recognises and "are you sure?" is worthless if the answer
//! cannot be checked against what the operator meant.

use crate::index::label::Named;
use crate::index::parts::PartRow;
use crate::index::set_row::SetRow;

/// One set's removal: the messages to delete and what they hold.
#[derive(Debug, Clone, PartialEq, Eq)]
pub struct Removal {
    pub set_id: String,
    /// How a person recognises this set, rather than how the index does.
    pub label: String,
    pub message_ids: Vec<i64>,
    /// Bytes actually in the channel; a part never uploaded holds none.
    pub bytes: u64,
}

impl Removal {
    /// Sorted and deduplicated, so a repeated message id cannot make the
    /// count lie or the deletion ask twice.
    pub fn normalized(mut self) -> Self {
        self.message_ids.sort_unstable();
        self.message_ids.dedup();
        self
    }

    /// What the operator is shown before confirming.
    pub fn describe(&self) -> String {
        format!(
            "{}  ({})\n  {} message(s), {:.2} GB in the channel",
            self.label,
            self.set_id,
            self.message_ids.len(),
            self.bytes as f64 / 1_073_741_824.0
        )
    }
}

/// Works out what removing `set` costs.
pub fn plan_removal(set: &SetRow, parts: &[PartRow]) -> Removal {
    let mut message_ids = Vec::new();
    let mut bytes = 0u64;

    for part in parts {
        // A part with no message was never uploaded: its row still goes, but
        // nothing in the channel corresponds to it and nothing is reclaimed.
        if let Some(message_id) = part.message_id {
            message_ids.push(message_id);
            bytes += part.byte_length;
        }
    }

    Removal {
        set_id: set.set_id.clone(),
        label: label_of(set),
        message_ids,
        bytes,
    }
    .normalized()
}

/// A line a person can match against what they meant to delete: the label
/// every listing uses, and the container, which tells two copies apart.
fn label_of(set: &SetRow) -> String {
    let named = Named {
        set_id: &set.set_id,
        kind: set.kind.as_str(),
        show: set.show.as_deref(),
        title: set.title.as_deref(),
        season: set.season,
        episode: set.episode,
    };
    format!("{}  [{}]", named.label(), set.container)
}
