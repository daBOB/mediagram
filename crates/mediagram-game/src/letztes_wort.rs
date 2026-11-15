//! Letztes Wort: one question a round with many right answers, and a hidden clock. Players
//! buzz and answer out loud; the round's host, a different player each round, judges. A
//! right answer takes the stick, a wrong one is out for the round, and whoever holds the
//! stick when the hidden time runs out wins the round.

use std::collections::BTreeSet;

use rand::rngs::StdRng;
use rand::seq::IndexedRandom;
use rand::{Rng, SeedableRng};

pub use crate::letztes_wort_config::{LetztesWortConfig, Prompt};
use crate::game::{ActionError, Game, GameCtx, ViewCtx};
use crate::letztes_wort_config::PROMPTS;
use crate::protocol::{Action, Buzz, PhoneScreen, PlayerId, ServerTime, TvScene};
use crate::room_seats::Player;

#[derive(Debug, Clone, Copy, PartialEq)]
pub(crate) enum Stage {
    /// The buzzers are live from `opens_at` for everyone who may buzz.
    Open { opens_at: ServerTime },
    /// `player` buzzed at `since` and answers out loud; the host decides.
    Floor { player: PlayerId, since: ServerTime },
    /// The round is over; `winner` held the stick (none: nobody did).
    RoundEnd { winner: Option<PlayerId>, ends_at: ServerTime },
    Final,
}

pub struct LetztesWortGame {
    pub(crate) cfg: LetztesWortConfig,
    pub(crate) stage: Stage,
    /// 1-based.
    pub(crate) round: u8,
    pub(crate) rounds: u8,
    /// Who hosts each round, in seat order from a random start; round `r` is `order[(r - 1) % len]`.
    order: Vec<PlayerId>,
    pub(crate) host: PlayerId,
    pub(crate) prompt: &'static Prompt,
    /// This round's right answers: the listed one (none: "Auch richtig") and who gave it.
    pub(crate) given: Vec<(Option<usize>, PlayerId)>,
    pub(crate) holder: Option<PlayerId>,
    /// Out for this round after a wrong answer.
    pub(crate) out: BTreeSet<PlayerId>,
    /// When this round's hidden time runs out; never shown.
    pub(crate) time_up_at: ServerTime,
    /// Buzzes taken this round, so the TV plays each one once.
    pub(crate) buzzes: u16,
    /// When the host took this round over, for the away-host grace.
    since: ServerTime,
    prompts: Vec<&'static Prompt>,
    rng: StdRng,
}

impl LetztesWortGame {
    /// `prompts` are asked last first; when they run out (hosts who left), any prompt fills in.
    pub fn new(cfg: LetztesWortConfig, prompts: Vec<&'static Prompt>, players: &[Player], now: ServerTime) -> Self {
        let mut rng = cfg.seed.map_or_else(StdRng::from_os_rng, StdRng::seed_from_u64);
        let mut order: Vec<PlayerId> = players.iter().map(|p| p.id).collect();
        let start = if order.is_empty() { 0 } else { rng.random_range(0..order.len()) };
        order.rotate_left(start);
        let rounds = (cfg.laps * order.len()).clamp(1, usize::from(u8::MAX)) as u8;
        let mut game = Self {
            cfg,
            stage: Stage::Final,
            round: 1,
            rounds,
            host: order.first().copied().unwrap_or(0),
            order,
            prompt: &PROMPTS[0],
            given: Vec::new(),
            holder: None,
            out: BTreeSet::new(),
            time_up_at: now,
            buzzes: 0,
            since: now,
            prompts,
            rng,
        };
        game.start_round(game.host, now);
        game
    }

    /// Who hosts round `round` (1-based).
    pub(crate) fn host_of(&self, round: u8) -> Option<PlayerId> {
        self.order.get(usize::from(round.checked_sub(1)?) % self.order.len().max(1)).copied()
    }

    /// Who hosts the next round; none after the last.
    pub(crate) fn next_host(&self) -> Option<PlayerId> {
        if self.round < self.rounds { self.host_of(self.round + 1) } else { None }
    }

    /// Whether `player` may buzz now: not hosting, not out, not holding the stick.
    pub(crate) fn may_buzz(&self, player: PlayerId) -> bool {
        player != self.host && !self.out.contains(&player) && self.holder != Some(player)
    }

    /// The listed answer `index` has been given this round.
    pub(crate) fn is_given(&self, index: usize) -> bool {
        self.given.iter().any(|&(a, _)| a == Some(index))
    }

    fn start_round(&mut self, host: PlayerId, now: ServerTime) {
        self.host = host;
        self.since = now;
        self.prompt = self.prompts.pop().unwrap_or_else(|| PROMPTS.choose(&mut self.rng).expect("the bundled pack is never empty"));
        self.given.clear();
        self.holder = None;
        self.out.clear();
        self.buzzes = 0;
        let opens_at = now + self.cfg.intro_ms;
        self.time_up_at = opens_at + self.rng.random_range(self.cfg.time_min_ms..=self.cfg.time_max_ms);
        self.stage = Stage::Open { opens_at };
    }

    fn buzz(&mut self, player: PlayerId, now: ServerTime) -> Result<(), ActionError> {
        let Stage::Open { opens_at } = self.stage else { return Err(ActionError::Ignored) };
        if now < opens_at - self.cfg.early_tolerance_ms || now >= self.time_up_at || !self.may_buzz(player) {
            return Err(ActionError::Ignored);
        }
        self.buzzes += 1;
        self.stage = Stage::Floor { player, since: now };
        Ok(())
    }

    /// The host's verdict: a listed answer, then Falsch, then Auch richtig.
    fn judge(&mut self, ctx: &mut GameCtx, index: u8) -> Result<(), ActionError> {
        let Stage::Floor { player, since } = self.stage else { return Err(ActionError::Ignored) };
        // Nobody judges a spoken answer this fast: it is the late half of a double tap on the last one.
        if ctx.now < since + self.cfg.verdict_gap_ms {
            return Err(ActionError::Ignored);
        }
        let (index, listed) = (usize::from(index), self.prompt.answers.len());
        let right = match index {
            // A repeat is no new answer.
            i if i < listed => (!self.is_given(i)).then_some(Some(i)),
            i if i == listed => None,
            i if i == listed + 1 => Some(None),
            _ => return Err(ActionError::NotAllowed("Diese Antwort gibt es nicht.")),
        };
        match right {
            Some(answer) => {
                self.given.push((answer, player));
                self.holder = Some(player);
            }
            None => {
                self.out.insert(player);
            }
        }
        let now = ctx.now;
        let all_given = (0..listed).all(|i| self.is_given(i));
        let anyone = ctx.players.iter().any(|p| p.connected() && self.may_buzz(p.id));
        if now >= self.time_up_at || all_given || !anyone {
            self.end_round(ctx.players, now);
        } else {
            self.stage = Stage::Open { opens_at: now + self.cfg.verdict_gap_ms };
        }
        Ok(())
    }

    fn end_round(&mut self, players: &mut [Player], now: ServerTime) {
        if let Some(p) = self.holder.and_then(|w| players.iter_mut().find(|p| p.id == w)) {
            p.score += 1;
        }
        self.stage = Stage::RoundEnd { winner: self.holder, ends_at: now + self.cfg.round_end_ms };
    }

    /// The first present player in seat order from `from` (itself included unless `skip`).
    fn present_from(players: &[Player], from: PlayerId, skip: bool) -> Option<PlayerId> {
        let at = players.iter().position(|p| p.id == from)?;
        (usize::from(skip)..players.len()).map(|k| &players[(at + k) % players.len()]).find(|p| p.connected()).map(|p| p.id)
    }
}

impl Game for LetztesWortGame {
    fn on_action(&mut self, ctx: &mut GameCtx, player: PlayerId, action: &Action) -> Result<(), ActionError> {
        let Action::Choose { index, .. } = *action else { return Err(ActionError::Ignored) };
        if player == self.host { self.judge(ctx, index) } else { self.buzz(player, ctx.now) }
    }

    fn on_tick(&mut self, ctx: &mut GameCtx) {
        let now = ctx.now;
        match self.stage {
            Stage::Open { .. } | Stage::Floor { .. } if ctx.house_at(self.host, self.since).is_some_and(|at| now >= at) => {
                // Nobody can judge for an absent host: the next one present takes the round over afresh.
                if let Some(host) = Self::present_from(ctx.players, self.host, true) {
                    self.start_round(host, now);
                }
            }
            // A buzz before time ran out is still judged; only an open round ends on the clock.
            Stage::Open { .. } if now >= self.time_up_at => self.end_round(ctx.players, now),
            Stage::RoundEnd { ends_at, .. } if now >= ends_at => {
                if self.round >= self.rounds {
                    self.stage = Stage::Final;
                } else if let Some(next) = self.next_host() {
                    // A next host who already left is passed over at once, before anyone sees a question.
                    let host = Self::present_from(ctx.players, next, false).unwrap_or(next);
                    self.round += 1;
                    self.start_round(host, now);
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
            Stage::Floor { .. } if player == self.host => Some(Buzz::Turn),
            Stage::Floor { player: p, .. } if p == player => Some(Buzz::Accepted),
            _ => None,
        }
    }
}

#[cfg(test)]
#[path = "letztes_wort_tests.rs"]
pub(crate) mod tests;
