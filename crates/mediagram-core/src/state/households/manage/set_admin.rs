//! The owner names who runs a household.

use super::{give_pin, pinless, unusable_pin, writes};
use crate::state::households::rules::HouseholdAction;
use crate::state::profiles::ProfileOutcome::{Done, Invalid, NoPin};
use crate::state::profiles::{Answer, ProfileManager};

impl ProfileManager<'_> {
    /// One of the household's own grown-ups. Never where the owner is: the
    /// owner runs the household they are in (the rule says so). An
    /// admin-to-be with no PIN takes `new_pin` as their first.
    pub fn set_household_admin(
        &mut self,
        actor_id: &str,
        pin: &str,
        id: &str,
        admin_id: &str,
        new_pin: Option<&str>,
    ) -> Answer {
        if unusable_pin(new_pin) {
            return Ok(Invalid);
        }
        let placed = match self.owned(actor_id, pin, HouseholdAction::SetHouseholdAdmin, id, Some(admin_id))? {
            Ok(placed) => placed,
            Err(refused) => return Ok(refused),
        };
        let target = placed.row(admin_id);
        if pinless(target, new_pin) {
            return Ok(NoPin);
        }
        let tx = self.conn.unchecked_transaction()?;
        writes::admin(&tx, id, admin_id, self.now)?;
        give_pin(&tx, admin_id, target, new_pin, self.now)?;
        tx.commit()?;
        Ok(Done)
    }
}
