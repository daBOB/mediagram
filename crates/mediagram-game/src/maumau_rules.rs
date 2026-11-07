//! Rules of Mau-Mau as plain functions: which cards may be played, what the
//! special cards do, how the players rank. No state, so each rule is tested
//! without a game.

use crate::cards::{Card, Rank, Suit};

/// Cards dealt to each player.
pub const DEAL: usize = 5;
/// A seven makes the next player draw this many (stacking adds up).
pub const SEVEN_DRAWS: u8 = 2;

/// What the next card must match: the top discard, and the suit that counts.
/// `suit` is the card's own, a wished suit after a jack, or `None` when a jack
/// was turned up to start (anything goes).
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Top {
    pub card: Card,
    pub suit: Option<Suit>,
}

impl Top {
    #[cfg(test)]
    pub fn plain(card: Card) -> Self {
        Self { card, suit: Some(card.suit) }
    }
}

/// 32 cards for two to five players, the full 52 for six to eight.
pub fn lowest_rank(players: usize) -> Rank {
    if players <= 5 { Rank::Seven } else { Rank::Two }
}

/// While sevens are owed only a seven may be played. Otherwise a jack goes on
/// anything, and any other card must match the suit or the rank.
pub fn playable(top: Top, owed: u8, card: Card) -> bool {
    if owed > 0 {
        return card.rank == Rank::Seven;
    }
    card.rank == Rank::Jack || card.rank == top.card.rank || top.suit.is_none_or(|s| s == card.suit)
}

/// Sevens, eights and jacks do something; the others just pass the turn.
pub fn is_special(card: Card) -> bool {
    matches!(card.rank, Rank::Seven | Rank::Eight | Rank::Jack)
}

/// Value of a card left in hand when someone else goes out.
pub fn points(card: Card) -> u32 {
    match card.rank {
        Rank::Ace => 11,
        Rank::Ten => 10,
        Rank::Nine => 9,
        Rank::Eight => 8,
        Rank::Seven => 7,
        Rank::King => 4,
        Rank::Queen => 3,
        Rank::Jack => 2,
        _ => 0,
    }
}

/// Hands are kept in suit and rank order, which is also the order the phone shows.
pub fn sort_hand(hand: &mut [Card]) {
    hand.sort();
}

/// The suit held most often (ties go to the first in suit order); hearts for an empty hand.
pub fn most_held_suit(hand: &[Card]) -> Suit {
    if hand.is_empty() {
        return Suit::Hearts;
    }
    Suit::ALL.iter().copied().rev().max_by_key(|&s| hand.iter().filter(|c| c.suit == s).count()).unwrap_or(Suit::Hearts)
}

/// What an absent player plays: the first legal card, one without an effect before one with.
pub fn auto_pick(top: Top, owed: u8, hand: &[Card]) -> Option<usize> {
    let mut legal: Vec<usize> = (0..hand.len()).filter(|&i| playable(top, owed, hand[i])).collect();
    legal.sort_by_key(|&i| is_special(hand[i]));
    legal.first().copied()
}

/// Seat indices from first to last: the winner, then the fewest cards left,
/// then the fewest points in hand, then seating order.
pub fn ranking(winner: usize, hands: &[Vec<Card>]) -> Vec<usize> {
    let mut rest: Vec<usize> = (0..hands.len()).filter(|&i| i != winner).collect();
    rest.sort_by_key(|&i| (hands[i].len(), hands[i].iter().map(|&c| points(c)).sum::<u32>(), i));
    std::iter::once(winner).chain(rest).collect()
}

#[cfg(test)]
#[path = "maumau_rules_tests.rs"]
mod tests;
