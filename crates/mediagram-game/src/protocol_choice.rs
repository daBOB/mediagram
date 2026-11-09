//! Wire types of the generic phone screens: answer buttons and result tone.

use serde::{Deserialize, Serialize};
use ts_rs::TS;

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct ChoiceOption {
    pub label: String,
    pub color: AnswerColor,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, TS)]
#[serde(rename_all = "snake_case")]
#[ts(export)]
pub enum AnswerColor {
    Blue,
    Orange,
    Yellow,
    Pink,
}

impl AnswerColor {
    pub const ALL: [AnswerColor; 4] = [Self::Blue, Self::Orange, Self::Yellow, Self::Pink];
}

/// A line under a choice's clock, e.g. whether the player is still in.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct ChoiceStatus {
    pub text: String,
    /// The player's answers no longer count: the buttons fade.
    pub out: bool,
}

/// One answer on a result screen, drawn as a small copy of its numbered button.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct OutcomeKey {
    /// What the key stands for, e.g. `Richtig` or `Dein Tipp`.
    pub caption: String,
    /// The button's number, counted from 1.
    pub number: u8,
    pub label: String,
    pub color: AnswerColor,
    /// Struck through: the player's own wrong pick.
    pub crossed: bool,
}

/// One header line of a `PhoneScreen::Sheet`, e.g. `Taler` / `12.000`.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct SheetLine {
    pub label: String,
    pub value: String,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, TS)]
#[serde(rename_all = "snake_case")]
#[ts(export)]
pub enum Tone {
    Good,
    Bad,
    Neutral,
    /// A result that no longer counts, e.g. for a player who is out.
    Muted,
}
