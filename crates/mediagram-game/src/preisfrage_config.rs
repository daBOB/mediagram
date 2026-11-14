//! Preisfrage's numbers, in one place.

use crate::protocol::{GameLength, GameSpeed, ServerTime};

/// Fewer articles than this and the lobby says the game needs some.
pub(crate) const MIN_ARTICLES: usize = 3;
/// Fewer reviews than this and nobody knows the article: such articles are drawn first.
pub(crate) const FEW_REVIEWS: u32 = 20;

#[derive(Debug, Clone)]
pub struct PreisfrageConfig {
    /// Articles per game, as many as the show prices in an episode.
    pub rounds: usize,
    /// The article alone before the keypads open.
    pub intro_ms: ServerTime,
    /// The keypads open: the lobby's tempo sets it, see [`PreisfrageConfig::at`].
    pub guess_ms: ServerTime,
    pub reveal_ms: ServerTime,
    /// On top of the 1000 a guess to the cent earns.
    pub exact_bonus: u32,
}

impl Default for PreisfrageConfig {
    fn default() -> Self {
        Self { rounds: 6, intro_ms: 3_000.0, guess_ms: 60_000.0, reveal_ms: 7_000.0, exact_bonus: 500 }
    }
}

impl PreisfrageConfig {
    /// At the lobby's length: the number of articles.
    pub fn sized(mut self, length: GameLength) -> Self {
        self.rounds = length.scale(self.rounds);
        self
    }

    /// At the lobby's tempo: 2 min to guess, 1 min, or 30 s.
    pub fn at(mut self, speed: GameSpeed) -> Self {
        self.guess_ms = speed.stretch(self.guess_ms);
        self
    }
}
