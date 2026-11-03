//! Wire types of the buzzer quiz's TV scene.

use serde::{Deserialize, Serialize};
use ts_rs::TS;

use crate::protocol::{PlayerId, ServerTime};

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct QuizScene {
    /// 1-based.
    pub round: u8,
    pub rounds: u8,
    pub stage: QuizStage,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct QuizQuestionView {
    pub category: String,
    pub text: String,
    pub options: Vec<String>,
    /// Poster for the TV only; phones never receive it.
    pub image: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(tag = "stage", rename_all = "snake_case")]
#[ts(export)]
pub enum QuizStage {
    /// Question is shown, buttons are not live yet.
    Intro {
        question: QuizQuestionView,
        opens_at: ServerTime,
        closes_at: ServerTime,
    },
    Open {
        question: QuizQuestionView,
        opens_at: ServerTime,
        closes_at: ServerTime,
        answered: Vec<PlayerId>,
    },
    Reveal {
        question: QuizQuestionView,
        correct: u8,
        results: Vec<QuizResult>,
        ends_at: ServerTime,
    },
    Final,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct QuizResult {
    pub player: PlayerId,
    pub choice: Option<u8>,
    /// Reaction time after the buttons opened.
    pub ms: Option<u32>,
    pub points: i32,
}
