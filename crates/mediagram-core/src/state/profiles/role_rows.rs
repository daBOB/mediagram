//! A profile's row with everything stored about it, the PIN included, and
//! the writes that make one or change its PIN or limit — the half of the
//! web's `profiles.ts` the management calls stand on. Never sent as it is:
//! `Profile` says whether there is a PIN, never what.

use rusqlite::{Connection, params};

use super::rules::RoleView;
use super::{Profile, clean_name, pin};
use crate::state::record::MAX_STAMP;

/// Everything stored about a profile, the PIN included.
#[derive(Debug, Clone)]
pub(crate) struct Stored {
    pub(crate) id: String,
    pub(crate) name: String,
    pub(crate) kids: bool,
    pub(crate) parent_id: Option<String>,
    pub(crate) admin_claimed_at: Option<i64>,
    pub(crate) pin_hash: Option<String>,
    pub(crate) pin_salt: Option<String>,
}

impl Stored {
    /// A grown-up that holds the claim. A grown-up another device later
    /// calls a kid keeps its old claim in the column; it must not make a kid
    /// the admin here.
    pub(crate) fn is_admin(&self) -> bool {
        !self.kids && self.admin_claimed_at.is_some()
    }

    /// The rule's view of this profile, a kid never the admin.
    pub(crate) fn view(&self) -> RoleView {
        RoleView {
            id: self.id.clone(),
            kids: self.kids,
            admin: self.is_admin(),
            parent_id: self.parent_id.clone(),
        }
    }
}

/// Every profile's row, oldest first.
pub(crate) fn load(conn: &Connection) -> rusqlite::Result<Vec<Stored>> {
    let mut stmt = conn.prepare(
        "SELECT id, name, kids, parent_id, admin_claimed_at, pin_hash, pin_salt
           FROM profiles ORDER BY created_at",
    )?;
    let rows = stmt.query_map([], |row| {
        Ok(Stored {
            id: row.get(0)?,
            name: row.get(1)?,
            kids: row.get::<_, i64>(2)? != 0,
            parent_id: row.get(3)?,
            admin_claimed_at: row.get(4)?,
            pin_hash: row.get(5)?,
            pin_salt: row.get(6)?,
        })
    })?;
    rows.collect()
}

/// What a new profile is, beside its name.
#[derive(Debug, Default)]
pub(crate) struct NewProfile<'a> {
    pub(crate) kids: bool,
    /// A kid's limit, chosen now. `None` is FSK 12, dated 0.
    pub(crate) kids_age: Option<u8>,
    /// The grown-up adding this kid.
    pub(crate) parent_id: Option<&'a str>,
    /// A grown-up's first PIN, salted and hashed here.
    pub(crate) pin: Option<&'a str>,
    /// The first grown-up on a player, which runs the household from the start.
    pub(crate) admin: bool,
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
    let pin = role.pin.filter(|_| !kids).map(|pin| {
        let salt = pin::new_salt();
        (pin::hash(&salt, pin), salt)
    });
    let profile = Profile {
        id: ulid::Ulid::new().to_string(),
        name: clean,
        kids,
    };
    let limit_at = if kids && role.kids_age.is_some() { now } else { 0 };
    conn.execute(
        "INSERT INTO profiles(id, name, created_at, kids, kids_age, kids_age_updated_at, parent_id,
                              admin_claimed_at, pin_hash, pin_salt, pin_updated_at)
           VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11)",
        params![
            profile.id,
            profile.name,
            now,
            i64::from(kids),
            kids.then(|| role.kids_age.unwrap_or(12)),
            limit_at,
            role.parent_id.filter(|_| kids),
            (!kids && role.admin).then_some(now),
            pin.as_ref().map(|(hash, _)| hash),
            pin.as_ref().map(|(_, salt)| salt),
            if pin.is_some() { now } else { 0 },
        ],
    )?;
    Ok(Some(profile))
}

/// The latest stamp an own write of a PIN or a limit takes. Each is dated at
/// least a millisecond past the last change: an imported one can carry
/// another device's clock, and this one running behind must not write a
/// change that loses to the value it replaced. Kept below the largest time a
/// peer keeps, as `set_watched`'s stamps are.
const LAST_STAMP: i64 = MAX_STAMP as i64 - 1;

/// A fresh salt, and the hash of `pin` under it.
pub(crate) fn write_pin(conn: &Connection, id: &str, pin: &str, now: i64) -> rusqlite::Result<()> {
    let salt = pin::new_salt();
    conn.execute(
        "UPDATE profiles SET pin_hash = ?2, pin_salt = ?3,
                pin_updated_at = MAX(?4, MIN(pin_updated_at, ?5) + 1) WHERE id = ?1",
        params![id, pin::hash(&salt, pin), salt, now, LAST_STAMP],
    )?;
    Ok(())
}

/// A kid's limit. Written, and dated, even when it is the limit already
/// there: a parent's choice is news to every device that hears of it.
pub(crate) fn write_kids_age(conn: &Connection, id: &str, age: u8, now: i64) -> rusqlite::Result<()> {
    conn.execute(
        "UPDATE profiles SET kids_age = ?2,
                kids_age_updated_at = MAX(?3, MIN(kids_age_updated_at, ?4) + 1) WHERE id = ?1",
        params![id, age, now, LAST_STAMP],
    )?;
    Ok(())
}

/// Removes `id`, and with a grown-up the kids it is the parent of — by
/// hand, not by a foreign key: `parent_id` deliberately is not one, so a kid
/// whose parent is gone here but known elsewhere stays readable. A kid takes
/// only itself, even when another kid names it as parent (sync sets a parent
/// by name, so one can).
pub(crate) fn remove(conn: &Connection, id: &str, grown_up: bool) -> rusqlite::Result<()> {
    let tx = conn.unchecked_transaction()?;
    if grown_up {
        tx.execute("DELETE FROM profiles WHERE parent_id = ?1 AND kids = 1", [id])?;
    }
    tx.execute("DELETE FROM profiles WHERE id = ?1", [id])?;
    tx.commit()
}

/// Makes `id` the admin from `now`, with `first_pin` as its PIN when it has
/// none yet — both or neither.
pub(crate) fn claim_admin(conn: &Connection, id: &str, first_pin: Option<&str>, now: i64) -> rusqlite::Result<()> {
    let tx = conn.unchecked_transaction()?;
    if let Some(pin) = first_pin {
        write_pin(&tx, id, pin, now)?;
    }
    tx.execute("UPDATE profiles SET admin_claimed_at = ?2 WHERE id = ?1", params![id, now])?;
    tx.commit()
}
