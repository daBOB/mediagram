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
use super::rules::{Permit, Rules, Table};
use crate::game::{ActionError, Game, GameCtx, ViewCtx};
use crate::protocol::{Action, PhoneScreen, PlayerId, ServerTime, TvScene};
use crate::room_seats::Player;

/// Decides for an absent player and when a clock runs out.
pub(super) const HOUSE: Bot = Bot { style: Style::Balanced };

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
    /// An absent player's decision happens after this.
    pub auto_ms: f64,
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
            auto_ms: 1500.0,
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
}

#[derive(Debug, Clone, PartialEq)]
pub(crate) enum Stage {
    /// The mover decides, rolling included.
    Ask { ask: Ask, since: ServerTime },
    Rolled { value: u8, at: ServerTime },
    Walking { value: u8, from: u8, at: ServerTime, ends_at: ServerTime, bank: bool },
    Guest { owner: usize, hotel: u8, nights: u8, amount: i32, at: ServerTime },
    Permit { hotel: u8, count: u8, permit: Permit, at: ServerTime },
    Auction(Auction),
    /// The mover's property went to `winner`, or to the bank for `price`.
    Sold { hotel: u8, winner: Option<usize>, price: i32, at: ServerTime },
    Note { text: String, at: ServerTime },
    Final,
}

/// What a move still has to settle after its walk, in the design's order.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub(crate) enum Step {
    Rathaus,
    Guest,
    Field,
    Done,
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
    pub(crate) step: Step,
    /// The move's roll (a 6 rolls again; 0 before the first) and whether it passed the Rathaus.
    pub(crate) roll: u8,
    pub(crate) rathaus: bool,
    /// Hotels that got an entrance on this pass of the Rathaus.
    pub(crate) served: Vec<u8>,
    /// What the mover still owes: to whom (the bank when `None`) and how much.
    pub(crate) debt: Option<(Option<usize>, i32)>,
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
        let (stage, step, dice) = (Stage::Final, Step::Done, Dice::default());
        let mut g = Self { cfg, t, ids, turn: 0, round: 1, rounds, stage, step, roll: 0, rathaus: false, served: Vec::new(), debt: None, dice, idle: false, gone: Vec::new(), rng, rolls };
        let ask = g.roll_ask();
        g.ask(now, ask);
        g
    }

    pub(crate) fn die(&mut self) -> u8 {
        self.rolls.pop_front().unwrap_or_else(|| self.rng.random_range(1..=6))
    }
}

impl Game for GrandAlleeGame {
    fn on_action(&mut self, ctx: &mut GameCtx, player: PlayerId, action: &Action) -> Result<(), ActionError> {
        let Action::Choose { index, at } = *action else {
            return Err(ActionError::Ignored);
        };
        let seat = self.ids.iter().position(|&id| id == player).ok_or(ActionError::Ignored)?;
        match self.stage.clone() {
            Stage::Auction(_) => self.bid(seat, index, at)?,
            // a tap that began before this prompt appeared answered the one before it
            Stage::Ask { ask, since } if seat == self.turn && at >= since - self.cfg.early_ms => {
                self.idle = false;
                let opt = *ask.opts.get(usize::from(index)).ok_or(ActionError::NotAllowed("Das gibt es nicht."))?;
                self.choose(ctx, opt);
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
