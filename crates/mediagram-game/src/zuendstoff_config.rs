//! Zündstoff's lives, fuse and timing, and the per-game state types.

use rand::rngs::StdRng;

use crate::protocol::{GameLength, GameSpeed, PlayerId, ServerTime};
use crate::quiz_config::Question;

/// Draws the next question; never runs dry (the factory falls back on the whole pack).
pub type QuestionDeck = Box<dyn FnMut(&mut StdRng) -> Question + Send>;

#[derive(Debug, Clone)]
pub struct ZuendstoffConfig {
    pub lives: u8,
    /// A round's hidden fuse burns for a time between these two.
    pub fuse_min_ms: ServerTime,
    pub fuse_max_ms: ServerTime,
    /// A round's first question shows this long before its keys go live.
    pub intro_ms: ServerTime,
    /// A caught bomb's question goes live after this, so the catcher sees it coming.
    pub catch_ms: ServerTime,
    /// A wrong answer locks the keys this long; the next question shows meanwhile.
    pub wrong_wait_ms: ServerTime,
    /// How long the explosion stays on screen before the next round.
    pub boom_ms: ServerTime,
    /// Taps arriving this early are accepted, to absorb clock-sync error.
    pub early_tolerance_ms: ServerTime,
    pub seed: Option<u64>,
}

impl Default for ZuendstoffConfig {
    fn default() -> Self {
        Self {
            lives: 3,
            fuse_min_ms: 18_000.0,
            fuse_max_ms: 40_000.0,
            intro_ms: 2500.0,
            catch_ms: 1200.0,
            wrong_wait_ms: 2000.0,
            boom_ms: 4500.0,
            early_tolerance_ms: 150.0,
            seed: None,
        }
    }
}

impl ZuendstoffConfig {
    /// At the lobby's length: the lives each player starts with.
    pub fn sized(mut self, length: GameLength) -> Self {
        self.lives = length.scale(self.lives.into()) as u8;
        self
    }

    /// At the lobby's tempo: the fuse burns longer or shorter.
    pub fn at(mut self, speed: GameSpeed) -> Self {
        self.fuse_min_ms = speed.stretch(self.fuse_min_ms);
        self.fuse_max_ms = speed.stretch(self.fuse_max_ms);
        self
    }
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub(crate) enum Stage {
    /// `holder` has had the bomb since `since`.
    Lit { holder: PlayerId, since: ServerTime, step: Step },
    /// The fuse ran out in `victim`'s hands.
    Boom { victim: PlayerId, ends_at: ServerTime },
    Final,
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub(crate) enum Step {
    /// The question's keys go live at `opens_at`; `wrong` after a wrong answer.
    Answer { opens_at: ServerTime, wrong: bool },
    /// Answered right: the holder picks who catches.
    Throw,
}
