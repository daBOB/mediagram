//! Claiming the admin's role: the owner's, while the install has none; else
//! that of a household nobody runs. Here, beside the owner's actions, as the
//! web's `claimAdmin` is.

use rusqlite::params;

use super::{Placed, give_pin, writes};
use crate::state::households::rules::HouseholdAction;
use crate::state::profiles::ProfileOutcome::{Done, Invalid, NotAllowed, NotFound};
use crate::state::profiles::role_rows::Stored;
use crate::state::profiles::{Answer, ProfileManager, pin};

impl ProfileManager<'_> {
    /// Makes `id` the admin: the owner while this install has none, else its
    /// household's admin while that has none. A grown-up with a PIN proves
    /// it, last; one without takes the PIN given, which is why that has to be
    /// a PIN.
    pub fn claim_admin(&mut self, id: &str, pin: &str) -> Answer {
        let placed = Placed::read(self.conn)?;
        let target = placed.row(id);
        if target.is_some_and(|t| !t.kids && t.pin_hash.is_none()) && !pin::valid(pin) {
            return Ok(Invalid);
        }
        let (Some(target), Some(household)) = (target, placed.profiles.iter().find(|one| one.id == id)) else {
            return Ok(NotFound);
        };
        let household = household.household.as_str();
        // A claim a kid's row still holds is no owner: see `Stored::is_owner`.
        let first_claim = !placed.rows.iter().any(Stored::is_owner);
        let may_claim = !target.kids
            && (first_claim || placed.allowed(id, HouseholdAction::ClaimHousehold, household, ""));
        if !may_claim {
            return Ok(NotAllowed);
        }
        if target.pin_hash.is_some()
            && let Some(refused) = self.prove(target, pin)?
        {
            return Ok(refused);
        }
        let tx = self.conn.unchecked_transaction()?;
        give_pin(&tx, id, Some(target), Some(pin), self.now)?;
        if first_claim {
            tx.execute("UPDATE profiles SET admin_claimed_at = ?2 WHERE id = ?1", params![id, self.now])?;
        } else {
            writes::admin(&tx, household, id, self.now)?;
        }
        tx.commit()?;
        Ok(Done)
    }
}
