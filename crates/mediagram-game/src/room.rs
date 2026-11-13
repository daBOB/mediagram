use std::collections::BTreeMap;

use crate::game::{ActionError, Game, GameCtx, GameFactory};
use crate::protocol::{Action, ClientMsg, ErrorCode, LobbySettings, PlayerId, Role, ServerMsg, ServerTime};
use crate::room_seats::{Conn, LOBBY_GRACE_MS, Player};
use crate::room_settings::{Readiness, initial_settings};

pub type ConnId = u64;

/// Serialized messages to send, addressed by connection.
pub type Outbox = Vec<(ConnId, String)>;

#[derive(Debug, Clone)]
pub struct RoomConfig {
    /// URL phones open to join, shown as QR code on the TV.
    pub join_url: String,
    pub min_players: u8,
    pub max_players: u8,
    /// Set when a kids profile started the night: the toggle stays on.
    pub kids_locked: bool,
    pub readiness: Readiness,
}

/// Transport-agnostic room state. The server feeds it connection events and
/// ticks; everything here is synchronous and deterministic given `now`.
pub struct Room {
    pub(crate) cfg: RoomConfig,
    pub(crate) players: Vec<Player>,
    pub(crate) conns: BTreeMap<ConnId, Conn>,
    pub(crate) game: Option<Box<dyn Game>>,
    factory: GameFactory,
    pub(crate) settings: LobbySettings,
    pub(crate) next_player_id: PlayerId,
}

impl Room {
    pub fn new(cfg: RoomConfig, factory: GameFactory) -> Self {
        Self {
            settings: initial_settings(cfg.kids_locked),
            cfg,
            players: Vec::new(),
            conns: BTreeMap::new(),
            game: None,
            factory,
            next_player_id: 0,
        }
    }

    pub fn players(&self) -> &[Player] {
        &self.players
    }

    pub fn in_game(&self) -> bool {
        self.game.is_some()
    }

    /// Ends a running game. `true` if one was running.
    pub fn back_to_lobby(&mut self) -> bool {
        self.game.take().is_some()
    }

    /// `local`: the peer is loopback. Only the box itself may show the TV page.
    pub fn connect(&mut self, conn: ConnId, local: bool) {
        self.conns.insert(conn, Conn { local, ..Conn::default() });
    }

    pub fn disconnect(&mut self, conn: ConnId, now: ServerTime) {
        self.release(conn, now);
        self.conns.remove(&conn);
    }

    /// Handles one raw text frame. Returns direct replies (welcome, pong,
    /// errors). Views go out through [`Room::flush`].
    pub fn handle(&mut self, conn: ConnId, text: &str, now: ServerTime) -> Outbox {
        let reply = match serde_json::from_str::<ClientMsg>(text) {
            Ok(ClientMsg::Action { action: Action::SpikeInk { points, at } }) => {
                let text = encode(&ServerMsg::SpikeInk { points, at });
                return self.conns.iter().filter(|(_, c)| c.role == Some(Role::Tv)).map(|(id, _)| (*id, text.clone())).collect();
            }
            Ok(msg) => self.handle_msg(conn, msg, now),
            Err(_) => Some(error(ErrorCode::BadMessage, "Nachricht nicht lesbar.".into())),
        };
        reply.map(|m| vec![(conn, encode(&m))]).unwrap_or_default()
    }

    pub fn tick(&mut self, now: ServerTime) {
        if let Some(game) = self.game.as_mut() {
            let mut ctx = GameCtx { now, players: &mut self.players };
            game.on_tick(&mut ctx);
        } else {
            self.players.retain(|p| p.connected() || p.left_at.is_some_and(|t| now - t < LOBBY_GRACE_MS));
        }
    }

    fn handle_msg(&mut self, conn: ConnId, msg: ClientMsg, now: ServerTime) -> Option<ServerMsg> {
        match msg {
            ClientMsg::Ping { t0 } => Some(ServerMsg::Pong { t0, now }),
            ClientMsg::Hello { role: Role::Tv, .. } => {
                let c = self.conns.get_mut(&conn)?;
                if !c.local {
                    return Some(error(
                        ErrorCode::NotAllowed,
                        "Der Fernseher-Bildschirm läuft nur auf der Box.".into(),
                    ));
                }
                c.role = Some(Role::Tv);
                self.release(conn, now);
                Some(ServerMsg::Welcome { player: None, token: None, now })
            }
            ClientMsg::Hello { role: Role::Phone, name, token, avatar } => {
                self.release(conn, now);
                Some(match self.seat_phone(conn, name, token, avatar) {
                    Ok(id) => {
                        let token = self.player(id).map(|p| p.token.clone());
                        ServerMsg::Welcome { player: Some(id), token, now }
                    }
                    Err(e) => e,
                })
            }
            ClientMsg::Action { action } => self.handle_action(conn, action, now).err(),
        }
    }

    #[allow(clippy::result_large_err)]
    fn handle_action(&mut self, conn: ConnId, action: Action, now: ServerTime) -> Result<(), ServerMsg> {
        let c = self.conns.get(&conn).ok_or_else(not_allowed)?;
        let is_tv = c.role == Some(Role::Tv);
        let player = c.player;
        let is_leader = player.is_some() && player == self.leader();
        match action {
            Action::Configure { game, quiz_mode, kids_only, adult } => {
                // The TV's poster row picks only the game: a pick crossing the leader's kids-mode tap must not undo it.
                let s = &self.settings;
                let (quiz_mode, kids_only, adult) = if is_tv { (s.quiz_mode, s.kids_only, s.adult) } else { (quiz_mode, kids_only, adult) };
                self.configure(is_tv || is_leader, game, quiz_mode, kids_only, adult).then_some(()).ok_or_else(not_allowed)
            }
            Action::StartGame => {
                if !(is_tv || is_leader) || self.game.is_some() {
                    return Err(not_allowed());
                }
                if let Some(why) = self.blocked() {
                    return Err(error(ErrorCode::NotAllowed, why));
                }
                if self.connected_count() < self.cfg.min_players {
                    return Err(error(ErrorCode::NotAllowed, "Noch zu wenige Spieler.".into()));
                }
                self.players.retain(|p| p.connected());
                for p in &mut self.players {
                    p.score = 0;
                }
                let mut ctx = GameCtx { now, players: &mut self.players };
                self.game = Some((self.factory)(&mut ctx, &self.settings));
                Ok(())
            }
            Action::PickAvatar { id } => {
                let ok = self.game.is_none() && player.is_some_and(|p| crate::avatars::pick(&mut self.players, p, &id));
                ok.then_some(()).ok_or_else(not_allowed)
            }
            Action::BackToLobby => {
                if !(is_tv || is_leader) {
                    return Err(not_allowed());
                }
                self.back_to_lobby();
                Ok(())
            }
            other => {
                let (Some(player), Some(game)) = (player, self.game.as_mut()) else {
                    return Err(not_allowed());
                };
                let mut ctx = GameCtx { now, players: &mut self.players };
                match game.on_action(&mut ctx, player, &other) {
                    Ok(()) | Err(ActionError::Ignored) => Ok(()),
                    Err(ActionError::NotAllowed(msg)) => Err(error(ErrorCode::NotAllowed, msg.into())),
                }
            }
        }
    }
}

pub(crate) fn error(code: ErrorCode, message: String) -> ServerMsg {
    ServerMsg::Error { code, message }
}

fn not_allowed() -> ServerMsg {
    error(ErrorCode::NotAllowed, "Das geht gerade nicht.".into())
}

pub(crate) fn encode(msg: &ServerMsg) -> String {
    serde_json::to_string(msg).expect("protocol types always serialize")
}

#[cfg(test)]
#[path = "room_tests.rs"]
pub(crate) mod tests;

#[cfg(test)]
#[path = "room_avatar_tests.rs"]
mod avatar_tests;
