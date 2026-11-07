//! Where everyone sits in Durak: seats, the table, whose move it is, and the
//! events and banner the views read.

use crate::cards::{Card, Suit};
use crate::durak::{DurakGame, Stage};
use crate::protocol::{CardsBanner, CardsEvent, CardsEventKind, PlayerId, ServerTime};
use crate::room_seats::Player;

impl DurakGame {
    pub(crate) fn trump_suit(&self) -> Suit {
        self.trump.suit
    }

    pub(crate) fn player_at(&self, seat: usize) -> PlayerId {
        self.ids[seat]
    }

    pub(crate) fn seat_of(&self, player: PlayerId) -> Option<usize> {
        self.ids.iter().position(|&p| p == player)
    }

    /// The first seat after `from` that is still in the game.
    pub(crate) fn next_active(&self, from: usize) -> usize {
        let n = self.ids.len();
        (1..=n).map(|k| (from + k) % n).find(|s| !self.out.contains(s)).expect("two players are still in")
    }

    pub(crate) fn defender(&self) -> usize {
        self.next_active(self.attacker)
    }

    pub(crate) fn thrower_seat(&self) -> usize {
        (self.attacker + self.thrower) % self.ids.len()
    }

    /// Every card lying on the table, attack and defence.
    pub(crate) fn table(&self) -> Vec<Card> {
        self.bout.iter().flat_map(|&(a, d)| std::iter::once(a).chain(d)).collect()
    }

    /// The seat that has to act now, if the stage waits for a player.
    pub(crate) fn actor(&self) -> Option<usize> {
        match self.stage {
            Stage::Attack { .. } => Some(self.attacker),
            Stage::Defend { .. } => Some(self.defender()),
            Stage::Throw { .. } => Some(self.thrower_seat()),
            Stage::Settle { .. } | Stage::Final { .. } => None,
        }
    }

    pub(crate) fn since(&self) -> Option<ServerTime> {
        match self.stage {
            Stage::Attack { since } | Stage::Defend { since } | Stage::Throw { since, .. } | Stage::Settle { since, .. } => Some(since),
            Stage::Final { .. } => None,
        }
    }

    pub(crate) fn event(&mut self, now: ServerTime, player: PlayerId, kind: CardsEventKind) {
        self.events.push(CardsEvent { id: self.next_event, at: now, player, kind });
        self.next_event += 1;
        if self.events.len() > 6 {
            self.events.remove(0);
        }
    }

    pub(crate) fn say(&mut self, now: ServerTime, player: PlayerId, text: impl Into<String>) {
        self.banner = Some((CardsBanner { player, text: text.into() }, now));
    }

    /// When `seat` dropped out, if they are gone.
    pub(crate) fn absent_since(&self, players: &[Player], seat: usize) -> Option<ServerTime> {
        let p = players.iter().find(|p| p.id == self.player_at(seat))?;
        (!p.connected()).then(|| p.left_at.unwrap_or(0.0))
    }
}
