//! Wire types of Zündstoff's TV scene.

use serde::{Deserialize, Serialize};
use ts_rs::TS;

use crate::protocol::{PlayerId, ServerTime};

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct ZuendstoffScene {
    /// 1-based.
    pub round: u8,
    /// Every player of the game, in seat order.
    pub seats: Vec<ZuendstoffSeat>,
    pub stage: ZuendstoffStage,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct ZuendstoffSeat {
    pub player: PlayerId,
    /// Lives left; 0 = out.
    pub lives: u8,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(tag = "stage", rename_all = "snake_case")]
#[ts(export)]
pub enum ZuendstoffStage {
    /// `holder` has had the bomb since `since` and may throw it from `ready_at`.
    /// `caught`: whether it landed in their hands at `since`, None at a round's
    /// start; a dropped one lies at their feet until `ready_at`.
    Hold { holder: PlayerId, since: ServerTime, ready_at: ServerTime, caught: Option<bool> },
    /// `from` threw at `thrown_at` (the throw clip starts); the bomb leaves the
    /// hand at `released_at` and lands by `to` at `lands_at`.
    Flight {
        from: PlayerId,
        to: PlayerId,
        thrown_at: ServerTime,
        released_at: ServerTime,
        lands_at: ServerTime,
        /// Whether `to` caught it; None until they tap. Without a tap the flight
        /// ends as a drop once the catch window closes, after `lands_at`.
        caught: Option<bool>,
    },
    /// The bomb went off on `victim` at `at`; they are `out` if that was their last life.
    Boom { victim: PlayerId, out: bool, at: ServerTime },
    /// Players in finishing order, winner first.
    Final { order: Vec<PlayerId> },
}
