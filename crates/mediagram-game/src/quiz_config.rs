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

impl QuizConfig {
    /// When a tap counts: its claimed time `at`, trusted only within
    /// `max_claim_lag_ms` before its arrival `now`, and never before `opens_at`.
    pub(crate) fn claim_time(&self, now: ServerTime, opens_at: ServerTime, at: ServerTime) -> ServerTime {
        let hi = now.max(opens_at);
        let lo = (now - self.max_claim_lag_ms).max(opens_at).min(hi);
        if at.is_finite() { at.clamp(lo, hi) } else { hi }
    }

    /// Points for a right answer given at `at`: `max_points` at once, `min_points` at the close.
    pub(crate) fn speed_points(&self, opens_at: ServerTime, at: ServerTime) -> i32 {
        let elapsed = (at - opens_at).clamp(0.0, self.answer_ms);
        let span = f64::from(self.max_points - self.min_points);
        self.max_points - (span * elapsed / self.answer_ms).round() as i32
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
    /// `q` with its answers shuffled, `count` of them (the right one always
    /// among them); `correct` indexes the right one.
    pub(crate) fn shuffled(q: Question, rng: &mut StdRng, count: usize) -> Self {
        let mut options: Vec<String> = q.wrong.to_vec();
        options.push(q.correct.clone());
        options.shuffle(rng);
        if options.len() > count {
            // Fewer fields: drop the first wrong answers, then shuffle again, since
            // dropping from the front leaves the right answer in field 1 too often.
            let mut extra = options.len() - count;
            options.retain(|o| {
                let drop = extra > 0 && *o != q.correct;
                extra -= usize::from(drop);
                !drop
            });
            options.shuffle(rng);
        }
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
