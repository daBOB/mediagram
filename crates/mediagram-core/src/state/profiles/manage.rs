//! Managing profiles: who may add, remove, enter and re-PIN whom — a port of
//! the web's `profiles-manage.ts`, refusing for the same reasons in the same
//! order. Claiming the admin's role, per household, is
//! `crate::state::households::manage`'s, as on the web.
//!
//! Every operation answers in that order: input that cannot be used (a name,
//! a new PIN, an age); a name another profile here already answers to;
//! somebody not there; what no PIN could make allowed — a kid acting, a
//! second admin, a first profile beside grown-ups; a grown-up with no PIN
//! yet; then, only when a PIN is about to be compared, that profile's
//! wrong-PIN wait; a wrong PIN; and last the rule in `rules`. So the page can
//! say exactly what went wrong, and a guess without the PIN learns nothing
//! about who may do what. A first profile alone also waits for a sync round
//! of the library this device follows, after everything else says yes — as
//! the web's does.
//!
//! Not a login, and not meant as one. A PIN keeps a child from tapping into
//! a grown-up's profile; the catalog filter runs on this device, and anyone
//! with `adb` gets past every check here. What is enforced is that no
//! management action happens unless the PIN and the rule both agree.

use rusqlite::Connection;

use super::Answer;
use super::role_rows::{self, NewProfile};
use super::role_writes;
use super::rules::Action;
use super::{ProfileOutcome::*, pin};
use crate::state::sync;

mod checks;
mod daily_limit;
mod hidden_sections;

fn valid_age(age: u8) -> bool {
    age == 6 || age == 12
}

/// One management call's view of the store — the wrong-PIN counts in it —
/// and the time. Made by `StateDb::manage`, one per call.
pub struct ProfileManager<'a> {
    pub(crate) conn: &'a Connection,
    pub(crate) now: i64,
}

impl ProfileManager<'_> {
    /// The first grown-up on a player that has none — the only way a fresh
    /// install gets anyone at all. It runs the household from the start; a
    /// device that later hears of an older claim hands the role over by the
    /// earliest-claim rule the merge already applies.
    ///
    /// Only once this player has taken in a sync round of the library it
    /// follows. Before, it has not heard that household's names, so it
    /// cannot refuse one already taken:
    /// sync knows a viewer by name, and the newer PIN wins — a first profile
    /// made blind as "André" would hand its PIN to André on every device.
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
        if !sync::round_imported(self.conn)? {
            return Ok(NotSynced);
        }
        let role = NewProfile { pin: Some(new_pin), admin: true, ..Default::default() };
        self.insert(name, &role)
    }

    /// A grown-up, with its first PIN, in its maker's household. The admin's to add.
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
        let household = self.household_of(actor_id)?;
        self.insert(name, &NewProfile { pin: Some(new_pin), household, ..Default::default() })
    }

    /// A kid, belonging to whichever grown-up adds it, with its own limit,
    /// in that grown-up's household.
    pub fn create_kid(
        &mut self,
        actor_id: &str,
        pin: &str,
        name: &str,
        kids_age: u8,
        daily_limit: Option<u16>,
    ) -> Answer {
        if !valid_age(kids_age) || !daily_limit.is_none_or(daily_limit::valid_minutes) {
            return Ok(Invalid);
        }
        if let Some(refused) = self.unusable(name)? {
            return Ok(refused);
        }
        if let Some(refused) = self.check(actor_id, pin, Action::CreateKid, None, false)? {
            return Ok(refused);
        }
        let (parent_id, household) = (Some(actor_id), self.household_of(actor_id)?);
        let role = NewProfile { kids: true, kids_age: Some(kids_age), daily_limit, parent_id, household, ..Default::default() };
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
        role_writes::write_kids_age(self.conn, id, kids_age, self.now)?;
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
        Ok(self.prove(&target, pin)?.unwrap_or(Done))
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
        // Proven unless it is that first one: the actor's own PIN was right,
        // whether it changed its own or, as the admin, reset another's.
        role_writes::write_pin(self.conn, id, new_pin, self.now, !first)?;
        Ok(Done)
    }
}

#[cfg(test)]
#[path = "manage_tests.rs"]
mod tests;
