//! Wire types of Grand Allee's TV scene. The board travels as data (field
//! kinds, hotels with their frontage and prices) so any renderer draws from
//! the one source of truth, `grand_allee::board`.

use serde::{Deserialize, Serialize};
use ts_rs::TS;

pub use crate::grand_allee::board::Field as GrandAlleeFieldKind;
pub use crate::grand_allee::rules::Permit as GrandAlleePermit;
use crate::protocol::{AnswerColor, PlayerId, ServerTime};

/// One building stage: its cost and the nightly rate once it stands.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct GrandAlleeStage {
    pub cost: i32,
    pub rate: i32,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct GrandAlleeHotel {
    pub id: u8,
    pub name: String,
    /// First and last field the hotel borders, inclusive.
    pub first: u8,
    pub last: u8,
    pub land: i32,
    pub entrance: i32,
    /// The last stage is the leisure wing, built without a permit.
    pub stages: Vec<GrandAlleeStage>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct GrandAlleeBoard {
    /// Field kinds by id, clockwise from the Bank.
    pub fields: Vec<GrandAlleeFieldKind>,
    pub hotels: Vec<GrandAlleeHotel>,
}

/// A hotel somebody owns, with the stages built so far.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct GrandAlleeEstate {
    pub hotel: u8,
    pub owner: PlayerId,
    pub levels: u8,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct GrandAlleeEntrance {
    pub field: u8,
    pub hotel: u8,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct GrandAlleeSeat {
    pub player: PlayerId,
    /// Where the pawn stands (already at the end of a walk in progress).
    pub field: u8,
    pub cash: i32,
    /// Cash + land + built stages.
    pub worth: i32,
    pub out: bool,
}

/// A candidate field the TV marks in the colour of the phone button.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct GrandAlleeMark {
    pub field: u8,
    pub color: AnswerColor,
}

/// A debt auction on every other phone at once: sealed, so the TV learns who
/// has answered, never how much.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct GrandAlleeAuction {
    pub seller: PlayerId,
    pub hotel: u8,
    /// Land plus built stages.
    pub value: i32,
    pub opened_at: ServerTime,
    pub closes_at: ServerTime,
    /// Everyone who may bid, nearest after the seller first.
    pub bidders: Vec<PlayerId>,
    pub bid: Vec<PlayerId>,
    pub passed: Vec<PlayerId>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(tag = "stage", rename_all = "snake_case")]
#[ts(export)]
pub enum GrandAlleeTurn {
    /// The player decides on the phone; at `closes_at` the house decides.
    Ask {
        player: PlayerId,
        prompt: String,
        hotel: Option<u8>,
        marks: Vec<GrandAlleeMark>,
        opened_at: ServerTime,
        closes_at: ServerTime,
    },
    /// The die is in the air from `rolled_at` until `lands_at`.
    Rolled { player: PlayerId, value: u8, rolled_at: ServerTime, lands_at: ServerTime },
    /// `seats` already show the walk done; the pawn hops from `from` to `to`
    /// one field per `hop_ms` from `started_at`. `bank`: passing it paid.
    Walking { player: PlayerId, value: u8, from: u8, to: u8, started_at: ServerTime, hop_ms: f64, bank: bool },
    /// Nights die in the air until `lands_at`; the guest pays `owner` at `paid_at`.
    Guest {
        player: PlayerId,
        owner: PlayerId,
        hotel: u8,
        nights: u8,
        amount: i32,
        rolled_at: ServerTime,
        lands_at: ServerTime,
        paid_at: ServerTime,
    },
    /// Permit die in the air until `lands_at`.
    Permit { player: PlayerId, hotel: u8, count: u8, permit: GrandAlleePermit, rolled_at: ServerTime, lands_at: ServerTime },
    Auction { auction: GrandAlleeAuction },
    /// The auction's outcome; without a winner the bank paid `price`.
    Sold { seller: PlayerId, hotel: u8, winner: Option<PlayerId>, price: i32, started_at: ServerTime },
    /// What the move came to, shown briefly before the game goes on.
    Note { player: PlayerId, text: String, started_at: ServerTime },
    Final { winner: PlayerId, ranking: Vec<PlayerId> },
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct GrandAlleeScene {
    pub board: GrandAlleeBoard,
    pub estates: Vec<GrandAlleeEstate>,
    pub entrances: Vec<GrandAlleeEntrance>,
    pub seats: Vec<GrandAlleeSeat>,
    pub round: u32,
    pub rounds: u32,
    pub turn: GrandAlleeTurn,
    /// The dice as they last fell: number die, permit die, nights die.
    pub die: Option<u8>,
    pub permit: Option<GrandAlleePermit>,
    pub nights: Option<u8>,
}
