//! Wire types of the race game's TV scene.

use serde::{Deserialize, Serialize};
use ts_rs::TS;

use crate::protocol::{PlayerId, ServerTime};

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct RaceScene {
    pub seats: Vec<RaceSeat>,
    pub stage: RaceStage,
    /// The last roll, so the die keeps showing it between turns.
    pub die: Option<u8>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct RaceSeat {
    pub player: PlayerId,
    /// Board corner 0..=3; the start field is track field `corner * 10`.
    pub corner: u8,
    /// Per piece: `null` at home, else fields walked from the own start
    /// (0..=39 on the track, 40..=43 in the goal).
    pub pieces: Vec<Option<u8>>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(tag = "stage", rename_all = "snake_case")]
#[ts(export)]
pub enum RaceStage {
    Roll {
        player: PlayerId,
        /// Rolled a 6 and goes again.
        again: bool,
    },
    /// The die is in the air from `rolled_at` until `lands_at`. With two or
    /// more `moves` the player picks one on the phone; the TV marks them in
    /// the answer colours, in order.
    Rolled {
        player: PlayerId,
        value: u8,
        rolled_at: ServerTime,
        lands_at: ServerTime,
        moves: Vec<RaceMove>,
    },
    /// `seats` already show the move done; the piece hops from `from` to `to`
    /// one field per `hop_ms`, starting at `started_at`.
    Moving {
        player: PlayerId,
        value: u8,
        mv: RaceMove,
        started_at: ServerTime,
        hop_ms: f64,
    },
    Final {
        winner: PlayerId,
        /// First to last place.
        ranking: Vec<PlayerId>,
    },
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct RaceMove {
    pub piece: u8,
    pub from: Option<u8>,
    pub to: u8,
    pub captures: Option<RaceCapture>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct RaceCapture {
    pub player: PlayerId,
    pub piece: u8,
}
