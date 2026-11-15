use crate::protocol::{Action, BoardAct, BoardScreen, Buzz, DrawOp, LobbySettings, PhoneScreen, Placed, PlayerId, Ranking, ServerTime, TvScene};

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

/// What a game's own view gives where the room speaks instead: to a player not
/// seated, and once the game is over. Never shown.
pub(crate) fn unasked() -> PhoneScreen {
    PhoneScreen::Wait { title: String::new(), text: None }
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

    /// What `player`'s phone shows while the game runs. Only include what this player may see.
    /// Never asked of a player who is not seated, nor once the game has its ranking: the room shows those.
    fn phone_screen(&self, view: &ViewCtx, player: PlayerId) -> PhoneScreen;

    fn tv_scene(&self, view: &ViewCtx) -> TvScene;

    /// Who plays, in seat order; anyone else in the room waits for the next game.
    /// `None` when everyone in the room plays, late joiners too.
    fn seats(&self) -> Option<Vec<PlayerId>> {
        None
    }

    /// How the game placed its players once it is over; `None` while it runs.
    fn ranking(&self, players: &[Player]) -> Option<Ranking>;

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

impl Ranking {
    /// Places by position in `order`, winner first: nobody shares a place.
    pub(crate) fn in_order(order: impl IntoIterator<Item = PlayerId>) -> Self {
        Self { places: order.into_iter().zip(1..).map(|(player, place)| Placed { player, place }).collect() }
    }

    /// Places by score, highest first: equal scores share a place, and keep `players`' order.
    pub(crate) fn by_score<'a>(players: impl IntoIterator<Item = &'a Player>) -> Self {
        let mut scored: Vec<(PlayerId, i32)> = players.into_iter().map(|p| (p.id, p.score)).collect();
        scored.sort_by_key(|&(_, score)| std::cmp::Reverse(score));
        let better = |score: i32| scored.iter().filter(|&&(_, s)| s > score).count() as u8;
        Self { places: scored.iter().map(|&(player, score)| Placed { player, place: better(score) + 1 }).collect() }
    }

    pub(crate) fn place_of(&self, player: PlayerId) -> Option<u8> {
        self.places.iter().find(|p| p.player == player).map(|p| p.place)
    }
}

/// Builds a fresh game when the leader presses start, from the lobby
/// settings in force. Scores are already reset when this runs.
pub type GameFactory = Box<dyn FnMut(&mut GameCtx, &LobbySettings) -> Box<dyn Game> + Send>;

#[cfg(test)]
#[path = "game_tests.rs"]
mod tests;
