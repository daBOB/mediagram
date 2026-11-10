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

/// One header line of a `PhoneScreen::Sheet`, e.g. `Taler` / `12.000`; the
/// first is drawn large.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct SheetLine {
    pub label: String,
    pub value: String,
}

/// One of the player's hotels on a `PhoneScreen::Sheet`.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct HotelCard {
    pub name: String,
    /// The hotel's own colour, `#rrggbb`.
    pub color: String,
    /// Stages built, of `stages`.
    pub built: u8,
    pub stages: u8,
    /// Per night; 0 while nothing is built.
    pub rate: i32,
    pub entrances: u8,
    pub value: i32,
}

/// A sealed bid to set on a `PhoneScreen::Sheet`, answered with `Action::Bid`.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct BidRange {
    /// The property's value, which the quick shares are of.
    pub value: i32,
    /// What the bank pays the seller if nobody bids; the phone's bid starts there.
    pub bank: i32,
    /// The highest bid: the bidder's cash in whole steps.
    pub max: i32,
    pub step: i32,
    /// What this phone sent: 0 for a pass; None while it may still bid.
    pub placed: Option<i32>,
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
