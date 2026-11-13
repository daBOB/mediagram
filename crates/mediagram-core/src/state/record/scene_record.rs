//! A marked scene on the sync record: a moment in a title, kept apart from the
//! viewing position. A new optional key on a profile, not a format bump, for
//! the reason `record.rs` gives. Matches `web/src/state/scenes-record.ts`.

use serde::{Deserialize, Serialize};
use serde_json::Value;

use super::hostile_json::{js_number, text_};
use super::{is_false, is_stamp};

/// Rows are keyed by `id`, not `set_id` — one title holds many marks — and
/// marks are never edited, so `updated_at` is the marking time or the removal.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SceneRow {
    pub id: String,
    pub set_id: String,
    /// Film seconds, to the millisecond.
    pub at: f64,
    pub updated_at: f64,
    #[serde(default, skip_serializing_if = "is_false")]
    pub removed: bool,
}

/// Whether `id` is one a removal route can address. Checked on the way in so
/// that a synced mark can always be removed afterwards.
fn is_addressable(id: &str) -> bool {
    (1..=64).contains(&id.len()) && id.bytes().all(|b| b.is_ascii_alphanumeric())
}

/// The web's `numberFromScalar`: `null` and containers are not numbers here,
/// where plain `Number(null)` would make a missing second the film's start.
fn scalar_number(value: Option<&Value>) -> f64 {
    match value {
        Some(Value::Null | Value::Array(_) | Value::Object(_)) => f64::NAN,
        other => js_number(other),
    }
}

pub(super) fn scene_row(raw: &Value) -> Option<SceneRow> {
    let row = raw.as_object()?;
    let id = text_(row.get("id")).filter(|id| is_addressable(id))?;
    let at = scalar_number(row.get("at"));
    let updated_at = scalar_number(row.get("updatedAt"));
    if !at.is_finite() || at < 0.0 || !is_stamp(updated_at) {
        return None;
    }
    Some(SceneRow {
        id,
        set_id: text_(row.get("setId"))?,
        at,
        updated_at,
        removed: row.get("removed") == Some(&Value::Bool(true)),
    })
}

#[cfg(test)]
#[path = "scene_record_tests.rs"]
mod tests;
