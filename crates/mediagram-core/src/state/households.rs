//! Households: which one each profile is in, who runs each, and which one
//! this device shows. See [`rules`] for the rule and [`device`] for the
//! household this device joined.

use rusqlite::Connection;

use super::profiles::{Profile, role_rows::Stored};
use super::record::normal_name;
use rules::{HouseholdRecord, MemberView};

pub mod device;
pub mod rules;

/// The first household. The same id on every device, so each can migrate
/// alone; never deleted.
pub const HOME: &str = "home";

/// A live household and the profile that runs it.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct Household {
    pub id: String,
    pub name: String,
    pub admin_id: Option<String>,
}

/// A household's stored row, a removed one included.
#[derive(Debug, Clone)]
pub(crate) struct Row {
    id: String,
    name: String,
    admin_id: Option<String>,
    removed: bool,
}

/// Every household's row, removed ones included, by id.
pub(crate) fn load_rows(conn: &Connection) -> rusqlite::Result<Vec<Row>> {
    let mut stmt = conn.prepare("SELECT id, name, admin_id, removed FROM households ORDER BY id")?;
    let rows = stmt.query_map([], |row| {
        Ok(Row { id: row.get(0)?, name: row.get(1)?, admin_id: row.get(2)?, removed: row.get::<_, i64>(3)? != 0 })
    })?;
    rows.collect()
}

/// The live households over `profiles` as [`resolve`] placed them: by
/// normalised name in code-point order, equal names in id order. Home is
/// always live.
pub(crate) fn live(conn: &Connection, profiles: &[Profile]) -> rusqlite::Result<Vec<Household>> {
    let mut live: Vec<Household> = load_rows(conn)?
        .into_iter()
        .filter(|row| !row.removed || row.id == HOME)
        .map(|row| Household {
            admin_id: profiles.iter().find(|one| one.admin && one.household == row.id).map(|one| one.id.clone()),
            id: row.id,
            name: row.name,
        })
        .collect();
    live.sort_by_cached_key(|one| normal_name(&one.name).unwrap_or_default());
    Ok(live)
}

/// Each profile with its household and whether it runs it, oldest first as
/// `rows` come.
pub(crate) fn resolve(rows: Vec<Stored>, households: &[Row]) -> Vec<Profile> {
    let members: Vec<MemberView> = rows
        .iter()
        .map(|row| MemberView {
            id: row.id.clone(),
            kids: row.kids,
            owner: row.is_owner(),
            parent_id: row.parent_id.clone(),
            household: Some(row.household_id.clone()),
        })
        .collect();
    let records: Vec<HouseholdRecord> = households
        .iter()
        .map(|row| HouseholdRecord { id: row.id.clone(), admin_id: row.admin_id.clone(), removed: row.removed })
        .collect();
    let (placed, _) = rules::view(&members, &records);
    rows.into_iter()
        .map(|row| {
            let one = Profile::from(row);
            let at = &placed[&one.id];
            Profile { household: at.household.clone(), admin: at.admin, ..one }
        })
        .collect()
}

#[cfg(test)]
#[path = "households_tests.rs"]
mod tests;
