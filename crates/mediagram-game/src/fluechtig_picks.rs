//! Flüchtig's question on the mover's phone: its buttons, and what a tap on
//! one does, up to the move played and, once the chase ends, the scores.

use crate::fluechtig::{Ask, FluechtigGame, Pick, Step};
use crate::fluechtig_map::Field;
use crate::fluechtig_rules::{Move, Turn};
use crate::protocol::{FluechtigSide as Side, FluechtigTicket as Ticket, ServerTime};
use crate::room_seats::Player;

const TICKETS: [Ticket; 4] = [Ticket::Taxi, Ticket::Bus, Ticket::Ubahn, Ticket::Schatten];

impl FluechtigGame {
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

    pub(crate) fn take(&mut self, players: &mut [Player], now: ServerTime, pick: Pick) {
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
}
