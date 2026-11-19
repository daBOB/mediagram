//! Strokes from the board: when they count, and what is refused or dropped.

use crate::game::{ActionError, Game};
use crate::kritzelei::tests::{WORDS, act, game, line, players};
use crate::kritzelei::{KritzeleiConfig, KritzeleiGame};
use crate::protocol::{BoardAct, DrawOp};
use crate::room_seats::Player;

#[test]
fn strokes_count_only_while_drawing() {
    let mut ps = players(2);
    let mut g = game(&ps);
    assert_eq!(act(&mut g, &mut ps, 0.0, line(vec![1, 1])), Err(ActionError::Ignored), "in the hand-over");
    act(&mut g, &mut ps, 0.0, BoardAct::Ready).unwrap();
    assert_eq!(act(&mut g, &mut ps, 100.0, line(vec![1, 1])), Ok(()));
    assert_eq!(act(&mut g, &mut ps, 150.0, BoardAct::Draw { op: DrawOp::More { points: vec![2, 2] } }), Ok(()));
    act(&mut g, &mut ps, 200.0, BoardAct::Guessed).unwrap();
    assert_eq!(act(&mut g, &mut ps, 300.0, line(vec![3, 3])), Err(ActionError::Ignored), "while picking");
    let ops = [DrawOp::Line { color: 0, width: 0, points: vec![1, 1] }, DrawOp::More { points: vec![2, 2] }];
    assert_eq!(g.drawing(), Some((1, &ops[..])));
}

#[test]
fn garbage_is_refused_and_a_full_turn_takes_no_more() {
    let mut ps = players(2);
    let mut g = small_turn(&ps);
    act(&mut g, &mut ps, 0.0, BoardAct::Ready).unwrap();
    assert!(matches!(act(&mut g, &mut ps, 10.0, line(vec![1, 2, 3])), Err(ActionError::NotAllowed(_))), "odd length");
    assert!(matches!(act(&mut g, &mut ps, 20.0, line(vec![1, 10_001])), Err(ActionError::NotAllowed(_))), "off the paper");
    assert_eq!(act(&mut g, &mut ps, 30.0, line(vec![1, 1, 2, 2])), Ok(()));
    assert_eq!(act(&mut g, &mut ps, 40.0, line(vec![5, 5, 6, 6])), Err(ActionError::Ignored), "over the cap");
    let more = BoardAct::Draw { op: DrawOp::More { points: vec![7, 7] } };
    assert_eq!(act(&mut g, &mut ps, 50.0, more), Err(ActionError::Ignored), "never lands on the stroke before the dropped one");
    assert_eq!(g.drawing().map(|(_, ops)| ops.len()), Some(1));
}

fn small_turn(ps: &[Player]) -> KritzeleiGame {
    KritzeleiGame::new(KritzeleiConfig { max_points: 3, ..KritzeleiConfig::default() }, WORDS.to_vec(), ps, 0.0)
}

#[test]
fn an_undo_flood_stops_at_the_cap() {
    let mut ps = players(2);
    let mut g = small_turn(&ps);
    act(&mut g, &mut ps, 0.0, BoardAct::Ready).unwrap();
    act(&mut g, &mut ps, 10.0, line(vec![1, 1])).unwrap();
    for now in 0..50 {
        let _ = act(&mut g, &mut ps, 20.0 + f64::from(now), BoardAct::Draw { op: DrawOp::Undo });
    }
    assert_eq!(g.drawing().map(|(_, ops)| ops.len()), Some(3), "no more ops than the turn's points cap");
}

#[test]
fn a_refused_line_breaks_the_stroke() {
    let mut ps = players(2);
    let mut g = game(&ps);
    let more = |x: u16| BoardAct::Draw { op: DrawOp::More { points: vec![x, x] } };
    act(&mut g, &mut ps, 0.0, BoardAct::Ready).unwrap();
    act(&mut g, &mut ps, 10.0, line(vec![1, 1])).unwrap();
    let bad = BoardAct::Draw { op: DrawOp::Line { color: 9, width: 0, points: vec![2, 2] } };
    assert!(matches!(act(&mut g, &mut ps, 20.0, bad), Err(ActionError::NotAllowed(_))));
    assert_eq!(act(&mut g, &mut ps, 30.0, more(3)), Err(ActionError::Ignored), "never lands on the stroke before the refused one");
    assert_eq!(act(&mut g, &mut ps, 40.0, line(vec![4, 4])), Ok(()));
    assert_eq!(act(&mut g, &mut ps, 50.0, more(5)), Ok(()), "the next accepted line mends it");
    assert_eq!(g.drawing().map(|(_, ops)| ops.len()), Some(3));
}
