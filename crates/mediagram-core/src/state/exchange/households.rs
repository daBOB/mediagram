//! The households between the `households` table and the wire. A port of
//! `web/src/state/households-exchange.ts`, split out of `exchange.rs` for the
//! reason `exchange/roles.rs` is, and corrective like it: a local field the
//! merge does not beat is kept, by the same newer-or-different-at-a-tie
//! rule. SQLite failures propagate, so `import_merged` rolls the whole import
//! back.

use std::collections::HashMap;

use rusqlite::{Connection, params};

use super::roles::first_of_each_name;
use crate::state::households::{HOME, Row, load_rows};
use crate::state::profiles;
use crate::state::record::{HouseholdAdmin, HouseholdEntry, HouseholdName, normal_name};

/// What this device says about households: a row only once something in it
/// is dated, or it is removed — so a Home nobody touched says nothing, and
/// an untouched install writes no key an older build has not seen. An admin
/// whose profile is not here is not said: there is no name to say.
pub(super) fn export(conn: &Connection) -> rusqlite::Result<Vec<HouseholdEntry>> {
    let names: HashMap<String, String> =
        profiles::list(conn)?.into_iter().map(|one| (one.id, one.name)).collect();
    let mut said = Vec::new();
    for row in load_rows(conn)? {
        let name = (row.name_updated_at > 0)
            .then(|| HouseholdName { text: row.name.clone(), updated_at: row.name_updated_at as f64 });
        let admin = row.admin_id.as_ref().and_then(|admin| names.get(admin)).filter(|_| row.admin_updated_at > 0);
        let admin = admin.map(|name| HouseholdAdmin { name: name.clone(), updated_at: row.admin_updated_at as f64 });
        let removed = row.removed && row.id != HOME;
        if name.is_some() || admin.is_some() || removed {
            said.push(HouseholdEntry { id: row.id, name, admin, removed });
        }
    }
    Ok(said)
}

/// Merged news beats local when it is newer, or as new and different.
fn newer(merged: f64, local: i64, different: bool) -> bool {
    merged > local as f64 || (merged == local as f64 && different)
}

/// Takes in the merged households. Run after the roles, once every merged
/// profile has a local row: an admin may be a viewer this same import made.
pub(super) fn import(conn: &Connection, entries: &[HouseholdEntry]) -> rusqlite::Result<u64> {
    let local = first_of_each_name(conn)?;
    let mut stored: HashMap<String, Row> = load_rows(conn)?.into_iter().map(|row| (row.id.clone(), row)).collect();
    let mut changed = 0;
    for entry in entries {
        let id = entry.id.as_str();
        if !stored.contains_key(id) {
            // Made only from a name: a household is never listed without one.
            let Some(name) = &entry.name else { continue };
            conn.execute(
                "INSERT INTO households(id, name, name_updated_at) VALUES (?1, ?2, ?3)",
                params![id, name.text, name.updated_at as i64],
            )?;
            let made = Row {
                id: id.to_string(),
                name: name.text.clone(),
                name_updated_at: name.updated_at as i64,
                admin_id: None,
                admin_updated_at: 0,
                removed: false,
            };
            stored.insert(id.to_string(), made);
            changed += 1;
        } else if let Some(name) = &entry.name
            && newer(name.updated_at, stored[id].name_updated_at, name.text != stored[id].name)
        {
            conn.execute(
                "UPDATE households SET name = ?2, name_updated_at = ?3 WHERE id = ?1",
                params![id, name.text, name.updated_at as i64],
            )?;
            changed += 1;
        }
        let row = &stored[id];
        // Onto the first local profile of that name; one not here yet waits
        // for the round that brings it.
        let admin = entry.admin.as_ref().and_then(|admin| Some((admin, local.get(&normal_name(&admin.name)?)?)));
        if let Some((admin, who)) = admin
            && newer(admin.updated_at, row.admin_updated_at, row.admin_id.as_ref() != Some(who))
        {
            conn.execute(
                "UPDATE households SET admin_id = ?2, admin_updated_at = ?3 WHERE id = ?1",
                params![id, who, admin.updated_at as i64],
            )?;
            changed += 1;
        }
        // Set, never cleared: deletion is final.
        if entry.removed && !row.removed && id != HOME {
            conn.execute("UPDATE households SET removed = 1 WHERE id = ?1", [id])?;
            changed += 1;
        }
    }
    Ok(changed)
}

#[cfg(test)]
#[path = "households_tests.rs"]
mod tests;
