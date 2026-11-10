//! Builds the chosen game, a quiz, a poster game or a board game, from
//! the lobby settings, remembering what was asked and shown tonight.

use std::collections::HashSet;
use std::sync::{Arc, Mutex};

use rand::SeedableRng;
use rand::rngs::StdRng;

use crate::game::{Game, GameFactory};
use crate::kopfnuss::KopfnussGame;
use crate::kopfnuss_config::KopfnussConfig;
use crate::kopfnuss_supply::QuestionSupply;
use crate::film_questions::film_questions;
use crate::fluechtig::{FluechtigConfig, FluechtigGame};
use crate::grand_allee::live::{GrandAlleeConfig, GrandAlleeGame};
use crate::durak::{DurakConfig, DurakGame};
use crate::maumau::{MauMauConfig, MauMauGame};
use crate::poster::PosterGame;
use crate::poster_config::PosterConfig;
use crate::poster_lookup::PosterLookup;
use crate::protocol::GameKind;
use crate::quiz::Quiz;
use crate::quiz_config::{Question, QuizConfig};
use crate::quiz_mix::quiz_questions;
use crate::race::{RaceConfig, RaceGame};
use crate::schwimmen::{SchwimmenConfig, SchwimmenGame};
use crate::titles::TitlePool;

/// The lobby blocks starting a game its pool cannot supply, so neither arm
/// is built without enough titles.
pub fn game_factory(pool: Arc<TitlePool>, general: Arc<Vec<Question>>, textless: Option<PosterLookup>) -> GameFactory {
    // Shared with a running Kopfnuss, which marks questions as it asks them.
    let used = Arc::new(Mutex::new(HashSet::new()));
    Box::new(move |ctx, settings| {
        // A poisoned lock still holds a usable history; losing it only risks a repeat.
        let mut history = used.lock().unwrap_or_else(|e| e.into_inner());
        match settings.game {
            GameKind::Kopfnuss => {
                let cfg = KopfnussConfig::default();
                // Up to three film rounds; drawn now, so they are marked as asked tonight.
                let mut rng = StdRng::from_os_rng();
                let films = film_questions(&pool, &pool.eligible(false), &mut rng, 3 * cfg.per_round, &mut history);
                drop(history);
                let supply = QuestionSupply::new(general.clone(), films, used.clone(), cfg.questions_needed());
                Box::new(KopfnussGame::new(cfg, supply, ctx.players, ctx.now)) as Box<dyn Game>
            }
            GameKind::Race => Box::new(RaceGame::new(RaceConfig::default(), ctx.players, ctx.now)),
            GameKind::MauMau => Box::new(MauMauGame::new(MauMauConfig::default(), ctx.players, ctx.now)),
            GameKind::Schwimmen => Box::new(SchwimmenGame::new(SchwimmenConfig::default(), ctx.players, ctx.now)),
            GameKind::Durak => Box::new(DurakGame::new(DurakConfig::default(), ctx.players, ctx.now)),
            GameKind::GrandAllee => Box::new(GrandAlleeGame::new(GrandAlleeConfig::default(), ctx.players, ctx.now)),
            GameKind::Fluechtig => Box::new(FluechtigGame::new(FluechtigConfig::default(), ctx.players, ctx.now)),
            GameKind::Poster => {
                let eligible = pool.eligible(settings.kids_only);
                let cfg = PosterConfig::default();
                let game = PosterGame::new(cfg, &pool, &eligible, &mut history, ctx.now);
                if let Some(lookup) = &textless {
                    // Fetched ahead, so a round's poster is cached before it is shown.
                    for round in &game.rounds {
                        tokio::spawn(lookup(pool.get(round.title).key.clone()));
                    }
                }
                Box::new(game)
            }
            GameKind::Quiz => {
                let cfg = QuizConfig::default();
                let mut rng = StdRng::from_os_rng();
                let mut questions =
                    quiz_questions(settings.quiz_mode, &pool, settings.kids_only, &general, &mut rng, cfg.rounds, &mut history);
                if questions.is_empty() {
                    // `Quiz::new` panics on an empty list; the pack is never empty.
                    questions = general.to_vec();
                }
                Box::new(Quiz::new(cfg, questions, ctx.now))
            }
        }
    })
}
