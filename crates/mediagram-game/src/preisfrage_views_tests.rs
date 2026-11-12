use super::*;
use crate::game::{Game, GameCtx};
use crate::preisfrage::PreisfrageConfig;
use crate::preisfrage::tests::players;
use crate::protocol::Action;

#[test]
fn euros_read_the_german_way() {
    assert_eq!([euro(899), euro(100_000), euro(12_345_678)], ["8,99 €", "1.000,00 €", "123.456,78 €"]);
}

#[test]
fn the_reveal_tells_each_phone_its_own_result_and_the_tv_everyones() {
    let mut ps = players(2);
    let article = crate::articles::Article { asin: "B000000001".into(), name: "Halter".into(), title: "Halter".into(), cents: 1000, reviews: 0 };
    let mut g = PreisfrageGame::new(PreisfrageConfig::default(), vec![article], "12.11.".into(), &ps, 0.0);
    g.on_action(&mut GameCtx { now: 4_000.0, players: &mut ps }, 0, &Action::GuessPrice { cents: 900 }).unwrap();
    g.on_tick(&mut GameCtx { now: 23_000.0, players: &mut ps });
    let view = ViewCtx { now: 23_000.0, players: &ps, leader: Some(0) };

    let PhoneScreen::Outcome { title, text, tone, points, .. } = g.phone_screen(&view, 0) else { panic!() };
    assert_eq!((title.as_str(), text.as_deref(), tone, points), ("10,00 €", Some("Dein Tipp: 9,00 € · am nächsten dran"), Tone::Good, 900));
    let PhoneScreen::Outcome { text, tone, points, .. } = g.phone_screen(&view, 1) else { panic!() };
    assert_eq!((text.as_deref(), tone, points), (Some("Kein Tipp"), Tone::Muted, 0));

    let TvScene::Preisfrage { scene } = g.tv_scene(&view) else { panic!() };
    let PreisfrageStage::Reveal { cents, checked, rows, .. } = scene.stage else { panic!() };
    assert_eq!((cents, checked.as_str(), rows.len()), (1000, "12.11.", 2));
    assert_eq!(scene.article.photo, "/img/article/B000000001");
}

#[test]
fn the_final_ranks_by_total_and_ties_share_a_place() {
    let mut ps = players(3);
    let article = crate::articles::Article { asin: "B000000001".into(), name: "Halter".into(), title: "Halter".into(), cents: 1000, reviews: 0 };
    let mut g = PreisfrageGame::new(PreisfrageConfig::default(), vec![article], "12.11.".into(), &ps, 0.0);
    for (p, cents) in [(0, 900), (1, 1100), (2, 1500)] {
        g.on_action(&mut GameCtx { now: 4_000.0, players: &mut ps }, p, &Action::GuessPrice { cents }).unwrap();
    }
    g.on_tick(&mut GameCtx { now: 4_000.0, players: &mut ps });
    g.on_tick(&mut GameCtx { now: 11_000.0, players: &mut ps });
    assert!(g.is_finished());
    let view = ViewCtx { now: 11_000.0, players: &ps, leader: Some(0) };
    let PhoneScreen::Final { rank, of, score, can_restart } = g.phone_screen(&view, 1) else { panic!() };
    assert_eq!((rank, of, score, can_restart), (1, 3, 900, false), "Ana and Ben share first place");
    let TvScene::Preisfrage { scene } = g.tv_scene(&view) else { panic!() };
    let PreisfrageStage::Final { standings } = scene.stage else { panic!() };
    assert_eq!(standings.iter().map(|s| (s.player, s.place)).collect::<Vec<_>>(), [(0, 1), (1, 1), (2, 3)]);
}
