//! What each connection sees, and the change-only flush.

use crate::game::ViewCtx;
use crate::protocol::{PhoneScreen, PhoneView, PlayerInfo, Role, ServerMsg, ServerTime, TvScene, TvView};
use crate::room::{ConnId, Outbox, Room, encode};
use crate::room_seats::Player;

impl Room {
    /// Current view for every connection whose view changed since last flush.
    pub fn flush(&mut self, now: ServerTime) -> Outbox {
        let ids: Vec<ConnId> = self.conns.keys().copied().collect();
        let mut out = Vec::new();
        for id in ids {
            let Some(msg) = self.view_for(id, now) else {
                continue;
            };
            let json = encode(&msg);
            let conn = self.conns.get_mut(&id).expect("conn exists");
            if conn.last_view.as_deref() != Some(json.as_str()) {
                conn.last_view = Some(json.clone());
                out.push((id, json));
            }
        }
        out
    }

    pub(crate) fn view_for(&self, conn: ConnId, now: ServerTime) -> Option<ServerMsg> {
        let c = self.conns.get(&conn)?;
        let vctx = ViewCtx { now, players: &self.players, leader: self.leader() };
        match c.role? {
            Role::Tv => Some(ServerMsg::Tv {
                view: TvView {
                    join_url: self.cfg.join_url.clone(),
                    players: self.players.iter().map(|p| self.info(p)).collect(),
                    scene: match &self.game {
                        Some(g) => g.tv_scene(&vctx),
                        None => TvScene::Lobby {
                            min_players: self.cfg.min_players,
                            settings: self.settings.clone(),
                            leader_name: self.leader_name(),
                        },
                    },
                },
            }),
            Role::Phone => {
                let p = self.player(c.player?)?;
                let screen = match &self.game {
                    Some(g) => g.phone_screen(&vctx, p.id),
                    None => {
                        let players = self.connected_count();
                        let is_leader = self.leader() == Some(p.id);
                        let blocked = self.blocked();
                        PhoneScreen::Lobby {
                            players,
                            min_players: self.cfg.min_players,
                            can_start: is_leader && players >= self.cfg.min_players && blocked.is_none(),
                            is_leader,
                            leader_name: self.leader_name(),
                            settings: self.settings.clone(),
                            blocked: blocked.map(String::from),
                        }
                    }
                };
                Some(ServerMsg::Phone { view: PhoneView { me: self.info(p), screen } })
            }
        }
    }

    fn leader_name(&self) -> Option<String> {
        self.player(self.leader()?).map(|p| p.name.clone())
    }

    fn info(&self, p: &Player) -> PlayerInfo {
        PlayerInfo {
            id: p.id,
            name: p.name.clone(),
            color: p.color.to_string(),
            score: p.score,
            connected: p.connected(),
            leader: self.leader() == Some(p.id),
        }
    }
}
