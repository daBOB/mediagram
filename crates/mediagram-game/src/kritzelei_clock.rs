//! The turn's clock: it runs while someone draws and stops while the board is away.

use crate::protocol::ServerTime;
use crate::protocol_board::Clock;

#[derive(Debug, Clone)]
pub(crate) struct TurnClock {
    left_ms: f64,
    since: Option<ServerTime>,
}

impl TurnClock {
    pub(crate) fn new(ms: f64) -> Self {
        Self { left_ms: ms, since: None }
    }

    pub(crate) fn left(&self, now: ServerTime) -> f64 {
        match self.since {
            Some(since) => (self.left_ms - (now - since)).max(0.0),
            None => self.left_ms.max(0.0),
        }
    }

    /// No-op while running or when nothing is left.
    pub(crate) fn start(&mut self, now: ServerTime) {
        if self.since.is_none() && self.left_ms > 0.0 {
            self.since = Some(now);
        }
    }

    pub(crate) fn pause(&mut self, now: ServerTime) {
        self.left_ms = self.left(now);
        self.since = None;
    }

    pub(crate) fn wire(&self, total_ms: f64) -> Clock {
        Clock { ends_at: self.since.map(|s| s + self.left_ms), left_ms: self.left_ms, total_ms }
    }
}

#[cfg(test)]
#[path = "kritzelei_clock_tests.rs"]
mod tests;
