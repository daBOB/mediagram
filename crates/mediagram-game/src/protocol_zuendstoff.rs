//! Wire types of Zündstoff's TV scene.

use serde::{Deserialize, Serialize};
use ts_rs::TS;

use crate::protocol::{PlayerId, QuizQuestionView, ServerTime};

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
    /// `holder` answers `question`; its keys go live at `opens_at`.
    Answer {
        holder: PlayerId,
        question: QuizQuestionView,
        opens_at: ServerTime,
        /// The holder's last answer was wrong.
        wrong: bool,
        /// Who threw the bomb to the holder; none at a round's start.
        thrown_by: Option<PlayerId>,
    },
    /// `holder` answered right and picks who catches.
    Throw { holder: PlayerId },
    /// The bomb went off on `victim`, who is `out` if that was their last life.
    Boom { victim: PlayerId, out: bool },
    /// Players in finishing order, winner first.
    Final { order: Vec<PlayerId> },
}
