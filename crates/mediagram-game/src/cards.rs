//! Playing cards: suits, ranks and a seeded deck. Pure data, no game rules.

use rand::Rng;
use rand::seq::SliceRandom;
use serde::{Deserialize, Serialize};
use ts_rs::TS;

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, PartialOrd, Ord, Serialize, Deserialize, TS)]
#[serde(rename_all = "snake_case")]
#[ts(export)]
pub enum Suit {
    Clubs,
    Spades,
    Hearts,
    Diamonds,
}

impl Suit {
    pub const ALL: [Suit; 4] = [Self::Clubs, Self::Spades, Self::Hearts, Self::Diamonds];

    /// The German suit name (Kreuz, Pik, Herz, Karo).
    pub fn name(self) -> &'static str {
        match self {
            Self::Clubs => "Kreuz",
            Self::Spades => "Pik",
            Self::Hearts => "Herz",
            Self::Diamonds => "Karo",
        }
    }

    pub fn symbol(self) -> char {
        match self {
            Self::Clubs => '♣',
            Self::Spades => '♠',
            Self::Hearts => '♥',
            Self::Diamonds => '♦',
        }
    }
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, PartialOrd, Ord, Serialize, Deserialize, TS)]
#[serde(rename_all = "snake_case")]
#[ts(export)]
pub enum Rank {
    Two,
    Three,
    Four,
    Five,
    Six,
    Seven,
    Eight,
    Nine,
    Ten,
    Jack,
    Queen,
    King,
    Ace,
}

impl Rank {
    pub const ALL: [Rank; 13] = [
        Self::Two,
        Self::Three,
        Self::Four,
        Self::Five,
        Self::Six,
        Self::Seven,
        Self::Eight,
        Self::Nine,
        Self::Ten,
        Self::Jack,
        Self::Queen,
        Self::King,
        Self::Ace,
    ];
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Hash, PartialOrd, Ord, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct Card {
    pub suit: Suit,
    pub rank: Rank,
}

impl Card {
    pub fn new(suit: Suit, rank: Rank) -> Self {
        Self { suit, rank }
    }
}

/// Every card from `lowest` up to the ace, in suit order: 7 gives the 32-card
/// deck, 6 the 36-card one, 2 the full 52.
pub fn deck_from(lowest: Rank) -> Vec<Card> {
    Suit::ALL.iter().flat_map(|&s| Rank::ALL.iter().filter(|&&r| r >= lowest).map(move |&r| Card::new(s, r))).collect()
}

pub fn shuffled(mut cards: Vec<Card>, rng: &mut impl Rng) -> Vec<Card> {
    cards.shuffle(rng);
    cards
}

#[cfg(test)]
#[path = "cards_tests.rs"]
mod tests;
