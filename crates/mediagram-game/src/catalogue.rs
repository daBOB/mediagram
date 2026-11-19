//! The game catalogue: the games a room can start, in the order the lobby
//! lists them. Each game declares its entry beside its config; `entry` finds
//! it by kind.

use std::borrow::Cow;

use serde::{Deserialize, Serialize};
use ts_rs::TS;

use crate::factory::Night;
use crate::game::{Game, GameCtx};
use crate::host::ROOM_CAP;
use crate::protocol::{GameKind, LobbySettings};

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
    /// The lobby's tempo sets its clocks; turn-based games have none.
    pub timed: bool,
    /// The lobby's length sets how much of it is played; games played to the end have none.
    pub sized: bool,
}

/// Every game, in the order the lobby lists them.
pub const CATALOGUE: &[GameEntry] = &[
    GameEntry::of(GameKind::Quiz),
    GameEntry::of(GameKind::Poster),
    GameEntry::of(GameKind::Grenzfall),
    GameEntry::of(GameKind::Spruchreif),
    GameEntry::of(GameKind::Zuendstoff),
    GameEntry::of(GameKind::LetztesWort),
    GameEntry::of(GameKind::Kopfnuss),
    GameEntry::of(GameKind::Dreifelder),
    GameEntry::of(GameKind::Preisfrage),
    GameEntry::of(GameKind::Kritzelei),
    GameEntry::of(GameKind::Race),
    GameEntry::of(GameKind::GrandAllee),
    GameEntry::of(GameKind::Fluechtig),
    GameEntry::of(GameKind::MauMau),
    GameEntry::of(GameKind::Schwimmen),
    GameEntry::of(GameKind::Durak),
];

/// Everything the room knows of one game: what the lobby shows, what keeps it from starting, how it starts.
#[derive(Clone, Copy)]
pub(crate) struct CatalogueEntry {
    /// The name players read.
    pub name: &'static str,
    pub seats: Seats,
    /// The lobby's tempo sets its clocks; turn-based games have none.
    pub timed: bool,
    /// The lobby's length sets how much of it is played (rounds, articles, lives, the round limit).
    pub sized: bool,
    pub kids: Kids,
    /// One draws on the drawing board, so the game waits for it.
    pub board: bool,
    pub needs: Need,
    /// Builds the game from the lobby settings and the night's material.
    pub start: fn(&mut Night, &mut GameCtx, &LobbySettings) -> Box<dyn Game>,
}

/// The fewest and the most players a game starts with.
#[derive(Clone, Copy)]
pub(crate) enum Seats {
    /// Any room plays it, so only the room's own minimum gates it: a seat
    /// message would stand on the TV before anyone has joined.
    AnyRoom,
    Range(u8, u8),
}

/// What kids mode does to a game.
#[derive(Clone, Copy)]
pub(crate) enum Kids {
    Plays,
    /// Kids mode keeps it off the table.
    Barred,
    /// Kids mode asks it from kids films alone: never the general questions, and
    /// only with a film for every round of its length.
    FilmsOnly,
}

/// What the night's material must hold for a game to start.
#[derive(Clone, Copy)]
pub(crate) enum Need {
    Nothing,
    /// Enough posters, kid-safe ones in kids mode.
    Posters,
    /// Enough of Preisfrage's articles.
    Articles,
}

/// The entry of `kind`. No wildcard arm, so a new game does not compile without its entry.
pub(crate) const fn entry(kind: GameKind) -> CatalogueEntry {
    match kind {
        GameKind::Quiz => crate::quiz_config::ENTRY,
        GameKind::Poster => crate::poster_config::ENTRY,
        GameKind::Grenzfall => crate::grenzfall::ENTRY,
        GameKind::Spruchreif => crate::spruchreif::ENTRY,
        GameKind::Zuendstoff => crate::zuendstoff_config::ENTRY,
        GameKind::LetztesWort => crate::letztes_wort_config::ENTRY,
        GameKind::Kopfnuss => crate::kopfnuss_config::ENTRY,
        GameKind::Dreifelder => crate::dreifelder_config::ENTRY,
        GameKind::Preisfrage => crate::preisfrage_config::ENTRY,
        GameKind::Kritzelei => crate::kritzelei_config::ENTRY,
        GameKind::Race => crate::race_config::ENTRY,
        GameKind::GrandAllee => crate::grand_allee::live_config::ENTRY,
        GameKind::Fluechtig => crate::fluechtig_config::ENTRY,
        GameKind::MauMau => crate::maumau::ENTRY,
        GameKind::Schwimmen => crate::schwimmen_config::ENTRY,
        GameKind::Durak => crate::durak::ENTRY,
    }
}

impl GameEntry {
    /// `kind` as the lobby shows it.
    const fn of(kind: GameKind) -> GameEntry {
        let e = entry(kind);
        let (min, max) = match e.seats {
            Seats::AnyRoom => (1, ROOM_CAP),
            Seats::Range(min, max) => (min, max),
        };
        GameEntry { kind, name: Cow::Borrowed(e.name), min, max, timed: e.timed, sized: e.sized }
    }
}

#[cfg(test)]
#[path = "catalogue_tests.rs"]
mod tests;
