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
    /// `holder` has the bomb and may throw it from `ready_at`; while `dropped`
    /// it lies at their feet until then.
    Hold { holder: PlayerId, ready_at: ServerTime, dropped: bool },
    /// `from` threw at `thrown_at` (the throw clip starts); the bomb lands by `to` at `lands_at`.
    Flight {
        from: PlayerId,
        to: PlayerId,
        thrown_at: ServerTime,
        lands_at: ServerTime,
        /// Whether `to` caught it; None until they tap. Without a tap the flight
        /// ends as a drop once the catch window closes, after `lands_at`.
        caught: Option<bool>,
    },
    /// The bomb went off on `victim`, who is `out` if that was their last life.
    Boom { victim: PlayerId, out: bool },
    /// Players in finishing order, winner first.
    Final { order: Vec<PlayerId> },
}
