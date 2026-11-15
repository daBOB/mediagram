//! Zündstoff: a bomb with a hidden fuse goes round a circle of players. The
//! holder taps a name to throw it; the target taps Fangen! as it lands, or it
//! drops at their feet and has to be picked up first. When the fuse runs out
//! the one it is on loses a life; the last one with lives left wins.

use std::collections::BTreeMap;

use rand::rngs::StdRng;
use rand::seq::IndexedRandom;
use rand::{Rng, SeedableRng};

pub use crate::zuendstoff_config::ZuendstoffConfig;
use crate::ask::Ask;
use crate::dreifelder_rules::placement_scores;
use crate::game::{ActionError, Game, GameCtx, ViewCtx};
use crate::protocol::{Action, Buzz, PhoneScreen, PlayerId, Ranking, ServerTime, TvScene};
use crate::room_seats::Player;
use crate::zuendstoff_config::{Catch, Stage};

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
    /// Finishing order, winner first; set at the end.
    pub(crate) order: Vec<PlayerId>,
    rng: StdRng,
}

impl ZuendstoffGame {
    pub fn new(cfg: ZuendstoffConfig, players: &[Player], now: ServerTime) -> Self {
        let mut rng = cfg.seed.map_or_else(StdRng::from_os_rng, StdRng::seed_from_u64);
        let lives = players.iter().map(|p| (p.id, cfg.lives)).collect();
        let first = players.choose(&mut rng).map_or(0, |p| p.id);
        let mut game = Self { cfg, stage: Stage::Final, fuse_at: now, round: 0, lives, out: Vec::new(), order: Vec::new(), rng };
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

    /// Who the bomb is on: its holder, or mid-flight the one it was thrown to.
    pub(crate) fn bearer(&self) -> Option<PlayerId> {
        match self.stage {
            Stage::Hold { ask, .. } => Some(ask.who()),
            Stage::Flight { to, .. } => Some(to),
            Stage::Boom { .. } | Stage::Final => None,
        }
    }

    fn start_round(&mut self, holder: PlayerId, now: ServerTime) {
        self.round += 1;
        self.fuse_at = now + self.rng.random_range(self.cfg.fuse_min_ms..=self.cfg.fuse_max_ms);
        self.stage = Stage::Hold { ask: Ask::open(holder, now, None), ready_at: now + self.cfg.intro_ms, dropped: false };
    }

    fn throw(&mut self, from: PlayerId, to: PlayerId, now: ServerTime) {
        let lands_at = now + self.cfg.throw_lead_ms + self.cfg.flight_ms;
        self.stage = Stage::Flight { from, to, thrown_at: now, lands_at, catch: Catch::Pending };
    }

    /// The target's tap at `now`: in the window a catch, before it a fumble, after it nothing.
    fn tap_counts_as(&self, lands_at: ServerTime, now: ServerTime) -> Option<Catch> {
        if now < lands_at - self.cfg.catch_before_ms - self.cfg.early_tolerance_ms {
            Some(Catch::Fumbled)
        } else if now <= lands_at + self.cfg.catch_after_ms {
            Some(Catch::Caught)
        } else {
            None
        }
    }

    /// The bomb lands by `to`: in their hands if `caught`, else at their feet.
    fn land(&mut self, to: PlayerId, lands_at: ServerTime, caught: bool) {
        let ready_at = if caught { lands_at } else { lands_at + self.cfg.pickup_ms };
        self.stage = Stage::Hold { ask: Ask::open(to, lands_at, None), ready_at, dropped: !caught };
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
        let now = ctx.now;
        match self.stage {
            Stage::Hold { ask, ready_at, dropped: false }
                if player == ask.who() && now >= ready_at - self.cfg.early_tolerance_ms =>
            {
                let Some(&to) = self.targets(ctx.players, player).get(usize::from(index)) else {
                    return Err(ActionError::NotAllowed("Diesen Mitspieler gibt es nicht."));
                };
                self.throw(player, to, now);
            }
            // Only the first tap counts, so mashing fumbles.
            Stage::Flight { from, to, thrown_at, lands_at, catch: Catch::Pending } if player == to => {
                let catch = self.tap_counts_as(lands_at, now).ok_or(ActionError::Ignored)?;
                self.stage = Stage::Flight { from, to, thrown_at, lands_at, catch };
            }
            _ => return Err(ActionError::Ignored),
        }
        Ok(())
    }

    fn on_tick(&mut self, ctx: &mut GameCtx) {
        let now = ctx.now;
        // The fuse comes first: it goes off on whoever the bomb is on, caught or not.
        if now >= self.fuse_at
            && let Some(victim) = self.bearer()
        {
            return self.explode(victim, now);
        }
        match self.stage {
            Stage::Hold { ask, ready_at, .. } if now >= ready_at => {
                self.stage = Stage::Hold { ask, ready_at, dropped: false };
                // A holder who is away has the house throw for them.
                if ask.house_decides(ctx)
                    && let Some(&to) = self.targets(ctx.players, ask.who()).choose(&mut self.rng)
                {
                    self.throw(ask.who(), to, now);
                }
            }
            // A pending catch waits for the window to close; an away target never taps.
            Stage::Flight { to, lands_at, catch, .. }
                if now >= lands_at && (catch != Catch::Pending || now >= lands_at + self.cfg.catch_after_ms) =>
            {
                self.land(to, lands_at, catch == Catch::Caught);
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

    fn seats(&self) -> Option<Vec<PlayerId>> {
        Some(self.lives.keys().copied().collect())
    }

    fn ranking(&self, _players: &[Player]) -> Option<Ranking> {
        (self.stage == Stage::Final).then(|| Ranking::in_order(self.order.iter().copied()))
    }

    fn phone_buzz(&self, _view: &ViewCtx, player: PlayerId) -> Option<Buzz> {
        self.buzz(player)
    }
}

#[cfg(test)]
#[path = "zuendstoff_tests.rs"]
mod tests;
