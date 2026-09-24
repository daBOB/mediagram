//! What one device says about where things were left off.
//!
//! Matches `web/src/state/sync-record.ts`, with shared compatibility fixtures
//! under `web/test/fixtures/watch-state/`.
//!
//! Each row has its own `updated_at`, so a device cannot overwrite unseen
//! changes merely by publishing a newer document.
//!
//! `watched` acts as the tombstone for `progress`; see `merge.rs`.
//!
//! Watchlist, Kids and collections use `removed` with their row timestamp.
//! Defaults let older documents omit these lists without losing other rows.

use serde::{Deserialize, Serialize};
use serde_json::Value;
use unicode_normalization::UnicodeNormalization;

mod hostile_json;
mod list_record;
use hostile_json::{as_array, is_integer, js_number, text_};
pub use list_record::{CollectionRow, ListRow};
use list_record::{collection_row, list_row};

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

/// Reads a document from the channel, or `None` if it cannot be trusted.
///
/// Written as if the input were hostile, because in the useful sense it is:
/// it came off the network, it was written by another machine, and it may
/// have been written by a *newer* version of this program. Every row is
/// checked and a bad one is dropped rather than taking the document down
/// with it — one unparseable position should not cost a viewer the rest of
/// their history.
pub fn parse_record(text: &str) -> Option<SyncRecord> {
    let raw: Value = serde_json::from_str(text).ok()?;
    let held = raw.as_object()?;

    let format = js_number(held.get("format"));
    // A document from the future is ignored rather than guessed at. Reading
    // it half-right would merge a half-right answer into a database that is
    // the source of truth for this machine.
    if !is_integer(format) || format < 1.0 || format > SYNC_FORMAT as f64 {
        return None;
    }

    let device = text_(held.get("device"))?;

    let profiles = as_array(held.get("profiles"))
        .iter()
        .filter_map(profile_state)
        .collect();
    let kids = as_array(held.get("kids"))
        .iter()
        .filter_map(list_row)
        .collect();

    // `Number(held.writtenAt) || 0`: NaN and 0 both fall back to 0, and a
    // negative or positive finite number passes through unchanged.
    let written_at = js_number(held.get("writtenAt"));
    let written_at = if written_at.is_nan() { 0.0 } else { written_at };

    Some(SyncRecord {
        format: format as i64,
        device,
        written_at,
        profiles,
        kids,
    })
}

/// For `skip_serializing_if`: an ordinary profile carries no `kids` key.
pub(crate) fn is_false(value: &bool) -> bool {
    !*value
}

fn profile_state(raw: &Value) -> Option<ProfileState> {
    let row = raw.as_object()?;
    let name = text_(row.get("name"))?;
    Some(ProfileState {
        name,
        local_id: text_(row.get("localId")),
        // Only a literal `true`, as on the web: a restricting flag must not
        // be switched on by a value that merely looks truthy.
        kids: row.get("kids") == Some(&Value::Bool(true)),
        progress: as_array(row.get("progress"))
            .iter()
            .filter_map(progress_row)
            .collect(),
        watched: as_array(row.get("watched"))
            .iter()
            .filter_map(watched_row)
            .collect(),
        watchlist: as_array(row.get("watchlist"))
            .iter()
            .filter_map(list_row)
            .collect(),
        collections: as_array(row.get("collections"))
            .iter()
            .filter_map(collection_row)
            .collect(),
    })
}

fn progress_row(raw: &Value) -> Option<ProgressRow> {
    let row = raw.as_object()?;
    let set_id = text_(row.get("setId"))?;
    let at = js_number(row.get("at"));
    // `at` may legitimately be 0 — the start of a film — so it is checked
    // for finiteness rather than truthiness.
    if !at.is_finite() || at < 0.0 {
        return None;
    }
    let updated_at = js_number(row.get("updatedAt"));
    if !updated_at.is_finite() || updated_at <= 0.0 {
        return None;
    }
    let runtime = js_number(row.get("duration"));
    let duration = if runtime.is_finite() && runtime > 0.0 {
        Some(runtime)
    } else {
        None
    };
    Some(ProgressRow {
        set_id,
        at,
        duration,
        updated_at,
    })
}

fn watched_row(raw: &Value) -> Option<WatchedRow> {
    let row = raw.as_object()?;
    let set_id = text_(row.get("setId"))?;
    let updated_at = js_number(row.get("updatedAt"));
    if !updated_at.is_finite() || updated_at <= 0.0 {
        return None;
    }
    Some(WatchedRow { set_id, updated_at })
}
