//! A profile's role between its `profiles` row and the wire: the admin
//! claim, a kid's limit and parent, a grown-up's PIN. A port of
//! `web/src/state/roles-exchange.ts`, split out of `exchange.rs` for the
//! reason `lists_exchange.rs` is.
//!
//! Corrective, like everything `import_merged` calls: local news the merge
//! ranks higher is kept — newer for a limit, by the PIN order for a PIN —
//! and an equal rank with a different value takes the winner the merge
//! already chose by device id. SQLite failures propagate, so `import_merged`
//! rolls the whole import back.

use std::collections::HashMap;

use rusqlite::{Connection, params};

use crate::state::merge::MergedProfile;
use crate::state::profiles;
use crate::state::record::{AdminClaim, KidsAge, PinRecord, ProfileRoles, normal_name};

/// This profile's role keys, as `export_record` writes them beside `kids`.
/// A claim and a PIN are written whatever the profile is now; the merge
/// drops them from a kid.
pub(super) fn export(conn: &Connection, profile_id: &str) -> rusqlite::Result<ProfileRoles> {
    conn.query_row(
        "SELECT kids, kids_age, kids_age_updated_at, admin_claimed_at, pin_hash, pin_salt,
                pin_updated_at, (SELECT parent.name FROM profiles AS parent WHERE parent.id = profiles.parent_id),
                pin_proven
           FROM profiles WHERE id = ?1",
        [profile_id],
        |row| {
            let kids: bool = row.get(0)?;
            let age: Option<i64> = row.get(1)?;
            let limit = KidsAge {
                // A kid with no limit stored predates limits: FSK 12, the
                // one limit there was.
                age: if age == Some(6) { 6 } else { 12 },
                updated_at: row.get::<_, i64>(2)? as f64,
            };
            let claimed_at: Option<i64> = row.get(3)?;
            let pin = row.get::<_, Option<String>>(4)?.zip(row.get::<_, Option<String>>(5)?);
            let pin_at = row.get::<_, i64>(6)? as f64;
            let proven: bool = row.get(8)?;
            Ok(ProfileRoles {
                admin: claimed_at.map(|at| AdminClaim { claimed_at: at as f64 }),
                kids_age: kids.then_some(limit),
                // By name, because a name is what sync knows a viewer by; a
                // parent removed here is simply not said.
                parent: row.get(7)?,
                pin: pin.map(|(hash, salt)| PinRecord { hash, salt, updated_at: pin_at, proven }),
            })
        },
    )
}

/// Takes in the merged role keys. Run after every merged viewer has a local
/// row, because a kid's parent may be a viewer this same import made.
pub(super) fn import(conn: &Connection, merged: &[MergedProfile]) -> rusqlite::Result<u64> {
    // The first profile of each name — the one `import_merged` matched.
    let mut local: HashMap<String, String> = HashMap::new();
    for profile in profiles::list(conn)? {
        if let Some(name) = normal_name(&profile.name) {
            local.entry(name).or_insert(profile.id);
        }
    }
    let local_id = |name: &str| normal_name(name).and_then(|name| local.get(&name));

    let mut changed = 0;
    for profile in merged {
        let Some(id) = local_id(&profile.name) else {
            continue;
        };
        let roles = &profile.roles;
        if let Some(limit) = &roles.kids_age {
            changed += conn.execute(
                "UPDATE profiles SET kids_age = ?2, kids_age_updated_at = ?3
                   WHERE id = ?1 AND (kids_age_updated_at < ?3
                     OR (kids_age_updated_at = ?3 AND kids_age IS NOT ?2))",
                params![id, limit.age, limit.updated_at as i64],
            )?;
        }
        // In the merge's own order (`merge::tie_break::keep_pin`): over no PIN
        // at all; a proven one over a first one; between proven ones the
        // newer, between first ones the older; as old and as proven but
        // different, the winner the merge chose by device id.
        if let Some(pin) = &roles.pin {
            changed += conn.execute(
                "UPDATE profiles SET pin_hash = ?2, pin_salt = ?3, pin_updated_at = ?4, pin_proven = ?5
                   WHERE id = ?1 AND (pin_hash IS NULL OR ?5 > pin_proven
                     OR (?5 = pin_proven AND (CASE WHEN ?5 THEN pin_updated_at < ?4 ELSE pin_updated_at > ?4 END
                       OR (pin_updated_at = ?4 AND (pin_hash IS NOT ?2 OR pin_salt IS NOT ?3)))))",
                params![id, pin.hash, pin.salt, pin.updated_at as i64, pin.proven],
            )?;
        }
        // Set once and never moved: two documents disagreeing about a kid's
        // parent is a bug to notice, not a change to follow.
        if let Some(parent) = roles.parent.as_deref().and_then(local_id) {
            changed += conn.execute(
                "UPDATE profiles SET parent_id = ?2 WHERE id = ?1 AND parent_id IS NULL AND id <> ?2",
                params![id, parent],
            )?;
        }
    }
    // The merge's admin becomes the only one here. A merge that names nobody
    // says nothing — it does not say "no admin" — so it changes nothing, and
    // a device that has not heard of the claim yet cannot undo it.
    let named = merged
        .iter()
        .find_map(|profile| Some((profile.roles.admin.as_ref()?, local_id(&profile.name)?)));
    if let Some((claim, id)) = named {
        changed += conn.execute(
            "UPDATE profiles SET admin_claimed_at = NULL WHERE id <> ?1 AND admin_claimed_at IS NOT NULL",
            [id],
        )?;
        changed += conn.execute(
            "UPDATE profiles SET admin_claimed_at = ?2 WHERE id = ?1 AND admin_claimed_at IS NOT ?2",
            params![id, claim.claimed_at as i64],
        )?;
    }
    Ok(changed as u64)
}

#[cfg(test)]
#[path = "roles_tests.rs"]
mod tests;
