//! Zündstoff's bomb in the air: the throw, the target's tap as it comes down,
//! and whether it lands in their hands or at their feet.

use crate::ask::Ask;
use crate::protocol::{PlayerId, ServerTime};
use crate::zuendstoff::ZuendstoffGame;
use crate::zuendstoff_config::{Catch, Stage};

impl ZuendstoffGame {
    pub(crate) fn throw(&mut self, from: PlayerId, to: PlayerId, now: ServerTime) {
        let released_at = now + self.cfg.throw_lead_ms;
        let lands_at = released_at + self.cfg.flight_ms;
        self.stage = Stage::Flight { from, to, thrown_at: now, released_at, lands_at, catch: Catch::Pending };
    }

    /// The target's tap at `now`: in the window a catch, before it a fumble, after it nothing.
    pub(crate) fn tap_counts_as(&self, lands_at: ServerTime, now: ServerTime) -> Option<Catch> {
        if now < lands_at - self.cfg.catch_before_ms - self.cfg.early_tolerance_ms {
            Some(Catch::Fumbled)
        } else if now <= lands_at + self.cfg.catch_after_ms {
            Some(Catch::Caught)
        } else {
            None
        }
    }

    /// The bomb lands by `to`, seen at `now`: in their hands if `caught`, else at their feet.
    pub(crate) fn land(&mut self, to: PlayerId, lands_at: ServerTime, caught: bool, now: ServerTime) {
        let ready_at = if caught { now + self.cfg.catch_guard_ms } else { lands_at + self.cfg.pickup_ms };
        self.stage = Stage::Hold { ask: Ask::open(to, lands_at, None), ready_at, caught: Some(caught) };
    }
}
