//! Who may manage whom — a port of the web's `profiles-rules.ts`, pinned to
//! it by `profile-rules.json` and `profile-names.json`. Pure: every local
//! profile in, yes or no out, never an error.
//!
//! A kid manages nothing. A household's admin adds and removes grown-ups and
//! may set any grown-up's PIN, but nobody in the household removes it, itself
//! included. A kid belongs to exactly one grown-up, the one who made it, and
//! only that one removes it or sets its limit; a kid whose parent is not a
//! grown-up here — none recorded, removed, or never met — is the admin's. All
//! of this stays inside one household, where the owner is the admin. Only the
//! owner reaches into another: there they set the PIN of, hide sections from,
//! set the limits of and remove anyone, that household's admin too.

use serde::Deserialize;

use super::clean_name;
use crate::state::record::{js_collapse, normal_name};

/// Whether a profile here already answers to `name`. Sync knows a viewer by
/// its normalised name and only ever turns `kids` on, so a second profile of
/// the same name would merge with the first everywhere — a kid called after
/// the admin would make the admin a kid. Compared as stored names are kept:
/// edges trimmed, runs of space collapsed, then normalised. `name` is cut as
/// it would be stored; `existing` is not, because a former name that
/// lower-casing lengthened past the cut must still be refused whole.
pub fn name_taken(existing: &[String], name: &str) -> bool {
    let Some(wanted) = clean_name(name).and_then(|clean| normal_name(&clean)) else {
        return false;
    };
    existing
        .iter()
        .any(|one| normal_name(&js_collapse(one)).as_ref() == Some(&wanted))
}

/// What the rule needs to know about a profile, and nothing else.
#[derive(Debug, Clone, PartialEq, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct RoleView {
    pub id: String,
    pub kids: bool,
    pub admin: bool,
    /// Holds the install-wide claim: manages households, and the profiles in
    /// every household but its own, where it is the admin.
    #[serde(default)]
    pub owner: bool,
    pub parent_id: Option<String>,
    /// Absent is the first household, as on a device from before households.
    #[serde(default)]
    pub household: Option<String>,
}

fn household_of(one: &RoleView) -> &str {
    one.household.as_deref().unwrap_or("home")
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
    SetAccess,
}

/// Only a grown-up is ever the admin. A view can still say a kid is — a
/// grown-up that held the claim and that another device later called a kid
/// — and a kid read as admin would own every kid nobody else does.
fn is_admin(one: &RoleView) -> bool {
    one.admin && !one.kids
}

/// Who manages `kid`: its parent while that is a grown-up in its household,
/// else that household's admin, else nobody.
fn owner_of<'a>(profiles: &'a [RoleView], kid: &RoleView) -> Option<&'a str> {
    let mut here = profiles
        .iter()
        .filter(|one| household_of(one) == household_of(kid));
    let parent = here
        .clone()
        .find(|one| kid.parent_id.as_deref() == Some(one.id.as_str()) && !one.kids);
    parent
        .or_else(|| here.find(|one| is_admin(one)))
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
        // Households are separate: only the owner manages a profile in another one.
        (_, Some(target)) if household_of(target) != household_of(actor) => {
            actor.owner && owner_reaches(action, target)
        }
        (Action::Remove, Some(target)) if is_admin(target) => false,
        (Action::Remove, Some(target)) if target.kids => owns(target),
        (Action::Remove, Some(target)) => target.id != actor.id && actor.admin,
        (Action::SetPin, Some(target)) => !target.kids && (target.id == actor.id || actor.admin),
        (Action::SetKidsAge, Some(target)) => target.kids && owns(target),
        // A household's admin never narrows its own view.
        (Action::SetAccess, Some(target)) if target.kids => owns(target),
        (Action::SetAccess, Some(target)) => !is_admin(target) && actor.admin,
    }
}

/// What the owner may do to anyone in another household, its admin too:
/// remove them, set their sections, a grown-up's PIN, a kid's limits.
fn owner_reaches(action: Action, target: &RoleView) -> bool {
    match action {
        Action::SetPin => !target.kids,
        Action::SetKidsAge => target.kids,
        Action::Remove | Action::SetAccess => true,
        Action::CreateGrownUp | Action::CreateKid => false,
    }
}
