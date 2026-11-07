//! Rules of Durak as plain functions: what beats what, what may be thrown in,
//! what an absent player does. No state, so each rule is tested without a game.

use crate::cards::{Card, Rank, Suit};

/// Hands are refilled to this many cards.
pub const DEAL: usize = 6;
/// Cards the attackers may lead against one defender in one bout.
pub const MAX_ATTACKS: usize = 6;

/// A card beats another of its suit that is lower, or any trump when the
/// attack is not a trump (a trump is beaten only by a higher trump).
pub fn beats(trump: Suit, attack: Card, defence: Card) -> bool {
    if defence.suit == attack.suit { defence.rank > attack.rank } else { defence.suit == trump }
}

/// A card may be added when its rank is already on the table, as attack or defence.
pub fn can_throw(table: &[Card], card: Card) -> bool {
    table.iter().any(|c| c.rank == card.rank)
}

/// At most six attacks, and never more than the defender held when the bout began.
pub fn attack_limit(defender_hand: usize) -> usize {
    defender_hand.min(MAX_ATTACKS)
}

/// Cheapest first: every plain card before any trump, then by rank.
fn cost(trump: Suit, card: Card) -> (bool, Rank) {
    (card.suit == trump, card.rank)
}

/// What an absent player leads: their cheapest card.
pub fn auto_attack(trump: Suit, hand: &[Card]) -> Option<usize> {
    (0..hand.len()).min_by_key(|&i| cost(trump, hand[i]))
}

/// What an absent defender beats `attack` with: the cheapest card that does.
pub fn auto_defence(trump: Suit, attack: Card, hand: &[Card]) -> Option<usize> {
    (0..hand.len()).filter(|&i| beats(trump, attack, hand[i])).min_by_key(|&i| cost(trump, hand[i]))
}

/// The seat holding the lowest trump leads the first bout; seat 0 when nobody holds one.
pub fn first_attacker(trump: Suit, hands: &[Vec<Card>]) -> usize {
    (0..hands.len())
        .filter_map(|i| hands[i].iter().filter(|c| c.suit == trump).map(|c| c.rank).min().map(|r| (r, i)))
        .min()
        .map_or(0, |(_, i)| i)
}

#[cfg(test)]
#[path = "durak_rules_tests.rs"]
mod tests;
