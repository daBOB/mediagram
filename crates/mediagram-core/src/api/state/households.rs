//! Households across the boundary: what there is, joining one on this
//! device, and the owner's actions on them. Split out of `state.rs` as
//! `profile_roles.rs` is; every rule lives in
//! `crate::state::households::manage`, which refuses as the web does.
//!
//! Nothing here throws: a store that cannot be read or written is logged and
//! answered `Invalid`, or an empty view. Every call that compares a PIN
//! shares this core's wrong-PIN counts; a join's are kept per household.

use std::sync::Arc;

use rusqlite::Connection;

use crate::state::households::{self, Household, NewHouseholdAdmin, device};
use crate::state::profiles::{self, Profile, ProfileOutcome};

use super::super::Core;

/// The households, and who is where: what the owner's screens draw.
#[derive(Debug, Clone, Default, PartialEq, uniffi::Record)]
pub struct HouseholdsView {
    /// The household this device shows; `None` asks to join one.
    pub joined: Option<String>,
    /// The live households by name, each with who runs it.
    pub households: Vec<Household>,
    /// Every profile on this install, each placed in its household — not
    /// only this device's, as `profiles` lists.
    pub everyone: Vec<Profile>,
}

fn view(conn: &Connection) -> rusqlite::Result<HouseholdsView> {
    let everyone = profiles::list(conn)?;
    let joined = device::joined_over(conn, &everyone)?;
    Ok(HouseholdsView { joined, households: households::live(conn, &everyone)?, everyone })
}

#[uniffi::export(async_runtime = "tokio")]
impl Core {
    pub async fn households(self: Arc<Self>) -> HouseholdsView {
        self.blocking(|core| core.state_db.with(view).unwrap_or_default()).await
    }

    /// This device shows household `id` from now, opened by its admin's PIN
    /// or the owner's. `NoPin` where nobody with a PIN runs it.
    pub async fn join_household(self: Arc<Self>, id: String, pin: String) -> ProfileOutcome {
        self.blocking(move |core| core.state_db.manage(|m| m.join_household(&id, &pin))).await
    }

    /// The owner makes a household, run by an existing grown-up who moves in
    /// with their kids, or by a new one.
    pub async fn create_household(
        self: Arc<Self>,
        actor_id: String,
        pin: String,
        name: String,
        admin: NewHouseholdAdmin,
    ) -> ProfileOutcome {
        self.blocking(move |core| core.state_db.manage(|m| m.create_household(&actor_id, &pin, &name, &admin)))
            .await
    }

    pub async fn rename_household(self: Arc<Self>, actor_id: String, pin: String, id: String, name: String) -> ProfileOutcome {
        self.blocking(move |core| core.state_db.manage(|m| m.rename_household(&actor_id, &pin, &id, &name)))
            .await
    }

    /// The owner names one of the household's grown-ups its admin; one with
    /// no PIN takes `new_pin` as their first.
    pub async fn set_household_admin(
        self: Arc<Self>,
        actor_id: String,
        pin: String,
        id: String,
        admin_id: String,
        new_pin: Option<String>,
    ) -> ProfileOutcome {
        self.blocking(move |core| {
            core.state_db
                .manage(|m| m.set_household_admin(&actor_id, &pin, &id, &admin_id, new_pin.as_deref()))
        })
        .await
    }

    /// The owner moves a grown-up and their kids into household `id`. Into
    /// one nobody runs they run it, and one with no PIN takes `new_pin` as
    /// their first; elsewhere `new_pin` changes nothing.
    pub async fn move_to_household(
        self: Arc<Self>,
        actor_id: String,
        pin: String,
        id: String,
        profile_id: String,
        new_pin: Option<String>,
    ) -> ProfileOutcome {
        self.blocking(move |core| {
            core.state_db
                .manage(|m| m.move_to_household(&actor_id, &pin, &id, &profile_id, new_pin.as_deref()))
        })
        .await
    }

    /// The owner deletes an empty household other than Home, for good.
    pub async fn delete_household(self: Arc<Self>, actor_id: String, pin: String, id: String) -> ProfileOutcome {
        self.blocking(move |core| core.state_db.manage(|m| m.delete_household(&actor_id, &pin, &id)))
            .await
    }
}

#[cfg(test)]
#[path = "households_tests.rs"]
mod tests;
