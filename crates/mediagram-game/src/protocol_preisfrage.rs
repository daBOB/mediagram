//! Wire types of Preisfrage: the TV scene and the phone's price keypad.
//! Prices are in cents. No guess and no price reaches anyone before the reveal.

use serde::{Deserialize, Serialize};
use ts_rs::TS;

use crate::protocol::{PlayerId, ServerTime};

/// An article as the TV and the phones show it.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct ArticleView {
    pub asin: String,
    /// The short name.
    pub name: String,
    /// Where its photo is served, `/img/article/{asin}`.
    pub photo: String,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct PreisfrageScene {
    /// 1-based.
    pub round: u8,
    pub rounds: u8,
    pub article: ArticleView,
    pub stage: PreisfrageStage,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(tag = "stage", rename_all = "snake_case")]
#[ts(export)]
pub enum PreisfrageStage {
    /// The article shows at once; the keypads open at `opens_at`.
    Guessing {
        opens_at: ServerTime,
        closes_at: ServerTime,
        /// Who has sent a guess, never what.
        guessed: Vec<PlayerId>,
    },
    Reveal {
        cents: u32,
        /// When the price was read, as `12.11.`.
        checked: String,
        /// Everyone who played this article, closest first, no guess last.
        rows: Vec<PriceRow>,
        ends_at: ServerTime,
    },
    /// Highest total first.
    Final { standings: Vec<PriceStanding> },
}

/// One player's guess at the reveal.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct PriceRow {
    pub player: PlayerId,
    /// `None` = no guess.
    pub cents: Option<u32>,
    pub points: u32,
    /// The smallest distance either side; equal distances share it.
    pub closest: bool,
    pub exact: bool,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct PriceStanding {
    pub player: PlayerId,
    pub points: u32,
    /// 1-based; equal totals share a place.
    pub place: u8,
}

/// The phone while an article is open: its keypad goes live at `opens_at`.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct PriceKeypad {
    pub article: ArticleView,
    pub round: u8,
    pub rounds: u8,
    pub opens_at: ServerTime,
    pub closes_at: ServerTime,
    /// The guess this player sent, which locks the keypad.
    pub sent: Option<u32>,
    /// The highest price the keypad takes.
    pub max_cents: u32,
}
