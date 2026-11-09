//! Grand Allee in the game-night room: the engine's rules as a live game
//! played from the phones. A turn: the player taps "Würfeln" (or first buys a
//! leisure wing), the die flies, the pawn walks; passing the Rathaus offers
//! entrances, a guest pays, then the field offers its action. Every decision
//! has a clock; when it runs out, or for an absent player, the house bot
//! decides. A debt auction opens on every other phone at once.

use std::collections::VecDeque;

use rand::rngs::StdRng;
use rand::{Rng, SeedableRng};

use super::bots::{Bot, Style};
use super::live_asks::Ask;
use super::live_debts::Debt;
use super::live_text::Phrase;
use super::live_turn::Step;
use super::rules::{Permit, Rules, Table};
use crate::game::{ActionError, Game, GameCtx, ViewCtx};
use crate::protocol::{Action, PhoneScreen, PlayerId, ServerTime, TvScene};
use crate::room_seats::Player;

/// Decides for an absent player and when a clock runs out.
pub(super) const HOUSE: Bot = Bot { style: Style::Balanced, house: true };

#[derive(Debug, Clone)]
pub struct GrandAlleeConfig {
    pub rules: Rules,
    /// How long a die flies on the TV, and how long it shows after landing.
    pub roll_ms: f64,
    pub show_ms: f64,
    pub hop_ms: f64,
    /// A walk starts this long after it is decided, so the TV can land the first hop.
    pub lead_ms: f64,
    /// How long the outcome of a move or an auction stays up.
    pub note_ms: f64,
    /// A pick may arrive this long before its prompt opened by the host's clock.
    pub early_ms: f64,
    /// A question that follows straight from a tap opens this long after it,
    /// so the second tap of a double tap cannot answer it.
    pub chain_ms: f64,
    /// A decision's clock; then the house decides.
    pub ask_ms: f64,
    pub auction_ms: f64,
    pub seed: Option<u64>,
    /// Dice used before the dice themselves, for tests.
    pub rolls: Vec<u8>,
}

impl Default for GrandAlleeConfig {
    fn default() -> Self {
        Self {
            rules: Rules::default(),
            roll_ms: 1150.0,
            show_ms: 900.0,
            hop_ms: 280.0,
            lead_ms: 200.0,
            note_ms: 1400.0,
            early_ms: 150.0,
            chain_ms: 400.0,
            ask_ms: 20_000.0,
            auction_ms: 15_000.0,
            seed: None,
            rolls: Vec::new(),
        }
    }
}

/// A bidder's answer in a debt auction.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub(crate) enum Bid {
    Open,
    Pass,
    Amount(i32),
}

#[derive(Debug, Clone, PartialEq)]
pub(crate) struct Auction {
    pub hotel: u8,
    pub since: ServerTime,
    /// Every bidder with their answer, nearest after the seller first.
    pub bids: Vec<(usize, Bid)>,
    /// The debt the sale goes toward; `None` when a permit gives the hotel up.
    pub owing: Option<Debt>,
}

#[derive(Debug, Clone, PartialEq)]
pub(crate) enum Stage {
    /// The mover decides, rolling included; `step` is where the move goes on.
    Ask { ask: Ask, since: ServerTime, step: Step },
    Rolled { value: u8, at: ServerTime },
    /// `rathaus`: the walk passed the Rathaus, whose menu comes first.
    Walking { value: u8, from: u8, at: ServerTime, ends_at: ServerTime, bank: bool, rathaus: bool },
    Guest { owner: usize, hotel: u8, nights: u8, amount: i32, at: ServerTime },
    Permit { hotel: u8, count: u8, permit: Permit, at: ServerTime },
    Auction(Auction),
    /// The mover's property went to `winner`, or to the bank for `price`;
    /// then the debt it went toward is settled, or the move ends.
    Sold { hotel: u8, winner: Option<usize>, price: i32, at: ServerTime, owing: Option<Debt> },
    Note { text: Phrase, broke: bool, at: ServerTime },
    Final,
}

/// The dice as they last fell, for the TV.
#[derive(Debug, Clone, Copy, Default)]
pub(crate) struct Dice {
    pub die: Option<u8>,
    pub permit: Option<Permit>,
    pub nights: Option<u8>,
}

pub struct GrandAlleeGame {
    pub(crate) cfg: GrandAlleeConfig,
    pub(crate) t: Table,
    /// The player in each seat.
    pub(crate) ids: Vec<PlayerId>,
    pub(crate) turn: usize,
    pub(crate) round: u32,
    pub(crate) rounds: u32,
    pub(crate) stage: Stage,
    /// The move's last roll was a 6: it rolls again once everything is settled.
    pub(crate) six: bool,
    pub(crate) dice: Dice,
    /// The mover let a clock run out this turn: the house answers the rest at once.
    pub(crate) idle: bool,
    /// Seats in the order they went out.
    pub(crate) gone: Vec<usize>,
    rng: StdRng,
    rolls: VecDeque<u8>,
}

impl GrandAlleeGame {
    /// Seats the players in joining order. The lobby starts this game with two to eight.
    pub fn new(cfg: GrandAlleeConfig, players: &[Player], now: ServerTime) -> Self {
        let ids: Vec<PlayerId> = players.iter().map(|p| p.id).collect();
        let rng = cfg.seed.map_or_else(StdRng::from_os_rng, StdRng::seed_from_u64);
        let rolls = cfg.rolls.iter().copied().collect();
        let (t, rounds) = (Table::new(ids.len(), cfg.rules.clone()), cfg.rules.rounds(ids.len()));
        let (stage, dice) = (Stage::Final, Dice::default());
        let mut g = Self { cfg, t, ids, turn: 0, round: 1, rounds, stage, six: false, dice, idle: false, gone: Vec::new(), rng, rolls };
        g.begin_move(0, now);
        g
    }

    pub(crate) fn die(&mut self) -> u8 {
        self.rolls.pop_front().unwrap_or_else(|| self.rng.random_range(1..=6))
    }
}

impl Game for GrandAlleeGame {
    fn on_action(&mut self, ctx: &mut GameCtx, player: PlayerId, action: &Action) -> Result<(), ActionError> {
        let seat = self.ids.iter().position(|&id| id == player).ok_or(ActionError::Ignored)?;
        let (index, at) = match *action {
            Action::Choose { index, at } => (index, at),
            Action::Bid { amount, at } => return self.bid(seat, amount, at),
            _ => return Err(ActionError::Ignored),
        };
        let Stage::Ask { ask, since, step } = &self.stage else { return Err(ActionError::Ignored) };
        // a tap that began before this prompt appeared answered the one before it
        if seat != self.turn || at < since - self.cfg.early_ms {
            return Err(ActionError::Ignored);
        }
        let opt = *ask.opts.get(usize::from(index)).ok_or(ActionError::NotAllowed("Das gibt es nicht."))?;
        let step = step.clone();
        self.idle = false;
        self.choose(ctx, opt, step);
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

    fn tv_scene(&self, view: &ViewCtx) -> TvScene {
        self.tv_view(view)
    }

    fn is_finished(&self) -> bool {
        self.stage == Stage::Final
    }
}
