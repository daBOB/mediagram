//! Wire types of a Flüchtig phone. The fugitive's field travels to their own
//! phone only; the other phones get what the room already knows.

use serde::{Deserialize, Serialize};
use ts_rs::TS;

use crate::protocol::{FluechtigIntro, FluechtigLogEntry, FluechtigTicket, FluechtigTickets, ServerTime};

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct FluechtigPhone {
    pub round: u8,
    pub rounds: u8,
    pub next_reveal: Option<u8>,
    /// Rounds whose fugitive move ends in plain sight.
    pub reveal_rounds: Vec<u8>,
    /// The fugitive's name.
    pub fugitive: String,
    /// Where the fugitive is; set on their own phone only.
    pub hiding: Option<u8>,
    /// The last field the room saw the fugitive on.
    pub seen: Option<FluechtigSeen>,
    /// Every field the fugitive may be on.
    pub hideouts: Vec<u8>,
    /// The hunter figures this phone moves; empty on the fugitive's.
    pub pieces: Vec<FluechtigPiece>,
    pub fugitive_tickets: FluechtigTickets,
    pub log: Vec<FluechtigLogEntry>,
    /// Who moves now, and who after; None once the chase is over.
    pub mover: Option<FluechtigMover>,
    pub next: Option<FluechtigMover>,
    /// The question this phone answers; None while someone else moves and
    /// while the rules cards run.
    pub ask: Option<FluechtigAsk>,
    /// The rules card on the TV; the phone shows its role card meanwhile.
    pub intro: Option<FluechtigIntro>,
    /// This phone may skip the rules cards: the room's leader's, while they run.
    pub may_skip: bool,
    /// The other hunting players' names in turn order; empty on the fugitive's.
    pub partners: Vec<String>,
}

#[derive(Debug, Clone, Copy, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct FluechtigSeen {
    pub field: u8,
    pub round: u8,
}

/// A hunter figure; `number` is 1 or 2 within its player's figures (only a
/// lone hunter has two).
#[derive(Debug, Clone, Copy, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct FluechtigPiece {
    pub number: u8,
    pub at: u8,
    pub tickets: FluechtigTickets,
}

/// The fugitive or the player moving a hunter figure.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct FluechtigMover {
    pub name: String,
    pub color: String,
    /// Which of the player's figures moves; only for a player with two.
    pub piece: Option<u8>,
    /// It is the fugitive.
    pub runs: bool,
    /// It is this phone's player.
    pub you: bool,
}

/// The mover's question, from `opens_at`: first where to, then (when more
/// than one ticket gets there) with what. `Action::Choose` indexes `options`.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct FluechtigAsk {
    pub opens_at: ServerTime,
    pub prompt: String,
    /// The moving piece: its field and tickets.
    pub at: u8,
    pub tickets: FluechtigTickets,
    /// The fugitive is seen where this ride ends.
    pub seen_after: bool,
    pub options: Vec<FluechtigOption>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(tag = "kind", rename_all = "snake_case")]
#[ts(export)]
pub enum FluechtigOption {
    /// A field to go to, drawn like the plan's: its strongest line as `ring`,
    /// and the mover's tickets that reach it.
    Field { field: u8, ring: FluechtigTicket, ways: Vec<FluechtigTicket> },
    Ticket { ticket: FluechtigTicket, left: u8 },
    Double { left: u8 },
    Back,
}
