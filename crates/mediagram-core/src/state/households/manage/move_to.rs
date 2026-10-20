//! The owner moves a grown-up, and their kids, into another household.

use super::{give_pin, pinless, unusable_pin, writes};
use crate::state::households::rules::HouseholdAction;
use crate::state::profiles::ProfileOutcome::{Done, Invalid, NoPin};
use crate::state::profiles::{Answer, ProfileManager};

impl ProfileManager<'_> {
    /// A grown-up and their kids. Into a household nobody runs, the grown-up
    /// runs it, so needs a PIN: theirs, or `new_pin` as a first one.
    /// Otherwise `new_pin` is not asked for, and changes nothing. Never the
    /// owner, and a household's admin only as its last grown-up.
    pub fn move_to_household(
        &mut self,
        actor_id: &str,
        pin: &str,
        id: &str,
        profile_id: &str,
        new_pin: Option<&str>,
    ) -> Answer {
        if unusable_pin(new_pin) {
            return Ok(Invalid);
        }
        let placed = match self.owned(actor_id, pin, HouseholdAction::MoveToHousehold, id, Some(profile_id))? {
            Ok(placed) => placed,
            Err(refused) => return Ok(refused),
        };
        let runs = placed.households.iter().any(|one| one.id == id && one.admin_id.is_none());
        let row = placed.row(profile_id);
        if runs && pinless(row, new_pin) {
            return Ok(NoPin);
        }
        let tx = self.conn.unchecked_transaction()?;
        writes::move_grown_up(&tx, profile_id, id, self.now)?;
        if runs {
            writes::admin(&tx, id, profile_id, self.now)?;
            give_pin(&tx, profile_id, row, new_pin, self.now)?;
        }
        tx.commit()?;
        Ok(Done)
    }
}
