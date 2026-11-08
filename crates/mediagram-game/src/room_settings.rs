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
/// 32 cards deal five to five players, the 52-card deck to eight.
const CARDS_MIN: u8 = 2;
const CARDS_MAX: u8 = 8;
pub(crate) const CARDS_TOO_FEW: &str = "Mau-Mau braucht mindestens 2 Spieler.";
pub(crate) const CARDS_TOO_MANY: &str = "Mau-Mau geht mit höchstens 8 Spielern.";
/// Schwimmen deals 3 cards to each and 3 to the middle from 32.
pub(crate) const SCHWIMMEN_TOO_FEW: &str = "Schwimmen braucht mindestens 2 Spieler.";
pub(crate) const SCHWIMMEN_TOO_MANY: &str = "Schwimmen geht mit höchstens 8 Spielern.";

/// Durak deals 36 cards, six each.
const DURAK_MIN: u8 = 2;
const DURAK_MAX: u8 = 6;
pub(crate) const DURAK_TOO_FEW: &str = "Durak braucht mindestens 2 Spieler.";
pub(crate) const DURAK_TOO_MANY: &str = "Durak geht mit höchstens 6 Spielern.";

pub(crate) const BAUHERR_TOO_FEW: &str = "Bauherr braucht mindestens 2 Spieler.";
pub(crate) const BAUHERR_TOO_MANY: &str = "Bauherr geht mit höchstens 4 Spielern.";

/// Grand Allee's round limit scales with the table, from two to eight.
const GRAND_ALLEE_MIN: u8 = 2;
const GRAND_ALLEE_MAX: u8 = 8;
pub(crate) const GRAND_ALLEE_TOO_FEW: &str = "Grand Allee braucht mindestens 2 Spieler.";
pub(crate) const GRAND_ALLEE_TOO_MANY: &str = "Grand Allee geht mit höchstens 8 Spielern.";

pub(crate) fn initial_settings(kids_locked: bool) -> LobbySettings {
    LobbySettings { game: GameKind::Quiz, quiz_mode: QuizMode::Mixed, kids_only: kids_locked, kids_locked }
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
        kids_only: bool,
    ) -> bool {
        if !allowed || self.game.is_some() {
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
        let (min, max, few, many) = match s.game {
            GameKind::Quiz => return None,
            GameKind::Poster => {
                let ready = if s.kids_only { r.poster_kids } else { r.poster };
                return (!ready).then_some(NOT_ENOUGH_POSTERS);
            }
            GameKind::Race => (BOARD_MIN, BOARD_MAX, RACE_TOO_FEW, RACE_TOO_MANY),
            GameKind::Bauherr => (BOARD_MIN, BOARD_MAX, BAUHERR_TOO_FEW, BAUHERR_TOO_MANY),
            GameKind::GrandAllee => (GRAND_ALLEE_MIN, GRAND_ALLEE_MAX, GRAND_ALLEE_TOO_FEW, GRAND_ALLEE_TOO_MANY),
            GameKind::MauMau => (CARDS_MIN, CARDS_MAX, CARDS_TOO_FEW, CARDS_TOO_MANY),
            GameKind::Schwimmen => (CARDS_MIN, CARDS_MAX, SCHWIMMEN_TOO_FEW, SCHWIMMEN_TOO_MANY),
            GameKind::Durak => (DURAK_MIN, DURAK_MAX, DURAK_TOO_FEW, DURAK_TOO_MANY),
        };
        let players = self.connected_count();
        (players < min).then_some(few).or((players > max).then_some(many))
    }
}

#[cfg(test)]
#[path = "room_settings_tests.rs"]
mod tests;
