//! The tie-break every kept row goes through. Split out only to keep
//! `merge.rs` under the line limit; `merge_states` and its `stats` and `roles`
//! halves are the callers.

use std::collections::HashMap;

use crate::state::record::{
    CollectionRow, DayStatRow, KidsAge, ListRow, PinRecord, ProgressRow, SyncPreference,
    TitleStatRow, UnwatchedRow, WatchedRow,
};

/// Which device a held row (or spelling) came from, for the tie-break below.
pub(super) struct Held<T> {
    pub(super) row: T,
    device: String,
}

/// Any row this merge keeps by timestamp: a position, a completion, a
/// watchlist or Kids mark, a collection, a preference, a stats row, or a
/// kid's limit. A PIN has an order of its own: `keep_pin`.
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
timestamped_by_own_field!(
    ProgressRow,
    WatchedRow,
    UnwatchedRow,
    ListRow,
    CollectionRow,
    SyncPreference,
    TitleStatRow,
    DayStatRow,
    KidsAge
);

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
    keep_ranked(into, key, row, device, |_| 0);
}

/// Keeps whichever of two PINs should win — the web's `keepPin`. A proven
/// PIN (set by someone who knew the one before, or the admin's reset) beats
/// any first PIN; between proven ones the newest wins, as `keep` would have
/// it; between first ones the *oldest* does, so a first PIN set later on a
/// copy that never heard of an earlier one — a kid's offline tablet — cannot
/// replace it. Ties by device id, as `keep`'s do.
pub(super) fn keep_pin(
    into: &mut HashMap<String, Held<PinRecord>>,
    key: String,
    row: PinRecord,
    device: &str,
) {
    let replace = into.get(&key).is_none_or(|standing| {
        let held = &standing.row;
        if row.proven != held.proven {
            return row.proven;
        }
        if row.updated_at != held.updated_at {
            return (row.updated_at > held.updated_at) == row.proven;
        }
        device > standing.device.as_str()
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

/// `keep`, with `rank` asked before the device id: at an equal time the
/// higher rank wins outright, so the order stays total. The web's `keep`
/// takes the same optional `rank`.
pub(super) fn keep_ranked<T: Timestamped>(
    into: &mut HashMap<String, Held<T>>,
    key: String,
    row: T,
    device: &str,
    rank: fn(&T) -> u8,
) {
    let replace = into.get(&key).is_none_or(|standing| {
        let standing_at = standing.row.updated_at();
        let row_at = row.updated_at();
        let ahead = rank(&row)
            .cmp(&rank(&standing.row))
            .then_with(|| device.cmp(standing.device.as_str()));
        row_at > standing_at || (row_at == standing_at && ahead.is_gt())
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

#[cfg(test)]
#[path = "tie_break_tests.rs"]
mod tests;
