//! Flüchtig's rules, pure: where each piece may go with which ticket, what the
//! room learns from a move, and when the chase ends. No players, no clock.
//! The legal moves are in `fluechtig_moves`, playing one in `fluechtig_play`.

use std::collections::BTreeSet;

use crate::fluechtig_map::{City, CityKind, Field};
use crate::protocol::{FluechtigSide as Side, FluechtigTicket as Ticket, FluechtigTickets as Tickets};

#[derive(Debug, Clone, PartialEq)]
pub struct Rules {
    pub city: CityKind,
    pub rounds: u8,
    /// Rounds whose fugitive move ends in plain sight.
    pub reveal_rounds: Vec<u8>,
    pub hunter_tickets: Tickets,
    pub fugitive_tickets: Tickets,
    /// A ticket a hunter spends goes to the fugitive.
    pub inflow: bool,
}

impl Rules {
    /// The big city for a table hunting with `figures` (clamped to 2..=5).
    /// Fewer figures see the fugitive more often and grant fewer shadow rides
    /// and double moves, which keeps the chase near even: bots won 43.0 / 45.6
    /// / 48.7 / 49.5 % as the fugitive with 5 / 4 / 3 / 2 figures on the
    /// bridged city. Five figures play the genre classic's numbers.
    pub fn big_for(figures: u8) -> Self {
        let figures = figures.clamp(2, 5);
        let (reveal_rounds, schatten, doppel) = match figures {
            5 => (vec![3, 8, 13, 18, 24], 5, 2),
            4 => ((3..=24).step_by(3).collect(), 5, 2),
            3 => ((3..=24).step_by(3).collect(), 2, 1),
            _ => ((3..=24).step_by(3).collect(), 1, 0),
        };
        Self {
            city: CityKind::Big,
            rounds: 24,
            reveal_rounds,
            hunter_tickets: Tickets { taxi: 10, bus: 8, ubahn: 4, schatten: 0, doppel: 0 },
            fugitive_tickets: Tickets { taxi: 4, bus: 3, ubahn: 3, schatten, doppel },
            inflow: true,
        }
    }

    /// The short chase in the small city, for tests.
    #[cfg_attr(not(test), allow(dead_code))]
    pub fn small() -> Self {
        Self {
            city: CityKind::Small,
            rounds: 18,
            reveal_rounds: vec![3, 8, 13, 18],
            hunter_tickets: Tickets { taxi: 10, bus: 6, ubahn: 3, schatten: 0, doppel: 0 },
            fugitive_tickets: Tickets { taxi: 4, bus: 3, ubahn: 2, schatten: 3, doppel: 1 },
            inflow: true,
        }
    }
}

impl Tickets {
    pub fn count(&self, t: Ticket) -> u8 {
        match t {
            Ticket::Taxi => self.taxi,
            Ticket::Bus => self.bus,
            Ticket::Ubahn => self.ubahn,
            Ticket::Schatten => self.schatten,
        }
    }

    pub(crate) fn count_mut(&mut self, t: Ticket) -> &mut u8 {
        match t {
            Ticket::Taxi => &mut self.taxi,
            Ticket::Bus => &mut self.bus,
            Ticket::Ubahn => &mut self.ubahn,
            Ticket::Schatten => &mut self.schatten,
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub(crate) struct Move {
    pub to: Field,
    pub ticket: Ticket,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub(crate) enum Turn {
    Fugitive,
    Hunter(usize),
    Over(Side),
}

#[derive(Debug, Clone, PartialEq)]
pub(crate) struct Piece {
    pub at: Field,
    pub tickets: Tickets,
}

#[derive(Debug, Clone, PartialEq)]
pub(crate) struct Entry {
    pub round: u8,
    pub tickets: Vec<Ticket>,
    /// Where each ticket took the fugitive: secret until the chase ends.
    pub stops: Vec<Field>,
    pub seen_at: Option<Field>,
}

#[derive(Debug, Clone)]
pub(crate) struct Chase {
    pub rules: Rules,
    pub city: &'static City,
    pub round: u8,
    pub turn: Turn,
    pub start: Field,
    pub fugitive: Piece,
    /// Steps left of an announced double move: 2 before its first, 1 before its second.
    pub double_left: u8,
    pub hunters: Vec<Piece>,
    /// What the news calls each hunter figure.
    pub names: Vec<String>,
    pub log: Vec<Entry>,
    /// Every field the fugitive may be on, from public information only.
    pub hideouts: BTreeSet<Field>,
    /// What the last step did, in words for the TV.
    pub news: Vec<String>,
}

impl Chase {
    pub fn new(rules: Rules, fugitive: Field, hunters: &[Field]) -> Self {
        let city = rules.city.city();
        let mut chase = Self {
            city,
            round: 1,
            turn: Turn::Fugitive,
            start: fugitive,
            fugitive: Piece { at: fugitive, tickets: rules.fugitive_tickets },
            double_left: 0,
            hunters: hunters.iter().map(|&at| Piece { at, tickets: rules.hunter_tickets }).collect(),
            names: (1..=hunters.len()).map(|i| format!("Jäger {i}")).collect(),
            log: Vec::new(),
            hideouts: city.fugitive_starts.iter().copied().filter(|f| !hunters.contains(f)).collect(),
            news: Vec::new(),
            rules,
        };
        chase.settle();
        chase
    }

    pub fn winner(&self) -> Option<Side> {
        match self.turn {
            Turn::Over(side) => Some(side),
            _ => None,
        }
    }

    /// The next round in which the fugitive will be seen, if any is left.
    pub fn next_reveal(&self) -> Option<u8> {
        let pending = |r: u8| r > self.round || (r == self.round && self.turn == Turn::Fugitive);
        self.rules.reveal_rounds.iter().copied().find(|&r| pending(r))
    }

    /// The fugitive's real route, start first.
    pub fn route(&self) -> Vec<Field> {
        std::iter::once(self.start).chain(self.log.iter().flat_map(|e| e.stops.iter().copied())).collect()
    }
}

#[cfg(test)]
#[path = "fluechtig_rules_tests.rs"]
mod tests;
