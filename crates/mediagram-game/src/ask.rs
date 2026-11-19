//! An ask: one thing the host wants one player to decide on their phone. It
//! knows who is asked, when it opened and its clock, if it runs on one, and so
//! when the house decides instead. What is asked stays with each game.

use crate::game::GameCtx;
use crate::protocol::{PlayerId, ServerTime};

/// How long the house waits before deciding for an absent player; the same at every table.
pub(crate) const HOUSE_GRACE_MS: ServerTime = 1500.0;

#[derive(Debug, Clone, Copy, PartialEq)]
pub(crate) struct Ask {
    who: PlayerId,
    opened_at: ServerTime,
    closes_at: Option<ServerTime>,
}

impl Ask {
    /// Asks `who` from `at`, on a clock of `clock_ms` if there is one.
    pub(crate) fn open(who: PlayerId, at: ServerTime, clock_ms: Option<ServerTime>) -> Self {
        Self { who, opened_at: at, closes_at: clock_ms.map(|ms| at + ms) }
    }

    pub(crate) fn who(&self) -> PlayerId {
        self.who
    }

    pub(crate) fn opened_at(&self) -> ServerTime {
        self.opened_at
    }

    /// When the clock runs out; `None` when the ask waits as long as it takes.
    pub(crate) fn closes_at(&self) -> Option<ServerTime> {
        self.closes_at
    }

    /// When the house decides: when the clock runs out, or the grace after the
    /// later of the player leaving and the ask opening, whichever comes first;
    /// `None` while neither will. Counting from the later of the two means a
    /// phone that blips after a long think does not have a move made for it at once.
    pub(crate) fn house_due(&self, ctx: &GameCtx) -> Option<ServerTime> {
        let grace = ctx.absent_since(self.who).map(|left| left.max(self.opened_at) + HOUSE_GRACE_MS);
        match (grace, self.closes_at) {
            (Some(grace), Some(clock)) => Some(clock.min(grace)),
            (grace, clock) => grace.or(clock),
        }
    }

    pub(crate) fn house_decides(&self, ctx: &GameCtx) -> bool {
        self.house_due(ctx).is_some_and(|at| ctx.now >= at)
    }
}

#[cfg(test)]
#[path = "ask_tests.rs"]
mod tests;
