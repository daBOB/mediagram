//! Households, managed: the owner's actions on them, a device joining one,
//! and claiming the admin's role per household — a port of the web's
//! `households-manage.ts` and `households-join.ts`, refusing for the same
//! reasons in the same order. The calls hang off [`ProfileManager`], as the
//! web's `HouseholdManager` extends its profile manager, so they share its
//! store, its clock and its wrong-PIN counts.
//!
//! Every owner action answers in one order, as a profile's does: input that
//! cannot be used; a name already taken; somebody, or a household, not
//! there; a kid acting; the actor's PIN; the household rule; and last an
//! admin-to-be with no PIN and none given, since every household's admin has
//! one. Each writes in one transaction.

use rusqlite::Connection;

use super::rules::{self, HouseholdAction};
use super::{Household, live, load_rows, resolve};
use crate::state::profiles::ProfileOutcome::{self, Done, Invalid, NameTaken, NotAllowed, NotFound};
use crate::state::profiles::role_rows::{self, Stored};
use crate::state::profiles::role_writes::write_pin;
use crate::state::profiles::rules::{RoleView, name_taken};
use crate::state::profiles::{Answer, Profile, ProfileManager, clean_name, pin};

mod claim;
mod create;
mod join;
mod move_to;
mod set_admin;
mod writes;

pub use create::NewHouseholdAdmin;

/// One read of the store, everyone placed: what an action decides over and
/// goes on with.
struct Placed {
    rows: Vec<Stored>,
    profiles: Vec<Profile>,
    households: Vec<Household>,
}

impl Placed {
    fn read(conn: &Connection) -> rusqlite::Result<Self> {
        let rows = role_rows::load(conn)?;
        let profiles = resolve(rows.clone(), &load_rows(conn)?);
        let households = live(conn, &profiles)?;
        Ok(Placed { rows, profiles, households })
    }

    fn row(&self, id: &str) -> Option<&Stored> {
        self.rows.iter().find(|row| row.id == id)
    }

    fn is_live(&self, id: &str) -> bool {
        self.households.iter().any(|one| one.id == id)
    }

    /// The household rule, over everyone as placed.
    fn allowed(&self, actor_id: &str, action: HouseholdAction, household_id: &str, target_id: &str) -> bool {
        let views: Vec<RoleView> = self.profiles.iter().map(RoleView::from).collect();
        let live: Vec<String> = self.households.iter().map(|one| one.id.clone()).collect();
        rules::allowed(&views, &live, actor_id, action, household_id, target_id)
    }
}

/// A new PIN that came and is not one. None at all is `NoPin`'s to say, last.
fn unusable_pin(new_pin: Option<&str>) -> bool {
    new_pin.is_some_and(|new_pin| !pin::valid(new_pin))
}

fn has_pin(row: Option<&Stored>) -> bool {
    row.is_some_and(|row| row.pin_hash.is_some())
}

/// An admin-to-be (`None`: one not made yet) with no PIN, and none to give it.
fn pinless(row: Option<&Stored>, new_pin: Option<&str>) -> bool {
    !has_pin(row) && !new_pin.is_some_and(pin::valid)
}

/// A first PIN, where the admin-to-be has none; unproven, so one set
/// elsewhere still wins. One who has a PIN keeps it.
fn give_pin(conn: &Connection, id: &str, row: Option<&Stored>, new_pin: Option<&str>, now: i64) -> rusqlite::Result<()> {
    match new_pin.filter(|new_pin| pin::valid(new_pin)) {
        Some(new_pin) if !has_pin(row) => write_pin(conn, id, new_pin, now, false),
        _ => Ok(()),
    }
}

impl ProfileManager<'_> {
    pub fn rename_household(&mut self, actor_id: &str, pin: &str, id: &str, name: &str) -> Answer {
        let Some(clean) = clean_name(name) else {
            return Ok(Invalid);
        };
        if self.household_name_taken(name, Some(id))? {
            return Ok(NameTaken);
        }
        if let Err(refused) = self.owned(actor_id, pin, HouseholdAction::RenameHousehold, id, None)? {
            return Ok(refused);
        }
        writes::name(self.conn, id, &clean, self.now)?;
        Ok(Done)
    }

    /// An empty household other than Home, for good.
    pub fn delete_household(&mut self, actor_id: &str, pin: &str, id: &str) -> Answer {
        if let Err(refused) = self.owned(actor_id, pin, HouseholdAction::DeleteHousehold, id, None)? {
            return Ok(refused);
        }
        writes::remove(self.conn, id)?;
        Ok(Done)
    }

    /// Whether a live household other than `except` already answers to
    /// `name`, compared as profile names are. Only here, at a local create
    /// or rename: two devices may still end up with the same name.
    fn household_name_taken(&self, name: &str, except: Option<&str>) -> rusqlite::Result<bool> {
        // Names only: who runs each is not asked, so no profiles are read.
        let others = live(self.conn, &[])?.into_iter().filter(|one| Some(one.id.as_str()) != except);
        Ok(name_taken(&others.map(|one| one.name).collect::<Vec<_>>(), name))
    }

    /// Somebody there and the household too, a grown-up acting, its PIN,
    /// then the household rule. `target_id` `None` is an action on no profile.
    fn owned(
        &self,
        actor_id: &str,
        pin: &str,
        action: HouseholdAction,
        household_id: &str,
        target_id: Option<&str>,
    ) -> rusqlite::Result<Result<Placed, ProfileOutcome>> {
        let placed = Placed::read(self.conn)?;
        let Some(actor) = placed.row(actor_id) else {
            return Ok(Err(NotFound));
        };
        let creating = action == HouseholdAction::CreateHousehold;
        if target_id.is_some_and(|id| placed.row(id).is_none()) || (!creating && !placed.is_live(household_id)) {
            return Ok(Err(NotFound));
        }
        // A kid manages nothing, and has no PIN to prove otherwise with.
        if actor.kids {
            return Ok(Err(NotAllowed));
        }
        if let Some(refused) = self.prove(actor, pin)? {
            return Ok(Err(refused));
        }
        let allowed = placed.allowed(actor_id, action, household_id, target_id.unwrap_or_default());
        Ok(if allowed { Ok(placed) } else { Err(NotAllowed) })
    }
}

#[cfg(test)]
#[path = "manage_tests.rs"]
mod tests;
