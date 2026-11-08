//! Buzzer quiz: everyone answers on their phone at once, correct answers
//! score more the faster they come in.
//!
//! Fairness: phones send their estimate of server time at the tap. The host
//! trusts it only within `max_claim_lag_ms` before arrival, so a slow phone is
//! not punished for network lag, and a cheating phone gains at most that much.

use std::collections::BTreeMap;

use rand::SeedableRng;
use rand::rngs::StdRng;

use crate::game::{ActionError, Game, GameCtx, ViewCtx};
use crate::protocol::{Action, PhoneScreen, PlayerId, QuizResult, ServerTime, TvScene};
use crate::quiz_config::{Answer, Question, QuizConfig, Round, Stage};

pub struct Quiz {
    pub(crate) cfg: QuizConfig,
    pub(crate) rounds: Vec<Round>,
    pub(crate) index: usize,
    pub(crate) stage: Stage,
    pub(crate) answers: BTreeMap<PlayerId, Answer>,
    pub(crate) results: Vec<QuizResult>,
}

impl Quiz {
    /// Asks the first `cfg.rounds` questions in the order given (the caller
    /// picks and orders them); only the answer options are shuffled.
    pub fn new(cfg: QuizConfig, questions: Vec<Question>, now: ServerTime) -> Self {
        assert!(!questions.is_empty(), "a quiz needs at least one question");
        let mut rng = match cfg.seed {
            Some(s) => StdRng::seed_from_u64(s),
            None => StdRng::from_os_rng(),
        };
        let rounds = questions
            .into_iter()
            .take(cfg.rounds.max(1))
            .map(|q| Round::shuffled(q, &mut rng))
            .collect();
        let mut quiz = Self {
            cfg,
            rounds,
            index: 0,
            stage: Stage::Final,
            answers: BTreeMap::new(),
            results: Vec::new(),
        };
        quiz.start_round(now);
        quiz
    }

    #[cfg(test)]
    pub(crate) fn round_texts(&self) -> Vec<String> {
        self.rounds.iter().map(|r| r.text.clone()).collect()
    }

    fn start_round(&mut self, now: ServerTime) {
        self.answers.clear();
        self.results.clear();
        let opens_at = now + self.cfg.intro_ms;
        self.stage = Stage::Intro { opens_at, closes_at: opens_at + self.cfg.answer_ms };
    }

    pub(crate) fn round(&self) -> &Round {
        &self.rounds[self.index.min(self.rounds.len() - 1)]
    }

    fn points_for(&self, answer: &Answer, opens_at: ServerTime) -> i32 {
        if answer.choice != self.round().correct {
            return 0;
        }
        let elapsed = (answer.at - opens_at).clamp(0.0, self.cfg.answer_ms);
        let span = f64::from(self.cfg.max_points - self.cfg.min_points);
        self.cfg.max_points - (span * elapsed / self.cfg.answer_ms).round() as i32
    }

    fn reveal(&mut self, ctx: &mut GameCtx, opens_at: ServerTime) {
        let mut results: Vec<QuizResult> = ctx
            .players
            .iter()
            .map(|p| {
                let answer = self.answers.get(&p.id);
                QuizResult {
                    player: p.id,
                    choice: answer.map(|a| a.choice),
                    ms: answer.map(|a| (a.at - opens_at).max(0.0).round() as u32),
                    points: answer.map_or(0, |a| self.points_for(a, opens_at)),
                }
            })
            .collect();
        for r in &results {
            if let Some(p) = ctx.players.iter_mut().find(|p| p.id == r.player) {
                p.score += r.points;
            }
        }
        results.sort_by_key(|r| (std::cmp::Reverse(r.points), r.ms.unwrap_or(u32::MAX)));
        self.results = results;
        self.stage = Stage::Reveal { ends_at: ctx.now + self.cfg.reveal_ms };
    }
}

impl Game for Quiz {
    fn on_action(
        &mut self,
        ctx: &mut GameCtx,
        player: PlayerId,
        action: &Action,
    ) -> Result<(), ActionError> {
        let Action::Choose { index, at } = *action else {
            return Err(ActionError::Ignored);
        };
        let (Stage::Intro { opens_at, .. } | Stage::Open { opens_at, .. }) = self.stage else {
            return Err(ActionError::Ignored);
        };
        let now = ctx.now;
        if now < opens_at - self.cfg.early_tolerance_ms || self.answers.contains_key(&player) {
            return Err(ActionError::Ignored);
        }
        if usize::from(index) >= self.round().options.len() {
            return Err(ActionError::NotAllowed("Diese Antwort gibt es nicht."));
        }
        let hi = now.max(opens_at);
        let lo = (now - self.cfg.max_claim_lag_ms).max(opens_at).min(hi);
        let at = if at.is_finite() { at.clamp(lo, hi) } else { hi };
        self.answers.insert(player, Answer { choice: index, at });
        Ok(())
    }

    fn on_tick(&mut self, ctx: &mut GameCtx) {
        let now = ctx.now;
        match self.stage {
            Stage::Intro { opens_at, closes_at } if now >= opens_at => {
                self.stage = Stage::Open { opens_at, closes_at };
                self.on_tick(ctx);
            }
            Stage::Open { opens_at, closes_at } => {
                let everyone = ctx
                    .players
                    .iter()
                    .filter(|p| p.connected())
                    .all(|p| self.answers.contains_key(&p.id));
                if now >= closes_at || everyone {
                    self.reveal(ctx, opens_at);
                }
            }
            Stage::Reveal { ends_at } if now >= ends_at => {
                self.index += 1;
                if self.index < self.rounds.len() {
                    self.start_round(now);
                } else {
                    self.stage = Stage::Final;
                }
            }
            _ => {}
        }
    }

    fn phone_screen(&self, view: &ViewCtx, player: PlayerId) -> PhoneScreen {
        self.phone_view(view, player)
    }

    fn tv_scene(&self, _view: &ViewCtx) -> TvScene {
        self.tv_view()
    }

    fn is_finished(&self) -> bool {
        self.stage == Stage::Final
    }
}

#[cfg(test)]
#[path = "quiz_tests.rs"]
mod tests;
