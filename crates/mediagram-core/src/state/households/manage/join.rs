//! A device joining a household: one PIN, the household admin's or the
//! owner's — the web's `households-join.ts`. Nothing synced is written: the
//! household joined is this device's own, in `state_meta` (`device`).

use super::Placed;
use crate::state::households::device;
use crate::state::profiles::ProfileOutcome::{Done, NoPin, NotFound};
use crate::state::profiles::role_rows::Stored;
use crate::state::profiles::{Answer, ProfileManager};

impl ProfileManager<'_> {
    /// Whether `pin` opens household `id`: its admin's PIN or the owner's;
    /// a right one makes it the household this device shows. Wrong guesses
    /// wait per household, never charged to a profile, so a guesser at the
    /// door cannot lock anybody out of their own profile.
    pub fn join_household(&mut self, id: &str, pin: &str) -> Answer {
        let placed = Placed::read(self.conn)?;
        let Some(household) = placed.households.iter().find(|one| one.id == id) else {
            return Ok(NotFound);
        };
        // The owner the view reads: the first of any claim holders a merge has not settled yet.
        let owner = placed.profiles.iter().find(|one| one.owner).map(|one| one.id.as_str());
        let opens = |row: &&Stored| {
            let door = household.admin_id.as_deref() == Some(row.id.as_str()) || owner == Some(row.id.as_str());
            door && row.pin_hash.is_some() && row.pin_salt.is_some()
        };
        let doors: Vec<&Stored> = placed.rows.iter().filter(opens).collect();
        if doors.is_empty() {
            return Ok(NoPin);
        }
        // `:` is in no profile id, so a household's count never meets a profile's.
        if let Some(refused) = self.compare(&format!("household:{id}"), &doors, pin)? {
            return Ok(refused);
        }
        device::remember(self.conn, id)?;
        Ok(Done)
    }
}
