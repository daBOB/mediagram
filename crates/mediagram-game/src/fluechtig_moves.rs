//! Which moves Flüchtig's rules allow the fugitive and each hunter, and the
//! one the house plays for an absent mover.

use std::collections::BTreeSet;

use crate::fluechtig_map::{CITY, Field};
use crate::fluechtig_rules::{Chase, Move, Turn};
use crate::protocol::{FluechtigTicket as Ticket, FluechtigTickets as Tickets};

impl Chase {
    /// The legal moves of whoever is on the move.
    pub fn moves(&self) -> Vec<Move> {
        match self.turn {
            Turn::Fugitive => self.fugitive_moves(),
            Turn::Hunter(i) => self.hunter_moves(i),
            Turn::Over(_) => Vec::new(),
        }
    }

    /// A double move's first step must leave a second: one that strands the
    /// fugitive would tell the room his field has no way on.
    pub(crate) fn fugitive_moves(&self) -> Vec<Move> {
        let steps = self.steps_from(self.fugitive.at, self.fugitive.tickets);
        if self.double_left < 2 {
            return steps;
        }
        steps.into_iter().filter(|&m| self.leaves_a_second_step(m)).collect()
    }

    fn leaves_a_second_step(&self, Move { to, ticket }: Move) -> bool {
        let mut left = self.fugitive.tickets;
        *left.count_mut(ticket) -= 1;
        !self.steps_from(to, left).is_empty()
    }

    /// Whether the fugitive may announce a double move now.
    pub fn can_double(&self) -> bool {
        self.turn == Turn::Fugitive
            && self.double_left == 0
            && self.fugitive.tickets.doppel > 0
            && self.steps_from(self.fugitive.at, self.fugitive.tickets).into_iter().any(|m| self.leaves_a_second_step(m))
    }

    fn steps_from(&self, from: Field, tickets: Tickets) -> Vec<Move> {
        let taken = self.hunter_fields();
        let mut out = Vec::new();
        let mut shadow = BTreeSet::new();
        for &(to, kind) in CITY.exits(from) {
            if taken.contains(&to) {
                continue;
            }
            if tickets.count(kind) > 0 {
                out.push(Move { to, ticket: kind });
            }
            if tickets.schatten > 0 && shadow.insert(to) {
                out.push(Move { to, ticket: Ticket::Schatten });
            }
        }
        out
    }

    pub(crate) fn hunter_moves(&self, i: usize) -> Vec<Move> {
        let me = &self.hunters[i];
        let taken = |to: Field| self.hunters.iter().enumerate().any(|(j, h)| j != i && h.at == to);
        CITY.exits(me.at)
            .iter()
            .filter(|&&(to, kind)| me.tickets.count(kind) > 0 && !taken(to))
            .map(|&(to, ticket)| Move { to, ticket })
            .collect()
    }

    pub(crate) fn hunter_fields(&self) -> Vec<Field> {
        self.hunters.iter().map(|h| h.at).collect()
    }

    /// What plays for an absent mover: the fugitive keeps away from hunters and
    /// saves its shadow rides; a hunter searches a possible hideout.
    pub(crate) fn house_move(&self) -> Option<Move> {
        let moves = self.moves();
        let hunters = self.hunter_fields();
        let near = |f: Field| CITY.exits(f).iter().filter(|(to, _)| hunters.contains(to)).count();
        match self.turn {
            Turn::Fugitive => moves.into_iter().min_by_key(|m| (near(m.to), m.ticket == Ticket::Schatten)),
            _ => moves.into_iter().max_by_key(|m| self.hideouts.contains(&m.to)),
        }
    }
}
