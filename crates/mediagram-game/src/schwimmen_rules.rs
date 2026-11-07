//! Rules of Schwimmen (31) as plain functions: what a hand is worth, which swap
//! an absent player makes. No state, so each rule is tested without a game.

use crate::cards::{Card, Rank, Suit};

/// Cards in a hand and in the middle.
pub const HAND: usize = 3;
/// Lives each player starts with.
pub const LIVES: u8 = 3;

/// A hand's worth in half points, so 30½ is a whole number and ties compare exactly.
#[derive(Debug, Clone, Copy, PartialEq, Eq, PartialOrd, Ord)]
pub struct Value(pub u8);

/// An absent player knocks with at least this much (27).
pub const KNOCK_AT: Value = Value(54);
/// Three of a kind counts 30½, a little less than 31.
pub const TRIPS: Value = Value(61);
/// 31, the best a hand can be: "Feuer", the round ends at once.
pub const FEUER: Value = Value(62);

impl Value {
    /// "27", "30½", "31".
    pub fn show(self) -> String {
        if self.0.is_multiple_of(2) { format!("{}", self.0 / 2) } else { format!("{}½", self.0 / 2) }
    }
}

fn points(card: Card) -> u8 {
    match card.rank {
        Rank::Ace => 11,
        Rank::Ten | Rank::Jack | Rank::Queen | Rank::King => 10,
        other => Rank::ALL.iter().position(|&r| r == other).map_or(0, |i| i as u8 + 2),
    }
}

/// Three of a kind, else the best suit's sum.
pub fn value(hand: &[Card]) -> Value {
    if hand.len() == HAND && hand.iter().all(|c| c.rank == hand[0].rank) {
        return TRIPS;
    }
    let best = Suit::ALL.iter().map(|&s| hand.iter().filter(|c| c.suit == s).map(|&c| points(c)).sum::<u8>()).max();
    Value(best.unwrap_or(0) * 2)
}

/// A swap of one hand card with one middle card, or of all three.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Swap {
    One { hand: usize, middle: usize },
    All,
}

pub fn apply(hand: &mut [Card], middle: &mut [Card], swap: Swap) {
    match swap {
        Swap::One { hand: h, middle: m } => std::mem::swap(&mut hand[h], &mut middle[m]),
        Swap::All => hand.swap_with_slice(middle),
    }
}

/// The swap that makes the hand worth most, if any beats the hand as it is.
pub fn best_swap(hand: &[Card], middle: &[Card]) -> Option<Swap> {
    let swaps = (0..hand.len()).flat_map(|h| (0..middle.len()).map(move |m| Swap::One { hand: h, middle: m })).chain([Swap::All]);
    let worth = |s: Swap| {
        let (mut h, mut m) = (hand.to_vec(), middle.to_vec());
        apply(&mut h, &mut m, s);
        value(&h)
    };
    swaps.map(|s| (worth(s), s)).filter(|&(v, _)| v > value(hand)).max_by_key(|&(v, _)| v).map(|(_, s)| s)
}

#[cfg(test)]
#[path = "schwimmen_rules_tests.rs"]
mod tests;
