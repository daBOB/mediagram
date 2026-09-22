//! What one device says about where things were left off.
//!
//! A line-for-line port of `web/src/state/sync-record.ts`: the two have to
//! agree on every byte of this format, because a document one of them wrote
//! is read by the other. The fixtures under
//! `web/test/fixtures/watch-state/` pin the agreement; this file exists to
//! pass them, not the other way round.
//!
//! **Every row carries its own `updated_at`.** Without one a merge could
//! only prefer whole documents, and whichever device pushed last would
//! overwrite a position it had never heard of.
//!
//! **Scope: positions and completions, and nothing else yet.** A watchlist
//! entry is deleted outright, leaving nothing to carry the fact that it
//! *was* deleted, so syncing it would resurrect on every merge whatever
//! another device had not yet heard was gone.

use serde::{Deserialize, Serialize};
use serde_json::Value;
use unicode_normalization::UnicodeNormalization;

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
    #[serde(default)]
    pub progress: Vec<ProgressRow>,
    #[serde(default)]
    pub watched: Vec<WatchedRow>,
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

    let profiles = as_array(held.get("profiles")).iter().filter_map(profile_state).collect();

    // `Number(held.writtenAt) || 0`: NaN and 0 both fall back to 0, and a
    // negative or positive finite number passes through unchanged.
    let written_at = js_number(held.get("writtenAt"));
    let written_at = if written_at.is_nan() { 0.0 } else { written_at };

    Some(SyncRecord { format: format as i64, device, written_at, profiles })
}

fn profile_state(raw: &Value) -> Option<ProfileState> {
    let row = raw.as_object()?;
    let name = text_(row.get("name"))?;
    Some(ProfileState {
        name,
        local_id: text_(row.get("localId")),
        progress: as_array(row.get("progress")).iter().filter_map(progress_row).collect(),
        watched: as_array(row.get("watched")).iter().filter_map(watched_row).collect(),
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
    let duration = if runtime.is_finite() && runtime > 0.0 { Some(runtime) } else { None };
    Some(ProgressRow { set_id, at, duration, updated_at })
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

fn as_array(value: Option<&Value>) -> &[Value] {
    match value {
        Some(Value::Array(items)) => items,
        _ => &[],
    }
}

/// A non-empty string, trimmed — the only kind of text worth keeping here.
fn text_(value: Option<&Value>) -> Option<String> {
    let clean = value?.as_str()?.trim();
    (!clean.is_empty()).then(|| clean.to_string())
}

/// `Number(value)`, the coercion `parseRecord` leans on throughout: a
/// missing key is `undefined` and becomes NaN, `null` becomes 0, booleans
/// become 0/1, a numeric string is parsed and anything else is NaN.
fn js_number(value: Option<&Value>) -> f64 {
    let Some(value) = value else { return f64::NAN };
    match value {
        Value::Null => 0.0,
        Value::Bool(b) => {
            if *b {
                1.0
            } else {
                0.0
            }
        }
        Value::Number(n) => n.as_f64().unwrap_or(f64::NAN),
        Value::String(s) => {
            let trimmed = s.trim();
            if trimmed.is_empty() { 0.0 } else { trimmed.parse::<f64>().unwrap_or(f64::NAN) }
        }
        Value::Array(_) | Value::Object(_) => f64::NAN,
    }
}

fn is_integer(n: f64) -> bool {
    n.is_finite() && n.fract() == 0.0
}
