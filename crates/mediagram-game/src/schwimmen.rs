//! Schwimmen (31): the turn flow around the rules in `schwimmen_rules`.
//!
//! A turn: swap one hand card with one middle card, swap all three, pass, or
//! knock (everyone else then has one more turn before the reveal). If every
//! player passes in a row the middle is replaced from the deck. A hand worth 31
//! ends the round at once. The house plays for a player who is gone. Lives
//! and the reveal are in `schwimmen_round`.

use rand::SeedableRng;
use rand::rngs::StdRng;

use crate::cards::{Card, Rank, deck_from, shuffled};
use crate::game::{ActionError, Game, GameCtx, ViewCtx};
use crate::maumau_rules::sort_hand;
use crate::protocol::{Action, CardsBanner, CardsEvent, CardsEventKind, PhoneScreen, PlayerId, ServerTime, TvScene};
use crate::room_seats::Player;
pub use crate::schwimmen_config::SchwimmenConfig;
use crate::schwimmen_rules::{FEUER, HAND, KNOCK_AT, LIVES, Swap, best_swap, value};

#[derive(Debug, Clone, PartialEq)]
pub(crate) enum Stage {
    Turn { since: ServerTime },
    /// All hands are face up; the next round follows.
    Reveal { since: ServerTime },
    Final { ranking: Vec<usize> },
}

pub struct SchwimmenGame {
    pub(crate) cfg: SchwimmenConfig,
    pub(crate) ids: Vec<PlayerId>,
    /// In suit and rank order, which is also the order the phone shows.
    pub(crate) hands: Vec<Vec<Card>>,
    pub(crate) middle: Vec<Card>,
    pub(crate) deck: Vec<Card>,
    /// Lives left; 0 means the player "schwimmt" and is out with the next loss.
    pub(crate) lives: Vec<u8>,
    pub(crate) out: Vec<bool>,
    /// Seats in the order they went out.
    pub(crate) out_order: Vec<usize>,
    /// Who lost a life (or went out) in the round just revealed.
    pub(crate) lost: Vec<bool>,
    pub(crate) revealed: bool,
    pub(crate) starter: usize,
    pub(crate) turn: usize,
    pub(crate) knocker: Option<usize>,
    /// Passes in a row since the last swap or refresh.
    pub(crate) passes: usize,
    pub(crate) stage: Stage,
    pub(crate) events: Vec<CardsEvent>,
    pub(crate) banner: Option<(CardsBanner, ServerTime)>,
    next_event: u32,
    pub(crate) scored: bool,
    pub(crate) rng: StdRng,
}

impl SchwimmenGame {
    pub fn new(cfg: SchwimmenConfig, players: &[Player], now: ServerTime) -> Self {
        let n = players.len();
        let rng = cfg.seed.map_or_else(StdRng::from_os_rng, StdRng::seed_from_u64);
        let mut game = Self {
            cfg,
            ids: players.iter().map(|p| p.id).collect(),
            hands: vec![vec![]; n],
            middle: vec![],
            deck: vec![],
            lives: vec![LIVES; n],
            out: vec![false; n],
            out_order: vec![],
            lost: vec![false; n],
            revealed: false,
            starter: 0,
            turn: 0,
            knocker: None,
            passes: 0,
            stage: Stage::Turn { since: now },
            events: vec![],
            banner: None,
            next_event: 0,
            scored: false,
            rng,
        };
        game.deal(now);
        game
    }

    pub(crate) fn player_at(&self, seat: usize) -> PlayerId {
        self.ids[seat]
    }

    pub(crate) fn seat_of(&self, player: PlayerId) -> Option<usize> {
        self.ids.iter().position(|&p| p == player)
    }

    pub(crate) fn alive(&self) -> Vec<usize> {
        (0..self.ids.len()).filter(|&s| !self.out[s]).collect()
    }

    /// The next player still in the game after `seat`.
    pub(crate) fn after(&self, seat: usize) -> usize {
        let n = self.ids.len();
        (1..=n).map(|k| (seat + k) % n).find(|&s| !self.out[s]).unwrap_or(seat)
    }

    pub(crate) fn say(&mut self, now: ServerTime, player: PlayerId, text: impl Into<String>) {
        self.banner = Some((CardsBanner { player, text: text.into() }, now));
    }

    pub(crate) fn event(&mut self, now: ServerTime, player: PlayerId, kind: CardsEventKind) {
        self.events.push(CardsEvent { id: self.next_event, at: now, player, kind });
        self.next_event += 1;
        if self.events.len() > 4 {
            self.events.remove(0);
        }
    }

    /// A fresh deck: three cards to everyone still in, three face up in the middle.
    pub(crate) fn deal(&mut self, now: ServerTime) {
        self.deck = shuffled(deck_from(Rank::Seven), &mut self.rng);
        for seat in 0..self.ids.len() {
            self.hands[seat] = if self.out[seat] { vec![] } else { self.deck.split_off(self.deck.len() - HAND) };
            sort_hand(&mut self.hands[seat]);
        }
        self.middle = self.deck.split_off(self.deck.len() - HAND);
        self.lost.fill(false);
        self.revealed = false;
        self.knocker = None;
        self.passes = 0;
        self.turn = self.starter;
        self.stage = Stage::Turn { since: now };
        if self.alive().into_iter().any(|s| value(&self.hands[s]) == FEUER) {
            self.reveal_round(now);
        }
    }
}

impl Game for SchwimmenGame {
    fn on_action(&mut self, ctx: &mut GameCtx, player: PlayerId, action: &Action) -> Result<(), ActionError> {
        let Action::Cards { button, hand, table } = action else {
            return Err(ActionError::Ignored);
        };
        if !matches!(self.stage, Stage::Turn { .. }) || self.seat_of(player) != Some(self.turn) {
            return Err(ActionError::Ignored);
        }
        match button.as_str() {
            "swap" => {
                let swap = self.pick_swap(hand, *table)?;
                self.swap(ctx.now, swap);
            }
            "swap_all" => self.swap(ctx.now, Swap::All),
            "pass" => self.pass(ctx.now),
            "knock" if self.knocker.is_some() => return Err(ActionError::NotAllowed("Es wurde schon geklopft.")),
            "knock" => self.knock(ctx.now),
            _ => return Err(ActionError::Ignored),
        }
        self.settle(ctx);
        Ok(())
    }

    fn on_tick(&mut self, ctx: &mut GameCtx) {
        match self.stage {
            Stage::Reveal { since } if ctx.now >= since + self.cfg.reveal_ms => {
                self.starter = self.after(self.starter);
                self.deal(ctx.now);
            }
            Stage::Turn { since } => {
                if !ctx.house_at(self.player_at(self.turn), since).is_some_and(|at| ctx.now >= at) {
                    return;
                }
                let hand = &self.hands[self.turn];
                if self.knocker.is_none() && value(hand) >= KNOCK_AT {
                    self.knock(ctx.now);
                } else if let Some(swap) = best_swap(hand, &self.middle) {
                    self.swap(ctx.now, swap);
                } else {
                    self.pass(ctx.now);
                }
            }
            _ => {}
        }
        self.settle(ctx);
    }

    fn phone_screen(&self, view: &ViewCtx, player: PlayerId) -> PhoneScreen {
        self.phone_view(view, player)
    }

    fn tv_scene(&self, view: &ViewCtx) -> TvScene {
        self.tv_view(view)
    }

    fn is_finished(&self) -> bool {
        matches!(self.stage, Stage::Final { .. })
    }
}

#[cfg(test)]
#[path = "schwimmen_tests.rs"]
mod tests;
