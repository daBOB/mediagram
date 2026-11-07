//! Seating: who sits where, name cleaning and reconnect tokens.

use rand::Rng;

use crate::protocol::{ErrorCode, PlayerId, Role, ServerMsg, ServerTime};
use crate::room::{ConnId, Room, error};

/// One open connection to the room.
#[derive(Default)]
pub(crate) struct Conn {
    pub(crate) role: Option<Role>,
    pub(crate) player: Option<PlayerId>,
    /// The peer is a loopback connection, i.e. the box itself.
    pub(crate) local: bool,
    /// Last view sent, so unchanged views are not resent on every tick.
    pub(crate) last_view: Option<String>,
}

const PLAYER_COLORS: [&str; 8] = [
    "#ef476f", "#06d6a0", "#118ab2", "#ffd166", "#9b5de5", "#f15bb5", "#00bbf9", "#fb8500",
];
pub(crate) const MAX_NAME_CHARS: usize = 16;
/// How long a disconnected player keeps their seat in the lobby. During a game
/// seats are kept until the game ends, so a sleeping phone can rejoin.
pub(crate) const LOBBY_GRACE_MS: ServerTime = 30_000.0;

#[derive(Debug, Clone)]
pub struct Player {
    pub id: PlayerId,
    pub name: String,
    pub color: &'static str,
    pub score: i32,
    pub avatar: Option<&'static str>,
    pub(crate) token: String,
    pub(crate) conn: Option<ConnId>,
    pub(crate) left_at: Option<ServerTime>,
}

impl Player {
    pub fn connected(&self) -> bool {
        self.conn.is_some()
    }

    /// A seated player outside of any room, for testing game modules.
    #[doc(hidden)]
    pub fn for_test(id: PlayerId, name: &str, connected: bool) -> Self {
        Self {
            id,
            name: name.into(),
            color: PLAYER_COLORS[id as usize % PLAYER_COLORS.len()],
            score: 0,
            avatar: crate::avatars::AVATARS.get(id as usize).copied(),
            token: String::new(),
            conn: connected.then_some(u64::from(id)),
            left_at: None,
        }
    }
}

impl Room {
    // The error is the reply sent to the phone; rare, so not worth boxing.
    #[allow(clippy::result_large_err)]
    pub(crate) fn seat_phone(
        &mut self,
        conn: ConnId,
        name: Option<String>,
        token: Option<String>,
        avatar: Option<String>,
    ) -> Result<PlayerId, ServerMsg> {
        let name = name.map(|n| clean_name(&n)).filter(|n| !n.is_empty());

        let existing = token
            .as_deref()
            .and_then(|t| self.players.iter().position(|p| p.token == t));

        let id = if let Some(idx) = existing {
            let p = &mut self.players[idx];
            if let Some(old) = p.conn.replace(conn)
                && let Some(c) = self.conns.get_mut(&old)
            {
                c.player = None;
                c.role = None;
            }
            p.left_at = None;
            if let Some(n) = name {
                p.name = n;
            }
            p.id
        } else {
            let Some(name) = name else {
                return Err(error(ErrorCode::NameRequired, "Bitte gib einen Namen ein.".into()));
            };
            if self.players.len() >= self.cfg.max_players as usize {
                return Err(error(ErrorCode::RoomFull, "Der Tisch ist voll.".into()));
            }
            let color = PLAYER_COLORS
                .iter()
                .copied()
                .find(|c| !self.players.iter().any(|p| p.color == *c))
                .unwrap_or(PLAYER_COLORS[0]);
            let avatar = crate::avatars::assign(&self.players, avatar.as_deref());
            let id = self.next_player_id;
            self.next_player_id = self.next_player_id.wrapping_add(1);
            self.players.push(Player {
                id,
                name,
                color,
                score: 0,
                avatar,
                token: new_token(),
                conn: Some(conn),
                left_at: None,
            });
            id
        };

        let c = self.conns.get_mut(&conn).expect("conn exists");
        c.role = Some(Role::Phone);
        c.player = Some(id);
        Ok(id)
    }

    /// Frees the seat `conn` holds, if any; the player stays seated as absent,
    /// so a socket that says hello again never leaves a ghost behind.
    pub(crate) fn release(&mut self, conn: ConnId, now: ServerTime) {
        let held = self.conns.get_mut(&conn).and_then(|c| c.player.take());
        if let Some(p) = held.and_then(|id| self.player_mut(id)) {
            // A reconnect may already have moved the player to a new socket.
            if p.conn == Some(conn) {
                p.conn = None;
                p.left_at = Some(now);
            }
        }
    }

    /// The earliest-joined connected player.
    pub fn leader(&self) -> Option<PlayerId> {
        self.players.iter().find(|p| p.connected()).map(|p| p.id)
    }

    pub(crate) fn connected_count(&self) -> u8 {
        self.players.iter().filter(|p| p.connected()).count() as u8
    }

    pub(crate) fn player(&self, id: PlayerId) -> Option<&Player> {
        self.players.iter().find(|p| p.id == id)
    }

    pub(crate) fn player_mut(&mut self, id: PlayerId) -> Option<&mut Player> {
        self.players.iter_mut().find(|p| p.id == id)
    }
}

pub(crate) fn clean_name(raw: &str) -> String {
    raw.split_whitespace()
        .collect::<Vec<_>>()
        .join(" ")
        .chars()
        .filter(|c| !c.is_control())
        .take(MAX_NAME_CHARS)
        .collect()
}

fn new_token() -> String {
    let bytes: [u8; 16] = rand::rng().random();
    bytes.iter().map(|b| format!("{b:02x}")).collect()
}
