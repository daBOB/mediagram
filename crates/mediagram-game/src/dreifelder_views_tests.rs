use crate::dreifelder::tests::{answer, game, next, opens_at, players, tick};
use crate::dreifelder::DreifelderGame;
use crate::game::{Game, ViewCtx};
use crate::protocol::{DreifelderStage, FieldPick, OutPlayer, PhoneScreen, PlayerId, Tone, TvScene};
use crate::room_seats::Player;

fn phone(g: &DreifelderGame, ps: &[Player], p: PlayerId) -> PhoneScreen {
    g.phone_screen(&ViewCtx { now: 0.0, players: ps, leader: Some(0) }, p)
}

fn tv(g: &DreifelderGame, ps: &[Player]) -> crate::protocol::DreifelderScene {
    let TvScene::Dreifelder { scene } = g.tv_scene(&ViewCtx { now: 0.0, players: ps, leader: Some(0) }) else { panic!() };
    scene
}

fn outcome(screen: PhoneScreen) -> (String, Tone) {
    let PhoneScreen::Outcome { title, tone, points, .. } = screen else { panic!("no outcome: {screen:?}") };
    assert_eq!(points, 0, "Dreifelder pays no points per question");
    (title, tone)
}

#[test]
fn alive_and_out_players_both_get_three_fields() {
    let mut ps = players(3);
    let mut g = game(12, &ps);
    let t = answer(&mut g, &mut ps, &[0, 1], &[2], 500.0);
    next(&mut g, &mut ps, t);
    let PhoneScreen::Choice { prompt, options, closes_at, picked, .. } = phone(&g, &ps, 0) else { panic!() };
    assert_eq!(options.len(), 3);
    assert_eq!(prompt.as_deref(), Some(g.round().category.as_str()));
    assert!(closes_at.is_some() && picked.is_none());
    let PhoneScreen::Choice { prompt, options, .. } = phone(&g, &ps, 2) else { panic!() };
    assert_eq!((prompt.as_deref(), options.len()), (Some("Du bist raus – rate mit"), 3));
    answer(&mut g, &mut ps, &[0], &[], 500.0);
    assert!(matches!(phone(&g, &ps, 0), PhoneScreen::Choice { picked: Some(_), .. }));
}

#[test]
fn the_reveal_tells_each_phone_where_it_stands() {
    let mut ps = players(6);
    let mut g = game(12, &ps);
    let t = answer(&mut g, &mut ps, &[0, 1, 2, 5], &[3, 4], 500.0);
    let t = next(&mut g, &mut ps, t);
    // Cem does not pick; Dana and Eli guess as ghosts.
    let t = answer(&mut g, &mut ps, &[0, 3, 5], &[1, 4], 500.0).max(t);
    let close = opens_at(&g) + 12_000.0;
    tick(&mut g, &mut ps, close);
    assert_eq!(outcome(phone(&g, &ps, 0)), ("Weiter!".into(), Tone::Good));
    assert_eq!(outcome(phone(&g, &ps, 1)), ("Raus!".into(), Tone::Bad));
    assert_eq!(outcome(phone(&g, &ps, 2)), ("Raus!".into(), Tone::Bad), "no pick is out too");
    assert_eq!(outcome(phone(&g, &ps, 3)), ("Richtig, ohne Wertung".into(), Tone::Neutral));
    assert_eq!(outcome(phone(&g, &ps, 4)), ("Falsch, ohne Wertung".into(), Tone::Neutral));
    let t = next(&mut g, &mut ps, t.max(close));
    let t = answer(&mut g, &mut ps, &[0], &[5], 500.0).max(t);
    tick(&mut g, &mut ps, t);
    assert_eq!(outcome(phone(&g, &ps, 0)), ("Richtig!".into(), Tone::Good), "the last one in goes on to the final, not on");
}

#[test]
fn when_everyone_alive_is_wrong_nobody_is_told_to_leave() {
    let mut ps = players(2);
    let mut g = game(12, &ps);
    let t = answer(&mut g, &mut ps, &[], &[0, 1], 500.0);
    tick(&mut g, &mut ps, t);
    assert_eq!(outcome(phone(&g, &ps, 0)), ("Alle daneben – alle bleiben drin".into(), Tone::Neutral));
}

#[test]
fn the_tv_shows_the_round_the_fields_and_who_is_out() {
    let mut ps = players(3);
    let mut g = game(12, &ps);
    let scene = tv(&g, &ps);
    assert_eq!((scene.round, scene.rounds, scene.alive.clone()), (1, 12, vec![0, 1, 2]));
    let DreifelderStage::Question { question, .. } = scene.stage else { panic!() };
    assert_eq!(question.options.len(), 3);
    let close = opens_at(&g) + 12_000.0;
    answer(&mut g, &mut ps, &[0], &[1], 500.0);
    tick(&mut g, &mut ps, close);
    let scene = tv(&g, &ps);
    assert_eq!(scene.out, vec![OutPlayer { player: 1, round: 1 }, OutPlayer { player: 2, round: 1 }]);
    let DreifelderStage::Reveal { picks, eliminated, correct, .. } = scene.stage else { panic!() };
    assert_eq!(eliminated, vec![1, 2]);
    assert_eq!(picks[0], FieldPick { player: 0, choice: Some(correct) });
    assert_eq!(picks[2], FieldPick { player: 2, choice: None });
    next(&mut g, &mut ps, close);
    assert!(matches!(tv(&g, &ps).stage, DreifelderStage::Final { order } if order == vec![0, 1, 2]));
    assert!(matches!(phone(&g, &ps, 1), PhoneScreen::Final { rank: 2, of: 3, score: 100, .. }));
}
