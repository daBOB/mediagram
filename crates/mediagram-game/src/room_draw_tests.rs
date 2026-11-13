use super::sketch::{board_act, board_hello, draws, flushed, line, sketch_room, start, tv_hello};
use super::tests::{action, decode};
use super::*;
use crate::protocol::{BoardAct, ServerMsg};

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
fn a_second_game_starts_the_drawing_afresh_whatever_the_tv_last_saw() {
    let (mut r, log) = sketch_room();
    log.lock().unwrap().start.1 = vec![line(); 2];
    start(&mut r);
    flushed(&mut r, 11);
    r.back_to_lobby();
    log.lock().unwrap().start.1 = vec![line(); 3];
    // No flush between the games: the cursor must not outlive the old game.
    assert!(action(&mut r, 11, Action::StartGame).is_empty());
    assert_eq!(draws(&flushed(&mut r, 11)), [(1, 0, 3)]);
}
