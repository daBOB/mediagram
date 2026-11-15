//! Lobby settings: what the leader picked, and whether it can be started.

use crate::articles::ArticleList;
use crate::catalogue::{Kids, Need, Seats, entry};
use crate::poster_config::poster_ready;
use crate::preisfrage_draw::enough_articles;
use crate::protocol::{GameKind, GameLength, GameSpeed, LobbySettings, QuizMode};
use crate::game::GameCtx;
use crate::protocol::{ErrorCode, ServerMsg, ServerTime};
use crate::quiz_config::QuizConfig;
use crate::quiz_mix::kids_quiz_capacity;
use crate::room::{Room, error};
use crate::titles::TitlePool;

/// Whether the night's material holds what the games need.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Readiness {
    pub poster: bool,
    /// Enough kid-safe posters, for when *Nur für Kinder* is on.
    pub poster_kids: bool,
    /// Enough of Preisfrage's articles for a game.
    pub preisfrage: bool,
    /// Film questions a kids quiz can ask, counted up to the longest quiz's.
    pub kids_quiz: usize,
}

impl Default for Readiness {
    fn default() -> Self {
        Self { poster: true, poster_kids: true, preisfrage: true, kids_quiz: usize::MAX }
    }
}

impl Readiness {
    /// What `pool` and `articles` hold.
    pub(crate) fn of(pool: &TitlePool, articles: &ArticleList) -> Self {
        Self {
            poster: poster_ready(pool, &pool.eligible(false)),
            poster_kids: poster_ready(pool, &pool.eligible(true)),
            preisfrage: enough_articles(articles),
            kids_quiz: kids_quiz_capacity(pool),
        }
    }

    /// Why the night's material falls short of `need`, if it does.
    fn lacks(&self, need: Need, kids_only: bool) -> Option<&'static str> {
        let posters = if kids_only { self.poster_kids } else { self.poster };
        match need {
            Need::Nothing => None,
            Need::Posters => (!posters).then_some(NOT_ENOUGH_POSTERS),
            Need::Articles => (!self.preisfrage).then_some(PREISFRAGE_NEEDS_ARTICLES),
        }
    }
}

pub(crate) const NOT_ENOUGH_POSTERS: &str = "Nicht genug Poster für dieses Spiel.";
/// The quiz's general questions are the general pack, which is not checked for children.
pub(crate) const GENERAL_QUIZ_NOT_FOR_KIDS: &str = "Allgemeine Fragen gibt es nicht im Kindermodus.";
/// A kids quiz asks only films, so the library must supply every question.
pub(crate) const NOT_ENOUGH_KIDS_FILMS: &str = "Nicht genug Kinderfilme für ein Quiz dieser Dauer.";
pub(crate) const PREISFRAGE_NEEDS_ARTICLES: &str = "Preisfrage braucht Artikel: am PC „bun run fetch“ starten.";

pub(crate) fn initial_settings(kids_locked: bool) -> LobbySettings {
    LobbySettings {
        game: GameKind::Quiz,
        quiz_mode: QuizMode::Mixed,
        speed: GameSpeed::Normal,
        length: GameLength::Normal,
        kids_only: kids_locked,
        kids_locked,
    }
}

impl Room {
    pub fn settings(&self) -> &LobbySettings {
        &self.settings
    }

    /// Leader or TV only, and only while no game runs. `false` if refused.
    pub(crate) fn configure(
        &mut self,
        allowed: bool,
        game: GameKind,
        quiz_mode: QuizMode,
        speed: GameSpeed,
        length: GameLength,
        kids_only: bool,
    ) -> bool {
        if !allowed || self.game.is_some() {
            return false;
        }
        self.settings.game = game;
        self.settings.quiz_mode = quiz_mode;
        self.settings.speed = speed;
        self.settings.length = length;
        self.settings.kids_only = kids_only || self.settings.kids_locked;
        true
    }

    /// The TV's poster row picks only the game: a pick crossing the leader's kids-mode tap must not undo it.
    pub(crate) fn pick_game(&mut self, game: GameKind) -> bool {
        let LobbySettings { quiz_mode, speed, length, kids_only, .. } = self.settings;
        self.configure(true, game, quiz_mode, speed, length, kids_only)
    }

    /// Seats only the connected players, zeroes scores and builds the chosen game.
    #[allow(clippy::result_large_err)]
    pub(crate) fn start_game(&mut self, now: ServerTime) -> Result<(), ServerMsg> {
        if let Some(why) = self.blocked() {
            return Err(error(ErrorCode::NotAllowed, why));
        }
        if self.connected_count() < self.cfg.min_players {
            return Err(error(ErrorCode::NotAllowed, "Noch zu wenige Spieler.".into()));
        }
        self.players.retain(|p| p.connected());
        for p in &mut self.players {
            p.score = 0;
        }
        let mut ctx = GameCtx { now, players: &mut self.players };
        self.game = Some((self.factory)(&mut ctx, &self.settings));
        self.announce_board(now);
        Ok(())
    }

    /// Why the chosen game cannot start, if it cannot: kids mode first, then the
    /// night's material, the drawing board and the seats.
    pub(crate) fn blocked(&self) -> Option<String> {
        let s = &self.settings;
        let r = self.cfg.readiness;
        let game = entry(s.game);
        let name = game.name;
        if s.kids_only {
            match game.kids {
                Kids::Plays => {}
                Kids::Barred => return Some(format!("{name} gibt es nicht im Kindermodus.")),
                Kids::FilmsOnly if s.quiz_mode == QuizMode::General => return Some(GENERAL_QUIZ_NOT_FOR_KIDS.into()),
                Kids::FilmsOnly if r.kids_quiz < QuizConfig::default().sized(s.length).rounds => {
                    return Some(NOT_ENOUGH_KIDS_FILMS.into());
                }
                Kids::FilmsOnly => {}
            }
        }
        if let Some(why) = r.lacks(game.needs, s.kids_only) {
            return Some(why.into());
        }
        if game.board && !self.board_connected() {
            return Some(format!("{name} braucht das iPad als Zeichenbrett."));
        }
        let Seats::Range(min, max) = game.seats else { return None };
        let players = self.connected_count();
        if players < min {
            Some(format!("{name} braucht mindestens {min} Spieler."))
        } else if players > max {
            Some(format!("{name} geht mit höchstens {max} Spielern."))
        } else {
            None
        }
    }
}

#[cfg(test)]
#[path = "room_settings_tests.rs"]
mod tests;
