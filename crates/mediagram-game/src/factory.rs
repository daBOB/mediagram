//! Builds a quiz from the lobby settings, remembering what was asked tonight.

use std::collections::HashSet;
use std::sync::Arc;

use rand::SeedableRng;
use rand::rngs::StdRng;

use crate::game::{Game, GameFactory};
use crate::quiz::Quiz;
use crate::quiz_config::{Question, QuizConfig};
use crate::quiz_mix::quiz_questions;
use crate::titles::TitlePool;

/// Every game kind builds the quiz for now: the lobby blocks starting any
/// other, so no other arm can be reached.
pub fn game_factory(pool: Arc<TitlePool>, general: Arc<Vec<Question>>) -> GameFactory {
    let mut used = HashSet::new();
    Box::new(move |ctx, settings| {
        let cfg = QuizConfig::default();
        let mut rng = StdRng::from_os_rng();
        let mut questions =
            quiz_questions(settings.quiz_mode, &pool, settings.kids_only, &general, &mut rng, cfg.rounds, &mut used);
        if questions.is_empty() {
            // `Quiz::new` panics on an empty list; the pack is never empty.
            questions = general.to_vec();
        }
        Box::new(Quiz::new(cfg, questions, ctx.now)) as Box<dyn Game>
    })
}
