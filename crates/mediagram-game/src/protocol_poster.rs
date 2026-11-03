//! Wire types of the poster game's TV scene.

use serde::{Deserialize, Serialize};
use ts_rs::TS;

use crate::protocol::{PlayerId, ServerTime};

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct PosterScene {
    /// 1-based.
    pub round: u8,
    pub rounds: u8,
    pub stage: PosterStage,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(tag = "stage", rename_all = "snake_case")]
#[ts(export)]
pub enum PosterStage {
    /// The poster sharpens one step every `step_ms`, starting at `started_at`.
    Showing {
        /// For the TV only; phones never receive it.
        image: String,
        started_at: ServerTime,
        step_ms: f64,
        steps: u8,
        /// Players who guessed wrong and sit this poster out.
        locked: Vec<PlayerId>,
    },
    Reveal {
        image: String,
        title: String,
        winner: Option<PlayerId>,
        points: i32,
        ends_at: ServerTime,
    },
    Final,
}
