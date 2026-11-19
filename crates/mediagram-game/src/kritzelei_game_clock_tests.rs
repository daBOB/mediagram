//! The turn's clock inside the game: when it starts, when it stands, when it runs out.

use super::*;
use crate::kritzelei::tests::{act, clock, game, pick, players, scene, tick};
use crate::protocol::{Clock, KritzeleiStage};

#[test]
fn ich_bin_dran_starts_the_clock() {
    let mut ps = players(2);
    let mut g = game(&ps);
    assert_eq!(scene(&g, &ps).stage, KritzeleiStage::Handover);
    assert_eq!(act(&mut g, &mut ps, 1_000.0, BoardAct::Ready), Ok(()));
    let s = scene(&g, &ps);
    assert_eq!((s.turn, s.turns, s.drawer, s.board), (1, 4, 0, true), "two players draw twice each");
    assert_eq!(clock(&g, &ps), Clock { ends_at: Some(91_000.0), left_ms: 90_000.0, total_ms: 90_000.0 });
    assert_eq!(act(&mut g, &mut ps, 2_000.0, BoardAct::Ready), Err(ActionError::Ignored), "already drawing");
}

#[test]
fn the_clock_stands_while_the_list_is_open() {
    let mut ps = players(2);
    let mut g = game(&ps);
    act(&mut g, &mut ps, 0.0, BoardAct::Ready).unwrap();
    assert_eq!(act(&mut g, &mut ps, 10_000.0, BoardAct::Guessed), Ok(()));
    let c = clock(&g, &ps);
    assert!(matches!(scene(&g, &ps).stage, KritzeleiStage::Picking { .. }));
    assert_eq!((c.ends_at, c.left_ms), (None, 80_000.0));
    assert_eq!(act(&mut g, &mut ps, 20_000.0, BoardAct::KeepDrawing), Ok(()));
    assert_eq!(clock(&g, &ps).ends_at, Some(100_000.0));
}

#[test]
fn the_clock_stands_while_the_board_is_gone() {
    let mut ps = players(2);
    let mut g = game(&ps);
    act(&mut g, &mut ps, 0.0, BoardAct::Ready).unwrap();
    g.on_board_presence(10_000.0, false);
    assert_eq!((clock(&g, &ps).ends_at, scene(&g, &ps).board), (None, false));
    g.on_board_presence(30_000.0, true);
    assert_eq!((clock(&g, &ps).ends_at, scene(&g, &ps).board), (Some(110_000.0), true));
}

#[test]
fn the_board_announced_twice_moves_the_clock_once() {
    let mut ps = players(2);
    let mut g = game(&ps);
    g.on_board_presence(0.0, true);
    act(&mut g, &mut ps, 0.0, BoardAct::Ready).unwrap();
    g.on_board_presence(5_000.0, true);
    assert_eq!(clock(&g, &ps).ends_at, Some(90_000.0), "back without having left");
    g.on_board_presence(10_000.0, false);
    g.on_board_presence(20_000.0, false);
    g.on_board_presence(30_000.0, true);
    g.on_board_presence(40_000.0, true);
    assert_eq!(clock(&g, &ps).ends_at, Some(110_000.0), "gone twice, back twice: paused from the first leave to the first return");
    act(&mut g, &mut ps, 50_000.0, BoardAct::Guessed).unwrap();
    g.on_board_presence(55_000.0, false);
    g.on_board_presence(60_000.0, true);
    assert_eq!((clock(&g, &ps).ends_at, clock(&g, &ps).left_ms), (None, 60_000.0), "the board coming back leaves the list's clock standing");
}

#[test]
fn time_up_opens_the_list_for_good() {
    let mut ps = players(2);
    let mut g = game(&ps);
    act(&mut g, &mut ps, 0.0, BoardAct::Ready).unwrap();
    tick(&mut g, &mut ps, 89_999.0);
    assert!(matches!(scene(&g, &ps).stage, KritzeleiStage::Drawing { .. }));
    tick(&mut g, &mut ps, 90_001.0);
    assert_eq!(clock(&g, &ps), Clock { ends_at: None, left_ms: 0.0, total_ms: 90_000.0 });
    assert!(matches!(scene(&g, &ps).stage, KritzeleiStage::Picking { .. }));
    assert_eq!(act(&mut g, &mut ps, 90_100.0, BoardAct::KeepDrawing), Err(ActionError::Ignored));
    assert!(matches!(scene(&g, &ps).stage, KritzeleiStage::Picking { .. }));
    assert_eq!(act(&mut g, &mut ps, 91_000.0, pick(None)), Ok(()), "a shout in the last second still counts");
}
