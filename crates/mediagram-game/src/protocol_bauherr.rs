//! Wire types of Bauherr's TV scene. The board travels as data (tiles with
//! exits, plots with their bordering fields) so any renderer draws from the
//! one source of truth.

use serde::{Deserialize, Serialize};
use ts_rs::TS;

use crate::protocol::{AnswerColor, PlayerId, ServerTime};

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, TS)]
#[serde(rename_all = "snake_case")]
#[ts(export)]
pub enum BauherrTileKind {
    /// Start; passing or landing pays.
    Bank,
    Rathaus,
    /// Buy the bordering plot.
    Bauplatz,
    /// Build on the bordering own hotel.
    Baustelle,
    /// Place one entrance for free.
    Eingang,
    Strasse,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct BauherrTile {
    pub id: u8,
    pub kind: BauherrTileKind,
    /// Grid position in tile units.
    pub x: u8,
    pub y: u8,
    /// Fields reachable in one step; a pawn follows the first.
    pub exits: Vec<u8>,
}

/// One building stage of a hotel.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct BauherrLevel {
    pub name: String,
    pub cost: i32,
    /// Nightly rate once this stage is complete.
    pub rate: i32,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct BauherrPlot {
    pub id: u8,
    /// The hotel's name.
    pub name: String,
    /// Footprint in tile units.
    pub x: u8,
    pub y: u8,
    pub w: u8,
    pub h: u8,
    /// Path fields bordering the plot, where entrances can sit.
    pub fields: Vec<u8>,
    pub land: i32,
    pub entrance: i32,
    pub levels: Vec<BauherrLevel>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct BauherrBoard {
    pub tiles: Vec<BauherrTile>,
    pub plots: Vec<BauherrPlot>,
}

/// A plot somebody owns, with the stages built so far.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct BauherrEstate {
    pub plot: u8,
    pub owner: PlayerId,
    pub levels: u8,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct BauherrEntrance {
    pub field: u8,
    pub plot: u8,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct BauherrSeat {
    pub player: PlayerId,
    /// Where the pawn stands (already at the end of a walk in progress).
    pub field: u8,
    pub cash: i32,
    /// Cash + land + built stages.
    pub worth: i32,
    pub out: bool,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, TS)]
#[serde(rename_all = "snake_case")]
#[ts(export)]
pub enum BauherrPermit {
    Approved,
    Rejected,
    /// The next stage is free.
    Free,
    /// Everything costs double.
    Double,
}

/// A candidate field the TV marks in the colour of the phone button.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct BauherrMark {
    pub field: u8,
    pub color: AnswerColor,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(tag = "stage", rename_all = "snake_case")]
#[ts(export)]
pub enum BauherrTurn {
    Roll { player: PlayerId },
    /// The die is in the air from `rolled_at` until `lands_at`.
    Rolled { player: PlayerId, value: u8, rolled_at: ServerTime, lands_at: ServerTime },
    /// `seats` already show the walk done; the pawn hops from `from` to `to`
    /// one field per `hop_ms`, starting at `started_at`.
    Walking { player: PlayerId, value: u8, from: u8, to: u8, started_at: ServerTime, hop_ms: f64, bonus: bool },
    /// Nights die in the air until `lands_at`; the guest pays `owner` at `paid_at`.
    Guest {
        player: PlayerId,
        owner: PlayerId,
        plot: u8,
        nights: u8,
        amount: i32,
        rolled_at: ServerTime,
        lands_at: ServerTime,
        paid_at: ServerTime,
    },
    /// Permit die in the air until `lands_at`.
    Permit { player: PlayerId, plot: u8, outcome: BauherrPermit, rolled_at: ServerTime, lands_at: ServerTime },
    /// The player decides on the phone; `plot` is the hotel in question.
    Ask { player: PlayerId, prompt: String, plot: Option<u8>, marks: Vec<BauherrMark> },
    /// What the turn came to, shown briefly before the next one.
    Note { player: PlayerId, text: String, started_at: ServerTime },
    Final { winner: PlayerId, ranking: Vec<PlayerId> },
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct BauherrScene {
    pub board: BauherrBoard,
    pub estates: Vec<BauherrEstate>,
    pub entrances: Vec<BauherrEntrance>,
    pub seats: Vec<BauherrSeat>,
    pub round: u8,
    pub rounds: u8,
    pub turn: BauherrTurn,
}
