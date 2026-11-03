use crate::protocol::{Action, LobbySettings, PhoneScreen, PlayerId, ServerTime, TvScene};

use crate::room_seats::Player;

/// Mutable access a game gets while it handles input or time.
pub struct GameCtx<'a> {
    pub now: ServerTime,
    /// All seated players, including disconnected ones. Games own `score`.
    pub players: &'a mut [Player],
}

/// Read-only context for building views.
pub struct ViewCtx<'a> {
    pub now: ServerTime,
    pub players: &'a [Player],
    /// The player allowed to start, restart or abort a game.
    pub leader: Option<PlayerId>,
}

#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ActionError {
    /// Shown to the player as an error.
    NotAllowed(&'static str),
    /// Silently dropped, e.g. a late tap after the round closed.
    Ignored,
}

/// One game module (quiz, Ludo, economy game, …).
///
/// The host is authoritative: games mutate their own state in `on_action` and
/// `on_tick`, and describe what each screen shows through the view methods.
/// Views must be pure, because they are recomputed and diffed on every event.
pub trait Game: Send {
    fn on_action(&mut self, ctx: &mut GameCtx, player: PlayerId, action: &Action) -> Result<(), ActionError>;

    /// Called roughly every 50 ms. Drive timers from `ctx.now`, never from a
    /// wall clock, so games stay deterministic and testable.
    fn on_tick(&mut self, ctx: &mut GameCtx);

    /// What `player`'s phone shows. Only include what this player may see.
    fn phone_screen(&self, view: &ViewCtx, player: PlayerId) -> PhoneScreen;

    fn tv_scene(&self, view: &ViewCtx) -> TvScene;

    fn is_finished(&self) -> bool;
}

/// Builds a fresh game when the leader presses start, from the lobby
/// settings in force. Scores are already reset when this runs.
pub type GameFactory = Box<dyn FnMut(&mut GameCtx, &LobbySettings) -> Box<dyn Game> + Send>;
