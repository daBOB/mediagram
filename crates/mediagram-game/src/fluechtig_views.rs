//! What the phones and the TV show during Flüchtig. Only the fugitive's own
//! phone carries their field; the TV gets it on a reveal and at the end.

use crate::fluechtig::{FluechtigGame, Pick, Step};
use crate::fluechtig_map::{City, Field};
use crate::fluechtig_rules::Turn;
use crate::game::{ViewCtx, name};
use crate::protocol::{
    FluechtigAsk, FluechtigBoard, FluechtigHunter, FluechtigLink, FluechtigLogEntry, FluechtigMover, FluechtigOption, FluechtigPhone,
    FluechtigPiece, FluechtigScene, FluechtigSeen, FluechtigSide as Side, FluechtigSpot, FluechtigTicket as Ticket, FluechtigTurn,
    PhoneScreen, PlayerId, TvScene,
};

/// A field's strongest line, which the plan draws as its ring; `Schatten` rings a ferry pier.
fn ring(city: &City, field: Field) -> Ticket {
    let exits = city.exits(field);
    [Ticket::Ubahn, Ticket::Bus, Ticket::Schatten].into_iter().find(|&k| exits.iter().any(|&(_, t)| t == k)).unwrap_or(Ticket::Taxi)
}

impl FluechtigGame {
    pub(crate) fn phone_view(&self, view: &ViewCtx, player: PlayerId) -> PhoneScreen {
        let c = &self.chase;
        let runs = player == self.fugitive;
        let mine: Vec<usize> = (0..self.owners.len()).filter(|&i| self.owners[i] == player).collect();
        if !runs && mine.is_empty() {
            return PhoneScreen::Wait { title: "Flüchtig läuft".into(), text: Some("Du spielst in der nächsten Runde mit.".into()) };
        }
        if let Some(side) = c.winner() {
            let mut hunters = self.owners.clone();
            hunters.sort_unstable();
            hunters.dedup();
            let won = (side == Side::Fugitive) == runs;
            let winners = if side == Side::Fugitive { 1 } else { hunters.len() as u8 };
            return PhoneScreen::Final {
                rank: if won { 1 } else { winners + 1 },
                of: 1 + hunters.len() as u8,
                score: i32::from(won),
                can_restart: view.leader == Some(player),
            };
        }
        let phone = FluechtigPhone {
            round: c.round,
            rounds: c.rules.rounds,
            next_reveal: c.next_reveal(),
            fugitive: name(view.players, self.fugitive).into(),
            hiding: runs.then_some(c.fugitive.at),
            seen: c.log.iter().rev().find_map(|e| e.seen_at.map(|field| FluechtigSeen { field, round: e.round })),
            hideouts: c.hideouts.iter().copied().collect(),
            pieces: mine.iter().map(|&i| FluechtigPiece { number: i as u8 + 1, at: c.hunters[i].at, tickets: c.hunters[i].tickets }).collect(),
            fugitive_tickets: c.fugitive.tickets,
            log: self.log(),
            mover: self.who(view, player, c.turn),
            next: self.who(view, player, self.after()),
            // only the mover gets the question and its clock: the others would be
            // re-sent on every tap of the fugitive's, and learn when they hesitate
            ask: (self.mover() == Some(player)).then(|| self.question()),
        };
        PhoneScreen::Fluechtig { phone }
    }

    /// The player behind `turn`, as a phone names them.
    fn who(&self, view: &ViewCtx, me: PlayerId, turn: Turn) -> Option<FluechtigMover> {
        let (id, piece) = match turn {
            Turn::Fugitive => (self.fugitive, None),
            Turn::Hunter(i) => (self.owners[i], Some(i as u8 + 1)),
            Turn::Over(_) => return None,
        };
        let color = view.players.iter().find(|p| p.id == id).map_or("#888888", |p| p.color);
        Some(FluechtigMover { name: name(view.players, id).into(), color: color.into(), piece, you: id == me })
    }

    /// Whose turn comes next, as far as the table can tell now.
    fn after(&self) -> Turn {
        match self.chase.turn {
            Turn::Fugitive if self.chase.double_left == 2 => Turn::Fugitive,
            Turn::Fugitive => Turn::Hunter(0),
            Turn::Hunter(i) if i + 1 < self.owners.len() => Turn::Hunter(i + 1),
            Turn::Hunter(_) => Turn::Fugitive,
            over => over,
        }
    }

    fn question(&self) -> FluechtigAsk {
        let c = &self.chase;
        let (at, tickets) = match c.turn {
            Turn::Hunter(i) => (c.hunters[i].at, c.hunters[i].tickets),
            _ => (c.fugitive.at, c.fugitive.tickets),
        };
        let options = self
            .picks()
            .into_iter()
            .map(|pick| match pick {
                Pick::Go(field) => FluechtigOption::Field { field, ring: ring(self.chase.city, field), ways: self.ways(field) },
                Pick::Ride(ticket) => FluechtigOption::Ticket { ticket, left: tickets.count(ticket) },
                Pick::Double => FluechtigOption::Double { left: tickets.doppel },
                Pick::Back => FluechtigOption::Back,
            })
            .collect();
        let seen_after = c.turn == Turn::Fugitive && c.double_left != 2 && c.rules.reveal_rounds.contains(&c.round);
        FluechtigAsk { opens_at: self.ask.opens_at, prompt: self.prompt(), at, tickets, seen_after, options }
    }

    fn prompt(&self) -> String {
        let c = &self.chase;
        match (self.ask.step, c.turn) {
            (Step::Ticket(to), _) => format!("Nach Feld {to} – womit?"),
            (Step::Field, Turn::Hunter(i)) => format!("Wohin fährt Jäger {}?", i + 1),
            (Step::Field, _) => match c.double_left {
                2 => "Doppelzug, 1. Fahrt: wohin?",
                1 => "Doppelzug, 2. Fahrt: wohin?",
                _ => "Wohin fährst du?",
            }
            .into(),
        }
    }

    fn log(&self) -> Vec<FluechtigLogEntry> {
        self.chase.log.iter().map(|e| FluechtigLogEntry { round: e.round, tickets: e.tickets.clone(), seen_at: e.seen_at }).collect()
    }

    pub(crate) fn tv_view(&self) -> TvScene {
        let c = &self.chase;
        let turn = match c.turn {
            Turn::Fugitive => FluechtigTurn::Fugitive { double_left: c.double_left },
            Turn::Hunter(i) => FluechtigTurn::Hunter { index: i as u8 },
            Turn::Over(winner) => FluechtigTurn::Over { winner, reason: c.news.last().cloned().unwrap_or_default() },
        };
        let mut targets: Vec<u8> = match c.turn {
            Turn::Hunter(_) => c.moves().iter().map(|m| m.to).collect(),
            _ => Vec::new(),
        };
        targets.sort_unstable();
        targets.dedup();
        let scene = FluechtigScene {
            board: FluechtigBoard {
                width: c.city.size.0,
                height: c.city.size.1,
                spots: c.city.spots.iter().map(|&(x, y)| FluechtigSpot { x, y }).collect(),
                links: c.city.links.iter().map(|l| FluechtigLink { a: l.a, b: l.b, kind: l.kind }).collect(),
                river: c.city.river.iter().map(|&(x, y)| FluechtigSpot { x, y }).collect(),
            },
            round: c.round,
            rounds: c.rules.rounds,
            reveal_rounds: c.rules.reveal_rounds.clone(),
            next_reveal: c.next_reveal(),
            fugitive: self.fugitive,
            hunters: c.hunters.iter().zip(&self.owners).map(|(h, &player)| FluechtigHunter { player, at: h.at, tickets: h.tickets }).collect(),
            fugitive_tickets: c.fugitive.tickets,
            turn,
            targets,
            log: self.log(),
            hideouts: c.hideouts.iter().copied().collect(),
            news: c.news.clone(),
            route: c.winner().map(|_| c.route()),
        };
        TvScene::Fluechtig { scene }
    }
}
