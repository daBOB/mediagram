use crate::protocol::{Action, BoardAct, BoardScreen, Buzz, DrawOp, LobbySettings, PhoneScreen, PlayerId, ServerTime, TvScene};

use crate::room_seats::Player;

/// Mutable access a game gets while it handles input or time.
pub struct GameCtx<'a> {
    pub now: ServerTime,
    /// All seated players, including disconnected ones. Games own `score`.
    pub players: &'a mut [Player],
}

impl GameCtx<'_> {
    /// Since when `player` has been absent, or `None` while they are connected (or not seated).
    pub(crate) fn absent_since(&self, player: PlayerId) -> Option<ServerTime> {
        let p = self.players.iter().find(|p| p.id == player).filter(|p| !p.connected())?;
        // A real room always records when a player left; only test fixtures seat an absent player without one.
        Some(p.left_at.unwrap_or(0.0))
    }
}

/// A seated player's name, or "Jemand" for one who is not seated.
pub(crate) fn name(players: &[Player], id: PlayerId) -> &str {
    players.iter().find(|p| p.id == id).map_or("Jemand", |p| &p.name)
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

    /// An act from the drawing board.
    fn on_board(&mut self, _ctx: &mut GameCtx, _act: &BoardAct) -> Result<(), ActionError> {
        Err(ActionError::Ignored)
    }

    /// The board connected (`true`) or went away (`false`) while this game runs.
    fn on_board_presence(&mut self, _now: ServerTime, _present: bool) {}

    /// What the drawing board shows.
    fn board_screen(&self, _view: &ViewCtx) -> BoardScreen {
        BoardScreen::Wait { title: "Zeichenbrett".into(), text: Some("Gerade läuft ein anderes Spiel.".into()), can_leave: true }
    }

    /// The current turn's drawing: its turn number and every op so far, append-only within a turn.
    fn drawing(&self) -> Option<(u32, &[DrawOp])> {
        None
    }

    /// A short vibration for `player`'s phone.
    fn phone_buzz(&self, _view: &ViewCtx, _player: PlayerId) -> Option<Buzz> {
        None
    }

    /// Log lines recorded since the last call, which the host appends to a
    /// file so a night's decisions can be measured afterwards; most games keep none.
    fn take_log(&mut self) -> Vec<serde_json::Value> {
        Vec::new()
    }
}

/// Builds a fresh game when the leader presses start, from the lobby
/// settings in force. Scores are already reset when this runs.
pub type GameFactory = Box<dyn FnMut(&mut GameCtx, &LobbySettings) -> Box<dyn Game> + Send>;
