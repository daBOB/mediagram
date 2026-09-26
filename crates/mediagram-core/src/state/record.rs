//! What one device says about where things were left off.
//!
//! Matches `web/src/state/sync-record.ts`, with shared compatibility fixtures
//! under `web/test/fixtures/watch-state/`.
//!
//! Each row has its own `updated_at`, so a device cannot overwrite unseen
//! changes merely by publishing a newer document.
//!
//! A completion acts as the tombstone for `progress`; see `merge.rs`.
//!
//! The watchlist, Kids and collections use `removed` with their row
//! timestamp. `watched` carries its removal under a different key instead —
//! `UnwatchedRow`, not a `removed` flag on `WatchedRow` — because a reader
//! that predates this one cannot both understand that flag and not, and
//! there are readers in the fleet right now that do not: one that saw
//! `{setId, updatedAt, removed: true}` would drop the flag it does not
//! recognise and import the row as a *live* mark at that same moment, and
//! the next merge would decide a real removal against that resurrected live
//! row by device id rather than by what happened. `unwatched` on its own key
//! is what such a reader simply never learns exists (`parse.rs`), the same
//! way it already drops `kids` and its optional siblings — leaving its own
//! live mark unchanged and always older than the removal a new device
//! holds. See `merge.rs` for the reconciliation this makes possible.
//! Defaults let older documents omit any of these without losing other
//! rows.

use serde::{Deserialize, Serialize};
use unicode_normalization::UnicodeNormalization;

mod hostile_json;
mod list_record;
mod parse;
pub use list_record::{CollectionRow, ListRow};
pub use parse::parse_record;

/// Bumped when a reader could no longer make sense of an older document.
pub const SYNC_FORMAT: i64 = 1;

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ProgressRow {
    pub set_id: String,
    pub at: f64,
    pub duration: Option<f64>,
    pub updated_at: f64,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct WatchedRow {
    pub set_id: String,
    pub updated_at: f64,
}

/// Un-marking `watched` — its own row, its own key. See this module's
/// header for why, and `merge.rs` for how a `WatchedRow` and an
/// `UnwatchedRow` for the same title are reconciled.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct UnwatchedRow {
    pub set_id: String,
    pub updated_at: f64,
    /// The `finished_at` this removal took the mark from; see `merge.rs`.
    pub last_finished_at: f64,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ProfileState {
    pub name: String,
    /// The writing device's own id for this profile — provenance, not
    /// identity.
    #[serde(default)]
    pub local_id: Option<String>,
    /// Present only on a kids profile. Written only when true, so an
    /// ordinary profile's entry reads exactly as it did before the flag.
    #[serde(default, skip_serializing_if = "is_false")]
    pub kids: bool,
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
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SyncRecord {
    pub format: i64,
    /// Stable per install, so a device can recognise and replace its own.
    pub device: String,
    pub written_at: f64,
    #[serde(default)]
    pub profiles: Vec<ProfileState>,
    /// Not scoped to a profile — see `schema.rs` on why `kids` alone has
    /// none.
    #[serde(default)]
    pub kids: Vec<ListRow>,
}

/// How a viewer is the same person on two machines.
///
/// Case and surrounding space are not part of who someone is; a name typed
/// "andré" on the phone and "André " on the desktop is one viewer.
/// Normalised to NFC first, because the same name can be typed as a
/// composed `é` or as an `e` with a combining accent and the two are not
/// otherwise equal.
pub fn normal_name(name: &str) -> Option<String> {
    let clean = name.nfc().collect::<String>();
    let clean = clean.trim().to_lowercase();
    (!clean.is_empty()).then_some(clean)
}

/// For `skip_serializing_if`: an ordinary profile carries no `kids` key.
pub(crate) fn is_false(value: &bool) -> bool {
    !*value
}
