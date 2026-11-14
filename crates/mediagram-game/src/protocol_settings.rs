//! Wire types of the lobby: which game, which quiz questions, how fast, kids only.

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
    /// Kopfnuss, the quiz where players play tricks on each other's phones.
    Kopfnuss,
    /// Grand Allee, the hotel game for two to eight.
    GrandAllee,
    /// Dreifelder: pick one of three fields, a wrong pick is out, last one standing wins.
    Dreifelder,
    /// Flüchtig, the hidden-move chase for two to six.
    Fluechtig,
    /// Preisfrage: guess what an odd article costs; the closer, the more points.
    Preisfrage,
    /// Kritzelei: one draws on the iPad, the room guesses out loud.
    Kritzelei,
    /// Grenzfall: name the country from its outline on the TV.
    Grenzfall,
    /// Zündstoff: answer to throw the bomb on before its hidden fuse runs out.
    Zuendstoff,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, TS)]
#[serde(rename_all = "snake_case")]
#[ts(export)]
pub enum QuizMode {
    Films,
    General,
    Mixed,
}

/// How long the clocks give: Entspannt doubles every game's time to think and
/// act, Speedrun halves it. Animations and the fair-tap margins stay as they are.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default, Serialize, Deserialize, TS)]
#[serde(rename_all = "snake_case")]
#[ts(export)]
pub enum GameSpeed {
    Relaxed,
    #[default]
    Normal,
    Speedrun,
}

impl GameSpeed {
    /// `ms`, a game's clock at normal tempo, at this tempo.
    pub fn stretch(self, ms: f64) -> f64 {
        match self {
            GameSpeed::Relaxed => ms * 2.0,
            GameSpeed::Normal => ms,
            GameSpeed::Speedrun => ms / 2.0,
        }
    }
}

/// How much of a game is played: Kurz halves its rounds (or articles, lives …),
/// Lang adds half again. Games played to the end, like the card games, have no length.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Default, Serialize, Deserialize, TS)]
#[serde(rename_all = "snake_case")]
#[ts(export)]
pub enum GameLength {
    Short,
    #[default]
    Normal,
    Long,
}

impl GameLength {
    /// `n`, a game's count at normal length, at this length; rounded up, so never 0 from 1.
    pub fn scale(self, n: usize) -> usize {
        match self {
            GameLength::Short => n.div_ceil(2),
            GameLength::Normal => n,
            GameLength::Long => (n * 3).div_ceil(2),
        }
    }
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct LobbySettings {
    pub game: GameKind,
    pub quiz_mode: QuizMode,
    pub speed: GameSpeed,
    pub length: GameLength,
    pub kids_only: bool,
    /// The kids-only toggle cannot be switched off (a kids profile started it).
    pub kids_locked: bool,
}

impl LobbySettings {
    /// Whether tonight's quizzes may ask the adult questions about sexuality (*Matratzensport*):
    /// kids mode is the one content switch, so every evening without it is a grown-ups' one.
    pub fn asks_adult_questions(&self) -> bool {
        !self.kids_only
    }
}

#[cfg(test)]
#[path = "game_speed_tests.rs"]
mod tests;

#[cfg(test)]
#[path = "game_length_tests.rs"]
mod length_tests;
