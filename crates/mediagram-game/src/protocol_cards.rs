//! Wire types of the card table, shared by every card game: the desk a phone
//! shows (own hand, buttons) and the scene the TV draws (seats, piles, deck).
//! Phones know nothing about the rules; the host says what can be tapped.

use serde::{Deserialize, Serialize};
use ts_rs::TS;

use crate::cards::{Card, Suit};
use crate::protocol::{PlayerId, ServerTime};

// ---------------------------------------------------------------- phone

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct CardDesk {
    pub prompt: String,
    pub info: Option<String>,
    /// Cards on the table the phone shows above the hand; the ones with
    /// `enabled` can be picked for a button that needs a table card.
    pub table: Vec<DeskCard>,
    /// The player's own hand in display order; actions refer to it by index.
    pub hand: Vec<DeskCard>,
    pub buttons: Vec<DeskButton>,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct DeskCard {
    pub card: Card,
    pub enabled: bool,
}

#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct DeskButton {
    /// Sent back in `Action::Cards`.
    pub id: String,
    pub label: String,
    pub needs: ButtonNeeds,
    pub primary: bool,
    /// Shown in the suit's colour next to the label (e.g. a wish).
    pub suit: Option<Suit>,
}

/// What the player must have picked before the button works.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Serialize, Deserialize, TS)]
#[serde(rename_all = "snake_case")]
#[ts(export)]
pub enum ButtonNeeds {
    Nothing,
    OneHand,
    SomeHand,
    HandAndTable,
}

// ---------------------------------------------------------------- TV

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct CardsScene {
    /// Names the game, e.g. "Mau-Mau"; the TV pre-draws that game's texts.
    pub game: String,
    pub seats: Vec<CardsSeat>,
    /// Face-up stacks in the middle, bottom card first (only the last few).
    pub piles: Vec<CardsPile>,
    /// Cards left in the face-down draw pile.
    pub deck: u16,
    pub trump: Option<Card>,
    /// Whose turn it is; `None` once the game is over.
    pub turn: Option<PlayerId>,
    /// A suit wished for, shown beside the pile.
    pub wish: Option<Suit>,
    /// Standing note by the piles, e.g. "Zieh 4".
    pub note: Option<String>,
    /// What the banner says after the player's name.
    pub banner: Option<CardsBanner>,
    /// The latest card movements, oldest first, so the TV can animate them.
    pub events: Vec<CardsEvent>,
    /// Finishing order once the game is over, winner first.
    pub ranking: Option<Vec<PlayerId>>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct CardsSeat {
    pub player: PlayerId,
    /// Cards in hand, face down on the TV.
    pub cards: u8,
    /// Cards this player shows face up.
    pub open: Vec<Card>,
    pub marker: Option<String>,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct CardsPile {
    pub cards: Vec<Card>,
    pub count: u16,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct CardsBanner {
    pub player: PlayerId,
    pub text: String,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct CardsEvent {
    /// Counts up, so the TV animates each event once.
    pub id: u32,
    pub at: ServerTime,
    pub player: PlayerId,
    pub kind: CardsEventKind,
}

#[derive(Debug, Clone, PartialEq, Serialize, Deserialize, TS)]
#[serde(tag = "kind", rename_all = "snake_case")]
#[ts(export)]
pub enum CardsEventKind {
    /// A card flies from the player's hand onto pile `pile`.
    Play { card: Card, pile: u8 },
    /// `count` cards fly from the draw pile into the player's hand.
    Draw { count: u8 },
    /// The discards were shuffled back under the top card.
    Reshuffle,
}
