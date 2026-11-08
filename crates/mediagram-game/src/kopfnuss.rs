//! Kopfnuss: players vote the category and play tricks on each other's phones,
//! three questions a round, then the Gipfelsturm climb. The questions run in a
//! contained buzzer `Quiz` (its timing, fair taps and scoring); Kopfnuss adds the picks and tricks.

use std::collections::BTreeMap;

use rand::rngs::StdRng;
use rand::{Rng, SeedableRng, seq::IndexedRandom};

use crate::game::{ActionError, Game, GameCtx, ViewCtx};
use crate::kopfnuss_climb::Climb;
use crate::{kopfnuss_config::KopfnussConfig, kopfnuss_facts::pick_fact};
use crate::kopfnuss_picks::{Sent, Sheet, Step, resolve_category, resolve_tricks};
use crate::kopfnuss_supply::QuestionSupply;
use crate::protocol::{Action, PhoneScreen, PlayerId, ServerTime, Trick, TrickHit, TvScene};
use crate::quiz::Quiz;
use crate::quiz_config::QuizConfig;
use crate::room_seats::Player;

#[derive(Debug, Clone, Copy, PartialEq)]
pub(crate) enum Stage {
    Pick { closes_at: ServerTime },
    Resolve { ends_at: ServerTime },
    Question,
    Climb,
    Fact { ends_at: ServerTime },
    Final,
}

pub struct KopfnussGame {
    pub(crate) cfg: KopfnussConfig,
    supply: QuestionSupply,
    rng: StdRng,
    /// Rounds started so far; the current one while it runs.
    pub(crate) round: usize,
    pub(crate) stage: Stage,
    pub(crate) categories: Vec<String>,
    pub(crate) sheets: BTreeMap<PlayerId, Sheet>,
    pub(crate) votes: Vec<u8>,
    pub(crate) chosen: u8,
    pub(crate) hits: Vec<TrickHit>,
    /// What each phone carries during this round's questions.
    pub(crate) tricks: BTreeMap<PlayerId, Vec<Trick>>,
    pub(crate) quiz: Option<Quiz>,
    pub(crate) climb: Option<Climb>,
    pub(crate) fact: String,
    pub(crate) order: Vec<PlayerId>,
}

impl KopfnussGame {
    pub(crate) fn new(cfg: KopfnussConfig, supply: QuestionSupply, players: &[Player], now: ServerTime) -> Self {
        let rng = cfg.seed.map_or_else(StdRng::from_os_rng, StdRng::seed_from_u64);
        let mut game = Self {
            cfg,
            supply,
            rng,
            round: 0,
            stage: Stage::Final,
            categories: Vec::new(),
            sheets: BTreeMap::new(),
            votes: Vec::new(),
            chosen: 0,
            hits: Vec::new(),
            tricks: BTreeMap::new(),
            quiz: None,
            climb: None,
            fact: String::new(),
            order: Vec::new(),
        };
        game.start_round(players, now);
        game
    }

    fn start_round(&mut self, players: &[Player], now: ServerTime) {
        self.categories = self.supply.offer(&mut self.rng, self.cfg.offered, self.cfg.per_round);
        if self.round >= self.cfg.rounds || self.categories.is_empty() {
            return self.start_climb(players, now);
        }
        let targets: Vec<PlayerId> = players.iter().filter(|p| p.connected()).map(|p| p.id).collect();
        self.sheets = players
            .iter()
            .map(|p| {
                let offered = Trick::ALL.choose_multiple(&mut self.rng, self.cfg.trick_offer).copied().collect();
                let targets = targets.iter().copied().filter(|&t| t != p.id).collect();
                (p.id, Sheet { offered, targets, vote: None, trick: None, target: None, step_at: now })
            })
            .collect();
        self.stage = Stage::Pick { closes_at: now + self.cfg.pick_ms };
    }

    fn resolve(&mut self, players: &[Player], now: ServerTime) {
        let votes: Vec<(PlayerId, u8)> = self.sheets.iter().filter_map(|(&p, s)| s.vote.map(|v| (p, v))).collect();
        // Last place among those still playing: one who left casts no vote to break a tie with.
        let here = || players.iter().filter(|p| p.connected());
        let lowest = here().map(|p| p.score).min().unwrap_or(0);
        let last: Vec<PlayerId> = here().filter(|p| p.score == lowest).map(|p| p.id).collect();
        self.chosen = resolve_category(&votes, &last, self.categories.len(), &mut self.rng);
        self.votes = (0..self.categories.len() as u8).map(|c| votes.iter().filter(|v| v.1 == c).count() as u8).collect();
        let sent = self
            .sheets
            .iter()
            .filter_map(|(&from, s)| Some(Sent { at: s.step_at, from, trick: s.trick?, to: s.target? }))
            .collect();
        (self.tricks, self.hits) = resolve_tricks(sent, self.cfg.max_tricks);
        self.stage = Stage::Resolve { ends_at: now + self.cfg.resolve_ms };
    }

    fn start_questions(&mut self, players: &[Player], now: ServerTime) {
        let category = self.categories[usize::from(self.chosen)].clone();
        let questions = self.supply.take(&category, self.cfg.per_round, &mut self.rng);
        if questions.is_empty() {
            return self.end_round(players, now);
        }
        let cfg = QuizConfig { rounds: self.cfg.per_round, seed: Some(self.rng.random()), ..self.cfg.quiz.clone() };
        self.quiz = Some(Quiz::new(cfg, questions, now));
        self.stage = Stage::Question;
    }

    fn end_round(&mut self, players: &[Player], now: ServerTime) {
        self.tricks.clear();
        self.hits.clear();
        self.quiz = None;
        self.round += 1;
        self.start_round(players, now);
    }

    fn start_climb(&mut self, players: &[Player], now: ServerTime) {
        let deck = self.supply.deck(self.cfg.deck, &mut self.rng);
        self.climb = Some(Climb::new(&self.cfg, deck, &mut self.rng, players, now));
        self.stage = Stage::Climb;
    }
}

impl Game for KopfnussGame {
    fn on_action(&mut self, ctx: &mut GameCtx, player: PlayerId, action: &Action) -> Result<(), ActionError> {
        let Action::Choose { index, .. } = *action else { return Err(ActionError::Ignored) };
        match self.stage {
            Stage::Pick { .. } => {
                let (offered, gap, early) = (self.categories.len(), self.cfg.next_gap_ms, self.cfg.quiz.early_tolerance_ms);
                let sheet = self.sheets.get_mut(&player).ok_or(ActionError::Ignored)?;
                sheet.tap(index, offered, ctx.now, gap, early).then_some(()).ok_or(ActionError::Ignored)
            }
            Stage::Question => self.quiz.as_mut().map_or(Err(ActionError::Ignored), |q| q.on_action(ctx, player, action)),
            Stage::Climb => self.climb.as_mut().map_or(Err(ActionError::Ignored), |c| c.on_choose(player, index, ctx.now)),
            _ => Err(ActionError::Ignored),
        }
    }

    fn on_tick(&mut self, ctx: &mut GameCtx) {
        let now = ctx.now;
        match self.stage {
            Stage::Pick { closes_at } => {
                let all_done = ctx
                    .players
                    .iter()
                    .filter(|p| p.connected())
                    .all(|p| self.sheets.get(&p.id).is_none_or(|s| s.step() == Step::Done));
                if now >= closes_at || all_done {
                    self.resolve(ctx.players, now);
                }
            }
            Stage::Resolve { ends_at } if now >= ends_at => self.start_questions(ctx.players, now),
            Stage::Question => {
                let Some(quiz) = self.quiz.as_mut() else { return };
                quiz.on_tick(ctx);
                if quiz.is_finished() {
                    self.end_round(ctx.players, now);
                }
            }
            Stage::Climb => {
                let Some(climb) = self.climb.as_mut() else { return };
                climb.on_tick(ctx.players, now);
                if climb.winner.is_some() {
                    self.order = climb.standings();
                    climb.award(ctx.players, &self.order);
                    self.fact = pick_fact(&mut self.rng);
                    self.stage = Stage::Fact { ends_at: now + self.cfg.fact_ms };
                }
            }
            Stage::Fact { ends_at } if now >= ends_at => self.stage = Stage::Final,
            _ => {}
        }
    }

    fn phone_screen(&self, view: &ViewCtx, player: PlayerId) -> PhoneScreen {
        self.phone_view(view, player)
    }

    fn tv_scene(&self, view: &ViewCtx) -> TvScene {
        self.tv_view(view)
    }

    fn is_finished(&self) -> bool {
        self.stage == Stage::Final
    }
}

#[cfg(test)]
#[path = "kopfnuss_tests.rs"]
mod tests;
