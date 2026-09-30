//! The subtitle choices that follow a viewer from device to device — the
//! part of `record.rs`'s format that carries preferences. Matches
//! `web/src/state/preferences-record.ts`, pinned by the shared fixtures.
//!
//! Only four names travel. Audio, speed and framing are about the screen and
//! the room it is in and stay on the device that made them.

use serde::{Deserialize, Serialize};
use serde_json::Value;

use super::hostile_json::{js_number, text_};

/// The names that travel, in every scope (`key:`, `show:`, `set:`, `profile`).
pub const SYNCED_NAMES: [&str; 4] = ["subtitle", "cue-size", "cue-backing", "cue-offset"];

/// The same cap `preferences.rs` puts on what this player writes; this caps
/// what a stranger's document is allowed to claim.
const MAX_PREFERENCE: usize = 200;

/// One remembered choice with the time it was made.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct SyncPreference {
    pub scope: String,
    pub name: String,
    pub value: String,
    pub updated_at: f64,
}

/// A string, trimmed and capped, or `None` when nothing is left.
fn capped(value: Option<&Value>) -> Option<String> {
    Some(text_(value)?.chars().take(MAX_PREFERENCE).collect())
}

const MAX_SAFE_INTEGER: f64 = 9_007_199_254_740_991.0;

pub(super) fn preference_row(raw: &Value) -> Option<SyncPreference> {
    let row = raw.as_object()?;
    let name = capped(row.get("name"))?;
    if !SYNCED_NAMES.contains(&name.as_str()) {
        return None;
    }
    let updated_at = js_number(row.get("updatedAt"));
    // Whole milliseconds a JS peer can hold exactly: past 2^53 a local write's
    // `stored + 1` stamp no longer advances, and the peer would win every tie.
    if updated_at.fract() != 0.0 || !(1.0..=MAX_SAFE_INTEGER).contains(&updated_at) {
        return None;
    }
    Some(SyncPreference {
        scope: capped(row.get("scope"))?,
        name,
        value: capped(row.get("value"))?,
        updated_at,
    })
}
