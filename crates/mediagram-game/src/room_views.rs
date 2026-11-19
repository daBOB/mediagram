//! What each connection sees, and the change-only flush.

use crate::catalogue::{CATALOGUE, entry};
use crate::game::{Game, ViewCtx};
use crate::protocol::{BoardScreen, PhoneScreen, PhoneView, PlayerInfo, Ranking, Role, ServerMsg, ServerTime, TvScene, TvView};
use crate::room::{ConnId, Outbox, Room, encode};
use crate::room_seats::Player;

impl Room {
    /// Current view for every connection whose view changed since last flush.
    pub fn flush(&mut self, now: ServerTime) -> Outbox {
        let ids: Vec<ConnId> = self.conns.keys().copied().collect();
        let mut out = Vec::new();
        for id in ids {
            out.extend(self.draw_for(id).map(|json| (id, json)));
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
                            blocked: self.blocked(),
                            games: CATALOGUE.to_vec(),
                            board: self.board_connected(),
                        },
                    },
                    ranking: self.ranking().map(Box::new),
                },
            }),
            Role::Board => Some(ServerMsg::Board {
                screen: match &self.game {
                    Some(g) => g.board_screen(&vctx),
                    None => BoardScreen::Wait {
                        title: "Zeichenbrett".into(),
                        text: Some("Das iPad ist das Zeichenbrett. Es geht los, sobald die Spielleitung startet.".into()),
                        can_leave: true,
                    },
                },
            }),
            Role::Phone => {
                let p = self.player(c.player?)?;
                let screen = match &self.game {
                    Some(g) => self.game_screen(g.as_ref(), &vctx, p),
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
                            blocked,
                            avatars: crate::avatars::slots_for(&self.players, p.id),
                            games: CATALOGUE.to_vec(),
                        }
                    }
                };
                Some(ServerMsg::Phone { view: PhoneView { me: self.info(p), screen, buzz: self.game.as_ref().and_then(|g| g.phone_buzz(&vctx, p.id)) } })
            }
        }
    }

    /// How the running game placed its players once it is over.
    pub(crate) fn ranking(&self) -> Option<Ranking> {
        let game = self.game.as_ref()?;
        self.ended.clone().or_else(|| game.ranking(&self.players))
    }

    /// A phone during a game: the game's own screen while it runs, then the player's place among
    /// everyone ranked; a player the game does not seat, or did not rank, waits for the next one.
    fn game_screen(&self, game: &dyn Game, view: &ViewCtx, p: &Player) -> PhoneScreen {
        let place = match self.ranking() {
            Some(ranking) => ranking.place_of(p.id).map(|rank| (rank, ranking.places.len() as u8)),
            None if game.seats().is_none_or(|s| s.contains(&p.id)) => return game.phone_screen(view, p.id),
            None => None,
        };
        match place {
            Some((rank, of)) => PhoneScreen::Final { rank, of, score: p.score, can_restart: view.leader == Some(p.id) },
            None => {
                let title = format!("{} läuft", entry(self.settings.game).name);
                PhoneScreen::Wait { title, text: Some("Du spielst in der nächsten Runde mit.".into()) }
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
            avatar: p.avatar.map(String::from),
        }
    }
}
