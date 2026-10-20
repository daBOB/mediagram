//! A profile's row with everything stored about it, the PIN included, and
//! the writes that make one, remove one or make it the admin — the half of
//! the web's `profiles.ts` the management calls stand on. Never sent as it
//! is: `Profile` says whether there is a PIN, never what. A PIN's and a
//! limit's own writes are `role_writes`.

use rusqlite::{Connection, params};

use super::role_writes::write_pin;
use super::rules::RoleView;

mod insert;
pub(crate) use insert::{NewProfile, insert};

/// Everything stored about a profile, the PIN included.
#[derive(Debug, Clone)]
pub(crate) struct Stored {
    pub(crate) id: String,
    pub(crate) name: String,
    pub(crate) kids: bool,
    pub(crate) kids_age: Option<i64>,
    pub(crate) daily_limit: Option<i64>,
    pub(crate) parent_id: Option<String>,
    pub(crate) admin_claimed_at: Option<i64>,
    pub(crate) pin_hash: Option<String>,
    pub(crate) pin_salt: Option<String>,
    /// Section keys this profile does not see, sorted, each once.
    pub(crate) hidden_sections: Vec<String>,
    pub(crate) household_id: String,
    /// When `household_id` was last chosen; 0 for the default.
    pub(crate) household_updated_at: i64,
}

impl Stored {
    /// A grown-up that holds the claim. A grown-up another device later
    /// calls a kid keeps its old claim in the column; it must not make a kid
    /// the admin here.
    pub(crate) fn is_admin(&self) -> bool {
        !self.kids && self.admin_claimed_at.is_some()
    }

    /// The owner: the grown-up holding the install-wide claim, as the web
    /// reads it.
    pub(crate) fn is_owner(&self) -> bool {
        self.is_admin()
    }

    /// The rule's view of this profile, a kid never the admin.
    pub(crate) fn view(&self) -> RoleView {
        RoleView {
            id: self.id.clone(),
            kids: self.kids,
            admin: self.is_admin(),
            owner: self.is_owner(),
            parent_id: self.parent_id.clone(),
            household: Some(self.household_id.clone()),
        }
    }
}

/// Every profile's row, oldest first.
pub(crate) fn load(conn: &Connection) -> rusqlite::Result<Vec<Stored>> {
    let mut stmt = conn.prepare(
        "SELECT id, name, kids, kids_age, parent_id, admin_claimed_at, pin_hash, pin_salt, daily_limit,
                hidden_sections, household_id, household_updated_at
           FROM profiles ORDER BY created_at",
    )?;
    let rows = stmt.query_map([], |row| {
        Ok(Stored {
            id: row.get(0)?,
            name: row.get(1)?,
            kids: row.get::<_, i64>(2)? != 0,
            kids_age: row.get(3)?,
            parent_id: row.get(4)?,
            admin_claimed_at: row.get(5)?,
            pin_hash: row.get(6)?,
            pin_salt: row.get(7)?,
            daily_limit: row.get(8)?,
            hidden_sections: hidden_sections(row, 9)?,
            household_id: row.get(10)?,
            household_updated_at: row.get(11)?,
        })
    })?;
    rows.collect()
}

/// The `hidden_sections` column: a JSON list only this store's own writes
/// and imports put there, so one that does not read is an error, not a guess.
pub(crate) fn hidden_sections(row: &rusqlite::Row, idx: usize) -> rusqlite::Result<Vec<String>> {
    let text: String = row.get(idx)?;
    serde_json::from_str(&text)
        .map_err(|err| rusqlite::Error::FromSqlConversionFailure(idx, rusqlite::types::Type::Text, Box::new(err)))
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
        write_pin(&tx, id, pin, now, false)?;
    }
    tx.execute("UPDATE profiles SET admin_claimed_at = ?2 WHERE id = ?1", params![id, now])?;
    tx.commit()
}
