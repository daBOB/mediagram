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
use crate::fluechtig_intro::Intro;
use crate::fluechtig_map::Field;
use crate::fluechtig_rules::{Chase, Rules, Turn};
use crate::game::{ActionError, Game, GameCtx, ViewCtx, name};
use crate::protocol::{Action, FluechtigTicket as Ticket, PhoneScreen, PlayerId, ServerTime, TvScene};
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

pub struct FluechtigGame {
    pub(crate) cfg: FluechtigConfig,
    pub(crate) chase: Chase,
    pub(crate) fugitive: PlayerId,
    /// Who moves each hunter figure, in turn order.
    pub(crate) owners: Vec<PlayerId>,
    pub(crate) ask: Ask,
    /// The rules card on the TV; None once the chase runs.
    pub(crate) intro: Option<Intro>,
}

impl FluechtigGame {
    /// One player at random runs; every other player hunts with one figure, a
    /// lone hunter (two players) with two. The lobby starts this with two to six.
    pub fn new(cfg: FluechtigConfig, players: &[Player], now: ServerTime) -> Self {
        let mut rng = cfg.seed.map_or_else(StdRng::from_os_rng, StdRng::seed_from_u64);
        let ids: Vec<PlayerId> = players.iter().map(|p| p.id).collect();
        let fugitive = ids[rng.random_range(0..ids.len())];
        let mut hunters: Vec<PlayerId> = ids.iter().copied().filter(|&id| id != fugitive).collect();
        if hunters.is_empty() {
            // never from the lobby; a lone player hunting themself beats a crashed host
            hunters.push(fugitive);
        }
        let figures = hunters.len().clamp(2, 5);
        let rules = cfg.rules.clone().unwrap_or_else(|| Rules::big_for(figures as u8));
        let city = rules.city.city();
        let owners = (0..figures).map(|i| hunters[i % hunters.len()]).collect();
        let mut starts = city.hunter_starts.clone();
        starts.shuffle(&mut rng);
        let start = city.fugitive_starts[rng.random_range(0..city.fugitive_starts.len())];
        let chase = Chase::new(rules, start, &starts[..figures]);
        let intro = (cfg.intro_ms > 0.0).then_some(Intro { card: 0, at: now });
        let mut game = Self { cfg, chase, fugitive, owners, ask: Ask { opens_at: now, step: Step::Field }, intro };
        game.chase.names = (0..figures)
            .map(|i| {
                let player = name(players, game.owners[i]);
                game.figure(i).map_or_else(|| player.to_string(), |k| format!("{player} (Figur {k})"))
            })
            .collect();
        game
    }

    /// The player whose phone holds the move.
    pub(crate) fn mover(&self) -> Option<PlayerId> {
        match self.chase.turn {
            Turn::Fugitive => Some(self.fugitive),
            Turn::Hunter(i) => Some(self.owners[i]),
            Turn::Over(_) => None,
        }
    }
}

impl Game for FluechtigGame {
    fn on_action(&mut self, ctx: &mut GameCtx, player: PlayerId, action: &Action) -> Result<(), ActionError> {
        let Action::Choose { index, at } = *action else {
            return Err(ActionError::Ignored);
        };
        if self.intro.is_some() {
            return self.skip_intro(ctx, player);
        }
        if self.mover() != Some(player) || at < self.ask.opens_at - self.cfg.early_ms {
            return Err(ActionError::Ignored);
        }
        let pick = *self.picks().get(usize::from(index)).ok_or(ActionError::Ignored)?;
        self.take(ctx.players, ctx.now, pick);
        Ok(())
    }

    fn on_tick(&mut self, ctx: &mut GameCtx) {
        if let Some(Intro { at, .. }) = self.intro {
            if ctx.now >= at + self.cfg.intro_ms {
                self.next_card(ctx.now);
            }
            return;
        }
        let Some(id) = self.mover() else { return };
        if !ctx.house_at(id, self.ask.opens_at).is_some_and(|at| ctx.now >= at) {
            return;
        }
        if let Some(mv) = self.chase.house_move() {
            self.ask.step = Step::Ticket(mv.to);
            self.take(ctx.players, ctx.now, Pick::Ride(mv.ticket));
        }
    }

    fn phone_screen(&self, view: &ViewCtx, player: PlayerId) -> PhoneScreen {
        self.phone_view(view, player)
    }

    fn tv_scene(&self, view: &ViewCtx) -> TvScene {
        self.tv_view(view)
    }

    fn is_finished(&self) -> bool {
        self.chase.winner().is_some()
    }
}

#[cfg(test)]
#[path = "fluechtig_tests.rs"]
pub(crate) mod tests;
