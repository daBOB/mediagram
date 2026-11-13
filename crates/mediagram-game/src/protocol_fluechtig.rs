//! Wire types of Flüchtig's TV scene. The scene never carries the fugitive's
//! field while the chase runs, except where a reveal showed it to everyone.

use serde::{Deserialize, Serialize};
use ts_rs::TS;

use crate::protocol::{PlayerId, ServerTime};

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct FluechtigScene {
    pub board: FluechtigBoard,
    pub round: u8,
    pub rounds: u8,
    pub reveal_rounds: Vec<u8>,
    /// The next round whose fugitive move ends in plain sight.
    pub next_reveal: Option<u8>,
    pub fugitive: PlayerId,
    /// Hunter figures in turn order: one per hunting player, two for a lone hunter.
    pub hunters: Vec<FluechtigHunter>,
    /// The fugitive's tickets lie open on the table.
    pub fugitive_tickets: FluechtigTickets,
    pub turn: FluechtigTurn,
    /// Where the hunter on the move may go; empty while the fugitive moves.
    pub targets: Vec<u8>,
    pub log: Vec<FluechtigLogEntry>,
    /// Every field the fugitive may be on, from what the room has seen.
    pub hideouts: Vec<u8>,
    /// What just happened, oldest first.
    pub news: Vec<String>,
    /// The fugitive's real route from the start field, once the chase is over.
    pub route: Option<Vec<u8>>,
    /// The rules card on show before round 1; None once the chase runs.
    pub intro: Option<FluechtigIntro>,
}

/// Rules card `card` of `cards` (1-based), shown from `opened_at` until
/// `closes_at`; the TV draws its content from the scene. `leader` may skip.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct FluechtigIntro {
    pub card: u8,
    pub cards: u8,
    pub opened_at: ServerTime,
    pub closes_at: ServerTime,
    pub leader: Option<String>,
}

/// The city on a plan `width` by `height`: `spots[field - 1]`, its links, and
/// the middle of its river from west to east, past both edges of the plan.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct FluechtigBoard {
    pub width: i16,
    pub height: i16,
    pub spots: Vec<FluechtigSpot>,
    pub links: Vec<FluechtigLink>,
    pub river: Vec<FluechtigSpot>,
}

#[derive(Debug, Clone, Copy, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct FluechtigSpot {
    pub x: i16,
    pub y: i16,
}

#[derive(Debug, Clone, Copy, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct FluechtigLink {
    pub a: u8,
    pub b: u8,
    /// `schatten` is the ferry: the shadow ride rides any link, and only it this one.
    pub kind: FluechtigTicket,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct FluechtigHunter {
    pub player: PlayerId,
    pub at: u8,
    pub tickets: FluechtigTickets,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(tag = "turn", rename_all = "snake_case")]
#[ts(export)]
pub enum FluechtigTurn {
    /// `double_left`: 2 or 1 while an announced double move runs, else 0.
    Fugitive { double_left: u8 },
    Hunter { index: u8 },
    Over { winner: FluechtigSide, reason: String },
}

/// One round of the fugitive's travel log: the tickets spent, and the field
/// where they were seen if it was a reveal round.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct FluechtigLogEntry {
    pub round: u8,
    pub tickets: Vec<FluechtigTicket>,
    pub seen_at: Option<u8>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, TS)]
#[serde(rename_all = "snake_case")]
#[ts(export)]
pub enum FluechtigTicket {
    Taxi,
    Bus,
    Ubahn,
    /// The fugitive's shadow ride: any link, and the log does not say which.
    Schatten,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Default, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct FluechtigTickets {
    pub taxi: u8,
    pub bus: u8,
    pub ubahn: u8,
    pub schatten: u8,
    /// Double moves; only the fugitive has them.
    pub doppel: u8,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, TS)]
#[serde(rename_all = "snake_case")]
#[ts(export)]
pub enum FluechtigSide {
    Fugitive,
    Hunters,
}
