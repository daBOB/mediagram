//! Lobby settings: what the leader picked, and whether it can be started.

use crate::catalogue::{GameEntry, entry};
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
/// Kopfnuss asks the general pack, which is not checked for children.
pub(crate) const KOPFNUSS_NOT_FOR_KIDS: &str = "Kopfnuss gibt es nicht im Kindermodus.";
/// Dreifelder asks the general pack too, which is not checked for children.
pub(crate) const DREIFELDER_NOT_FOR_KIDS: &str = "Dreifelder gibt es nicht im Kindermodus.";

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
    pub(crate) fn blocked(&self) -> Option<String> {
        let s = &self.settings;
        let r = self.cfg.readiness;
        match s.game {
            // Any room plays these, so only the room's own minimum gates them: a seat
            // message would stand on the TV before anyone has joined.
            GameKind::Quiz => return None,
            GameKind::Poster => {
                let ready = if s.kids_only { r.poster_kids } else { r.poster };
                return (!ready).then(|| NOT_ENOUGH_POSTERS.into());
            }
            GameKind::Kopfnuss if s.kids_only => return Some(KOPFNUSS_NOT_FOR_KIDS.into()),
            GameKind::Dreifelder if s.kids_only => return Some(DREIFELDER_NOT_FOR_KIDS.into()),
            _ => {}
        }
        let GameEntry { name, min, max, .. } = entry(s.game);
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
