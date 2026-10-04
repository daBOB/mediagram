//! What every management call asks before it writes — a usable name,
//! somebody there, a grown-up's PIN and the rule — and the one place the
//! calls are made from. Split out of `manage.rs` to keep it under the line
//! limit.

use std::sync::PoisonError;

use super::ProfileManager;
use crate::state::StateDb;
use crate::state::profiles::ProfileOutcome::{
    self, Done, Invalid, NameTaken, NoPin, NotAllowed, NotFound, Wait, WrongPin,
};
use crate::state::profiles::Answer;
use crate::state::profiles::role_rows::{self, NewProfile, Stored};
use crate::state::profiles::rules::{self, Action};
use crate::state::profiles::{clean_name, now_ms, pin};

/// `None` goes on; `Some` is the refusal.
pub(super) type Check = rusqlite::Result<Option<ProfileOutcome>>;

impl ProfileManager<'_> {
    pub(super) fn rows(&self) -> rusqlite::Result<Vec<Stored>> {
        role_rows::load(self.conn)
    }

    pub(super) fn insert(&self, name: &str, role: &NewProfile) -> Answer {
        let made = role_rows::insert(self.conn, name, role, self.now)?;
        Ok(if made.is_some() { Done } else { Invalid })
    }

    /// A new profile's name: one that cannot be stored, or one another
    /// profile here already answers to — which sync would read as the same
    /// viewer.
    pub(super) fn unusable(&self, name: &str) -> Check {
        if clean_name(name).is_none() {
            return Ok(Some(Invalid));
        }
        let names: Vec<String> = self.rows()?.into_iter().map(|row| row.name).collect();
        Ok(rules::name_taken(&names, name).then_some(NameTaken))
    }

    /// Somebody there, a grown-up acting, its PIN unless `unproven`, then
    /// the rule.
    pub(super) fn check(
        &mut self,
        actor_id: &str,
        pin: &str,
        action: Action,
        target_id: Option<&str>,
        unproven: bool,
    ) -> Check {
        let rows = self.rows()?;
        let found = |id: &str| rows.iter().find(|row| row.id == id);
        let Some(actor) = found(actor_id) else {
            return Ok(Some(NotFound));
        };
        if target_id.is_some_and(|id| found(id).is_none()) {
            return Ok(Some(NotFound));
        }
        // A kid manages nothing, and has no PIN to prove otherwise with.
        if actor.kids {
            return Ok(Some(NotAllowed));
        }
        if !unproven {
            if let Some(refused) = self.prove(actor, pin) {
                return Ok(Some(refused));
            }
        }
        let views: Vec<_> = rows.iter().map(Stored::view).collect();
        let allowed = rules::allowed(&views, actor_id, action, target_id.unwrap_or_default());
        Ok((!allowed).then_some(NotAllowed))
    }

    /// A grown-up's PIN: none yet; or — only now that one is about to be
    /// compared — that profile's wait, then the comparison and the count it
    /// keeps. A PIN that is not four digits is compared like any other, and
    /// is wrong.
    pub(super) fn prove(&mut self, row: &Stored, pin: &str) -> Option<ProfileOutcome> {
        let (Some(hash), Some(salt)) = (&row.pin_hash, &row.pin_salt) else {
            return Some(NoPin);
        };
        let seconds = self.wait.seconds_left(&row.id, self.now);
        if seconds > 0 {
            return Some(Wait { seconds });
        }
        if !pin::valid(pin) || !pin::matches(hash, salt, pin) {
            self.wait.failed(&row.id, self.now);
            return Some(WrongPin);
        }
        self.wait.succeeded(&row.id);
        None
    }
}

impl StateDb {
    /// Runs one management call against the store and this owner's
    /// wrong-PIN counts. A store that cannot be read or written is logged by
    /// `with` and answered `Invalid` — nothing on this surface throws.
    pub fn manage(&self, call: impl FnOnce(&mut ProfileManager<'_>) -> Answer) -> ProfileOutcome {
        self.manage_at(now_ms(), call)
    }

    /// `manage`, with the clock at `now`.
    pub(crate) fn manage_at(
        &self,
        now: i64,
        call: impl FnOnce(&mut ProfileManager<'_>) -> Answer,
    ) -> ProfileOutcome {
        self.with(|conn| {
            // Taken inside the connection's lock, as `ticks` is, so the two
            // are always taken in the same order.
            let mut wait = self.pin_wait.lock().unwrap_or_else(PoisonError::into_inner);
            let wait = &mut *wait;
            call(&mut ProfileManager { conn, wait, now })
        })
        .unwrap_or(Invalid)
    }
}
