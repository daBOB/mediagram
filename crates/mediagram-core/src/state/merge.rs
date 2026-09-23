//! Reconciling what several devices say about the same viewing.
//!
//! A line-for-line port of `web/src/state/merge.ts`, pinned by the same
//! fixtures. Every mistake here is silent: a merge that picks the older of
//! two positions loses an evening's watching; one that resurrects a
//! finished title puts it back on the Continue shelf for ever.
//!
//! **Last writer wins, per row** — the record for *one title* with the
//! highest `updated_at`, not per device and not per document.
//!
//! **`watched` is the tombstone for `progress`.** Finishing a title deletes
//! its position and writes a completion at the same moment, so a device
//! that has never heard of the completion still holds a position, and
//! merging naively would hand it back. A completion at least as new as a
//! position therefore beats it. Watchlist, Kids and collections need no such
//! trick — each row carries its own `removed` flag, reconciled by `keep`
//! below the same as any other.

use std::collections::HashMap;

use serde::{Deserialize, Serialize};

use super::record::{CollectionRow, ListRow, ProgressRow, SyncRecord, WatchedRow, normal_name};

/// Everything the devices agree on, once they have been reconciled.
#[derive(Debug, Clone, Default, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct MergedProfile {
    /// The normalised name, which is what identifies a viewer across
    /// machines.
    pub name: String,
    /// The name as somebody actually typed it. Carried separately because
    /// the identity is normalised and a name is not.
    pub display_name: String,
    // `#[serde(default)]` throughout: a fixture's `expect` need only name
    // the fields it is testing, the same tolerance `record.rs` has.
    #[serde(default)]
    pub progress: Vec<ProgressRow>,
    #[serde(default)]
    pub watched: Vec<WatchedRow>,
    #[serde(default)]
    pub watchlist: Vec<ListRow>,
    #[serde(default)]
    pub collections: Vec<CollectionRow>,
}

#[derive(Debug, Clone, Default, PartialEq, Serialize, Deserialize)]
pub struct MergedState {
    pub profiles: Vec<MergedProfile>,
    /// Not scoped to a profile — see `schema.rs` on why `kids` alone has
    /// none.
    #[serde(default)]
    pub kids: Vec<ListRow>,
}

/// Which device a held row (or spelling) came from, for the tie-break below.
struct Held<T> {
    row: T,
    device: String,
}

struct ViewerState {
    display_name: String,
    /// The device `display_name` was taken from.
    name_from: String,
    progress: HashMap<String, Held<ProgressRow>>,
    watched: HashMap<String, Held<WatchedRow>>,
    watchlist: HashMap<String, Held<ListRow>>,
    collections: HashMap<String, Held<CollectionRow>>,
}

/// Merges every device's document into one answer. Order-independent by
/// construction: merging A then B gives what merging B then A gives —
/// devices see each other's documents in whatever order Telegram hands them
/// over.
pub fn merge_states(records: &[SyncRecord]) -> MergedState {
    let mut by_viewer: HashMap<String, ViewerState> = HashMap::new();
    // Kids sits at the top level, not per viewer — see `schema.rs`.
    let mut kids: HashMap<String, Held<ListRow>> = HashMap::new();

    for record in records {
        let device = record.device.as_str();
        for row in &record.kids {
            keep(&mut kids, row.set_id.clone(), row.clone(), device);
        }

        for profile in &record.profiles {
            let Some(name) = normal_name(&profile.name) else { continue };

            match by_viewer.get_mut(&name) {
                None => {
                    by_viewer.insert(
                        name.clone(),
                        ViewerState {
                            display_name: profile.name.trim().to_string(),
                            name_from: device.to_string(),
                            progress: HashMap::new(),
                            watched: HashMap::new(),
                            watchlist: HashMap::new(),
                            collections: HashMap::new(),
                        },
                    );
                }
                Some(held) => {
                    // One viewer typed two ways on two devices. The
                    // spelling shown is decided by device id, as a tie
                    // between rows is, so it does not depend on which
                    // document Telegram happened to hand over first.
                    if device > held.name_from.as_str() {
                        held.display_name = profile.name.trim().to_string();
                        held.name_from = device.to_string();
                    }
                }
            }

            let held = by_viewer.get_mut(&name).expect("just inserted or already present");
            for row in &profile.progress {
                keep(&mut held.progress, row.set_id.clone(), row.clone(), device);
            }
            for row in &profile.watched {
                keep(&mut held.watched, row.set_id.clone(), row.clone(), device);
            }
            for row in &profile.watchlist {
                keep(&mut held.watchlist, row.set_id.clone(), row.clone(), device);
            }
            // A collection is one row on the wire, kept by its id: the later
            // edit wins outright, not item by item.
            for row in &profile.collections {
                keep(&mut held.collections, row.id.clone(), row.clone(), device);
            }
        }
    }

    let mut profiles = Vec::with_capacity(by_viewer.len());
    for (name, held) in by_viewer {
        let watched: Vec<WatchedRow> = held.watched.into_values().map(|h| h.row).collect();
        let finished_at: HashMap<&str, f64> =
            watched.iter().map(|row| (row.set_id.as_str(), row.updated_at)).collect();

        let progress: Vec<ProgressRow> = held
            .progress
            .into_values()
            .map(|h| h.row)
            // The tombstone rule. `>=` rather than `>`: the two writes
            // happen in one moment and can carry the same millisecond, and
            // in a tie the completion is the later intention.
            .filter(|row| finished_at.get(row.set_id.as_str()).copied().unwrap_or(-1.0) < row.updated_at)
            .collect();

        profiles.push(MergedProfile {
            name,
            display_name: held.display_name,
            progress,
            watched,
            watchlist: held.watchlist.into_values().map(|h| h.row).collect(),
            collections: held.collections.into_values().map(|h| h.row).collect(),
        });
    }
    MergedState { profiles, kids: kids.into_values().map(|h| h.row).collect() }
}

/// Any row this merge keeps by timestamp: a position, a completion, a
/// watchlist or Kids mark, or a collection.
trait Timestamped {
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
timestamped_by_own_field!(ProgressRow, WatchedRow, ListRow, CollectionRow);

/// Keeps whichever of two rows should win.
///
/// A tie breaks on the device id — arbitrary, but *consistently* arbitrary,
/// which is the property that matters. Two machines merging the same pair of
/// documents have to reach the same answer, or they will push their
/// disagreement back and forth for ever.
fn keep<T: Timestamped>(into: &mut HashMap<String, Held<T>>, key: String, row: T, device: &str) {
    match into.get(&key) {
        None => {
            into.insert(key, Held { row, device: device.to_string() });
        }
        Some(standing) => {
            let standing_at = standing.row.updated_at();
            let row_at = row.updated_at();
            if row_at > standing_at || (row_at == standing_at && device > standing.device.as_str()) {
                into.insert(key, Held { row, device: device.to_string() });
            }
        }
    }
}
