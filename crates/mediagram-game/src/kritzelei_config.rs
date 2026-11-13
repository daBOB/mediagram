//! Kritzelei's numbers, in one place.

use crate::protocol::ServerTime;

#[derive(Debug, Clone)]
#[allow(dead_code)]
pub struct KritzeleiConfig {
    pub draw_ms: ServerTime,
    pub reveal_ms: ServerTime,
    pub guesser_points: i32,
    pub drawer_points: i32,
    /// Fewer players than this draw twice each.
    pub two_rounds_below: usize,
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
            max_points: 20_000,
            max_batch: 512,
        }
    }
}
