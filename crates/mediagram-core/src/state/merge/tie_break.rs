//! The tie-break every kept row goes through. Split out only to keep
//! `merge.rs` under the line limit; `merge_states` is the only caller.

use std::collections::HashMap;

use crate::state::record::{CollectionRow, ListRow, ProgressRow, UnwatchedRow, WatchedRow};

/// Which device a held row (or spelling) came from, for the tie-break below.
pub(super) struct Held<T> {
    pub(super) row: T,
    device: String,
}

/// Any row this merge keeps by timestamp: a position, a completion, a
/// watchlist or Kids mark, or a collection.
pub(super) trait Timestamped {
    fn updated_at(&self) -> f64;
}

macro_rules! timestamped_by_own_field {
    ($($row:ty),+) => {
        $(impl Timestamped for $row {
            fn updated_at(&self) -> f64 {
                self.updated_at
            }
        })+
    };
}
timestamped_by_own_field!(ProgressRow, WatchedRow, UnwatchedRow, ListRow, CollectionRow);

/// Keeps whichever of two rows should win.
///
/// A tie breaks on the device id — arbitrary, but *consistently* arbitrary,
/// which is the property that matters. Two machines merging the same pair of
/// documents have to reach the same answer, or they will push their
/// disagreement back and forth for ever.
pub(super) fn keep<T: Timestamped>(
    into: &mut HashMap<String, Held<T>>,
    key: String,
    row: T,
    device: &str,
) {
    let replace = into.get(&key).is_none_or(|standing| {
        let standing_at = standing.row.updated_at();
        let row_at = row.updated_at();
        row_at > standing_at || (row_at == standing_at && device > standing.device.as_str())
    });
    if replace {
        into.insert(
            key,
            Held {
                row,
                device: device.to_string(),
            },
        );
    }
}
