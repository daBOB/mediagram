//! Playing a move in Flüchtig: the fugitive's steps and double moves, the
//! hunters' searches, what the TV says about each, and the forced steps after.

use std::collections::BTreeSet;

use crate::fluechtig_map::{City, Field};
use crate::fluechtig_rules::{Chase, Entry, Move, Turn};
use crate::protocol::{FluechtigSide as Side, FluechtigTicket as Ticket};

impl Chase {
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
        self.hideouts = spread(self.city, &self.hideouts, ticket, &self.hunter_fields());
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
            return self.finish(Side::Hunters, format!("{} fährt {} nach {to} und erwischt den Flüchtigen!", self.names[i], by(ticket)));
        }
        let searched = self.hideouts.remove(&to);
        let found = if searched { ": Dort ist der Flüchtige nicht." } else { "." };
        self.news.push(format!("{} fährt {} nach {to}{found}", self.names[i], by(ticket)));
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
    pub(crate) fn settle(&mut self) {
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
                    self.news.push(format!("{} kann nicht fahren und setzt aus.", self.names[i]));
                    self.next_hunter();
                }
                _ => {
                    debug_assert!(self.winner().is_some() || self.hideouts.contains(&self.fugitive.at), "hideouts lost the real field");
                    return;
                }
            }
        }
    }
}

/// Where the fugitive may be after spending `ticket` from any of `from`.
fn spread(city: &City, from: &BTreeSet<Field>, ticket: Ticket, blocked: &[Field]) -> BTreeSet<Field> {
    from.iter()
        .flat_map(|&f| city.exits(f))
        .filter(|&&(to, kind)| (ticket == Ticket::Schatten || kind == ticket) && !blocked.contains(&to))
        .map(|&(to, _)| to)
        .collect()
}

fn by(t: Ticket) -> &'static str {
    match t {
        Ticket::Taxi => "mit dem Taxi",
        Ticket::Bus => "mit dem Bus",
        Ticket::Ubahn => "mit der U-Bahn",
        Ticket::Schatten => "mit einer Schattenfahrt",
    }
}
