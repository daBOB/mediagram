//! Wire types of the lobby: which game, which quiz questions, kids only.

use serde::{Deserialize, Serialize};
use ts_rs::TS;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, TS)]
#[serde(rename_all = "snake_case")]
#[ts(export)]
pub enum GameKind {
    Quiz,
    Poster,
    /// Heimlauf, the race board game.
    Race,
    /// Mau-Mau on the card table.
    MauMau,
    /// Schwimmen (31) on the card table.
    Schwimmen,
    /// Durak (throw-in) on the card table.
    Durak,
    /// Bauherr, the hotel-building board game.
    Bauherr,
    /// Grand Allee, the hotel game for two to eight.
    GrandAllee,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, TS)]
#[serde(rename_all = "snake_case")]
#[ts(export)]
pub enum QuizMode {
    Films,
    General,
    Mixed,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct LobbySettings {
    pub game: GameKind,
    pub quiz_mode: QuizMode,
    pub kids_only: bool,
    /// The kids-only toggle cannot be switched off (a kids profile started it).
    pub kids_locked: bool,
}
