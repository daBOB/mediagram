//! Households: which one each profile is in, who runs each, what the owner and
//! a household's admin may change, and which household this device shows.
//! A port of the web's `household-rules.js`, pinned to it by
//! `household-rules.json`. Pure, and a household rule rather than real
//! separation: it decides what is shown and offered, and refuses no request
//! the server would otherwise take.

use std::collections::{BTreeMap, HashMap, HashSet};

use serde::Deserialize;

use super::HOME;
use crate::state::profiles::rules::RoleView;

/// What the view needs to know about a profile.
#[derive(Debug, Clone, PartialEq, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct MemberView {
    pub id: String,
    pub kids: bool,
    #[serde(default)]
    pub owner: bool,
    pub parent_id: Option<String>,
    #[serde(default)]
    pub household: Option<String>,
}

/// A household record: its named admin, and whether it has been deleted.
#[derive(Debug, Clone, PartialEq, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct HouseholdRecord {
    pub id: String,
    pub admin_id: Option<String>,
    #[serde(default)]
    pub removed: bool,
}

/// Where a profile is, and whether it runs that household.
#[derive(Debug, Clone, PartialEq, Deserialize)]
pub struct ResolvedView {
    pub household: String,
    pub admin: bool,
}

/// Each profile's household and whether it runs it, and each live household's
/// admin. A household that is gone or unknown reads as home, so nobody is
/// ever in none.
pub fn view(
    profiles: &[MemberView],
    households: &[HouseholdRecord],
) -> (HashMap<String, ResolvedView>, BTreeMap<String, Option<String>>) {
    let live: HashSet<&str> = std::iter::once(HOME)
        .chain(households.iter().filter(|one| !one.removed).map(|one| one.id.as_str()))
        .collect();
    let by_id: HashMap<&str, &MemberView> = profiles.iter().map(|one| (one.id.as_str(), one)).collect();
    let stored = |one: &MemberView| -> String {
        let own = one.household.as_deref().unwrap_or(HOME);
        (if live.contains(own) { own } else { HOME }).to_string()
    };
    // A kid is wherever its parent is, whatever its own field says: moving a
    // grown-up moves their kids, and a kid's stale field cannot split them.
    let household_of = |one: &MemberView| {
        let parent = one
            .parent_id
            .as_deref()
            .and_then(|id| by_id.get(id))
            .filter(|parent| one.kids && !parent.kids);
        stored(parent.copied().unwrap_or(one))
    };
    let owner = profiles.iter().find(|one| one.owner && !one.kids);

    // The owner always runs the household they are in, so no household admin
    // can reset the owner's PIN. Elsewhere the record names the admin, who
    // counts only while a grown-up of that household.
    let admin_of = |id: &str| -> Option<String> {
        if let Some(owner) = owner.filter(|owner| household_of(owner) == id) {
            return Some(owner.id.clone());
        }
        let named = households
            .iter()
            .find(|one| one.id == id)
            .and_then(|one| by_id.get(one.admin_id.as_deref()?))
            .filter(|named| !named.kids && household_of(named) == id);
        named.map(|named| named.id.clone())
    };

    let admins: BTreeMap<String, Option<String>> = live.iter().map(|id| (id.to_string(), admin_of(id))).collect();
    let resolved = profiles
        .iter()
        .map(|one| {
            let household = household_of(one);
            let admin = !one.kids && admins[&household].as_deref() == Some(one.id.as_str());
            (one.id.clone(), ResolvedView { household, admin })
        })
        .collect();
    (resolved, admins)
}

/// What an actor asks to do, spelled as the web spells it.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize)]
#[serde(rename_all = "kebab-case")]
pub enum HouseholdAction {
    CreateHousehold,
    RenameHousehold,
    SetHouseholdAdmin,
    MoveToHousehold,
    DeleteHousehold,
    JoinHousehold,
    ClaimHousehold,
}

/// Whether `actor_id` may do `action` to household `household_id` (and
/// profile `target_id`, where the action has one; empty for none), over
/// profiles [`view`] has already placed. Never fails. Households are compared
/// as stored: an absent one is not "home" here.
pub fn allowed(
    profiles: &[RoleView],
    live: &[String],
    actor_id: &str,
    action: HouseholdAction,
    household_id: &str,
    target_id: &str,
) -> bool {
    let Some(actor) = profiles.iter().find(|one| one.id == actor_id && !one.kids) else {
        return false;
    };
    let owner = actor.owner;
    let target = profiles.iter().find(|one| one.id == target_id && !one.kids);
    // The owner never moves: they run whatever household they are in, so a move
    // would quietly take it from its recorded admin and leave them stranded
    // there. A household's admin moves only as its last grown-up, which empties
    // it for deletion; while others remain, the owner changes the admin first.
    let movable = target.is_some_and(|target| {
        !target.owner
            && (!target.admin
                || !profiles
                    .iter()
                    .any(|one| !one.kids && one.id != target.id && one.household == target.household))
    });
    let here = Some(household_id);

    // A new household's admin is a new grown-up, or one who could be moved there.
    if action == HouseholdAction::CreateHousehold {
        return owner && (target_id.is_empty() || movable);
    }
    if !live.iter().any(|one| one == household_id) {
        return false;
    }
    let in_household = |one: &RoleView| one.household.as_deref() == here;
    match action {
        HouseholdAction::CreateHousehold => unreachable!("handled above"),
        HouseholdAction::RenameHousehold => owner,
        // The owner runs their own household, so there is no admin there to change.
        HouseholdAction::SetHouseholdAdmin => owner && !in_household(actor) && target.is_some_and(in_household),
        HouseholdAction::MoveToHousehold => owner && movable && target.is_some_and(|target| !in_household(target)),
        HouseholdAction::DeleteHousehold => owner && household_id != HOME && !profiles.iter().any(in_household),
        HouseholdAction::JoinHousehold => owner || (actor.admin && in_household(actor)),
        // A household is left with no admin by a race, an older build, or its
        // admin leaving as its last grown-up. A grown-up then moved in becomes
        // its admin (the server and core do that, not this rule); claiming
        // covers the rest.
        HouseholdAction::ClaimHousehold => {
            in_household(actor) && !profiles.iter().any(|one| !one.kids && one.admin && in_household(one))
        }
    }
}

/// Which household this device shows: the one it joined, else its chosen
/// profile's, so a device from before households stays where its profile is;
/// else, once a sync round has been heard, the only one there is. `None`
/// means ask "Join a household" — or, before anything is heard, wait.
pub fn device_household<'a>(
    stored: Option<&'a str>,
    chosen: Option<&'a str>,
    live: &[String],
    heard: bool,
) -> Option<String> {
    let is_live = |id: Option<&'a str>| id.filter(move |id| live.iter().any(|one| one == id));
    if let Some(id) = is_live(stored).or_else(|| is_live(chosen)) {
        return Some(id.to_string());
    }
    match live {
        [only] if heard => Some(only.clone()),
        _ => None,
    }
}
