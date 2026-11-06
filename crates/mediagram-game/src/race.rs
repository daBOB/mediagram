//! Race game (Heimlauf): the turn flow around the rules in `race_rules`.
//!
//! A turn: the player taps "Würfeln", the die flies on the TV for `roll_ms`,
//! then the player picks one of the legal moves on the phone. A single legal
//! move plays itself, none passes. A 6 rolls again; with nothing movable the
//! player gets three tries for a 6. An absent player's turns play themselves.

use std::collections::VecDeque;

use rand::rngs::StdRng;
use rand::{Rng, SeedableRng};

use crate::game::{ActionError, Game, GameCtx, ViewCtx};
use crate::protocol::{Action, PhoneScreen, PlayerId, ServerTime, TvScene};
use crate::race_rules::{Move, Seat, TRACK, apply, corners, legal_moves};
use crate::room_seats::Player;

#[derive(Debug, Clone)]
pub struct RaceConfig {
    /// How long the die flies on the TV.
    pub roll_ms: f64,
    /// Pause after the die lands before a single move plays itself.
    pub show_ms: f64,
    /// Pause after the die lands when no move is possible.
    pub pass_ms: f64,
    pub hop_ms: f64,
    /// A move starts this long after it is decided, so the TV hears of it in
    /// time to land the first hop's knock through the box's slow audio output
    /// (~370 ms from scheduling to speaker).
    pub lead_ms: f64,
    /// Extra time for a captured piece to fly home.
    pub capture_ms: f64,
    /// An absent player's roll or pick happens after this.
    pub auto_ms: f64,
    /// A pick may arrive this long before the die lands by the host's clock:
    /// the phone opens its buttons at its own estimate of that moment.
    pub early_ms: f64,
    pub seed: Option<u64>,
    /// Rolls used before the die itself, for tests.
    pub rolls: Vec<u8>,
}

impl Default for RaceConfig {
    fn default() -> Self {
        Self {
            roll_ms: 1150.0,
            show_ms: 800.0,
            pass_ms: 1600.0,
            hop_ms: 280.0,
            lead_ms: 200.0,
            capture_ms: 700.0,
            auto_ms: 1500.0,
            early_ms: 300.0,
            seed: None,
            rolls: Vec::new(),
        }
    }
}

#[derive(Debug, Clone, PartialEq)]
pub(crate) enum Stage {
    /// Waiting for the roll. `tries`: rolls left this turn (three when only a 6 helps).
    Roll { since: ServerTime, tries: u8, again: bool },
    Rolled { value: u8, at: ServerTime, moves: Vec<Move>, tries: u8 },
    Moving { value: u8, mv: Move, at: ServerTime, ends_at: ServerTime },
    Final { winner: usize },
}

pub struct RaceGame {
    pub(crate) cfg: RaceConfig,
    pub(crate) seats: Vec<Seat>,
    /// Seat index whose turn it is.
    pub(crate) turn: usize,
    pub(crate) stage: Stage,
    pub(crate) die: Option<u8>,
    rng: StdRng,
    rolls: VecDeque<u8>,
}

impl RaceGame {
    /// Seats the players in board corners, in joining order. The lobby only
    /// starts this game with two to four players.
    pub fn new(cfg: RaceConfig, players: &[Player], now: ServerTime) -> Self {
        let seats = players.iter().zip(corners(players.len())).map(|(p, &c)| Seat::new(p.id, c)).collect();
        let rng = cfg.seed.map_or_else(StdRng::from_os_rng, StdRng::seed_from_u64);
        let rolls = cfg.rolls.iter().copied().collect();
        let mut game = Self { cfg, seats, turn: 0, stage: Stage::Final { winner: 0 }, die: None, rng, rolls };
        game.start_turn(now, 0, false);
        game
    }

    pub(crate) fn player_at(&self, seat: usize) -> PlayerId {
        self.seats[seat].player
    }

    pub(crate) fn lands_at(&self, at: ServerTime) -> ServerTime {
        at + self.cfg.roll_ms
    }

    fn start_turn(&mut self, now: ServerTime, seat: usize, again: bool) {
        self.turn = seat;
        let tries = if self.seats[seat].three_tries() { 3 } else { 1 };
        self.stage = Stage::Roll { since: now, tries, again };
    }

    fn roll(&mut self, now: ServerTime, tries: u8) {
        let value = self.rolls.pop_front().unwrap_or_else(|| self.rng.random_range(1..=6));
        self.die = Some(value);
        let moves = legal_moves(&self.seats, self.turn, value);
        self.stage = Stage::Rolled { value, at: now, moves, tries };
    }

    fn play(&mut self, ctx: &mut GameCtx, value: u8, mv: Move) {
        apply(&mut self.seats, self.turn, &mv);
        for seat in &self.seats {
            if let Some(p) = ctx.players.iter_mut().find(|p| p.id == seat.player) {
                p.score = seat.in_goal() as i32;
            }
        }
        let hops = mv.from.map_or(1, |from| mv.to - from);
        let capture = if mv.captures.is_some() { self.cfg.capture_ms } else { 0.0 };
        let at = ctx.now + self.cfg.lead_ms;
        let ends_at = at + f64::from(hops) * self.cfg.hop_ms + capture;
        self.stage = Stage::Moving { value, mv, at, ends_at };
    }

    fn end_turn(&mut self, now: ServerTime, value: u8) {
        if self.seats[self.turn].finished() {
            self.stage = Stage::Final { winner: self.turn };
        } else if value == 6 {
            self.start_turn(now, self.turn, true);
        } else {
            self.start_turn(now, (self.turn + 1) % self.seats.len(), false);
        }
    }

    /// When the player whose turn it is dropped out, if they are gone. Their turn
    /// plays itself `auto_ms` after the later of this and the prompt, so a phone
    /// that blips after a long think does not have a move made for it at once.
    fn absent_since(&self, players: &[Player]) -> Option<ServerTime> {
        let id = self.player_at(self.turn);
        let p = players.iter().find(|p| p.id == id)?;
        (!p.connected()).then(|| p.left_at.unwrap_or(0.0))
    }
}

/// What plays for an absent player: a capture, else into the goal, else the piece furthest along.
fn auto_pick(moves: &[Move]) -> Move {
    *moves
        .iter()
        .max_by_key(|m| (m.captures.is_some(), m.to >= TRACK, m.from.map_or(-1, i16::from)))
        .expect("only called with moves to pick from")
}

impl Game for RaceGame {
    fn on_action(&mut self, ctx: &mut GameCtx, player: PlayerId, action: &Action) -> Result<(), ActionError> {
        let Action::Choose { index, .. } = *action else {
            return Err(ActionError::Ignored);
        };
        if player != self.player_at(self.turn) {
            return Err(ActionError::Ignored);
        }
        match self.stage.clone() {
            Stage::Roll { tries, .. } if index == 0 => self.roll(ctx.now, tries),
            Stage::Rolled { value, at, moves, .. }
                if moves.len() > 1 && ctx.now >= self.lands_at(at) - self.cfg.early_ms =>
            {
                let mv = *moves.get(usize::from(index)).ok_or(ActionError::NotAllowed("Diesen Zug gibt es nicht."))?;
                self.play(ctx, value, mv);
            }
            _ => return Err(ActionError::Ignored),
        }
        Ok(())
    }

    fn on_tick(&mut self, ctx: &mut GameCtx) {
        let now = ctx.now;
        let auto_after = |from: ServerTime| self.absent_since(ctx.players).map(|left| left.max(from) + self.cfg.auto_ms);
        match self.stage.clone() {
            Stage::Roll { since, tries, .. } if auto_after(since).is_some_and(|at| now >= at) => {
                self.roll(now, tries);
            }
            Stage::Rolled { value, at, moves, tries } => {
                let landed = self.lands_at(at);
                match moves.len() {
                    0 if now >= landed + self.cfg.pass_ms => {
                        if tries > 1 && value != 6 {
                            self.stage = Stage::Roll { since: now, tries: tries - 1, again: false };
                        } else {
                            self.end_turn(now, value);
                        }
                    }
                    1 if now >= landed + self.cfg.show_ms => self.play(ctx, value, moves[0]),
                    n if n > 1 && auto_after(landed).is_some_and(|at| now >= at) => {
                        self.play(ctx, value, auto_pick(&moves));
                    }
                    _ => {}
                }
            }
            Stage::Moving { value, ends_at, .. } if now >= ends_at => self.end_turn(now, value),
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
        matches!(self.stage, Stage::Final { .. })
    }
}

#[cfg(test)]
#[path = "race_tests.rs"]
mod tests;
