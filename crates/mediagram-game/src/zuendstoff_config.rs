//! Zündstoff's lives, fuse and timing, the per-game state types and its
//! catalogue entry.

use crate::ask::Ask;
use crate::catalogue::{CatalogueEntry, Kids, Need, Seats};
use crate::factory::Night;
use crate::game::{Game, GameCtx};
use crate::host::ROOM_CAP;
use crate::protocol::{GameLength, GameSpeed, LobbySettings, PlayerId, ServerTime};
use crate::zuendstoff::ZuendstoffGame;

#[derive(Debug, Clone)]
pub struct ZuendstoffConfig {
    pub lives: u8,
    /// A round's hidden fuse burns for a time between these two.
    pub fuse_min_ms: ServerTime,
    pub fuse_max_ms: ServerTime,
    /// A round's first holder may throw after this.
    pub intro_ms: ServerTime,
    /// From the tap to the bomb leaving the hand: the throw clip's lead-in.
    pub throw_lead_ms: ServerTime,
    /// From leaving the hand to landing.
    pub flight_ms: ServerTime,
    /// A tap this long before the landing, or `catch_after_ms` after it, catches.
    pub catch_before_ms: ServerTime,
    pub catch_after_ms: ServerTime,
    /// After a catch the catcher may throw this long later, so a second Fangen! tap throws nothing.
    pub catch_guard_ms: ServerTime,
    /// A dropped bomb can be thrown again this long after it landed.
    pub pickup_ms: ServerTime,
    /// How long the explosion stays on screen before the next round.
    pub boom_ms: ServerTime,
    /// Taps arriving this early are accepted, to absorb clock-sync error.
    pub early_tolerance_ms: ServerTime,
    pub seed: Option<u64>,
}

impl Default for ZuendstoffConfig {
    fn default() -> Self {
        Self {
            lives: 3,
            fuse_min_ms: 18_000.0,
            fuse_max_ms: 40_000.0,
            intro_ms: 2500.0,
            throw_lead_ms: 600.0,
            flight_ms: 1000.0,
            catch_before_ms: 400.0,
            catch_after_ms: 400.0,
            catch_guard_ms: 600.0,
            pickup_ms: 2000.0,
            boom_ms: 4500.0,
            early_tolerance_ms: 150.0,
            seed: None,
        }
    }
}

impl ZuendstoffConfig {
    /// At the lobby's length: the lives each player starts with.
    pub fn sized(mut self, length: GameLength) -> Self {
        self.lives = length.scale(self.lives.into()) as u8;
        self
    }

    /// At the lobby's tempo: the fuse burns longer or shorter; a throw takes as long as ever.
    pub fn at(mut self, speed: GameSpeed) -> Self {
        self.fuse_min_ms = speed.stretch(self.fuse_min_ms);
        self.fuse_max_ms = speed.stretch(self.fuse_max_ms);
        self
    }
}

pub(crate) const ENTRY: CatalogueEntry = CatalogueEntry {
    // A bomb needs someone to throw it to.
    name: "Zündstoff", seats: Seats::Range(2, ROOM_CAP), timed: true, sized: true,
    kids: Kids::Plays, board: false, needs: Need::Nothing, start,
};

fn start(_night: &mut Night, ctx: &mut GameCtx, settings: &LobbySettings) -> Box<dyn Game> {
    Box::new(ZuendstoffGame::new(ZuendstoffConfig::default().at(settings.speed).sized(settings.length), ctx.players, ctx.now))
}

#[derive(Debug, Clone, Copy, PartialEq)]
pub(crate) enum Stage {
    /// `ask` asks the holder whom to throw to, from `ready_at`. `caught`: how
    /// the bomb came to them at `ask`'s opening, None at a round's start; a
    /// dropped one lies at their feet until `ready_at`.
    Hold { ask: Ask, ready_at: ServerTime, caught: Option<bool> },
    /// Thrown by `from` at `thrown_at`; leaves the hand at `released_at` and
    /// lands by `to` at `lands_at`.
    Flight { from: PlayerId, to: PlayerId, thrown_at: ServerTime, released_at: ServerTime, lands_at: ServerTime, catch: Catch },
    /// The fuse ran out on `victim` at `at`.
    Boom { victim: PlayerId, at: ServerTime },
    Final,
}

/// The target's tap during a flight: none yet, in the window, or too early.
#[derive(Debug, Clone, Copy, PartialEq)]
pub(crate) enum Catch {
    Pending,
    Caught,
    Fumbled,
}
