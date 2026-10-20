//! The households, reconciled across devices — field by field, not row by
//! row. A port of `web/src/state/households-merge.ts`, pinned to it by
//! `households-sync.json`.
//!
//! **A name and an admin: each the newest, ties by device id** (`keep`), on
//! its own, so a rename on one device and a new admin on another both stand.
//! The admin is merged under the name sync knows a viewer by (`normal_name`).
//!
//! **A removal is sticky.** Once any device says a household is gone, no
//! document lacking the flag — however new — brings it back. Its other
//! fields are kept beside it, so the merge stays total.

use std::collections::{BTreeSet, HashMap, HashSet};

use super::tie_break::{Held, keep};
use crate::state::record::{HouseholdAdmin, HouseholdEntry, HouseholdName, SyncRecord, normal_name};

/// Every household any device names, sorted by id.
pub(super) fn merge(records: &[SyncRecord]) -> Vec<HouseholdEntry> {
    let mut ids = BTreeSet::new();
    let mut names: HashMap<String, Held<HouseholdName>> = HashMap::new();
    let mut admins: HashMap<String, Held<HouseholdAdmin>> = HashMap::new();
    let mut removed = HashSet::new();

    for record in records {
        let device = record.device.as_str();
        for entry in &record.households {
            ids.insert(entry.id.clone());
            if let Some(name) = &entry.name {
                keep(&mut names, entry.id.clone(), name.clone(), device);
            }
            if let Some(admin) = &entry.admin
                && let Some(name) = normal_name(&admin.name)
            {
                keep(&mut admins, entry.id.clone(), HouseholdAdmin { name, updated_at: admin.updated_at }, device);
            }
            if entry.removed {
                removed.insert(entry.id.clone());
            }
        }
    }

    ids.into_iter()
        .map(|id| HouseholdEntry {
            name: names.remove(&id).map(|held| held.row),
            admin: admins.remove(&id).map(|held| held.row),
            removed: removed.contains(&id),
            id,
        })
        .collect()
}
