//! Adding a grown-up or a kid — a maker's own household or kid, or, for the
//! owner, any household or any grown-up. Split out of `manage.rs` to keep it
//! under the line limit.

use super::{daily_limit, valid_age, ProfileManager};
use crate::state::households::{live, manage::writes, HOME};
use crate::state::profiles::role_rows::{self, NewProfile};
use crate::state::profiles::rules::Action;
use crate::state::profiles::ProfileOutcome::{Done, Invalid, NotFound};
use crate::state::profiles::{list, pin, Answer};

impl ProfileManager<'_> {
    /// A grown-up, with its first PIN, in `household` or else its maker's.
    /// The admin's to add; the owner's to add anywhere. A household nobody
    /// runs gets the new grown-up as its admin, in the same transaction.
    pub fn create_grown_up(
        &mut self,
        actor_id: &str,
        pin: &str,
        name: &str,
        new_pin: &str,
        household: Option<&str>,
    ) -> Answer {
        if !pin::valid(new_pin) {
            return Ok(Invalid);
        }
        if let Some(refused) = self.unusable(name)? {
            return Ok(refused);
        }
        let named = household.filter(|id| !id.is_empty());
        let households = live(self.conn, &list(self.conn)?)?;
        let target = named.and_then(|id| households.iter().find(|one| one.id == id));
        if named.is_some() && target.is_none() {
            return Ok(NotFound);
        }
        if let Some(refused) = self.check(actor_id, pin, Action::CreateGrownUp, named, false)? {
            return Ok(refused);
        }
        let placed = match named {
            None => self.household_of(actor_id)?,
            Some(id) => Some(id.to_string()).filter(|id| id != HOME),
        };
        let tx = self.conn.unchecked_transaction()?;
        let role = NewProfile {
            pin: Some(new_pin),
            household: placed,
            ..Default::default()
        };
        let Some(made) = role_rows::insert(&tx, name, &role, self.now)? else {
            return Ok(Invalid);
        };
        if let Some(empty) = target.filter(|one| one.admin_id.is_none()) {
            writes::admin(&tx, &empty.id, &made.id, self.now)?;
        }
        tx.commit()?;
        Ok(Done)
    }

    /// A kid, belonging to `parent_id` or else whichever grown-up adds it,
    /// with its own limit; it lives in its parent's household.
    pub fn create_kid(
        &mut self,
        actor_id: &str,
        pin: &str,
        name: &str,
        kids_age: u8,
        daily_limit: Option<u16>,
        parent_id: Option<&str>,
    ) -> Answer {
        if !valid_age(kids_age) || !daily_limit.is_none_or(daily_limit::valid_minutes) {
            return Ok(Invalid);
        }
        if let Some(refused) = self.unusable(name)? {
            return Ok(refused);
        }
        let named = parent_id.filter(|id| !id.is_empty());
        if let Some(refused) = self.check(actor_id, pin, Action::CreateKid, named, false)? {
            return Ok(refused);
        }
        let parent = named.unwrap_or(actor_id);
        let household = self.household_of(parent)?;
        let role = NewProfile {
            kids: true,
            kids_age: Some(kids_age),
            daily_limit,
            parent_id: Some(parent),
            household,
            ..Default::default()
        };
        self.insert(name, &role)
    }
}
