//! The preference half of `merge_states`, split out to keep `merge.rs` under
//! the line limit. Matches the `preferences` fold in `web/src/state/merge.ts`.

use std::collections::HashMap;

use super::tie_break::{Held, keep};
use crate::state::record::SyncPreference;

/// The best row seen so far for each (scope, name) of one viewer.
pub(super) type Kept = HashMap<String, Held<SyncPreference>>;

/// Folds one device's rows in, newest write winning and ties going to the
/// greater device id — per scope and name, so the language and the cue size
/// of one show are decided independently.
pub(super) fn absorb(into: &mut Kept, rows: &[SyncPreference], device: &str) {
    for row in rows {
        // Length-prefixed: a scope may hold any text, so a separator could
        // make two different (scope, name) pairs collide.
        let key = format!("{}:{}{}", row.scope.len(), row.scope, row.name);
        keep(into, key, row.clone(), device);
    }
}

pub(super) fn rows(kept: Kept) -> Vec<SyncPreference> {
    kept.into_values().map(|held| held.row).collect()
}
