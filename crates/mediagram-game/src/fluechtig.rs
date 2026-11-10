//! Flüchtig: one player runs unseen through the city, the others hunt them.
//! The seating and turn flow around the rules in `fluechtig_rules`.
//!
//! A move starts on the mover's phone with where to. A second question asks
//! which ticket, with "Zurück": always for the fugitive, for a hunter only
//! when more than one gets there. The fugitive's phone is the only screen that
//! ever shows their field before the chase ends. An absent mover's move is
//! played by the house after the table's grace.

use rand::rngs::StdRng;
use rand::seq::SliceRandom;
use rand::{Rng, SeedableRng};

pub use crate::fluechtig_config::FluechtigConfig;
use crate::fluechtig_map::{CITY, FUGITIVE_STARTS, Field, HUNTER_STARTS};
use crate::fluechtig_rules::{Chase, Move, Turn};
use crate::game::{ActionError, Game, GameCtx, ViewCtx};
use crate::protocol::{Action, FluechtigSide as Side, FluechtigTicket as Ticket, PhoneScreen, PlayerId, ServerTime, TvScene};
use crate::room_seats::Player;

/// The question open on the mover's phone, from `opens_at`.
#[derive(Debug, Clone, Copy, PartialEq)]
pub(crate) struct Ask {
    pub opens_at: ServerTime,
    pub step: Step,
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub(crate) enum Step {
    Field,
    /// Which ticket to this field.
    Ticket(Field),
}

/// One button on the mover's phone.
#[derive(Debug, Clone, Copy, PartialEq)]
pub(crate) enum Pick {
    Go(Field),
    Double,
    Ride(Ticket),
    Back,
}

const TICKETS: [Ticket; 4] = [Ticket::Taxi, Ticket::Bus, Ticket::Ubahn, Ticket::Schatten];

pub struct FluechtigGame {
    pub(crate) cfg: FluechtigConfig,
    pub(crate) chase: Chase,
    pub(crate) fugitive: PlayerId,
    /// Who moves each hunter piece, in turn order.
    pub(crate) owners: Vec<PlayerId>,
    pub(crate) ask: Ask,
}

impl FluechtigGame {
    /// One player at random runs; four hunter pieces (five at six players) are
    /// dealt round-robin to the others. The lobby starts this with two to six.
    pub fn new(cfg: FluechtigConfig, players: &[Player], now: ServerTime) -> Self {
        let mut rng = cfg.seed.map_or_else(StdRng::from_os_rng, StdRng::seed_from_u64);
        let ids: Vec<PlayerId> = players.iter().map(|p| p.id).collect();
        let fugitive = ids[rng.random_range(0..ids.len())];
        let mut hunters: Vec<PlayerId> = ids.iter().copied().filter(|&id| id != fugitive).collect();
        if hunters.is_empty() {
            // never from the lobby; a lone player hunting themself beats a crashed host
            hunters.push(fugitive);
        }
        let pieces = if ids.len() >= 6 { 5 } else { 4 };
        let owners = (0..pieces).map(|i| hunters[i % hunters.len()]).collect();
        let mut starts = HUNTER_STARTS;
        starts.shuffle(&mut rng);
        let start = FUGITIVE_STARTS[rng.random_range(0..FUGITIVE_STARTS.len())];
        let chase = Chase::new(cfg.rules.clone(), start, &starts[..pieces]);
        Self { cfg, chase, fugitive, owners, ask: Ask { opens_at: now, step: Step::Field } }
    }

    /// The player whose phone holds the move.
    pub(crate) fn mover(&self) -> Option<PlayerId> {
        match self.chase.turn {
            Turn::Fugitive => Some(self.fugitive),
            Turn::Hunter(i) => Some(self.owners[i]),
            Turn::Over(_) => None,
        }
    }

    /// The buttons of the open question, in order.
    pub(crate) fn picks(&self) -> Vec<Pick> {
        match self.ask.step {
            Step::Field => {
                let mut to: Vec<Field> = self.chase.moves().iter().map(|m| m.to).collect();
                to.sort_unstable();
                to.dedup();
                let mut out: Vec<Pick> = to.into_iter().map(Pick::Go).collect();
                if self.chase.can_double() {
                    out.push(Pick::Double);
                }
                out
            }
            Step::Ticket(to) => self.ways(to).into_iter().map(Pick::Ride).chain([Pick::Back]).collect(),
        }
    }

    /// The mover's tickets that reach `to`, in the plan's order.
    pub(crate) fn ways(&self, to: Field) -> Vec<Ticket> {
        let moves = self.chase.moves();
        TICKETS.into_iter().filter(|&t| moves.contains(&Move { to, ticket: t })).collect()
    }

    fn take(&mut self, players: &mut [Player], now: ServerTime, pick: Pick) {
        let later = now + self.cfg.chain_ms;
        let next = |step| Ask { opens_at: later, step };
        let ride = match pick {
            // a hunter with one ticket to the field moves at once; the fugitive is
            // always asked, or one tap or two would tell the table which line they ride
            Pick::Go(to) => match self.ways(to)[..] {
                [ticket] if self.chase.turn != Turn::Fugitive => Move { to, ticket },
                _ => {
                    self.ask = next(Step::Ticket(to));
                    return;
                }
            },
            Pick::Ride(ticket) => {
                let Step::Ticket(to) = self.ask.step else { return };
                Move { to, ticket }
            }
            Pick::Back => {
                self.ask = next(Step::Field);
                return;
            }
            Pick::Double => {
                if self.chase.announce_double().is_ok() {
                    self.ask = next(Step::Field);
                }
                return;
            }
        };
        if self.chase.play(ride).is_ok() {
            self.ask = next(Step::Field);
            self.score(players);
        }
    }

    /// Winners score 1, everyone else 0, for the awards ceremony.
    fn score(&self, players: &mut [Player]) {
        let Some(side) = self.chase.winner() else { return };
        for p in players {
            let won = match side {
                Side::Fugitive => p.id == self.fugitive,
                Side::Hunters => p.id != self.fugitive && self.owners.contains(&p.id),
            };
            p.score = i32::from(won);
        }
    }

    /// What plays for an absent mover: the fugitive keeps away from hunters and
    /// saves its shadow rides; a hunter searches a possible hideout.
    fn auto_move(&self) -> Option<Move> {
        let moves = self.chase.moves();
        let hunters: Vec<Field> = self.chase.hunters.iter().map(|h| h.at).collect();
        let near = |f: Field| CITY.exits(f).iter().filter(|(to, _)| hunters.contains(to)).count();
        match self.chase.turn {
            Turn::Fugitive => moves.into_iter().min_by_key(|m| (near(m.to), m.ticket == Ticket::Schatten)),
            _ => moves.into_iter().max_by_key(|m| self.chase.hideouts.contains(&m.to)),
        }
    }
}

impl Game for FluechtigGame {
    fn on_action(&mut self, ctx: &mut GameCtx, player: PlayerId, action: &Action) -> Result<(), ActionError> {
        let Action::Choose { index, at } = *action else {
            return Err(ActionError::Ignored);
        };
        if self.mover() != Some(player) || at < self.ask.opens_at - self.cfg.early_ms {
            return Err(ActionError::Ignored);
        }
        let pick = *self.picks().get(usize::from(index)).ok_or(ActionError::Ignored)?;
        self.take(ctx.players, ctx.now, pick);
        Ok(())
    }

    fn on_tick(&mut self, ctx: &mut GameCtx) {
        let Some(id) = self.mover() else { return };
        if !ctx.house_at(id, self.ask.opens_at).is_some_and(|at| ctx.now >= at) {
            return;
        }
        if let Some(mv) = self.auto_move() {
            self.ask.step = Step::Ticket(mv.to);
            self.take(ctx.players, ctx.now, Pick::Ride(mv.ticket));
        }
    }

    fn phone_screen(&self, view: &ViewCtx, player: PlayerId) -> PhoneScreen {
        self.phone_view(view, player)
    }

    fn tv_scene(&self, _view: &ViewCtx) -> TvScene {
        self.tv_view()
    }

    fn is_finished(&self) -> bool {
        self.chase.winner().is_some()
    }
}

#[cfg(test)]
#[path = "fluechtig_tests.rs"]
mod tests;
