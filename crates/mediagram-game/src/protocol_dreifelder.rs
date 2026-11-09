//! Wire types of Dreifelder's TV scene.

use serde::{Deserialize, Serialize};
use ts_rs::TS;

use crate::protocol::{PlayerId, QuizQuestionView, ServerTime};

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct DreifelderScene {
    /// 1-based.
    pub round: u8,
    /// The most questions a game asks.
    pub rounds: u8,
    pub alive: Vec<PlayerId>,
    /// In the order they went out.
    pub out: Vec<OutPlayer>,
    pub stage: DreifelderStage,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct OutPlayer {
    pub player: PlayerId,
    /// 1-based round they went out in.
    pub round: u8,
}

/// The field a player stands in at the reveal; `None` = no pick.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct FieldPick {
    pub player: PlayerId,
    pub choice: Option<u8>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(tag = "stage", rename_all = "snake_case")]
#[ts(export)]
pub enum DreifelderStage {
    /// The question shows at once; the fields go live at `opens_at`.
    Question {
        question: QuizQuestionView,
        opens_at: ServerTime,
        closes_at: ServerTime,
        /// Everyone who picked, out players included.
        answered: Vec<PlayerId>,
    },
    Reveal {
        question: QuizQuestionView,
        correct: u8,
        /// Every player of the game, alive and out.
        picks: Vec<FieldPick>,
        /// Out this round.
        eliminated: Vec<PlayerId>,
        ends_at: ServerTime,
    },
    /// Players in finishing order.
    Final { order: Vec<PlayerId> },
}
