//! Bauherr, the hotel-building board game: the turn flow around the rules in
//! `bauherr_rules`.
//!
//! A turn: the player taps "Würfeln", the die flies, the pawn hops along the
//! ring, and the field offers something on the phone: buy a plot, build (the
//! permit die decides, then the player picks how far), place an entrance. A
//! field with another player's entrance asks a nights die first and the guest
//! pays. An absent player's turns play themselves.

use std::collections::VecDeque;

use rand::rngs::StdRng;
use rand::{Rng, SeedableRng};

use crate::bauherr_asks::Ask;
use crate::bauherr_rules::{Table, rounds_for};
use crate::game::{ActionError, Game, GameCtx, ViewCtx};
use crate::protocol::{Action, BauherrPermit, PhoneScreen, PlayerId, ServerTime, TvScene};
use crate::room_seats::Player;

#[derive(Debug, Clone)]
pub struct BauherrConfig {
    /// How long a die flies on the TV.
    pub roll_ms: f64,
    /// Pause after a die lands before the game goes on.
    pub show_ms: f64,
    pub hop_ms: f64,
    /// A walk starts this long after it is decided, so the TV can land the first hop's knock.
    pub lead_ms: f64,
    /// How long the outcome of a turn stays up.
    pub note_ms: f64,
    /// An absent player's roll or pick happens after this.
    pub auto_ms: f64,
    /// A pick may arrive this long before its prompt opened by the host's clock.
    pub early_ms: f64,
    pub seed: Option<u64>,
    /// Rolls used before the die itself, for tests.
    pub rolls: Vec<u8>,
}

impl Default for BauherrConfig {
    fn default() -> Self {
        Self {
            roll_ms: 1150.0,
            show_ms: 900.0,
            hop_ms: 280.0,
            lead_ms: 200.0,
            note_ms: 1400.0,
            auto_ms: 1500.0,
            early_ms: 150.0,
            seed: None,
            rolls: Vec::new(),
        }
    }
}

#[derive(Debug, Clone, PartialEq)]
pub(crate) enum Stage {
    Roll { since: ServerTime },
    Rolled { value: u8, at: ServerTime },
    Walking { value: u8, from: u8, at: ServerTime, ends_at: ServerTime, bonus: bool },
    Guest { owner: usize, plot: u8, nights: u8, amount: i32, at: ServerTime },
    Permit { plot: u8, outcome: BauherrPermit, at: ServerTime },
    Ask { ask: Ask, since: ServerTime, permit: BauherrPermit },
    Note { text: String, at: ServerTime },
    Final,
}

pub struct BauherrGame {
    pub(crate) cfg: BauherrConfig,
    pub(crate) table: Table,
    /// Seat whose turn it is.
    pub(crate) turn: usize,
    pub(crate) round: u8,
    pub(crate) rounds: u8,
    pub(crate) stage: Stage,
    rng: StdRng,
    rolls: VecDeque<u8>,
}

pub(crate) fn name(players: &[Player], id: PlayerId) -> String {
    players.iter().find(|p| p.id == id).map_or_else(|| "Jemand".into(), |p| p.name.clone())
}

/// 12000 as "12.000".
pub(crate) fn grouped(n: i32) -> String {
    let digits = n.abs().to_string();
    let groups: Vec<String> = digits.as_bytes().rchunks(3).rev().map(|c| String::from_utf8_lossy(c).into_owned()).collect();
    format!("{}{}", if n < 0 { "-" } else { "" }, groups.join("."))
}

pub(crate) fn taler(n: i32) -> String {
    format!("{} Taler", grouped(n))
}

impl BauherrGame {
    /// Seats the players in joining order. The lobby only starts this game with two to four.
    pub fn new(cfg: BauherrConfig, players: &[Player], now: ServerTime) -> Self {
        let ids: Vec<PlayerId> = players.iter().map(|p| p.id).collect();
        let rng = cfg.seed.map_or_else(StdRng::from_os_rng, StdRng::seed_from_u64);
        let rolls = cfg.rolls.iter().copied().collect();
        Self {
            cfg,
            table: Table::new(&ids),
            turn: 0,
            round: 1,
            rounds: rounds_for(ids.len()),
            stage: Stage::Roll { since: now },
            rng,
            rolls,
        }
    }

    pub(crate) fn player_at(&self, seat: usize) -> PlayerId {
        self.table.seats[seat].player
    }

    pub(crate) fn lands_at(&self, at: ServerTime) -> ServerTime {
        at + self.cfg.roll_ms
    }

    pub(crate) fn die(&mut self) -> u8 {
        self.rolls.pop_front().unwrap_or_else(|| self.rng.random_range(1..=6))
    }

    pub(crate) fn sync_scores(&self, players: &mut [Player]) {
        for (s, seat) in self.table.seats.iter().enumerate() {
            if let Some(p) = players.iter_mut().find(|p| p.id == seat.player) {
                p.score = self.table.worth(s);
            }
        }
    }
}

impl Game for BauherrGame {
    fn on_action(&mut self, ctx: &mut GameCtx, player: PlayerId, action: &Action) -> Result<(), ActionError> {
        let Action::Choose { index, at } = *action else {
            return Err(ActionError::Ignored);
        };
        if player != self.player_at(self.turn) {
            return Err(ActionError::Ignored);
        }
        match self.stage.clone() {
            Stage::Roll { since } if index == 0 && at >= since - self.cfg.early_ms => {
                self.stage = Stage::Rolled { value: self.die(), at: ctx.now };
            }
            // a tap that began before this prompt appeared answered the one before it
            Stage::Ask { ask, since, permit } if at >= since - self.cfg.early_ms => {
                let opt = *ask.opts.get(usize::from(index)).ok_or(ActionError::NotAllowed("Das gibt es nicht."))?;
                self.choose(ctx, opt, permit);
            }
            _ => return Err(ActionError::Ignored),
        }
        self.sync_scores(ctx.players);
        Ok(())
    }

    fn on_tick(&mut self, ctx: &mut GameCtx) {
        self.tick(ctx);
        self.sync_scores(ctx.players);
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
#[path = "bauherr_tests.rs"]
mod tests;
