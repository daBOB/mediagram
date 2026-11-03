//! Builds a quiz from the lobby settings, remembering what was asked tonight.

use std::collections::HashSet;
use std::sync::Arc;

use rand::SeedableRng;
use rand::rngs::StdRng;

use crate::game::{Game, GameFactory};
use crate::poster::PosterGame;
use crate::poster_config::PosterConfig;
use crate::protocol::GameKind;
use crate::quiz::Quiz;
use crate::quiz_config::{Question, QuizConfig};
use crate::quiz_mix::quiz_questions;
use crate::titles::TitlePool;

/// The lobby blocks starting a game its pool cannot supply, so neither arm
/// is built without enough titles.
pub fn game_factory(pool: Arc<TitlePool>, general: Arc<Vec<Question>>) -> GameFactory {
    let mut used = HashSet::new();
    Box::new(move |ctx, settings| {
        if settings.game == GameKind::Poster {
            let eligible = pool.eligible(settings.kids_only);
            let cfg = PosterConfig::default();
            return Box::new(PosterGame::new(cfg, &pool, &eligible, &mut used, ctx.now)) as Box<dyn Game>;
        }
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
