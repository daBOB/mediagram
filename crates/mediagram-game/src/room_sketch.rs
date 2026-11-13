use std::sync::{Arc, Mutex};

use super::*;
use crate::game::{ActionError, ViewCtx};
use crate::protocol::{BoardAct, Buzz, DrawOp, PhoneScreen, TvScene, TvView};
use super::tests::{action, cfg, decode, hello};

#[derive(Default)]
pub(super) struct Log {
    pub(super) acts: Vec<BoardAct>,
    pub(super) presence: Vec<bool>,
    /// The drawing a game starts with.
    pub(super) start: (u32, Vec<DrawOp>),
}

pub(super) type Shared = Arc<Mutex<Log>>;

/// Keeps the drawing it was given; a board `Draw` appends, a board `Pick` opens that turn.
struct Sketch {
    log: Shared,
    turn: u32,
    ops: Vec<DrawOp>,
}

impl Game for Sketch {
    fn on_action(&mut self, _: &mut GameCtx, _: PlayerId, _: &Action) -> Result<(), ActionError> {
        Ok(())
    }
    fn on_tick(&mut self, _: &mut GameCtx) {}
    fn phone_screen(&self, _: &ViewCtx, _: PlayerId) -> PhoneScreen {
        PhoneScreen::Wait { title: "sketch".into(), text: None }
    }
    fn tv_scene(&self, _: &ViewCtx) -> TvScene {
        TvScene::Lobby {
            min_players: 0,
            settings: crate::room_settings::initial_settings(false),
            leader_name: None,
            blocked: None,
            games: Vec::new(),
            board: false,
        }
    }
    fn is_finished(&self) -> bool {
        false
    }
    fn on_board(&mut self, _: &mut GameCtx, act: &BoardAct) -> Result<(), ActionError> {
        self.log.lock().unwrap().acts.push(act.clone());
        match act {
            BoardAct::Draw { op } => self.ops.push(op.clone()),
            BoardAct::Pick { player: Some(turn) } => (self.turn, self.ops) = (u32::from(*turn), Vec::new()),
            _ => {}
        }
        Ok(())
    }
    fn on_board_presence(&mut self, _: ServerTime, present: bool) {
        self.log.lock().unwrap().presence.push(present);
    }
    fn drawing(&self) -> Option<(u32, &[DrawOp])> {
        Some((self.turn, &self.ops))
    }
    fn phone_buzz(&self, _: &ViewCtx, player: PlayerId) -> Option<Buzz> {
        (player == 0).then_some(Buzz::Turn)
    }
}

pub(super) fn sketch_room() -> (Room, Shared) {
    let log = Shared::default();
    log.lock().unwrap().start.0 = 1;
    let shared = log.clone();
    let room = Room::new(
        cfg(8),
        Box::new(move |_, _| {
            let (turn, ops) = shared.lock().unwrap().start.clone();
            Box::new(Sketch { log: shared.clone(), turn, ops })
        }),
    );
    (room, log)
}

pub(super) fn line() -> DrawOp {
    DrawOp::Line { color: 0, width: 0, points: vec![1, 2] }
}

pub(super) fn board_hello(r: &mut Room, conn: ConnId, token: Option<&str>) -> ServerMsg {
    r.connect(conn, false);
    let msg = ClientMsg::Hello { role: Role::Board, name: None, token: token.map(Into::into), avatar: None };
    decode(&r.handle(conn, &serde_json::to_string(&msg).unwrap(), 0.0)[0].1)
}

pub(super) fn tv_hello(r: &mut Room, conn: ConnId) {
    r.connect(conn, true);
    let msg = ClientMsg::Hello { role: Role::Tv, name: None, token: None, avatar: None };
    r.handle(conn, &serde_json::to_string(&msg).unwrap(), 0.0);
}

pub(super) fn token_of(msg: ServerMsg) -> String {
    let ServerMsg::Welcome { token: Some(t), .. } = msg else { panic!("{msg:?}") };
    t
}

/// Phone 10 and TV 11 seated; the TV starts the game.
pub(super) fn start(r: &mut Room) {
    hello(r, 10, "Ana", None);
    if !r.conns.contains_key(&11) {
        tv_hello(r, 11);
    }
    assert!(action(r, 11, Action::StartGame).is_empty());
    assert!(r.in_game());
}

pub(super) fn flushed(r: &mut Room, conn: ConnId) -> Vec<ServerMsg> {
    r.flush(0.0).into_iter().filter(|(c, _)| *c == conn).map(|(_, j)| decode(&j)).collect()
}

pub(super) fn draws(msgs: &[ServerMsg]) -> Vec<(u32, u32, usize)> {
    msgs.iter()
        .filter_map(|m| match m {
            ServerMsg::Draw { turn, from, ops } => Some((*turn, *from, ops.len())),
            _ => None,
        })
        .collect()
}

pub(super) fn board_act(r: &mut Room, conn: ConnId, act: BoardAct) -> Outbox {
    action(r, conn, Action::Board { act })
}

pub(super) fn lobby_board_flag(r: &mut Room) -> bool {
    tv_hello(r, 20);
    let msgs = flushed(r, 20);
    let [ServerMsg::Tv { view: TvView { scene: TvScene::Lobby { board, .. }, .. } }] = msgs.as_slice() else { panic!() };
    *board
}
