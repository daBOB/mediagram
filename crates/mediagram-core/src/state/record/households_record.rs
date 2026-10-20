//! The households on the sync record: a top-level `households` key, beside
//! `kids`, and each profile's `household` (`roles_record.rs`). A port of
//! `web/src/state/households-record.ts`, pinned to it by
//! `households-sync.json`.
//!
//! New optional keys, not a format bump: an older reader drops them and
//! merges everything else. Each field carries its own stamp, so a rename on
//! one device and a new admin on another both stand (`merge/households.rs`).
//! A field dated 0 is never written, so an install nobody has changed says
//! nothing an older build would not.
//!
//! Read as hostile as the rest of the document: a bad field is dropped on
//! its own, and a row left saying nothing is dropped with it.

use serde::{Deserialize, Serialize};
use serde_json::Value;

use super::hostile_json::{js_number, text_};
use super::is_stamp;
use crate::state::households::HOME;

/// A household's name and when it was last chosen.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct HouseholdName {
    pub text: String,
    pub updated_at: f64,
}

/// Who runs a household, by the name sync knows a viewer by, as `parent` is.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct HouseholdAdmin {
    pub name: String,
    pub updated_at: f64,
}

/// The household a profile is in as its record says, and when it moved.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ProfileHousehold {
    pub id: String,
    pub updated_at: f64,
}

/// One household as a device says it.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct HouseholdEntry {
    pub id: String,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub name: Option<HouseholdName>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub admin: Option<HouseholdAdmin>,
    /// Deleted for good: no later write brings it back. Never on Home.
    #[serde(default, skip_serializing_if = "std::ops::Not::not")]
    pub removed: bool,
}

const MAX_NAME: usize = 120;

/// `home`, a UUID or the core's ULID — and nothing a stranger could make longer.
fn household_id(raw: Option<&Value>) -> Option<String> {
    let id = raw?.as_str()?;
    let fine = (1..=64).contains(&id.len()) && id.bytes().all(|b| b.is_ascii_alphanumeric() || b == b'-');
    fine.then(|| id.to_string())
}

pub(super) fn household_entry(raw: &Value) -> Option<HouseholdEntry> {
    let held = raw.as_object()?;
    let id = household_id(held.get("id"))?;

    let name = held.get("name").and_then(Value::as_object).and_then(|name| {
        let updated_at = js_number(name.get("updatedAt"));
        // Cut by code point, as the web does, so both keep the same text.
        let text = text_(name.get("text"))?.chars().take(MAX_NAME).collect();
        is_stamp(updated_at).then_some(HouseholdName { text, updated_at })
    });
    let admin = held.get("admin").and_then(Value::as_object).and_then(|admin| {
        let updated_at = js_number(admin.get("updatedAt"));
        is_stamp(updated_at).then_some(HouseholdAdmin { name: text_(admin.get("name"))?, updated_at })
    });
    // Only the literal true: a removal is final, so it is not guessed at.
    let removed = held.get("removed") == Some(&Value::Bool(true)) && id != HOME;
    (name.is_some() || admin.is_some() || removed).then_some(HouseholdEntry { id, name, admin, removed })
}

/// A profile's `household` key, or `None` for a bad id or a stamp of 0.
pub(super) fn profile_household(raw: Option<&Value>) -> Option<ProfileHousehold> {
    let held = raw?.as_object()?;
    let updated_at = js_number(held.get("updatedAt"));
    is_stamp(updated_at).then_some(ProfileHousehold { id: household_id(held.get("id"))?, updated_at })
}
