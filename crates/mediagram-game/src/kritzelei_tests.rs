use super::*;
use crate::protocol::{Clock, KritzeleiScene, KritzeleiStage};

const WORDS: [&str; 8] = ["Hund", "Katze", "Haus", "Baum", "Sonne", "Mond", "Stern", "Boot"];

pub(crate) fn players(n: u8) -> Vec<Player> {
    (0..n).map(|i| Player::for_test(i, ["Ana", "Bo", "Cem", "Dana"][usize::from(i)], true)).collect()
}

pub(crate) fn game(ps: &[Player]) -> KritzeleiGame {
    KritzeleiGame::new(KritzeleiConfig::default(), WORDS.to_vec(), ps, 0.0)
}

pub(crate) fn act(g: &mut KritzeleiGame, ps: &mut [Player], now: f64, act: BoardAct) -> Result<(), ActionError> {
    g.on_board(&mut GameCtx { now, players: ps }, &act)
}

pub(crate) fn tick(g: &mut KritzeleiGame, ps: &mut [Player], now: f64) {
    g.on_tick(&mut GameCtx { now, players: ps });
}

pub(crate) fn scene(g: &KritzeleiGame, ps: &[Player]) -> KritzeleiScene {
    let TvScene::Kritzelei { scene } = g.tv_scene(&ViewCtx { now: 0.0, players: ps, leader: Some(0) }) else { panic!("not Kritzelei") };
    scene
}

pub(crate) fn clock(g: &KritzeleiGame, ps: &[Player]) -> Clock {
    match scene(g, ps).stage {
        KritzeleiStage::Drawing { clock } | KritzeleiStage::Picking { clock } => clock,
        other => panic!("no clock in {other:?}"),
    }
}

pub(crate) fn pick(player: Option<PlayerId>) -> BoardAct {
    BoardAct::Pick { player }
}

fn line(points: Vec<u16>) -> BoardAct {
    BoardAct::Draw { op: DrawOp::Line { color: 0, width: 0, points } }
}

#[test]
fn a_pick_gives_two_and_one() {
    let mut ps = players(3);
    let mut g = game(&ps);
    act(&mut g, &mut ps, 0.0, BoardAct::Ready).unwrap();
    act(&mut g, &mut ps, 10_000.0, BoardAct::Guessed).unwrap();
    assert_eq!(act(&mut g, &mut ps, 12_000.0, pick(Some(2))), Ok(()));
    assert_eq!(ps.iter().map(|p| p.score).collect::<Vec<_>>(), [1, 0, 2]);
    let stage = KritzeleiStage::Reveal { word: "Hund".into(), guesser: Some(2), ends_at: 18_000.0 };
    assert_eq!(scene(&g, &ps).stage, stage);
}

#[test]
fn a_pick_straight_from_drawing_counts_too() {
    let mut ps = players(2);
    let mut g = game(&ps);
    act(&mut g, &mut ps, 0.0, BoardAct::Ready).unwrap();
    assert_eq!(act(&mut g, &mut ps, 5_000.0, pick(Some(1))), Ok(()));
    assert_eq!((ps[0].score, ps[1].score), (1, 2));
}

#[test]
fn niemand_gives_nothing() {
    let mut ps = players(3);
    let mut g = game(&ps);
    act(&mut g, &mut ps, 0.0, BoardAct::Ready).unwrap();
    assert_eq!(act(&mut g, &mut ps, 3_000.0, pick(None)), Ok(()));
    assert!(ps.iter().all(|p| p.score == 0));
    assert_eq!(scene(&g, &ps).stage, KritzeleiStage::Reveal { word: "Hund".into(), guesser: None, ends_at: 9_000.0 });
}

#[test]
fn the_drawer_or_a_stranger_cannot_be_picked() {
    let mut ps = players(3);
    ps[2].conn = None;
    let mut g = game(&ps);
    act(&mut g, &mut ps, 0.0, BoardAct::Ready).unwrap();
    act(&mut g, &mut ps, 1_000.0, BoardAct::Guessed).unwrap();
    assert!(matches!(act(&mut g, &mut ps, 2_000.0, pick(Some(0))), Err(ActionError::NotAllowed(_))), "the drawer");
    assert!(matches!(act(&mut g, &mut ps, 2_000.0, pick(Some(9))), Err(ActionError::NotAllowed(_))), "nobody seated");
    assert!(matches!(scene(&g, &ps).stage, KritzeleiStage::Picking { .. }));
    assert!(ps.iter().all(|p| p.score == 0));
    assert_eq!(act(&mut g, &mut ps, 3_000.0, pick(Some(2))), Ok(()), "seated with the phone off still counts");
}

#[test]
fn a_second_pick_scores_nothing() {
    let mut ps = players(3);
    let mut g = game(&ps);
    act(&mut g, &mut ps, 0.0, BoardAct::Ready).unwrap();
    act(&mut g, &mut ps, 1_000.0, pick(Some(1))).unwrap();
    assert_eq!(act(&mut g, &mut ps, 1_100.0, pick(Some(1))), Err(ActionError::Ignored));
    assert_eq!(act(&mut g, &mut ps, 1_200.0, pick(Some(2))), Err(ActionError::Ignored));
    assert_eq!(act(&mut g, &mut ps, 1_300.0, BoardAct::Guessed), Err(ActionError::Ignored));
    assert_eq!(ps.iter().map(|p| p.score).collect::<Vec<_>>(), [1, 2, 0]);
}

#[test]
fn six_seconds_later_the_next_drawer_is_up() {
    let mut ps = players(2);
    let mut g = game(&ps);
    act(&mut g, &mut ps, 0.0, BoardAct::Ready).unwrap();
    act(&mut g, &mut ps, 1_000.0, line(vec![1, 2, 3, 4])).unwrap();
    act(&mut g, &mut ps, 2_000.0, pick(None)).unwrap();
    assert_eq!(g.drawing().map(|(turn, ops)| (turn, ops.len())), Some((1, 1)), "the reveal keeps the finished drawing");
    tick(&mut g, &mut ps, 7_999.0);
    assert!(matches!(scene(&g, &ps).stage, KritzeleiStage::Reveal { .. }));
    tick(&mut g, &mut ps, 8_000.0);
    let s = scene(&g, &ps);
    assert_eq!((s.stage, s.turn, s.drawer), (KritzeleiStage::Handover, 2, g.turns[1]));
    assert_eq!(g.drawing(), Some((2, &[][..])));
    assert_eq!(act(&mut g, &mut ps, 8_100.0, pick(Some(0))), Err(ActionError::Ignored), "a pick after the reveal scores nothing");
    assert_eq!((ps[0].score, ps[1].score), (0, 0));
    act(&mut g, &mut ps, 10_000.0, BoardAct::Ready).unwrap();
    assert_eq!(clock(&g, &ps).ends_at, Some(100_000.0), "a fresh clock");
    act(&mut g, &mut ps, 11_000.0, pick(Some(0))).unwrap();
    assert_eq!(scene(&g, &ps).stage, KritzeleiStage::Reveal { word: "Katze".into(), guesser: Some(0), ends_at: 17_000.0 }, "a new word");
}

#[test]
fn the_last_reveal_ends_the_game() {
    let mut ps = players(2);
    let mut g = game(&ps);
    for now in [0.0, 1.0, 2.0] {
        act(&mut g, &mut ps, now, BoardAct::Skip).unwrap();
    }
    assert_eq!(scene(&g, &ps).turn, 4);
    act(&mut g, &mut ps, 10.0, BoardAct::Ready).unwrap();
    act(&mut g, &mut ps, 20.0, pick(None)).unwrap();
    assert!(!g.is_finished());
    tick(&mut g, &mut ps, 6_020.0);
    assert!(g.is_finished());
    assert_eq!(scene(&g, &ps).stage, KritzeleiStage::Final);
    assert_eq!(g.drawing(), None);
    assert_eq!(act(&mut g, &mut ps, 7_000.0, BoardAct::Ready), Err(ActionError::Ignored));
}

#[test]
fn skip_passes_the_turn_without_points() {
    let mut ps = players(3);
    let mut g = game(&ps);
    assert_eq!(act(&mut g, &mut ps, 0.0, BoardAct::Skip), Ok(()));
    let s = scene(&g, &ps);
    assert_eq!((s.stage, s.turn, s.drawer), (KritzeleiStage::Handover, 2, 1));
    assert!(ps.iter().all(|p| p.score == 0));
    act(&mut g, &mut ps, 1_000.0, BoardAct::Ready).unwrap();
    assert_eq!(act(&mut g, &mut ps, 2_000.0, BoardAct::Skip), Err(ActionError::Ignored), "only before the turn is taken");
}

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
    let cfg = KritzeleiConfig { max_points: 3, ..KritzeleiConfig::default() };
    let mut g = KritzeleiGame::new(cfg, WORDS.to_vec(), &ps, 0.0);
    act(&mut g, &mut ps, 0.0, BoardAct::Ready).unwrap();
    assert!(matches!(act(&mut g, &mut ps, 10.0, line(vec![1, 2, 3])), Err(ActionError::NotAllowed(_))), "odd length");
    assert!(matches!(act(&mut g, &mut ps, 20.0, line(vec![1, 10_001])), Err(ActionError::NotAllowed(_))), "off the paper");
    assert_eq!(act(&mut g, &mut ps, 30.0, line(vec![1, 1, 2, 2])), Ok(()));
    assert_eq!(act(&mut g, &mut ps, 40.0, line(vec![5, 5, 6, 6])), Err(ActionError::Ignored), "over the cap");
    let more = BoardAct::Draw { op: DrawOp::More { points: vec![7, 7] } };
    assert_eq!(act(&mut g, &mut ps, 50.0, more), Err(ActionError::Ignored), "never lands on the stroke before the dropped one");
    assert_eq!(g.drawing().map(|(_, ops)| ops.len()), Some(1));
}

#[test]
fn phones_have_nothing_to_send() {
    let mut ps = players(2);
    let mut g = game(&ps);
    let r = g.on_action(&mut GameCtx { now: 0.0, players: &mut ps }, 1, &Action::GuessPrice { cents: 100 });
    assert_eq!(r, Err(ActionError::Ignored));
}
