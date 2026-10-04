//! Who may manage whom — a port of the web's `profiles-rules.ts`, pinned to
//! it by `profile-rules.json` and `profile-names.json`. Pure: every local
//! profile in, yes or no out, never an error.
//!
//! A kid manages nothing. The admin adds and removes grown-ups and may set
//! any grown-up's PIN, but is never removed — by anyone, itself included. A
//! kid belongs to exactly one grown-up, the one who made it, and only that one
//! removes it or sets its limit; a kid whose parent is not a grown-up here —
//! none recorded, removed, or never met — is the admin's.

use serde::Deserialize;

use super::clean_name;
use crate::state::record::normal_name;

/// Whether a profile here already answers to `name`. Sync knows a viewer by
/// its normalised name and only ever turns `kids` on, so a second profile of
/// the same name would merge with the first everywhere — a kid called after
/// the admin would make the admin a kid. Compared as stored names are kept:
/// edges trimmed, runs of space collapsed, then normalised.
pub fn name_taken(existing: &[String], name: &str) -> bool {
    let normal = |name: &str| clean_name(name).and_then(|clean| normal_name(&clean));
    let Some(wanted) = normal(name) else {
        return false;
    };
    existing
        .iter()
        .any(|one| normal(one).as_ref() == Some(&wanted))
}

/// What the rule needs to know about a profile, and nothing else.
#[derive(Debug, Clone, PartialEq, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RoleView {
    pub id: String,
    pub kids: bool,
    pub admin: bool,
    pub parent_id: Option<String>,
}

/// What an actor asks to do, spelled as the web spells it.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "kebab-case")]
pub enum Action {
    CreateGrownUp,
    CreateKid,
    Remove,
    SetPin,
    SetKidsAge,
}

/// Only a grown-up is ever the admin. A view can still say a kid is — a
/// grown-up that held the claim and that another device later called a kid
/// — and a kid read as admin would own every kid nobody else does.
fn is_admin(one: &RoleView) -> bool {
    one.admin && !one.kids
}

/// Who manages `kid`: its parent while that is a grown-up here, else the
/// admin, else nobody.
fn owner_of<'a>(profiles: &'a [RoleView], kid: &RoleView) -> Option<&'a str> {
    let parent = profiles
        .iter()
        .find(|one| kid.parent_id.as_deref() == Some(one.id.as_str()) && !one.kids);
    parent
        .or_else(|| profiles.iter().find(|one| is_admin(one)))
        .map(|one| one.id.as_str())
}

/// Whether `actor_id` may do `action` to `target_id` (ignored when creating).
pub fn allowed(profiles: &[RoleView], actor_id: &str, action: Action, target_id: &str) -> bool {
    let find = |id: &str| profiles.iter().find(|one| one.id == id);
    let Some(actor) = find(actor_id).filter(|actor| !actor.kids) else {
        return false;
    };
    let owns = |kid: &RoleView| owner_of(profiles, kid) == Some(actor.id.as_str());
    match (action, find(target_id)) {
        (Action::CreateGrownUp, _) => actor.admin,
        (Action::CreateKid, _) => true,
        (_, None) => false,
        (Action::Remove, Some(target)) if is_admin(target) => false,
        (Action::Remove, Some(target)) if target.kids => owns(target),
        (Action::Remove, Some(target)) => target.id != actor.id && actor.admin,
        (Action::SetPin, Some(target)) => !target.kids && (target.id == actor.id || actor.admin),
        (Action::SetKidsAge, Some(target)) => target.kids && owns(target),
    }
}
