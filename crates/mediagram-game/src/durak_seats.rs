//! Whose move it is in Durak: the attacker, the defender, the thrower, and the
//! cards on the table between them.

use crate::ask::Ask;
use crate::cards::{Card, Suit};
use crate::durak::{DurakGame, Stage};

impl DurakGame {
    pub(crate) fn trump_suit(&self) -> Suit {
        self.trump.suit
    }

    /// The first seat after `from` that is still in the game.
    pub(crate) fn next_active(&self, from: usize) -> usize {
        let n = self.table.players().len();
        (1..=n).map(|k| (from + k) % n).find(|s| !self.out.contains(s)).expect("two players are still in")
    }

    pub(crate) fn defender(&self) -> usize {
        self.next_active(self.attacker)
    }

    pub(crate) fn thrower_seat(&self) -> usize {
        (self.attacker + self.thrower) % self.table.players().len()
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

    /// What the seat that has to act is being asked; nothing while the table settles or once the game is over.
    pub(crate) fn open_ask(&self) -> Option<Ask> {
        match self.stage {
            Stage::Attack { ask } | Stage::Defend { ask } | Stage::Throw { ask, .. } => Some(ask),
            Stage::Settle { .. } | Stage::Final { .. } => None,
        }
    }
}
