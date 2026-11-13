use std::sync::{Arc, Mutex};

use super::*;
use crate::game::ViewCtx;
use crate::protocol::{BoardAct, BoardScreen, Buzz, DrawOp, PhoneScreen, TvScene, TvView};
use super::tests::{action, cfg, decode, hello};

#[derive(Default)]
struct Log {
    acts: Vec<BoardAct>,
    presence: Vec<bool>,
    /// The drawing a game starts with.
    start: (u32, Vec<DrawOp>),
}

type Shared = Arc<Mutex<Log>>;

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

fn sketch_room() -> (Room, Shared) {
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

fn line() -> DrawOp {
    DrawOp::Line { color: 0, width: 0, points: vec![1, 2] }
}

fn board_hello(r: &mut Room, conn: ConnId, token: Option<&str>) -> ServerMsg {
    r.connect(conn, false);
    let msg = ClientMsg::Hello { role: Role::Board, name: None, token: token.map(Into::into), avatar: None };
    decode(&r.handle(conn, &serde_json::to_string(&msg).unwrap(), 0.0)[0].1)
}

fn tv_hello(r: &mut Room, conn: ConnId) {
    r.connect(conn, true);
    let msg = ClientMsg::Hello { role: Role::Tv, name: None, token: None, avatar: None };
    r.handle(conn, &serde_json::to_string(&msg).unwrap(), 0.0);
}

fn token_of(msg: ServerMsg) -> String {
    let ServerMsg::Welcome { token: Some(t), .. } = msg else { panic!("{msg:?}") };
    t
}

/// Phone 10 and TV 11 seated; the TV starts the game.
fn start(r: &mut Room) {
    hello(r, 10, "Ana", None);
    if !r.conns.contains_key(&11) {
        tv_hello(r, 11);
    }
    assert!(action(r, 11, Action::StartGame).is_empty());
    assert!(r.in_game());
}

fn flushed(r: &mut Room, conn: ConnId) -> Vec<ServerMsg> {
    r.flush(0.0).into_iter().filter(|(c, _)| *c == conn).map(|(_, j)| decode(&j)).collect()
}

fn draws(msgs: &[ServerMsg]) -> Vec<(u32, u32, usize)> {
    msgs.iter()
        .filter_map(|m| match m {
            ServerMsg::Draw { turn, from, ops } => Some((*turn, *from, ops.len())),
            _ => None,
        })
        .collect()
}

fn board_act(r: &mut Room, conn: ConnId, act: BoardAct) -> Outbox {
    action(r, conn, Action::Board { act })
}

fn lobby_board_flag(r: &mut Room) -> bool {
    tv_hello(r, 20);
    let msgs = flushed(r, 20);
    let [ServerMsg::Tv { view: TvView { scene: TvScene::Lobby { board, .. }, .. } }] = msgs.as_slice() else { panic!() };
    *board
}

#[test]
fn a_board_gets_a_token_and_no_seat() {
    let (mut r, _) = sketch_room();
    hello(&mut r, 1, "Ana", None);
    let msg = board_hello(&mut r, 2, None);
    assert!(matches!(msg, ServerMsg::Welcome { player: None, token: Some(_), .. }));
    assert_eq!(r.players().len(), 1);
}

#[test]
fn a_phone_that_becomes_the_board_gives_up_its_seat() {
    let (mut r, _) = sketch_room();
    hello(&mut r, 1, "Ana", None);
    let msg = ClientMsg::Hello { role: Role::Board, name: None, token: None, avatar: None };
    r.handle(1, &serde_json::to_string(&msg).unwrap(), 5.0);
    assert_eq!(r.players()[0].conn, None);
    assert!(r.players()[0].left_at.is_some());
}

#[test]
fn in_the_lobby_another_device_takes_the_board_over() {
    let (mut r, _) = sketch_room();
    let first = token_of(board_hello(&mut r, 1, None));
    assert!(matches!(flushed(&mut r, 1).as_slice(), [ServerMsg::Board { .. }]));
    let second = token_of(board_hello(&mut r, 2, None));
    assert_ne!(first, second);
    let out = r.flush(0.0);
    assert!(out.iter().all(|(c, _)| *c == 2), "the old board hears nothing more");
    assert!(matches!(decode(&out[0].1), ServerMsg::Board { .. }));
}

#[test]
fn during_a_game_only_the_token_holder_gets_the_board_back() {
    let (mut r, _) = sketch_room();
    let token = token_of(board_hello(&mut r, 1, None));
    start(&mut r);
    r.disconnect(1, 5.0);
    let denied = board_hello(&mut r, 2, None);
    assert!(matches!(denied, ServerMsg::Error { code: ErrorCode::NotAllowed, .. }));
    assert_eq!(token_of(board_hello(&mut r, 3, Some(&token))), token);
}

#[test]
fn the_game_hears_the_board_go_and_come_back() {
    let (mut r, log) = sketch_room();
    let token = token_of(board_hello(&mut r, 1, None));
    start(&mut r);
    r.disconnect(1, 5.0);
    board_hello(&mut r, 2, Some(&token));
    assert_eq!(log.lock().unwrap().presence[1..], [false, true]);
}

#[test]
fn board_acts_count_only_from_the_board() {
    let (mut r, log) = sketch_room();
    board_hello(&mut r, 1, None);
    start(&mut r);
    let out = board_act(&mut r, 10, BoardAct::Skip);
    assert!(matches!(decode(&out[0].1), ServerMsg::Error { code: ErrorCode::NotAllowed, .. }));
    assert!(log.lock().unwrap().acts.is_empty());
    assert!(board_act(&mut r, 1, BoardAct::Skip).is_empty());
    assert_eq!(log.lock().unwrap().acts, [BoardAct::Skip]);
}

#[test]
fn tv_and_board_get_the_drawing_once_then_only_what_was_added() {
    let (mut r, log) = sketch_room();
    log.lock().unwrap().start.1 = vec![line(), line()];
    board_hello(&mut r, 1, None);
    start(&mut r);
    let out: Vec<_> = r.flush(0.0).into_iter().map(|(c, j)| (c, decode(&j))).collect();
    let of = |conn| out.iter().filter(|(c, _)| *c == conn).map(|(_, m)| m.clone()).collect::<Vec<_>>();
    assert_eq!(draws(&of(1)), [(1, 0, 2)]);
    assert_eq!(draws(&of(11)), [(1, 0, 2)]);
    assert!(draws(&of(10)).is_empty());
    board_act(&mut r, 1, BoardAct::Draw { op: line() });
    let out = r.flush(0.0);
    assert_eq!(out.len(), 2, "only the TV and the board hear of it");
    for (_, json) in out {
        assert!(matches!(decode(&json), ServerMsg::Draw { turn: 1, from: 2, ops } if ops.len() == 1));
    }
    assert!(r.flush(0.0).is_empty());
}

#[test]
fn a_tv_that_connects_mid_turn_gets_the_whole_drawing() {
    let (mut r, _) = sketch_room();
    board_hello(&mut r, 1, None);
    start(&mut r);
    for _ in 0..3 {
        board_act(&mut r, 1, BoardAct::Draw { op: line() });
    }
    tv_hello(&mut r, 12);
    assert_eq!(draws(&flushed(&mut r, 12)), [(1, 0, 3)]);
}

#[test]
fn a_new_turn_starts_the_drawing_afresh() {
    let (mut r, _) = sketch_room();
    board_hello(&mut r, 1, None);
    start(&mut r);
    board_act(&mut r, 1, BoardAct::Draw { op: line() });
    flushed(&mut r, 11);
    board_act(&mut r, 1, BoardAct::Pick { player: Some(2) });
    assert_eq!(draws(&flushed(&mut r, 11)), [(2, 0, 0)]);
}

#[test]
fn a_cursor_past_the_drawing_starts_afresh() {
    let (mut r, log) = sketch_room();
    log.lock().unwrap().start.1 = vec![line(); 5];
    start(&mut r);
    flushed(&mut r, 11);
    r.back_to_lobby();
    log.lock().unwrap().start.1 = vec![line(); 2];
    assert!(action(&mut r, 11, Action::StartGame).is_empty());
    assert_eq!(draws(&flushed(&mut r, 11)), [(1, 0, 2)]);
}

#[test]
fn draw_goes_out_before_the_view() {
    let (mut r, _) = sketch_room();
    board_hello(&mut r, 1, None);
    start(&mut r);
    let out = r.flush(0.0);
    for conn in [1, 11] {
        let msgs: Vec<_> = out.iter().filter(|(c, _)| *c == conn).map(|(_, j)| decode(j)).collect();
        assert!(matches!(msgs.as_slice(), [ServerMsg::Draw { .. }, ServerMsg::Board { .. } | ServerMsg::Tv { .. }]), "{msgs:?}");
    }
}

#[test]
fn the_phone_view_carries_the_buzz_the_game_asks_for() {
    let (mut r, _) = sketch_room();
    hello(&mut r, 1, "Ana", None);
    hello(&mut r, 2, "Bo", None);
    tv_hello(&mut r, 11);
    assert!(action(&mut r, 11, Action::StartGame).is_empty());
    let buzz = |r: &mut Room, conn| {
        let ServerMsg::Phone { view } = r.view_for(conn, 0.0).unwrap() else { panic!() };
        view.buzz
    };
    assert_eq!(buzz(&mut r, 1), Some(Buzz::Turn));
    assert_eq!(buzz(&mut r, 2), None);
}

#[test]
fn a_late_close_of_the_old_board_keeps_the_new_one() {
    let (mut r, log) = sketch_room();
    let token = token_of(board_hello(&mut r, 1, None));
    start(&mut r);
    board_hello(&mut r, 2, Some(&token));
    r.disconnect(1, 9.0);
    assert!(!log.lock().unwrap().presence.contains(&false));
    assert!(r.board_connected());
}

#[test]
fn a_game_started_with_the_board_seated_hears_it() {
    let (mut r, log) = sketch_room();
    board_hello(&mut r, 1, None);
    start(&mut r);
    assert_eq!(log.lock().unwrap().presence, [true]);
    let (mut r, log) = sketch_room();
    start(&mut r);
    assert_eq!(log.lock().unwrap().presence, [false]);
}

#[test]
fn leaving_the_board_in_the_lobby_frees_it() {
    let (mut r, _) = sketch_room();
    board_hello(&mut r, 1, None);
    hello(&mut r, 1, "Ana", None);
    assert!(!lobby_board_flag(&mut r));
}

#[test]
fn the_tv_lobby_shows_a_connected_board() {
    let (mut r, _) = sketch_room();
    board_hello(&mut r, 1, None);
    assert!(lobby_board_flag(&mut r));
}

#[test]
fn the_board_sees_the_lobby_waiting_screen() {
    let (mut r, _) = sketch_room();
    board_hello(&mut r, 1, None);
    let msgs = flushed(&mut r, 1);
    let [ServerMsg::Board { screen: BoardScreen::Wait { title, can_leave: true, .. } }] = msgs.as_slice() else { panic!() };
    assert_eq!(title, "Zeichenbrett");
}
