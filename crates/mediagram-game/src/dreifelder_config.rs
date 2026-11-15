//! Dreifelder tuning, the questions one game asks and its catalogue entry.

use rand::SeedableRng;
use rand::rngs::StdRng;
use rand::seq::SliceRandom;

use crate::catalogue::{CatalogueEntry, Kids, Need, Seats};
use crate::dreifelder::DreifelderGame;
use crate::factory::Night;
use crate::game::{Game, GameCtx};
use crate::host::ROOM_CAP;
use crate::kopfnuss_supply::{FILMS, QuestionSupply};
use crate::protocol::{GameLength, GameSpeed, LobbySettings};
use crate::quiz_config::{Question, QuizConfig};

#[derive(Debug, Clone)]
pub struct DreifelderConfig {
    /// Timing, fair taps and the speed points that break ties at the cap;
    /// `rounds` is the cap on questions.
    pub quiz: QuizConfig,
    /// Fields per question.
    pub fields: usize,
    /// Film questions mixed in when the library has them.
    pub films: usize,
}

impl Default for DreifelderConfig {
    fn default() -> Self {
        Self {
            quiz: QuizConfig { rounds: 12, intro_ms: 3000.0, reveal_ms: 4000.0, ..QuizConfig::default() },
            fields: 3,
            films: 3,
        }
    }
}

impl DreifelderConfig {
    /// At the lobby's length: the cap on questions, and the film questions among them.
    pub fn sized(mut self, length: GameLength) -> Self {
        self.quiz = self.quiz.sized(length);
        self.films = length.scale(self.films);
        self
    }

    /// At the lobby's tempo: the time to pick a field.
    pub fn at(mut self, speed: GameSpeed) -> Self {
        self.quiz = self.quiz.at(speed);
        self
    }
}

/// Up to `films` film questions, general ones for the rest, in random order;
/// all of them now marked as asked tonight.
pub(crate) fn dreifelder_questions(supply: &mut QuestionSupply, cfg: &DreifelderConfig, rng: &mut StdRng) -> Vec<Question> {
    let mut questions = supply.take(FILMS, cfg.films, rng);
    questions.extend(supply.deck(cfg.quiz.rounds.saturating_sub(questions.len()), rng));
    questions.shuffle(rng);
    questions
}

pub(crate) const ENTRY: CatalogueEntry = CatalogueEntry {
    // Dreifelder needs someone to outlast.
    name: "Dreifelder", seats: Seats::Range(2, ROOM_CAP), timed: true, sized: true,
    // It asks the general pack too, which is not checked for children.
    kids: Kids::Barred, board: false, needs: Need::Nothing, start,
};

fn start(night: &mut Night, ctx: &mut GameCtx, settings: &LobbySettings) -> Box<dyn Game> {
    let cfg = DreifelderConfig::default().at(settings.speed).sized(settings.length);
    let mut rng = StdRng::from_os_rng();
    let films = night.films(cfg.films, &mut rng);
    let needed = cfg.quiz.rounds.saturating_sub(films.len());
    let mut supply = QuestionSupply::new(night.general(settings).clone(), films, night.used.clone(), needed);
    let questions = dreifelder_questions(&mut supply, &cfg, &mut rng);
    Box::new(DreifelderGame::new(cfg, questions, ctx.players, ctx.now))
}

#[cfg(test)]
#[path = "dreifelder_config_tests.rs"]
mod tests;
