//! Kopfnuss tuning: every count and timing of a game, in one place, and its
//! catalogue entry.

use rand::SeedableRng;
use rand::rngs::StdRng;

use crate::catalogue::{CatalogueEntry, Kids, Need, Seats};
use crate::factory::Night;
use crate::game::{Game, GameCtx};
use crate::host::ROOM_CAP;
use crate::kopfnuss::KopfnussGame;
use crate::kopfnuss_supply::QuestionSupply;
use crate::protocol::{GameLength, GameSpeed, LobbySettings, ServerTime};
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
    /// At the lobby's length: the rounds before the climb; each still asks `per_round` questions.
    pub fn sized(mut self, length: GameLength) -> Self {
        self.rounds = length.scale(self.rounds);
        self
    }

    /// At the lobby's tempo: the pick, every question and the summit climb.
    pub fn at(mut self, speed: GameSpeed) -> Self {
        self.pick_ms = speed.stretch(self.pick_ms);
        self.climb_ms = speed.stretch(self.climb_ms);
        self.quiz = self.quiz.at(speed);
        self
    }

    /// General questions one game can ask at most, so a spent night starts over in time.
    pub(crate) fn questions_needed(&self) -> usize {
        self.rounds * self.per_round + self.deck
    }
}

pub(crate) const ENTRY: CatalogueEntry = CatalogueEntry {
    // Kopfnuss needs someone to send a trick to; any room size plays.
    name: "Kopfnuss", seats: Seats::Range(2, ROOM_CAP), timed: true, sized: true,
    // It asks the general pack, which is not checked for children.
    kids: Kids::Barred, board: false, needs: Need::Nothing, start,
};

fn start(night: &mut Night, ctx: &mut GameCtx, settings: &LobbySettings) -> Box<dyn Game> {
    let cfg = KopfnussConfig::default().at(settings.speed).sized(settings.length);
    // Up to three film rounds; drawn now, so they are marked as asked tonight.
    let films = night.films(3 * cfg.per_round, &mut StdRng::from_os_rng());
    let supply = QuestionSupply::new(night.general(settings).clone(), films, night.used.clone(), cfg.questions_needed());
    Box::new(KopfnussGame::new(cfg, supply, ctx.players, ctx.now))
}
