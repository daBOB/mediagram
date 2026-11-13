//! The drawing board: the third connection role. It holds a token but no seat,
//! and receives the drawing the game keeps, like the TV.

use crate::game::GameCtx;
use crate::protocol::{BoardAct, ErrorCode, Role, ServerMsg, ServerTime};
use crate::room::{ConnId, Room, action_reply, encode, error};
use crate::room_seats::new_token;

/// Who may hold the board; `conn` is `None` while the iPad is away.
pub(crate) struct BoardSeat {
    pub(crate) token: String,
    pub(crate) conn: Option<ConnId>,
}

impl Room {
    /// Welcome with the board token, or NotAllowed when a game runs and `token` isn't the board's.
    pub(crate) fn seat_board(&mut self, conn: ConnId, token: Option<String>, now: ServerTime) -> ServerMsg {
        let held = self.board.as_ref().map(|b| b.token.clone());
        let matched = token.is_some() && token == held;
        if self.game.is_some() && !matched {
            return error(ErrorCode::NotAllowed, "Ein anderes Gerät ist gerade das Zeichenbrett.".into());
        }
        self.release(conn, now);
        let old = self.board.as_ref().and_then(|b| b.conn).filter(|&old| old != conn);
        if let Some(c) = old.and_then(|old| self.conns.get_mut(&old)) {
            c.role = None;
            c.drawn = None;
        }
        let token = held.filter(|_| matched).unwrap_or_else(new_token);
        self.board = Some(BoardSeat { token: token.clone(), conn: Some(conn) });
        let c = self.conns.get_mut(&conn).expect("conn exists");
        c.role = Some(Role::Board);
        c.drawn = None;
        c.last_view = None;
        if let Some(g) = self.game.as_mut() {
            g.on_board_presence(now, true);
        }
        ServerMsg::Welcome { player: None, token: Some(token), now }
    }

    /// In the lobby the board is dropped; during a game it keeps its token and the game hears it left.
    /// A late close of a socket the board has since left changes nothing.
    pub(crate) fn release_board(&mut self, conn: ConnId, now: ServerTime) {
        if self.board.as_ref().is_none_or(|b| b.conn != Some(conn)) {
            return;
        }
        match self.game.as_mut() {
            Some(g) => {
                self.board.as_mut().expect("checked above").conn = None;
                g.on_board_presence(now, false);
            }
            None => self.board = None,
        }
    }

    pub(crate) fn board_connected(&self) -> bool {
        self.board.as_ref().is_some_and(|b| b.conn.is_some())
    }

    /// Tells a game that was just created whether the board is already seated.
    pub(crate) fn announce_board(&mut self, now: ServerTime) {
        let present = self.board_connected();
        if let Some(g) = self.game.as_mut() {
            g.on_board_presence(now, present);
        }
    }

    /// An act from the board; without a game there is nothing to act on.
    #[allow(clippy::result_large_err)]
    pub(crate) fn board_act(&mut self, act: &BoardAct, now: ServerTime) -> Result<(), ServerMsg> {
        let Some(game) = self.game.as_mut() else {
            return Ok(());
        };
        let mut ctx = GameCtx { now, players: &mut self.players };
        action_reply(game.on_board(&mut ctx, act))
    }

    /// The `Draw` message `conn` still needs, if it is a TV or the board; advances its cursor.
    pub(crate) fn draw_for(&mut self, conn: ConnId) -> Option<String> {
        let c = self.conns.get_mut(&conn).filter(|c| matches!(c.role, Some(Role::Tv | Role::Board)))?;
        let Some((turn, ops)) = self.game.as_ref().and_then(|g| g.drawing()) else {
            c.drawn = None;
            return None;
        };
        if c.drawn == Some((turn, ops.len())) {
            return None;
        }
        // A cursor from another turn, or past this drawing (a new game), starts afresh.
        let from = c.drawn.filter(|&(t, n)| t == turn && n <= ops.len()).map_or(0, |(_, n)| n);
        c.drawn = Some((turn, ops.len()));
        Some(encode(&ServerMsg::Draw { turn, from: from as u32, ops: ops[from..].to_vec() }))
    }
}
