//! Lobby settings: what the leader picked, and whether it can be started.

use crate::protocol::{GameKind, LobbySettings, QuizMode};
use crate::room::Room;

/// Which poster-based games have enough material. Filled by the caller from
/// the title pool.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Readiness {
    pub poster: bool,
    /// Enough kid-safe posters, for when *Nur für Kinder* is on.
    pub poster_kids: bool,
}

impl Default for Readiness {
    fn default() -> Self {
        Self { poster: true, poster_kids: true }
    }
}

pub(crate) const NOT_ENOUGH_POSTERS: &str = "Nicht genug Poster für dieses Spiel.";
/// The board games seat two to four: the race board has four corners, and
/// Bauherr's round limit is tuned for that many.
const BOARD_MIN: u8 = 2;
const BOARD_MAX: u8 = 4;
pub(crate) const RACE_TOO_FEW: &str = "Heimlauf braucht mindestens 2 Spieler.";
pub(crate) const RACE_TOO_MANY: &str = "Heimlauf geht mit höchstens 4 Spielern.";
pub(crate) const BAUHERR_TOO_FEW: &str = "Bauherr braucht mindestens 2 Spieler.";
pub(crate) const BAUHERR_TOO_MANY: &str = "Bauherr geht mit höchstens 4 Spielern.";

pub(crate) fn initial_settings(kids_locked: bool) -> LobbySettings {
    LobbySettings { game: GameKind::Quiz, quiz_mode: QuizMode::Mixed, kids_only: kids_locked, kids_locked }
}

impl Room {
    pub fn settings(&self) -> &LobbySettings {
        &self.settings
    }

    /// Leader only, and only while no game runs. `false` if refused.
    pub(crate) fn configure(
        &mut self,
        is_leader: bool,
        game: GameKind,
        quiz_mode: QuizMode,
        kids_only: bool,
    ) -> bool {
        if !is_leader || self.game.is_some() {
            return false;
        }
        self.settings.game = game;
        self.settings.quiz_mode = quiz_mode;
        self.settings.kids_only = kids_only || self.settings.kids_locked;
        true
    }

    /// Why the chosen game cannot start, if it cannot.
    pub(crate) fn blocked(&self) -> Option<&'static str> {
        let s = &self.settings;
        let r = self.cfg.readiness;
        let ready = match s.game {
            GameKind::Quiz => true,
            GameKind::Poster if s.kids_only => r.poster_kids,
            GameKind::Poster => r.poster,
            GameKind::Race | GameKind::Bauherr => {
                let players = self.connected_count();
                let (few, many) =
                    if s.game == GameKind::Race { (RACE_TOO_FEW, RACE_TOO_MANY) } else { (BAUHERR_TOO_FEW, BAUHERR_TOO_MANY) };
                return (players < BOARD_MIN).then_some(few).or((players > BOARD_MAX).then_some(many));
            }
        };
        (!ready).then_some(NOT_ENOUGH_POSTERS)
    }
}

#[cfg(test)]
#[path = "room_settings_tests.rs"]
mod tests;
