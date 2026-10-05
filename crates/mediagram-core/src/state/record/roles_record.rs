//! A profile's place in the household on the sync record: the admin claim,
//! a kid's limit and parent, a grown-up's PIN. A port of
//! `web/src/state/roles-record.ts`, pinned to it by
//! `profile-roles-record-parse.json`.
//!
//! New optional keys rather than a format bump: a reader that predates them
//! drops what it does not know and keeps merging the rest, so an older
//! build's kid arrives as `kids: true` and nothing more. Each key is read on
//! its own — a malformed one is dropped, never the profile — and its time
//! goes through the coercion and the bound every synced row's does.

use serde::{Deserialize, Serialize};
use serde_json::{Map, Value};

use super::hostile_json::{js_number, text_};
use super::is_stamp;

/// When a grown-up became the household's admin; the earliest claim
/// anywhere wins.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AdminClaim {
    pub claimed_at: f64,
}

/// A kid's limit — FSK 6 or 12 — and when it last changed. `updated_at` 0
/// is a limit nobody chose: the FSK 12 every kid had before there was a
/// choice.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct KidsAge {
    pub age: u8,
    pub updated_at: f64,
}

/// A grown-up's PIN as stored: lowercase hex SHA-256 of `salt + pin`, and
/// the salt. The sync record is the only place it leaves the store.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct PinRecord {
    pub hash: String,
    pub salt: String,
    pub updated_at: f64,
    /// Set by someone who knew the PIN it replaced, or by the admin's reset.
    /// Without it a PIN is a first one — set where its grown-up had none —
    /// which never replaces a PIN set earlier elsewhere (`merge::roles`).
    /// Written only when true; every PIN from before the flag is a first one.
    #[serde(default, skip_serializing_if = "std::ops::Not::not")]
    pub proven: bool,
}

/// Every role key a profile's entry may carry, each absent unless said.
#[derive(Debug, Clone, Default, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct ProfileRoles {
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub admin: Option<AdminClaim>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub kids_age: Option<KidsAge>,
    /// The grown-up who made this kid, by the name sync knows a viewer by.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub parent: Option<String>,
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub pin: Option<PinRecord>,
}

pub(super) fn profile_roles(row: &Map<String, Value>) -> ProfileRoles {
    ProfileRoles {
        admin: admin(row.get("admin")),
        kids_age: kids_age(row.get("kidsAge")),
        parent: text_(row.get("parent")),
        pin: pin(row.get("pin")),
    }
}

fn admin(raw: Option<&Value>) -> Option<AdminClaim> {
    let claimed_at = js_number(raw?.as_object()?.get("claimedAt"));
    is_stamp(claimed_at).then_some(AdminClaim { claimed_at })
}

/// The age is the number 6 or 12, never a string that looks like one: a
/// limit is not guessed at. Its time may be 0, what an older build's kid
/// merges to.
fn kids_age(raw: Option<&Value>) -> Option<KidsAge> {
    let held = raw?.as_object()?;
    let age = held.get("age").and_then(Value::as_f64);
    let age = if age == Some(6.0) {
        6
    } else if age == Some(12.0) {
        12
    } else {
        return None;
    };
    let updated_at = js_number(held.get("updatedAt"));
    (updated_at == 0.0 || is_stamp(updated_at)).then_some(KidsAge { age, updated_at })
}

fn pin(raw: Option<&Value>) -> Option<PinRecord> {
    let held = raw?.as_object()?;
    let updated_at = js_number(held.get("updatedAt"));
    Some(PinRecord {
        hash: lowercase_hex(held.get("hash"), 64)?,
        salt: lowercase_hex(held.get("salt"), 32)?,
        updated_at: is_stamp(updated_at).then_some(updated_at)?,
        // Only the literal true: a PIN that wins over others is not guessed at.
        proven: held.get("proven") == Some(&Value::Bool(true)),
    })
}

/// Exactly `len` characters of `0-9a-f`, as both surfaces write a hash and a
/// salt; anything else could never match a PIN.
fn lowercase_hex(raw: Option<&Value>, len: usize) -> Option<String> {
    let text = raw?.as_str()?;
    let hex = text.len() == len && text.bytes().all(|b| matches!(b, b'0'..=b'9' | b'a'..=b'f'));
    hex.then(|| text.to_string())
}

#[cfg(test)]
#[path = "roles_record_tests.rs"]
mod tests;
