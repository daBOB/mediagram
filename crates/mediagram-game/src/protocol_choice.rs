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

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, TS)]
#[serde(rename_all = "snake_case")]
#[ts(export)]
pub enum Tone {
    Good,
    Bad,
    Neutral,
}
