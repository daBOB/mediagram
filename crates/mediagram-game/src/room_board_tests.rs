use super::sketch::{board_act, board_hello, flushed, sketch_room, start, token_of, tv_hello, lobby_board_flag};
use super::tests::{action, decode, hello};
use super::*;
use crate::protocol::{BoardAct, BoardScreen, Buzz};

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
