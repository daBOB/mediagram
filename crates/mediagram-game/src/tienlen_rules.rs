//! Rules of Tiến Lên as plain functions: how the cards rank, which cards make
//! a combination, and what beats what. No state, so each rule is tested
//! without a game.
//!
//! Twos are high and threes low; within a rank hearts beat diamonds, clubs and
//! spades, in that order. A play is beaten only by a higher one of the same
//! kind and length, judged by its highest card. Twos alone also fall to bombs.

use crate::cards::{Card, Suit};

/// Cards dealt to each player at every table size; the rest of the pack stays out.
pub const DEAL: usize = 13;

/// The rank value of a two, the highest.
const TWO: u8 = 12;

/// A card's place among all 52, lowest first: rank counts before suit.
pub fn strength(card: Card) -> (u8, u8) {
    let suit = match card.suit {
        Suit::Spades => 0,
        Suit::Clubs => 1,
        Suit::Diamonds => 2,
        Suit::Hearts => 3,
    };
    // the two moves from the bottom of the pack's own order to the top
    ((card.rank as u8 + TWO) % 13, suit)
}

/// Hands lie lowest card first, so pairs and runs sit side by side on the phone.
pub fn sort_hand(hand: &mut [Card]) {
    hand.sort_by_key(|&c| strength(c));
}

/// The seat holding the lowest card dealt, and that card: it leads the game.
pub fn opener(hands: &[Vec<Card>]) -> Option<(usize, Card)> {
    hands.iter().enumerate().flat_map(|(seat, h)| h.iter().map(move |&c| (seat, c))).min_by_key(|&(_, c)| strength(c))
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Kind {
    Single,
    Pair,
    Triple,
    Quad,
    /// Three or more cards of consecutive rank, suits mixed.
    Run,
    /// Three or more pairs of consecutive rank.
    PairRun,
}

/// A legal play: its kind, how many cards it holds and its highest card.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Combo {
    pub kind: Kind,
    pub len: usize,
    pub high: (u8, u8),
}

impl Combo {
    /// One, two or three twos: the plays a bomb beats.
    pub fn twos(self) -> bool {
        self.high.0 == TWO && matches!(self.kind, Kind::Single | Kind::Pair | Kind::Triple)
    }

    /// The fewest cards of a bomb on these twos: four of a kind on one, else two pairs more than twos.
    pub fn bomb_len(self) -> usize {
        if self.len == 1 { 4 } else { 2 * (self.len + 2) }
    }

    /// Four of a kind or a run of pairs: the only play left to someone who passed.
    pub fn bomb(self) -> bool {
        matches!(self.kind, Kind::Quad | Kind::PairRun)
    }
}

/// The combination `cards` make, in any order; `None` when they make none.
/// A run may end on the two but never goes round the corner: A-2-3 is none.
pub fn combo(cards: &[Card]) -> Option<Combo> {
    let mut s: Vec<(u8, u8)> = cards.iter().map(|&c| strength(c)).collect();
    s.sort_unstable();
    let (len, high) = (s.len(), *s.last()?);
    let next = |a: usize, b: usize| s[b].0 == s[a].0 + 1;
    let kind = if len <= 4 && s[0].0 == high.0 {
        [Kind::Single, Kind::Pair, Kind::Triple, Kind::Quad][len - 1]
    } else if len >= 3 && (1..len).all(|i| next(i - 1, i)) {
        Kind::Run
    } else if len >= 6 && len % 2 == 0 && (0..len).step_by(2).all(|i| s[i].0 == s[i + 1].0 && (i == 0 || next(i - 2, i))) {
        Kind::PairRun
    } else {
        return None;
    };
    Some(Combo { kind, len, high })
}

/// Whether `play` beats `top`: the same kind and length with a higher top card.
/// Twos alone also fall to bombs: one two to four of a kind or three pairs in
/// a row, a pair of twos to four pairs in a row, three twos to five.
pub fn beats(top: Combo, play: Combo) -> bool {
    if play.kind == top.kind && play.len == top.len {
        return play.high > top.high;
    }
    top.twos()
        && match play.kind {
            Kind::Quad => top.len == 1,
            Kind::PairRun => play.len == 2 * (top.len + 2),
            _ => false,
        }
}

#[cfg(test)]
#[path = "tienlen_rules_tests.rs"]
mod tests;
