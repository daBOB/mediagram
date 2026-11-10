//! Flüchtig's rules, pure: where each piece may go with which ticket, what the
//! room learns from a move, and when the chase ends. No players, no clock.

use std::collections::BTreeSet;

use crate::fluechtig_map::{CITY, FUGITIVE_STARTS, Field};
use crate::protocol::{FluechtigSide as Side, FluechtigTicket as Ticket, FluechtigTickets as Tickets};

#[derive(Debug, Clone, PartialEq)]
pub struct Rules {
    pub rounds: u8,
    /// Rounds whose fugitive move ends in plain sight.
    pub reveal_rounds: Vec<u8>,
    pub hunter_tickets: Tickets,
    pub fugitive_tickets: Tickets,
    /// A ticket a hunter spends goes to the fugitive.
    pub inflow: bool,
}

impl Default for Rules {
    fn default() -> Self {
        Self {
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

    fn count_mut(&mut self, t: Ticket) -> &mut u8 {
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
    pub round: u8,
    pub turn: Turn,
    pub start: Field,
    pub fugitive: Piece,
    /// Steps left of an announced double move: 2 before its first, 1 before its second.
    pub double_left: u8,
    pub hunters: Vec<Piece>,
    pub log: Vec<Entry>,
    /// Every field the fugitive may be on, from public information only.
    pub hideouts: BTreeSet<Field>,
    /// What the last step did, in words for the TV.
    pub news: Vec<String>,
}

impl Chase {
    pub fn new(rules: Rules, fugitive: Field, hunters: &[Field]) -> Self {
        let mut chase = Self {
            round: 1,
            turn: Turn::Fugitive,
            start: fugitive,
            fugitive: Piece { at: fugitive, tickets: rules.fugitive_tickets },
            double_left: 0,
            hunters: hunters.iter().map(|&at| Piece { at, tickets: rules.hunter_tickets }).collect(),
            log: Vec::new(),
            hideouts: FUGITIVE_STARTS.iter().copied().filter(|f| !hunters.contains(f)).collect(),
            news: Vec::new(),
            rules,
        };
        chase.settle();
        chase
    }

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
    fn fugitive_moves(&self) -> Vec<Move> {
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

    fn hunter_moves(&self, i: usize) -> Vec<Move> {
        let me = &self.hunters[i];
        let taken = |to: Field| self.hunters.iter().enumerate().any(|(j, h)| j != i && h.at == to);
        CITY.exits(me.at)
            .iter()
            .filter(|&&(to, kind)| me.tickets.count(kind) > 0 && !taken(to))
            .map(|&(to, ticket)| Move { to, ticket })
            .collect()
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

    pub fn announce_double(&mut self) -> Result<(), &'static str> {
        if !self.can_double() {
            return Err("Ein Doppelzug geht gerade nicht.");
        }
        self.news.clear();
        self.fugitive.tickets.doppel -= 1;
        self.double_left = 2;
        self.news.push("Der Flüchtige sagt einen Doppelzug an: Er fährt zweimal, bevor die Jäger ziehen.".into());
        self.settle();
        Ok(())
    }

    /// Moves whoever is on the move; anything but a legal move is refused unchanged.
    pub fn play(&mut self, mv: Move) -> Result<(), &'static str> {
        if !self.moves().contains(&mv) {
            return Err("Dieser Zug ist nicht erlaubt.");
        }
        self.news.clear();
        match self.turn {
            Turn::Fugitive => self.move_fugitive(mv),
            Turn::Hunter(i) => self.move_hunter(i, mv),
            Turn::Over(_) => unreachable!("an ended chase has no moves"),
        }
        Ok(())
    }

    fn move_fugitive(&mut self, Move { to, ticket }: Move) {
        self.fugitive.at = to;
        *self.fugitive.tickets.count_mut(ticket) -= 1;
        if self.log.last().is_none_or(|e| e.round != self.round) {
            self.log.push(Entry { round: self.round, tickets: Vec::new(), stops: Vec::new(), seen_at: None });
        }
        let entry = self.log.last_mut().expect("pushed above");
        entry.tickets.push(ticket);
        entry.stops.push(to);
        self.hideouts = spread(&self.hideouts, ticket, &self.hunter_fields());
        self.news.push(match ticket {
            Ticket::Schatten => "Der Flüchtige nimmt eine Schattenfahrt: Womit, bleibt geheim.".into(),
            t => format!("Der Flüchtige fährt {}.", by(t)),
        });
        if self.double_left == 2 {
            self.double_left = 1;
        } else {
            self.double_left = 0;
            self.end_fugitive_turn();
        }
        self.settle();
    }

    fn end_fugitive_turn(&mut self) {
        if self.rules.reveal_rounds.contains(&self.round) {
            let at = self.fugitive.at;
            if let Some(entry) = self.log.last_mut() {
                entry.seen_at = Some(at);
            }
            self.hideouts = BTreeSet::from([at]);
            self.news.push(format!("Aufgetaucht! Der Flüchtige wird an Feld {at} gesehen."));
        }
        self.turn = Turn::Hunter(0);
    }

    fn move_hunter(&mut self, i: usize, Move { to, ticket }: Move) {
        let hunter = &mut self.hunters[i];
        hunter.at = to;
        *hunter.tickets.count_mut(ticket) -= 1;
        if self.rules.inflow {
            *self.fugitive.tickets.count_mut(ticket) += 1;
        }
        if to == self.fugitive.at {
            return self.finish(Side::Hunters, format!("Jäger {} fährt {} nach {to} und erwischt den Flüchtigen!", i + 1, by(ticket)));
        }
        let searched = self.hideouts.remove(&to);
        let found = if searched { ": Dort ist der Flüchtige nicht." } else { "." };
        self.news.push(format!("Jäger {} fährt {} nach {to}{found}", i + 1, by(ticket)));
        self.next_hunter();
        self.settle();
    }

    fn next_hunter(&mut self) {
        let Turn::Hunter(i) = self.turn else { return };
        if i + 1 < self.hunters.len() {
            self.turn = Turn::Hunter(i + 1);
        } else if self.round >= self.rules.rounds {
            self.finish(Side::Fugitive, format!("Der Flüchtige hat alle {} Runden überstanden.", self.rules.rounds));
        } else {
            self.round += 1;
            self.turn = Turn::Fugitive;
        }
    }

    fn finish(&mut self, side: Side, reason: String) {
        self.turn = Turn::Over(side);
        self.news.push(reason);
    }

    /// Plays the forced steps: a stuck hunter passes, a stuck fugitive loses,
    /// hunters who all are stuck lose, until someone has a real choice.
    fn settle(&mut self) {
        loop {
            match self.turn {
                // a double move only starts where a second step exists, so this is never its middle
                Turn::Fugitive if self.fugitive_moves().is_empty() => {
                    let at = self.fugitive.at;
                    return self.finish(Side::Hunters, format!("Der Flüchtige an Feld {at} ist eingekesselt: Er kann nirgendwohin."));
                }
                Turn::Hunter(0) if (0..self.hunters.len()).all(|i| self.hunter_moves(i).is_empty()) => {
                    return self.finish(Side::Fugitive, "Kein Jäger kann mehr fahren: Der Flüchtige ist entkommen.".into());
                }
                Turn::Hunter(i) if self.hunter_moves(i).is_empty() => {
                    self.news.push(format!("Jäger {} kann nicht fahren und setzt aus.", i + 1));
                    self.next_hunter();
                }
                _ => {
                    debug_assert!(self.winner().is_some() || self.hideouts.contains(&self.fugitive.at), "hideouts lost the real field");
                    return;
                }
            }
        }
    }

    fn hunter_fields(&self) -> Vec<Field> {
        self.hunters.iter().map(|h| h.at).collect()
    }
}

/// Where the fugitive may be after spending `ticket` from any of `from`.
fn spread(from: &BTreeSet<Field>, ticket: Ticket, blocked: &[Field]) -> BTreeSet<Field> {
    from.iter()
        .flat_map(|&f| CITY.exits(f))
        .filter(|&&(to, kind)| (ticket == Ticket::Schatten || kind == ticket) && !blocked.contains(&to))
        .map(|&(to, _)| to)
        .collect()
}

pub(crate) fn by(t: Ticket) -> &'static str {
    match t {
        Ticket::Taxi => "mit dem Taxi",
        Ticket::Bus => "mit dem Bus",
        Ticket::Ubahn => "mit der U-Bahn",
        Ticket::Schatten => "mit einer Schattenfahrt",
    }
}

#[cfg(test)]
#[path = "fluechtig_rules_tests.rs"]
mod tests;
