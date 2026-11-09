//! Dreifelder tuning, and the questions one game asks.

use rand::rngs::StdRng;
use rand::seq::SliceRandom;

use crate::kopfnuss_supply::{FILMS, QuestionSupply};
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

/// Up to `films` film questions, general ones for the rest, in random order;
/// all of them now marked as asked tonight.
pub(crate) fn dreifelder_questions(supply: &mut QuestionSupply, cfg: &DreifelderConfig, rng: &mut StdRng) -> Vec<Question> {
    let mut questions = supply.take(FILMS, cfg.films, rng);
    questions.extend(supply.deck(cfg.quiz.rounds.saturating_sub(questions.len()), rng));
    questions.shuffle(rng);
    questions
}

#[cfg(test)]
#[path = "dreifelder_config_tests.rs"]
mod tests;
