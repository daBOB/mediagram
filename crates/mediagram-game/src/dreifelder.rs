//! Dreifelder: everyone picks one of three fields; a wrong pick (or none) is
//! out, unless every alive player got it wrong. The last one in wins; at the
//! question cap the fastest right answers decide. Players who are out keep
//! guessing on their phone without points and without holding a round up.

use std::collections::BTreeMap;

use rand::SeedableRng;
use rand::rngs::StdRng;

pub use crate::dreifelder_config::DreifelderConfig;
use crate::dreifelder_rules::{Tally, placement_scores};
use crate::game::{ActionError, Game, GameCtx, ViewCtx};
use crate::protocol::{Action, PhoneScreen, PlayerId, ServerTime, TvScene};
use crate::quiz_config::{Answer, Question, Round};
use crate::room_seats::Player;

#[derive(Debug, Clone, Copy, PartialEq)]
pub(crate) enum Stage {
    /// Shown at once, live from `opens_at`.
    Question { opens_at: ServerTime, closes_at: ServerTime },
    Reveal { ends_at: ServerTime },
    Final,
}

pub struct DreifelderGame {
    pub(crate) cfg: DreifelderConfig,
    pub(crate) rounds: Vec<Round>,
    pub(crate) index: usize,
    pub(crate) stage: Stage,
    /// This question's picks, out players' included.
    pub(crate) answers: BTreeMap<PlayerId, Answer>,
    pub(crate) tally: Tally,
    /// Out at the current reveal.
    pub(crate) eliminated: Vec<PlayerId>,
    /// The current reveal is the last.
    pub(crate) over: bool,
    pub(crate) order: Vec<PlayerId>,
}

impl DreifelderGame {
    /// Asks up to `cfg.quiz.rounds` of `questions` in the order given.
    pub(crate) fn new(cfg: DreifelderConfig, questions: Vec<Question>, players: &[Player], now: ServerTime) -> Self {
        let mut rng = cfg.quiz.seed.map_or_else(StdRng::from_os_rng, StdRng::seed_from_u64);
        let rounds =
            questions.into_iter().take(cfg.quiz.rounds).map(|q| Round::shuffled(q, &mut rng, cfg.fields)).collect();
        let mut game = Self {
            cfg,
            rounds,
            index: 0,
            stage: Stage::Final,
            answers: BTreeMap::new(),
            tally: Tally::new(players.iter().map(|p| p.id)),
            eliminated: Vec::new(),
            over: false,
            order: Vec::new(),
        };
        if game.rounds.is_empty() {
            // Never from the factory (the bundled pack is never spent); ends at once, scores untouched.
            game.order = game.tally.finishing_order();
        } else {
            game.start_question(now);
        }
        game
    }

    pub(crate) fn round(&self) -> &Round {
        &self.rounds[self.index.min(self.rounds.len().saturating_sub(1))]
    }

    fn start_question(&mut self, now: ServerTime) {
        self.answers.clear();
        self.eliminated.clear();
        let opens_at = now + self.cfg.quiz.intro_ms;
        self.stage = Stage::Question { opens_at, closes_at: opens_at + self.cfg.quiz.answer_ms };
    }

    fn reveal(&mut self, players: &[Player], opens_at: ServerTime, now: ServerTime) {
        let picks = self.answers.iter().map(|(&p, a)| (p, (a.choice, self.cfg.quiz.speed_points(opens_at, a.at)))).collect();
        let connected: Vec<PlayerId> = players.iter().filter(|p| p.connected()).map(|p| p.id).collect();
        self.eliminated = self.tally.settle_round(self.index, self.round().correct, &picks, &connected);
        self.over = self.tally.is_over(self.index + 1, self.rounds.len(), &connected);
        self.stage = Stage::Reveal { ends_at: now + self.cfg.quiz.reveal_ms };
    }

    fn finish(&mut self, players: &mut [Player]) {
        self.order = self.tally.finishing_order();
        for (id, score) in placement_scores(&self.order) {
            if let Some(p) = players.iter_mut().find(|p| p.id == id) {
                p.score = score;
            }
        }
        self.stage = Stage::Final;
    }
}

impl Game for DreifelderGame {
    fn on_action(&mut self, ctx: &mut GameCtx, player: PlayerId, action: &Action) -> Result<(), ActionError> {
        let Action::Choose { index, at } = *action else { return Err(ActionError::Ignored) };
        let Stage::Question { opens_at, .. } = self.stage else { return Err(ActionError::Ignored) };
        let early = ctx.now < opens_at - self.cfg.quiz.early_tolerance_ms;
        if early || !self.tally.plays(player) || self.answers.contains_key(&player) {
            return Err(ActionError::Ignored);
        }
        if usize::from(index) >= self.round().options.len() {
            return Err(ActionError::NotAllowed("Dieses Feld gibt es nicht."));
        }
        let at = self.cfg.quiz.claim_time(ctx.now, opens_at, at);
        self.answers.insert(player, Answer { choice: index, at });
        Ok(())
    }

    fn on_tick(&mut self, ctx: &mut GameCtx) {
        let now = ctx.now;
        match self.stage {
            Stage::Question { opens_at, closes_at } => {
                // Only alive players who are here hold a round up.
                let waiting = ctx
                    .players
                    .iter()
                    .any(|p| p.connected() && self.tally.is_alive(p.id) && !self.answers.contains_key(&p.id));
                if now >= closes_at || (now >= opens_at && !waiting) {
                    self.reveal(ctx.players, opens_at, now);
                }
            }
            Stage::Reveal { ends_at } if now >= ends_at => {
                if self.over {
                    self.finish(ctx.players);
                } else {
                    self.index += 1;
                    self.start_question(now);
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
#[path = "dreifelder_tests.rs"]
pub(crate) mod tests;
