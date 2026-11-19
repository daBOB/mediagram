//! Builds the chosen game through its catalogue entry, from the lobby settings
//! and the night's material, remembering what was asked and shown tonight.

use std::collections::HashSet;
use std::sync::{Arc, Mutex, MutexGuard};

use rand::rngs::StdRng;

use crate::articles::ArticleList;
use crate::catalogue::entry;
use crate::film_questions::film_questions;
use crate::game::GameFactory;
use crate::general::adult_pack;
use crate::poster_lookup::PosterLookup;
use crate::protocol::LobbySettings;
use crate::quiz_config::Question;
use crate::titles::TitlePool;

/// What a night's games are built from, and what they have used of it.
pub(crate) struct Night {
    pub pool: Arc<TitlePool>,
    general: Arc<Vec<Question>>,
    /// The general pack plus *Matratzensport*, for nights with the 18+ switch on.
    with_adult: Arc<Vec<Question>>,
    pub textless: Option<PosterLookup>,
    pub articles: Arc<ArticleList>,
    /// Preisfrage's articles shown tonight, so a night repeats none until the list runs out.
    pub shown: HashSet<String>,
    /// What was asked tonight. Shared with a running Kopfnuss, which marks questions as it asks them,
    /// and with Dreifelder.
    pub used: Arc<Mutex<HashSet<String>>>,
}

impl Night {
    /// The general questions the settings allow.
    pub(crate) fn general(&self, settings: &LobbySettings) -> &Arc<Vec<Question>> {
        if settings.asks_adult_questions() { &self.with_adult } else { &self.general }
    }

    pub(crate) fn history(&self) -> MutexGuard<'_, HashSet<String>> {
        // A poisoned lock still holds a usable history; losing it only risks a repeat.
        self.used.lock().unwrap_or_else(|e| e.into_inner())
    }

    /// Up to `count` film questions, marked as asked tonight.
    pub(crate) fn films(&self, count: usize, rng: &mut StdRng) -> Vec<Question> {
        film_questions(&self.pool, &self.pool.eligible(false), rng, count, &mut self.history())
    }
}

/// The lobby blocks starting a game its material cannot supply, so none is
/// built short of it.
pub fn game_factory(
    pool: Arc<TitlePool>,
    general: Arc<Vec<Question>>,
    textless: Option<PosterLookup>,
    articles: Arc<ArticleList>,
) -> GameFactory {
    let with_adult = Arc::new(general.iter().cloned().chain(adult_pack()).collect());
    let used = Arc::new(Mutex::new(HashSet::new()));
    let mut night = Night { pool, general, with_adult, textless, articles, shown: HashSet::new(), used };
    Box::new(move |ctx, settings| (entry(settings.game).start)(&mut night, ctx, settings))
}
