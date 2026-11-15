//! Zündstoff's lives, fuse and timing, the per-game state types and its
//! catalogue entry.

use rand::rngs::StdRng;
use rand::seq::IndexedRandom;

use crate::ask::Ask;
use crate::catalogue::{CatalogueEntry, Kids, Need, Seats};
use crate::factory::Night;
use crate::game::{Game, GameCtx};
use crate::host::ROOM_CAP;
use crate::kopfnuss_supply::QuestionSupply;
use crate::protocol::{GameLength, GameSpeed, LobbySettings, PlayerId, ServerTime};
use crate::quiz_config::Question;
use crate::zuendstoff::ZuendstoffGame;

/// Unasked general questions a Zündstoff game wants tonight before the pack starts over.
const QUESTIONS: usize = 60;

/// Draws the next question; never runs dry (`start` falls back on the whole pack).
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

pub(crate) const ENTRY: CatalogueEntry = CatalogueEntry {
    // A bomb needs someone to throw it to.
    name: "Zündstoff", seats: Seats::Range(2, ROOM_CAP), timed: true, sized: true,
    // The bomb asks the general pack, which is not checked for children.
    kids: Kids::Barred, board: false, needs: Need::Nothing, start,
};

fn start(night: &mut Night, ctx: &mut GameCtx, settings: &LobbySettings) -> Box<dyn Game> {
    let pack = night.general(settings).clone();
    // Marked asked one by one as the bomb comes round; the whole pack is the fallback.
    let mut supply = QuestionSupply::new(pack.clone(), Vec::new(), night.used.clone(), QUESTIONS);
    let deck = Box::new(move |rng: &mut StdRng| {
        supply.deck(1, rng).pop().unwrap_or_else(|| pack.choose(rng).cloned().expect("the bundled pack is never empty"))
    });
    Box::new(ZuendstoffGame::new(ZuendstoffConfig::default().at(settings.speed).sized(settings.length), deck, ctx.players, ctx.now))
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub(crate) enum Stage {
    /// The player `ask` asks has held the bomb since it opened; a wrong answer keeps it open.
    Lit { ask: Ask, step: Step },
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
