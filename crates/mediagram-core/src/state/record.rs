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

use icu_normalizer::ComposingNormalizerBorrowed;
use serde::{Deserialize, Serialize};

mod hostile_json;
mod list_record;
mod parse;
mod preference_record;
mod roles_record;
mod stats_record;
pub(crate) use stats_record::is_day;
pub use list_record::{CollectionRow, ListRow};
pub use preference_record::{SYNCED_NAMES, SyncPreference};
pub use parse::parse_record;
pub use roles_record::{AdminClaim, KidsAge, PinRecord, ProfileRoles};
pub use stats_record::{DayStatRow, TitleStatRow};

/// Bumped when a reader could no longer make sense of an older document.
pub const SYNC_FORMAT: i64 = 1;

/// The latest time a synced row may carry: 2^53 − 1, the largest integer
/// every engine holds exactly (the web's `Number.MAX_SAFE_INTEGER`). A stamp
/// past it is no clock's, and one near the top of the integer range would
/// overflow the next own write's `+ 1`.
pub(crate) const MAX_STAMP: f64 = 9_007_199_254_740_991.0;

/// A time a synced row may carry: positive and no later than [`MAX_STAMP`].
/// Rows are last-writer-wins, so one stamped past it would outrank every
/// later edit of that row, on every device it reached, and the stamp would
/// not survive the trip through `i64` and back. Such a row is dropped, not
/// clamped: clamped, it would outrank those edits all the same.
fn is_stamp(at: f64) -> bool {
    at > 0.0 && at <= MAX_STAMP
}

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
    /// The synced subtitle choices; a new key like `kids`, not a format
    /// bump — a build that predates it drops it and keeps merging.
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub preferences: Vec<SyncPreference>,
    /// Viewing stats, every device's rows (`stats_record.rs`). New keys like
    /// `preferences`, written only when there are rows, so a document
    /// without stats reads exactly as it did before them.
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub title_stats: Vec<TitleStatRow>,
    #[serde(default, skip_serializing_if = "Vec::is_empty")]
    pub day_stats: Vec<DayStatRow>,
    /// Admin claim, limit, parent and PIN (`roles_record.rs`), each written
    /// only when set, beside `kids` on the wire.
    #[serde(flatten)]
    pub roles: ProfileRoles,
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
    /// The household's editor's choice marks; not scoped to a profile for
    /// the same reason `kids` is not. A new key like `kids`, not a format
    /// bump — an old document simply says nothing about it.
    #[serde(default)]
    pub editors_choice: Vec<ListRow>,
}

/// How a viewer is the same person on two machines.
///
/// Case and surrounding space are not part of who someone is; a name typed
/// "andré" on the phone and "André " on the desktop is one viewer.
/// Normalised to NFC first, because the same name can be typed as a
/// composed `é` or as an `e` with a combining accent and the two are not
/// otherwise equal.
pub fn normal_name(name: &str) -> Option<String> {
    let clean = ComposingNormalizerBorrowed::new_nfc().normalize(name);
    let clean = clean.trim().to_lowercase();
    (!clean.is_empty()).then_some(clean)
}

/// For `skip_serializing_if`: an ordinary profile carries no `kids` key.
pub(crate) fn is_false(value: &bool) -> bool {
    !*value
}
