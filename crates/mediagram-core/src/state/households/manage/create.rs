//! A new household, and its admin: a grown-up already here, who moves in with
//! their kids, or a new one made with it.

use super::{give_pin, pinless, unusable_pin, writes};
use crate::state::households::rules::HouseholdAction;
use crate::state::profiles::ProfileOutcome::{Done, Invalid, NameTaken, NoPin};
use crate::state::profiles::role_rows::{self, NewProfile};
use crate::state::profiles::rules::name_taken;
use crate::state::profiles::{Answer, ProfileManager, clean_name};

/// A new household's admin.
#[derive(Debug, Clone, PartialEq, uniffi::Enum)]
pub enum NewHouseholdAdmin {
    /// A grown-up already here, by id. `new_pin` is their first PIN, used
    /// only when they have none.
    Existing { id: String, new_pin: Option<String> },
    /// A grown-up made with the household, by name, with their first PIN.
    New { name: String, new_pin: String },
}

impl ProfileManager<'_> {
    /// A household and its admin, who — an existing grown-up — moves in with
    /// their kids. Never the owner: the rule keeps them where they are.
    pub fn create_household(&mut self, actor_id: &str, pin: &str, name: &str, admin: &NewHouseholdAdmin) -> Answer {
        let (admin_id, admin_name, new_pin) = match admin {
            NewHouseholdAdmin::Existing { id, new_pin } => (Some(id.as_str()), None, new_pin.as_deref()),
            NewHouseholdAdmin::New { name, new_pin } => (None, Some(name.as_str()), Some(new_pin.as_str())),
        };
        let Some(clean) = clean_name(name) else {
            return Ok(Invalid);
        };
        if admin_name.is_some_and(|name| clean_name(name).is_none()) || unusable_pin(new_pin) {
            return Ok(Invalid);
        }
        // A former name stays taken, as a current one is: sync would join the two.
        let names = crate::state::profiles::role_rows::taken_names(self.conn, None)?;
        if self.household_name_taken(name, None)? || admin_name.is_some_and(|name| name_taken(&names, name)) {
            return Ok(NameTaken);
        }
        let placed = match self.owned(actor_id, pin, HouseholdAction::CreateHousehold, "", admin_id)? {
            Ok(placed) => placed,
            Err(refused) => return Ok(refused),
        };
        let target = admin_id.and_then(|id| placed.row(id));
        if pinless(target, new_pin) {
            return Ok(NoPin);
        }
        let id = ulid::Ulid::new().to_string();
        let tx = self.conn.unchecked_transaction()?;
        let runs = match (admin_id, admin_name) {
            (Some(admin_id), _) => {
                writes::move_grown_up(&tx, admin_id, &id, self.now)?;
                admin_id.to_string()
            }
            (None, name) => {
                let role = NewProfile { household: Some(id.clone()), ..Default::default() };
                let Some(made) = role_rows::insert(&tx, name.unwrap_or_default(), &role, self.now)? else {
                    return Ok(Invalid);
                };
                made.id
            }
        };
        give_pin(&tx, &runs, target, new_pin, self.now)?;
        writes::insert(&tx, &id, &clean, &runs, self.now)?;
        tx.commit()?;
        Ok(Done)
    }
}
