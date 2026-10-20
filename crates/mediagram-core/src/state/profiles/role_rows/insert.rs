//! Making a profile.

use rusqlite::{Connection, params};

use super::Stored;
use crate::state::households::HOME;
use crate::state::profiles::{Profile, clean_name, pin};

/// What a new profile is, beside its name.
#[derive(Debug, Default)]
pub(crate) struct NewProfile<'a> {
    pub(crate) kids: bool,
    /// A kid's limit, chosen now. `None` is FSK 12, dated 0.
    pub(crate) kids_age: Option<u8>,
    /// A kid's daily limit in minutes, chosen now. `None` is none, dated 0.
    pub(crate) daily_limit: Option<u16>,
    /// The grown-up adding this kid.
    pub(crate) parent_id: Option<&'a str>,
    /// A grown-up's first PIN, salted and hashed here.
    pub(crate) pin: Option<&'a str>,
    /// The first grown-up on a player, which runs the household from the start.
    pub(crate) admin: bool,
    /// The household it is placed in, chosen now; `None` is Home, undated.
    pub(crate) household: Option<String>,
}

/// Makes a profile; `None` for a name with nothing left after trimming. A
/// kid nobody chose a limit for starts at FSK 12 dated 0 — older than any
/// limit a parent chooses, so the first real choice, made here or synced in,
/// wins; a chosen limit, a first PIN and a first admin's claim are dated
/// `now`, the moment the profile is made.
pub(crate) fn insert(
    conn: &Connection,
    name: &str,
    role: &NewProfile,
    now: i64,
) -> rusqlite::Result<Option<Profile>> {
    let Some(clean) = clean_name(name) else {
        return Ok(None);
    };
    let kids = role.kids;
    let (pin_hash, pin_salt) = role
        .pin
        .filter(|_| !kids)
        .map(|pin| {
            let salt = pin::new_salt();
            (pin::hash(&salt, pin), salt)
        })
        .unzip();
    let row = Stored {
        id: ulid::Ulid::new().to_string(),
        name: clean,
        kids,
        kids_age: kids.then(|| i64::from(role.kids_age.unwrap_or(12))),
        daily_limit: role.daily_limit.filter(|_| kids).map(i64::from),
        parent_id: role.parent_id.filter(|_| kids).map(str::to_string),
        admin_claimed_at: (!kids && role.admin).then_some(now),
        pin_hash,
        pin_salt,
        hidden_sections: Vec::new(), // the column's own default: hides nothing, undated
        household_id: role.household.clone().unwrap_or_else(|| HOME.to_string()),
        household_updated_at: if role.household.is_some() { now } else { 0 },
    };
    let limit_at = if kids && role.kids_age.is_some() { now } else { 0 };
    let daily_at = if row.daily_limit.is_some() { now } else { 0 };
    let pin_at = if row.pin_hash.is_some() { now } else { 0 };
    conn.execute(
        "INSERT INTO profiles(id, name, created_at, kids, kids_age, kids_age_updated_at, parent_id,
                              admin_claimed_at, pin_hash, pin_salt, pin_updated_at,
                              daily_limit, daily_limit_updated_at, household_id, household_updated_at)
           VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12, ?13, ?14, ?15)",
        params![
            row.id,
            row.name,
            now,
            i64::from(kids),
            row.kids_age,
            limit_at,
            row.parent_id,
            row.admin_claimed_at,
            row.pin_hash,
            row.pin_salt,
            pin_at,
            row.daily_limit,
            daily_at,
            row.household_id,
            row.household_updated_at,
        ],
    )?;
    Ok(Some(Profile::from(row)))
}
