//! A viewer's role, reconciled across devices: a kid's limit, a grown-up's
//! PIN, who made a kid, and which one viewer is the household's admin. A
//! port of `web/src/state/roles-merge.ts`, pinned to it by
//! `profile-roles-merge.json`; `merge_states` calls it beside its own pass.
//!
//! **A limit: the newest wins**, ties by device id through `keep`. Not
//! sticky the way `kids` is — a parent lowers a limit as often as it raises
//! one.
//!
//! **A PIN: a proven one, then the newest; else the oldest** (`keep_pin`).
//! A first PIN — set where its grown-up had none — never replaces one set
//! earlier elsewhere: a kid's tablet that never heard of a parent's PIN, and
//! lets the kid choose one, must not hand that PIN to the parent everywhere.
//! Only someone who knew the PIN, or the admin's reset, replaces it.
//!
//! **A kid nobody gave a limit is FSK 12 at time 0.** That is what a
//! document from before limits says by saying only `kids: true`, and 0 is
//! older than any real change, so the first limit a parent sets wins
//! wherever it lands.
//!
//! **A parent is set once, and only a kid has one.** Two documents naming
//! different parents is a bug, not a case, but it is still settled the way
//! `display_name` is — by device id — so the answer never depends on the
//! order documents arrive in.
//!
//! **One admin, household-wide, and a grown-up.** The earliest claim wins,
//! ties by the smaller name, and only that viewer carries the key: however
//! devices wake up, the merge never names two. A claim on a viewer any
//! document calls a kid is ignored — a kid manages nothing, and a grown-up
//! another device later calls a kid keeps its old claim in its columns.

use std::collections::{HashMap, HashSet};

use super::tie_break::{Held, keep, keep_pin};
use crate::state::record::{
    AdminClaim, KidsAge, ListRow, PinRecord, ProfileRoles, SyncRecord, normal_name,
};

const FROM_TWELVE: KidsAge = KidsAge {
    age: 12,
    updated_at: 0.0,
};

/// How a Kids mark ranks against another at the same `updated_at`, before
/// the device id is asked: "from 6" first. A build from before ages drops
/// `age` from every mark it takes in and writes the mark back at the same
/// time, so a tie between a mark from 6 and one without is that echo, not a
/// second choice — changing a mark's age always moves its clock
/// (`rows::set_kids`). Left to the device id, the echo would strip the age
/// everywhere whenever the older build's id sorted higher.
pub(super) fn kids_mark_rank(row: &ListRow) -> u8 {
    u8::from(row.age == Some(6) && !row.removed)
}

/// Each viewer's merged role keys by normalised name; a key is absent where
/// it does not apply.
pub(super) fn merge(records: &[SyncRecord]) -> HashMap<String, ProfileRoles> {
    let mut viewers = HashSet::new();
    // The sticky test `merge_states` applies to `kids`: once any document
    // says so, no document lacking the flag undoes it.
    let mut kids = HashSet::new();
    let mut limits: HashMap<String, Held<KidsAge>> = HashMap::new();
    let mut pins: HashMap<String, Held<PinRecord>> = HashMap::new();
    let mut parents: HashMap<String, (String, &str)> = HashMap::new();
    let mut claims: HashMap<String, f64> = HashMap::new();

    for record in records {
        let device = record.device.as_str();
        for profile in &record.profiles {
            let Some(name) = normal_name(&profile.name) else {
                continue;
            };
            let roles = &profile.roles;
            if profile.kids {
                kids.insert(name.clone());
            }
            if let Some(limit) = &roles.kids_age {
                keep(&mut limits, name.clone(), limit.clone(), device);
            }
            if let Some(pin) = &roles.pin {
                keep_pin(&mut pins, name.clone(), pin.clone(), device);
            }
            if let Some(parent) = roles.parent.as_deref().and_then(normal_name) {
                let standing = parents
                    .entry(name.clone())
                    .or_insert((parent.clone(), device));
                if device > standing.1 {
                    *standing = (parent, device);
                }
            }
            if let Some(claim) = &roles.admin {
                let earliest = claims.entry(name.clone()).or_insert(claim.claimed_at);
                *earliest = earliest.min(claim.claimed_at);
            }
            viewers.insert(name);
        }
    }

    let admin = earliest(&claims, &kids);
    viewers
        .into_iter()
        .map(|name| {
            let kid = kids.contains(&name);
            let roles = ProfileRoles {
                admin: admin
                    .filter(|(who, _)| **who == name)
                    .map(|(_, claimed_at)| AdminClaim { claimed_at }),
                kids_age: kid.then(|| limits.remove(&name).map_or(FROM_TWELVE, |held| held.row)),
                parent: parents
                    .remove(&name)
                    .filter(|_| kid)
                    .map(|(parent, _)| parent),
                pin: pins.remove(&name).filter(|_| !kid).map(|held| held.row),
            };
            (name, roles)
        })
        .collect()
}

/// The grown-up with the earliest claim; a tie goes to the smaller name in
/// UTF-16 order — JavaScript's string order — so the web and this core name
/// the same admin even when one name holds an emoji and the other a
/// character from U+E000 up, where UTF-8 and UTF-16 order disagree.
fn earliest<'a>(
    claims: &'a HashMap<String, f64>,
    kids: &HashSet<String>,
) -> Option<(&'a String, f64)> {
    claims
        .iter()
        .filter(|(name, _)| !kids.contains(*name))
        .map(|(name, at)| (name, *at))
        .min_by(|a, b| a.1.total_cmp(&b.1).then_with(|| a.0.encode_utf16().cmp(b.0.encode_utf16())))
}

#[cfg(test)]
#[path = "roles_tests.rs"]
mod tests;
