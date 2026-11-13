use std::sync::Arc;
use super::sketch::{board_act, board_hello, tv_hello};
use super::tests::{action, cfg, hello};
use super::*;
use crate::protocol::{BoardAct, DrawOp, GameKind, KritzeleiStage, QuizMode, TvScene, TvView};
use crate::titles::TitlePool;

fn kritzelei_room() -> Room {
    let factory = crate::factory::game_factory(Arc::new(TitlePool::new(vec![], false)), Arc::new(Vec::new()), None, Arc::default());
    Room::new(cfg(8), factory)
}

fn stage(r: &mut Room, tv: ConnId) -> KritzeleiStage {
    match r.view_for(tv, 0.0) {
        Some(ServerMsg::Tv { view: TvView { scene: TvScene::Kritzelei { scene }, .. } }) => scene.stage,
        other => panic!("expected the Kritzelei scene, got {other:?}"),
    }
}

#[test]
fn a_room_with_a_board_plays_kritzelei_to_the_final_screen() {
    let mut r = kritzelei_room();
    hello(&mut r, 1, "Ana", None);
    hello(&mut r, 2, "Ben", None);
    board_hello(&mut r, 3, None);
    tv_hello(&mut r, 4);
    let configure = Action::Configure { game: GameKind::Kritzelei, quiz_mode: QuizMode::Mixed, kids_only: false, adult: false };
    assert!(action(&mut r, 1, configure).is_empty());
    assert!(action(&mut r, 4, Action::StartGame).is_empty());
    assert!(r.in_game());

    // Two players draw twice each, so four turns.
    let mut now = 0.0;
    for turn in 1..=4 {
        assert_eq!(stage(&mut r, 4), KritzeleiStage::Handover, "turn {turn}");
        assert!(board_act(&mut r, 3, BoardAct::Ready).is_empty());
        let op = DrawOp::Line { color: 0, width: 0, points: vec![10, 20, 30, 40] };
        assert!(board_act(&mut r, 3, BoardAct::Draw { op }).is_empty());
        assert!(board_act(&mut r, 3, BoardAct::Pick { player: Some(if turn % 2 == 1 { 1 } else { 0 }) }).is_empty());
        assert!(matches!(stage(&mut r, 4), KritzeleiStage::Reveal { .. }));
        now += 7_000.0;
        r.tick(now);
    }
    assert_eq!(stage(&mut r, 4), KritzeleiStage::Final);
    assert!(r.players().iter().all(|p| p.score > 0));
}
