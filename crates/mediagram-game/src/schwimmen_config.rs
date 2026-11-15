//! Timings of Schwimmen, and its catalogue entry.

use crate::catalogue::{CatalogueEntry, Kids, Need, Seats};
use crate::schwimmen::SchwimmenGame;

#[derive(Debug, Clone)]
pub struct SchwimmenConfig {
    /// How long the banner keeps naming the last happening.
    pub banner_ms: f64,
    /// How long the revealed hands stay before the next round is dealt.
    pub reveal_ms: f64,
    pub seed: Option<u64>,
}

impl Default for SchwimmenConfig {
    fn default() -> Self {
        Self { banner_ms: 2600.0, reveal_ms: 8000.0, seed: None }
    }
}

pub(crate) const ENTRY: CatalogueEntry = CatalogueEntry {
    // Schwimmen deals 3 cards to each and 3 to the middle from 32.
    name: "Schwimmen", seats: Seats::Range(2, 8), timed: false, sized: false,
    kids: Kids::Plays, board: false, needs: Need::Nothing,
    start: |_, ctx, _| Box::new(SchwimmenGame::new(SchwimmenConfig::default(), ctx.players, ctx.now)),
};
