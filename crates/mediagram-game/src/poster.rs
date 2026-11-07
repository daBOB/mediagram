//! Poster game: a poster sharpens step by step on the TV and the first right
//! answer, by claimed tap time, wins; a wrong tap locks that player out.
//!
//! Taps are judged like the quiz's: a phone claims a server time, accepted
//! only within `max_claim_lag_ms` before arrival. Because a slower phone may
//! still arrive with an earlier claim, a winner is named only once the
//! earliest claim's lag window has fully passed.

use std::collections::{BTreeSet, HashSet};

use crate::game::{ActionError, Game, GameCtx, ViewCtx};
use crate::poster_config::{Claims, PosterConfig, Round, Stage, pick_rounds, points_at_step, rng_for, step_at};
use crate::protocol::{Action, PhoneScreen, PlayerId, ServerTime, TvScene};
use crate::titles::TitlePool;


pub struct PosterGame {
    pub(crate) cfg: PosterConfig,
    pub(crate) rounds: Vec<Round>,
    pub(crate) index: usize,
    pub(crate) stage: Stage,
    pub(crate) locked: BTreeSet<PlayerId>,
    /// Accepted times of right taps this round.
    pub(crate) claims: Claims,
    pub(crate) winner: Option<(PlayerId, i32)>,
}

impl PosterGame {
    /// Panics without a playable title; the lobby only starts this game when
    /// `poster_ready` holds for the same pool.
    pub fn new(cfg: PosterConfig, pool: &TitlePool, eligible: &[usize], used: &mut HashSet<String>, now: ServerTime) -> Self {
        let rounds = pick_rounds(pool, eligible, cfg.rounds.max(1), used, &mut rng_for(&cfg));
        assert!(!rounds.is_empty(), "a poster game needs titles with posters");
        let mut game = Self {
            cfg,
            rounds,
            index: 0,
            stage: Stage::Final,
            locked: BTreeSet::new(),
            claims: Claims::new(),
            winner: None,
        };
        game.start_round(now);
        game
    }

    #[cfg(test)]
    pub(crate) fn winner(&self) -> Option<(PlayerId, i32)> {
        self.winner
    }

    pub(crate) fn round(&self) -> &Round {
        &self.rounds[self.index.min(self.rounds.len() - 1)]
    }

    fn start_round(&mut self, now: ServerTime) {
        self.locked.clear();
        self.claims.clear();
        self.winner = None;
        let last_step = f64::from(self.cfg.steps - 1);
        self.stage = Stage::Showing {
            started_at: now,
            closes_at: now + last_step * self.cfg.step_ms + self.cfg.grace_ms,
        };
    }

    /// Names the earliest claim's holder, if any, and shows the answer.
    fn reveal(&mut self, ctx: &mut GameCtx, started_at: ServerTime) {
        let earliest = self.claims.iter().min_by(|a, b| a.1.total_cmp(b.1));
        if let Some((&player, &t)) = earliest {
            let points = points_at_step(&self.cfg, step_at(&self.cfg, started_at, t));
            if let Some(p) = ctx.players.iter_mut().find(|p| p.id == player) {
                p.score += points;
            }
            self.winner = Some((player, points));
        }
        self.stage = Stage::Reveal { ends_at: ctx.now + self.cfg.reveal_ms };
    }
}

impl Game for PosterGame {
    fn on_action(&mut self, ctx: &mut GameCtx, player: PlayerId, action: &Action) -> Result<(), ActionError> {
        let Action::Choose { index, at } = *action else {
            return Err(ActionError::Ignored);
        };
        let Stage::Showing { started_at, .. } = self.stage else {
            return Err(ActionError::Ignored);
        };
        let now = ctx.now;
        if now < started_at - self.cfg.early_tolerance_ms || self.locked.contains(&player) || self.claims.contains_key(&player) {
            return Err(ActionError::Ignored);
        }
        if usize::from(index) >= self.round().options.len() {
            return Err(ActionError::NotAllowed("Diese Antwort gibt es nicht."));
        }
        if index != self.round().correct {
            self.locked.insert(player);
            return Ok(());
        }
        let hi = now.max(started_at);
        let lo = (now - self.cfg.max_claim_lag_ms).max(started_at).min(hi);
        self.claims.insert(player, if at.is_finite() { at.clamp(lo, hi) } else { hi });
        Ok(())
    }

    fn on_tick(&mut self, ctx: &mut GameCtx) {
        let now = ctx.now;
        match self.stage {
            Stage::Showing { started_at, closes_at } => {
                let earliest = self.claims.values().copied().min_by(f64::total_cmp);
                let decided = earliest.is_some_and(|t| now >= t + self.cfg.max_claim_lag_ms);
                let mut connected = ctx.players.iter().filter(|p| p.connected()).peekable();
                let all_out = connected.peek().is_some() && connected.all(|p| self.locked.contains(&p.id));
                if decided || all_out || now >= closes_at {
                    self.reveal(ctx, started_at);
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
#[path = "poster_tests.rs"]
mod tests;
