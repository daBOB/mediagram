//! What `merge_states` answers with. Split out of `merge.rs` only to keep
//! it under the line limit; the rules that fill these live there.

use serde::{Deserialize, Serialize};

use crate::state::record::{
    CollectionRow, DayStatRow, ListRow, ProfileRoles, ProgressRow, SyncPreference, TitleStatRow,
    UnwatchedRow, WatchedRow,
};

/// Everything the devices agree on, once they have been reconciled.
#[derive(Debug, Clone, Default, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct MergedProfile {
    /// The normalised name, which is what identifies a viewer across
    /// machines.
    pub name: String,
    /// The name as typed; the identity is normalised, a name is not.
    pub display_name: String,
    /// A kids profile if any device's document says so.
    #[serde(default, skip_serializing_if = "crate::state::record::is_false")]
    pub kids: bool,
    // `#[serde(default)]`: a fixture's `expect` names only what it tests.
    #[serde(default)]
    pub progress: Vec<ProgressRow>,
    #[serde(default)]
    pub watched: Vec<WatchedRow>,
    #[serde(default)]
    pub unwatched: Vec<UnwatchedRow>,
    #[serde(default)]
    pub watchlist: Vec<ListRow>,
    #[serde(default)]
    pub collections: Vec<CollectionRow>,
    #[serde(default)]
    pub preferences: Vec<SyncPreference>,
    /// Viewing stats: the newest copy of each device's row, per title and
    /// per day. Omitted when empty, like the record's own keys.
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub title_stats: Vec<TitleStatRow>,
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub day_stats: Vec<DayStatRow>,
    /// Admin claim, limit, parent and PIN, settled across the household by
    /// `roles::merge` — each only where it applies.
    #[serde(flatten)]
    pub roles: ProfileRoles,
}

#[derive(Debug, Clone, Default, PartialEq, Serialize, Deserialize)]
pub struct MergedState {
    pub profiles: Vec<MergedProfile>,
    /// Not scoped to a profile — see `schema.rs` on why `kids` alone has
    /// none.
    #[serde(default)]
    pub kids: Vec<ListRow>,
    /// Household-wide too, and kept per title the same way; see
    /// `schema.rs`'s v5.
    #[serde(default, rename = "editorsChoice")]
    pub editors_choice: Vec<ListRow>,
}

#[cfg(test)]
#[path = "merged_tests.rs"]
mod tests;
