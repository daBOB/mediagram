//! Kopfnuss tuning: every count and timing of a game, in one place.

use crate::protocol::ServerTime;
use crate::quiz_config::QuizConfig;

#[derive(Debug, Clone)]
pub struct KopfnussConfig {
    pub rounds: usize,
    /// Categories offered per vote.
    pub offered: usize,
    /// Questions asked per chosen category.
    pub per_round: usize,
    /// Vote, trick and target, all on one clock.
    pub pick_ms: ServerTime,
    /// The TV shows the chosen category and the tricks landing.
    pub resolve_ms: ServerTime,
    /// Most tricks one phone carries; more bounce off.
    pub max_tricks: usize,
    /// Tricks each player picks from.
    pub trick_offer: usize,
    /// A tap opens the next pick step or climb question this much later, so the
    /// second tap of a double tap cannot answer a screen nobody saw.
    pub next_gap_ms: ServerTime,
    /// Timing and scoring of each round's questions (`rounds` is `per_round`).
    pub quiz: QuizConfig,
    /// Gipfelsturm: steps to the top.
    pub steps: u8,
    /// Head start by rank: 1st, 2nd, 3rd …; later ranks start at the bottom.
    pub head_start: Vec<u8>,
    /// A wrong answer keeps the next question shut this long.
    pub lock_ms: ServerTime,
    pub climb_ms: ServerTime,
    /// Summit bonus per step reached; the podium's points follow the climb.
    pub step_points: i32,
    /// Questions in the finale's shared stream.
    pub deck: usize,
    pub fact_ms: ServerTime,
    pub seed: Option<u64>,
}

impl Default for KopfnussConfig {
    fn default() -> Self {
        Self {
            rounds: 7,
            offered: 3,
            per_round: 3,
            pick_ms: 12_000.0,
            resolve_ms: 4000.0,
            max_tricks: 3,
            trick_offer: 2,
            next_gap_ms: 400.0,
            quiz: QuizConfig { intro_ms: 3000.0, reveal_ms: 4000.0, ..QuizConfig::default() },
            steps: 10,
            head_start: vec![3, 2, 1],
            lock_ms: 2000.0,
            climb_ms: 90_000.0,
            step_points: 100,
            deck: 30,
            fact_ms: 12_000.0,
            seed: None,
        }
    }
}

impl KopfnussConfig {
    /// General questions one game can ask at most, so a spent night starts over in time.
    pub(crate) fn questions_needed(&self) -> usize {
        self.rounds * self.per_round + self.deck
    }
}
