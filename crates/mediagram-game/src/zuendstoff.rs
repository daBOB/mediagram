//! Zündstoff: a bomb with a hidden fuse goes from phone to phone. Whoever
//! holds it answers a quick question; only a right answer lets them throw it
//! on, to a player of their choice, and a wrong one costs a pause. When the
//! fuse runs out the holder loses a life; the last one with lives left wins.

use std::collections::BTreeMap;

use rand::rngs::StdRng;
use rand::seq::IndexedRandom;
use rand::{Rng, SeedableRng};

pub use crate::zuendstoff_config::{QuestionDeck, ZuendstoffConfig};
use crate::ask::Ask;
use crate::dreifelder_rules::placement_scores;
use crate::game::{ActionError, Game, GameCtx, ViewCtx};
use crate::protocol::{Action, AnswerColor, Buzz, PhoneScreen, PlayerId, ServerTime, TvScene};
use crate::quiz_config::Round;
use crate::room_seats::Player;
use crate::zuendstoff_config::{Stage, Step};

pub struct ZuendstoffGame {
    pub(crate) cfg: ZuendstoffConfig,
    pub(crate) stage: Stage,
    /// When this round's bomb goes off; never shown.
    pub(crate) fuse_at: ServerTime,
    /// 1-based.
    pub(crate) round: u8,
    pub(crate) lives: BTreeMap<PlayerId, u8>,
    /// In the order they ran out of lives.
    pub(crate) out: Vec<PlayerId>,
    pub(crate) question: Round,
    /// The throw that brought the bomb to its holder: thrower and catcher.
    pub(crate) thrown: Option<(PlayerId, PlayerId)>,
    /// Finishing order, winner first; set at the end.
    pub(crate) order: Vec<PlayerId>,
    deck: QuestionDeck,
    rng: StdRng,
}

impl ZuendstoffGame {
    pub fn new(cfg: ZuendstoffConfig, deck: QuestionDeck, players: &[Player], now: ServerTime) -> Self {
        let mut rng = cfg.seed.map_or_else(StdRng::from_os_rng, StdRng::seed_from_u64);
        // Replaced by the first round's question below; drawing one here would mark it asked tonight.
        let question = Round { category: String::new(), text: String::new(), options: Vec::new(), correct: 0, image: None };
        let lives = players.iter().map(|p| (p.id, cfg.lives)).collect();
        let first = players.choose(&mut rng).map_or(0, |p| p.id);
        let mut game = Self {
            cfg,
            stage: Stage::Final,
            fuse_at: now,
            round: 0,
            lives,
            out: Vec::new(),
            question,
            thrown: None,
            order: Vec::new(),
            deck,
            rng,
        };
        game.start_round(first, now);
        game
    }

    pub(crate) fn lives_of(&self, player: PlayerId) -> u8 {
        self.lives.get(&player).copied().unwrap_or(0)
    }

    pub(crate) fn alive(&self, player: PlayerId) -> bool {
        self.lives_of(player) > 0
    }

    /// Who `holder` may throw to, in seat order: everyone else still in.
    pub(crate) fn targets(&self, players: &[Player], holder: PlayerId) -> Vec<PlayerId> {
        players.iter().map(|p| p.id).filter(|&id| id != holder && self.alive(id)).collect()
    }

    fn start_round(&mut self, holder: PlayerId, now: ServerTime) {
        self.round += 1;
        self.thrown = None;
        self.fuse_at = now + self.rng.random_range(self.cfg.fuse_min_ms..=self.cfg.fuse_max_ms);
        self.ask(Ask::open(holder, now, None), now + self.cfg.intro_ms, false);
    }

    /// Hands the holder `ask` asks a fresh question whose keys go live at `opens_at`.
    fn ask(&mut self, ask: Ask, opens_at: ServerTime, wrong: bool) {
        let q = (self.deck)(&mut self.rng);
        self.question = Round::shuffled(q, &mut self.rng, AnswerColor::ALL.len());
        self.stage = Stage::Lit { ask, step: Step::Answer { opens_at, wrong } };
    }

    fn throw(&mut self, from: PlayerId, to: PlayerId, now: ServerTime) {
        self.thrown = Some((from, to));
        self.ask(Ask::open(to, now, None), now + self.cfg.catch_ms, false);
    }

    fn explode(&mut self, victim: PlayerId, now: ServerTime) {
        if let Some(lives) = self.lives.get_mut(&victim) {
            *lives = lives.saturating_sub(1);
            if *lives == 0 {
                self.out.push(victim);
            }
        }
        self.stage = Stage::Boom { victim, ends_at: now + self.cfg.boom_ms };
    }

    fn finish(&mut self, players: &mut [Player], alive: Vec<PlayerId>) {
        self.order = alive.into_iter().chain(self.out.iter().rev().copied()).collect();
        for (id, score) in placement_scores(&self.order) {
            if let Some(p) = players.iter_mut().find(|p| p.id == id) {
                p.score = score;
            }
        }
        self.stage = Stage::Final;
    }
}

impl Game for ZuendstoffGame {
    fn on_action(&mut self, ctx: &mut GameCtx, player: PlayerId, action: &Action) -> Result<(), ActionError> {
        let Action::Choose { index, .. } = *action else { return Err(ActionError::Ignored) };
        let Stage::Lit { ask, step } = self.stage else { return Err(ActionError::Ignored) };
        if player != ask.who() {
            return Err(ActionError::Ignored);
        }
        let now = ctx.now;
        match step {
            Step::Answer { opens_at, .. } => {
                if now < opens_at - self.cfg.early_tolerance_ms {
                    return Err(ActionError::Ignored);
                }
                if usize::from(index) >= self.question.options.len() {
                    return Err(ActionError::NotAllowed("Diese Antwort gibt es nicht."));
                }
                if index == self.question.correct {
                    self.stage = Stage::Lit { ask, step: Step::Throw };
                } else {
                    self.ask(ask, now + self.cfg.wrong_wait_ms, true);
                }
            }
            Step::Throw => {
                let Some(&to) = self.targets(ctx.players, ask.who()).get(usize::from(index)) else {
                    return Err(ActionError::NotAllowed("Diesen Mitspieler gibt es nicht."));
                };
                self.throw(ask.who(), to, now);
            }
        }
        Ok(())
    }

    fn on_tick(&mut self, ctx: &mut GameCtx) {
        let now = ctx.now;
        match self.stage {
            Stage::Lit { ask, .. } if now >= self.fuse_at => self.explode(ask.who(), now),
            Stage::Lit { ask, .. } => {
                // A holder who is away has the house throw for them, without a question.
                if ask.house_decides(ctx) {
                    let targets = self.targets(ctx.players, ask.who());
                    if let Some(&to) = targets.choose(&mut self.rng) {
                        self.throw(ask.who(), to, now);
                    }
                }
            }
            Stage::Boom { victim, ends_at } if now >= ends_at => {
                let alive: Vec<PlayerId> = ctx.players.iter().map(|p| p.id).filter(|&id| self.alive(id)).collect();
                if alive.len() <= 1 {
                    self.finish(ctx.players, alive);
                } else {
                    // The one it went off on starts the next round, or anyone left if they are out.
                    let holder = if self.alive(victim) { victim } else { *alive.choose(&mut self.rng).unwrap_or(&victim) };
                    self.start_round(holder, now);
                }
            }
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

    fn phone_buzz(&self, _view: &ViewCtx, player: PlayerId) -> Option<Buzz> {
        match self.stage {
            Stage::Lit { ask, .. } if ask.who() == player => Some(Buzz::Turn),
            Stage::Boom { victim, .. } if victim == player => Some(Buzz::Wrong),
            _ => None,
        }
    }
}

#[cfg(test)]
#[path = "zuendstoff_tests.rs"]
mod tests;
