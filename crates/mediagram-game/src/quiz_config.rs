//! Quiz inputs (question, timing, scoring) and the per-game state types.

use rand::rngs::StdRng;
use rand::seq::SliceRandom;

use crate::protocol::ServerTime;

/// One multiple-choice question. `correct` is shuffled in among `wrong`.
#[derive(Debug, Clone)]
pub struct Question {
    pub category: String,
    pub text: String,
    pub correct: String,
    pub wrong: [String; 3],
    /// Shown on the TV only.
    pub image: Option<String>,
}

#[derive(Debug, Clone)]
pub struct QuizConfig {
    pub rounds: usize,
    /// Question visible on the TV before the buttons go live.
    pub intro_ms: ServerTime,
    pub answer_ms: ServerTime,
    pub reveal_ms: ServerTime,
    /// How far before its arrival a tap may claim to have happened.
    pub max_claim_lag_ms: ServerTime,
    /// Taps arriving this early are accepted, to absorb clock-sync error.
    pub early_tolerance_ms: ServerTime,
    pub max_points: i32,
    pub min_points: i32,
    pub seed: Option<u64>,
}

impl Default for QuizConfig {
    fn default() -> Self {
        Self {
            rounds: 8,
            intro_ms: 3500.0,
            answer_ms: 12_000.0,
            reveal_ms: 5000.0,
            max_claim_lag_ms: 400.0,
            early_tolerance_ms: 150.0,
            max_points: 1000,
            min_points: 500,
            seed: None,
        }
    }
}

pub(crate) struct Round {
    pub category: String,
    pub text: String,
    pub options: Vec<String>,
    pub correct: u8,
    pub image: Option<String>,
}

impl Round {
    /// `q` with its answers shuffled; `correct` indexes the right one.
    pub(crate) fn shuffled(q: Question, rng: &mut StdRng) -> Self {
        let mut options: Vec<String> = q.wrong.to_vec();
        options.push(q.correct.clone());
        options.shuffle(rng);
        let correct = options.iter().position(|o| *o == q.correct).unwrap_or(0) as u8;
        Self { category: q.category, text: q.text, options, correct, image: q.image }
    }
}

#[derive(Debug, Clone, Copy)]
pub(crate) struct Answer {
    pub choice: u8,
    /// Accepted tap time (server time, already clamped).
    pub at: ServerTime,
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub(crate) enum Stage {
    Intro { opens_at: ServerTime, closes_at: ServerTime },
    Open { opens_at: ServerTime, closes_at: ServerTime },
    Reveal { ends_at: ServerTime },
    Final,
}
