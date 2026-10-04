//! Managing profiles: who may add, remove, enter and re-PIN whom — a port of
//! the web's `profiles-manage.ts`, refusing for the same reasons in the same
//! order.
//!
//! Every operation answers in that order: input that cannot be used (a name,
//! a new PIN, an age); a name another profile here already answers to;
//! somebody not there; what no PIN could make allowed — a kid acting, a
//! second admin, a first profile beside grown-ups; a grown-up with no PIN
//! yet; then, only when a PIN is about to be compared, that profile's
//! wrong-PIN wait; a wrong PIN; and last the rule in `rules`. So the page can
//! say exactly what went wrong, and a guess without the PIN learns nothing
//! about who may do what.
//!
//! Not a login, and not meant as one. A PIN keeps a child from tapping into
//! a grown-up's profile; the catalog filter runs on this device, and anyone
//! with `adb` gets past every check here. What is enforced is that no
//! management action happens unless the PIN and the rule both agree.

use rusqlite::Connection;

use super::Answer;
use super::pin_wait::PinWait;
use super::role_rows::{self, NewProfile, Stored};
use super::rules::Action;
use super::{ProfileOutcome::*, pin};

mod checks;

fn valid_age(age: u8) -> bool {
    age == 6 || age == 12
}

/// One management call's view of the store, the wrong-PIN counts and the
/// time. Made by `StateDb::manage`, one per call.
pub struct ProfileManager<'a> {
    conn: &'a Connection,
    wait: &'a mut PinWait,
    now: i64,
}

impl ProfileManager<'_> {
    /// The first grown-up on a player that has none — the only way a fresh
    /// install gets anyone at all. It runs the household from the start; a
    /// device that later hears of an older claim hands the role over by the
    /// earliest-claim rule the merge already applies.
    pub fn create_first(&mut self, name: &str, new_pin: &str) -> Answer {
        if !pin::valid(new_pin) {
            return Ok(Invalid);
        }
        if let Some(refused) = self.unusable(name)? {
            return Ok(refused);
        }
        if self.rows()?.iter().any(|row| !row.kids) {
            return Ok(NotAllowed);
        }
        let role = NewProfile { pin: Some(new_pin), admin: true, ..Default::default() };
        self.insert(name, &role)
    }

    /// A grown-up, with its first PIN. The admin's to add.
    pub fn create_grown_up(&mut self, actor_id: &str, pin: &str, name: &str, new_pin: &str) -> Answer {
        if !pin::valid(new_pin) {
            return Ok(Invalid);
        }
        if let Some(refused) = self.unusable(name)? {
            return Ok(refused);
        }
        if let Some(refused) = self.check(actor_id, pin, Action::CreateGrownUp, None, false)? {
            return Ok(refused);
        }
        self.insert(name, &NewProfile { pin: Some(new_pin), ..Default::default() })
    }

    /// A kid, belonging to whichever grown-up adds it, with its own limit.
    pub fn create_kid(&mut self, actor_id: &str, pin: &str, name: &str, kids_age: u8) -> Answer {
        if !valid_age(kids_age) {
            return Ok(Invalid);
        }
        if let Some(refused) = self.unusable(name)? {
            return Ok(refused);
        }
        if let Some(refused) = self.check(actor_id, pin, Action::CreateKid, None, false)? {
            return Ok(refused);
        }
        let parent_id = Some(actor_id);
        let role = NewProfile { kids: true, kids_age: Some(kids_age), parent_id, ..Default::default() };
        self.insert(name, &role)
    }

    /// Removing a grown-up takes its kids with it; removing a kid takes only it.
    pub fn remove(&mut self, actor_id: &str, pin: &str, id: &str) -> Answer {
        if let Some(refused) = self.check(actor_id, pin, Action::Remove, Some(id), false)? {
            return Ok(refused);
        }
        let grown_up = self.rows()?.iter().any(|row| row.id == id && !row.kids);
        role_rows::remove(self.conn, id, grown_up)?;
        Ok(Done)
    }

    /// A kid's limit, set by the grown-up it belongs to.
    pub fn set_kids_age(&mut self, actor_id: &str, pin: &str, id: &str, kids_age: u8) -> Answer {
        if !valid_age(kids_age) {
            return Ok(Invalid);
        }
        if let Some(refused) = self.check(actor_id, pin, Action::SetKidsAge, Some(id), false)? {
            return Ok(refused);
        }
        role_rows::write_kids_age(self.conn, id, kids_age, self.now)?;
        Ok(Done)
    }

    /// Entering a profile from the picker. A kid's needs no PIN.
    pub fn unlock(&mut self, id: &str, pin: &str) -> Answer {
        let Some(target) = self.rows()?.into_iter().find(|row| row.id == id) else {
            return Ok(NotFound);
        };
        if target.kids {
            return Ok(Done);
        }
        Ok(self.prove(&target, pin).unwrap_or(Done))
    }

    /// Makes `id` the household's admin — once, while this player knows of
    /// none. A grown-up with a PIN gives it; one without takes the PIN given,
    /// which is why that one has to be a PIN at all.
    pub fn claim_admin(&mut self, id: &str, pin: &str) -> Answer {
        let rows = self.rows()?;
        let target = rows.iter().find(|row| row.id == id);
        if target.is_some_and(|t| !t.kids && t.pin_hash.is_none()) && !pin::valid(pin) {
            return Ok(Invalid);
        }
        let Some(target) = target else {
            return Ok(NotFound);
        };
        // A claim a kid's row still holds is no admin: see `Stored::is_admin`.
        if target.kids || rows.iter().any(Stored::is_admin) {
            return Ok(NotAllowed);
        }
        let first_pin = target.pin_hash.is_none().then_some(pin);
        if first_pin.is_none() {
            if let Some(refused) = self.prove(target, pin) {
                return Ok(refused);
            }
        }
        role_rows::claim_admin(self.conn, id, first_pin, self.now)?;
        Ok(Done)
    }

    /// A grown-up's PIN: its own, or — for the admin — anyone's.
    pub fn set_pin(&mut self, actor_id: &str, pin: &str, id: &str, new_pin: &str) -> Answer {
        if !pin::valid(new_pin) {
            return Ok(Invalid);
        }
        // A grown-up from before PINs sets its first one with nothing to
        // prove: until it has one, its profile is as open as every profile was.
        let rows = self.rows()?;
        let first = actor_id == id && rows.iter().any(|row| row.id == id && !row.kids && row.pin_hash.is_none());
        if let Some(refused) = self.check(actor_id, pin, Action::SetPin, Some(id), first)? {
            return Ok(refused);
        }
        role_rows::write_pin(self.conn, id, new_pin, self.now)?;
        Ok(Done)
    }
}

#[cfg(test)]
#[path = "manage_tests.rs"]
mod tests;
