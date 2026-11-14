//! Kritzelei's numbers, in one place.

use crate::protocol::{GameLength, GameSpeed, ServerTime};

#[derive(Debug, Clone)]
pub struct KritzeleiConfig {
    pub draw_ms: ServerTime,
    pub reveal_ms: ServerTime,
    pub guesser_points: i32,
    pub drawer_points: i32,
    /// Fewer players than this draw twice each.
    pub two_rounds_below: usize,
    /// The lobby's length: how many times round the table, see `turn_order`.
    pub length: GameLength,
    /// Points one turn's drawing may hold; ops beyond are dropped.
    pub max_points: usize,
    /// Values (x and y each count) one op may carry.
    pub max_batch: usize,
}

impl Default for KritzeleiConfig {
    fn default() -> Self {
        Self {
            draw_ms: 90_000.0,
            reveal_ms: 6_000.0,
            guesser_points: 2,
            drawer_points: 1,
            two_rounds_below: 5,
            length: GameLength::Normal,
            max_points: 20_000,
            max_batch: 512,
        }
    }
}

impl KritzeleiConfig {
    /// At the lobby's length: how many times everyone draws.
    pub fn sized(mut self, length: GameLength) -> Self {
        self.length = length;
        self
    }

    /// At the lobby's tempo: the time to draw.
    pub fn at(mut self, speed: GameSpeed) -> Self {
        self.draw_ms = speed.stretch(self.draw_ms);
        self
    }
}
