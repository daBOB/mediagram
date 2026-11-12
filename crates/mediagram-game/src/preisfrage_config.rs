//! Preisfrage's numbers, in one place.

use crate::protocol::ServerTime;

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
    pub guess_ms: ServerTime,
    pub reveal_ms: ServerTime,
    /// On top of the 1000 a guess to the cent earns.
    pub exact_bonus: u32,
}

impl Default for PreisfrageConfig {
    fn default() -> Self {
        Self { rounds: 6, intro_ms: 3_000.0, guess_ms: 20_000.0, reveal_ms: 7_000.0, exact_bonus: 500 }
    }
}
