//! The game catalogue: the games a room can start, each with its name and
//! seat range, in the order the lobby lists them.

use std::borrow::Cow;

use serde::{Deserialize, Serialize};
use ts_rs::TS;

use crate::host::ROOM_CAP;
use crate::protocol::GameKind;

/// One game a room can start, as the lobby shows it.
#[derive(Debug, Clone, PartialEq, Eq, Serialize, Deserialize, TS)]
#[ts(export)]
pub struct GameEntry {
    pub kind: GameKind,
    /// The name players read.
    pub name: Cow<'static, str>,
    /// The fewest players the game starts with.
    pub min: u8,
    /// The most players the game starts with.
    pub max: u8,
}

/// Every game, in the order the lobby lists them.
pub const CATALOGUE: &[GameEntry] = &[
    entry(GameKind::Quiz),
    entry(GameKind::Poster),
    entry(GameKind::Kopfnuss),
    entry(GameKind::Dreifelder),
    entry(GameKind::Preisfrage),
    entry(GameKind::Race),
    entry(GameKind::GrandAllee),
    entry(GameKind::Fluechtig),
    entry(GameKind::MauMau),
    entry(GameKind::Schwimmen),
    entry(GameKind::Durak),
];

/// The entry of `kind`. No wildcard arm, so a new game does not compile without its entry.
pub(crate) const fn entry(kind: GameKind) -> GameEntry {
    let (name, min, max) = match kind {
        GameKind::Quiz => ("Buzzer-Quiz", 1, ROOM_CAP),
        GameKind::Poster => ("Poster-Raten", 1, ROOM_CAP),
        // Kopfnuss needs someone to send a trick to; any room size plays.
        GameKind::Kopfnuss => ("Kopfnuss", 2, ROOM_CAP),
        // Dreifelder needs someone to outlast.
        GameKind::Dreifelder => ("Dreifelder", 2, ROOM_CAP),
        // Everyone guesses for themselves, so one player can play alone.
        GameKind::Preisfrage => ("Preisfrage", 1, ROOM_CAP),
        // The race board has four corners.
        GameKind::Race => ("Heimlauf", 2, 4),
        // Grand Allee's round limit scales with the table, from two to eight.
        GameKind::GrandAllee => ("Grand Allee", 2, 8),
        // One runs, the rest share four hunter pieces (five at six players).
        GameKind::Fluechtig => ("Flüchtig", 2, 6),
        // 32 cards deal five to five players, the 52-card deck to eight.
        GameKind::MauMau => ("Mau-Mau", 2, 8),
        // Schwimmen deals 3 cards to each and 3 to the middle from 32.
        GameKind::Schwimmen => ("Schwimmen", 2, 8),
        // Durak deals 36 cards, six each.
        GameKind::Durak => ("Durak", 2, 6),
    };
    GameEntry { kind, name: Cow::Borrowed(name), min, max }
}

#[cfg(test)]
#[path = "catalogue_tests.rs"]
mod tests;
