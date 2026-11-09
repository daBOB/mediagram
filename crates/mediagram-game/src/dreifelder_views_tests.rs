use crate::dreifelder::tests::{answer, game, next, opens_at, players, tick};
use crate::dreifelder::DreifelderGame;
use crate::game::{Game, ViewCtx};
use crate::protocol::{
    AnswerColor, ChoiceStatus, DreifelderStage, FieldPick, OutPlayer, OutcomeKey, PhoneScreen, PlayerId, Tone, TvScene,
};
use crate::room_seats::Player;

fn phone(g: &DreifelderGame, ps: &[Player], p: PlayerId) -> PhoneScreen {
    g.phone_screen(&ViewCtx { now: 0.0, players: ps, leader: Some(0) }, p)
}

fn tv(g: &DreifelderGame, ps: &[Player]) -> crate::protocol::DreifelderScene {
    let TvScene::Dreifelder { scene } = g.tv_scene(&ViewCtx { now: 0.0, players: ps, leader: Some(0) }) else { panic!() };
    scene
}

fn outcome(screen: PhoneScreen) -> (String, Tone) {
    let (title, tone, ..) = reveal(screen);
    (title, tone)
}

/// Title, tone, the line under it and the small keys of a reveal.
fn reveal(screen: PhoneScreen) -> (String, Tone, Option<String>, Vec<OutcomeKey>) {
    let PhoneScreen::Outcome { title, tone, points, text, keys, show_score } = screen else { panic!("no outcome: {screen:?}") };
    assert_eq!(points, 0, "Dreifelder pays no points per question");
    assert!(!show_score, "Dreifelder scores only at the end, so no running score");
    (title, tone, text, keys)
}

/// Field `i` of the current question as a reveal key.
fn key(g: &DreifelderGame, i: u8, caption: &str, crossed: bool) -> OutcomeKey {
    let label = g.round().options[usize::from(i)].clone();
    OutcomeKey { caption: caption.into(), number: i + 1, label, color: AnswerColor::ALL[usize::from(i)], crossed }
}

fn status(screen: PhoneScreen) -> (Option<String>, bool, Option<ChoiceStatus>) {
    let PhoneScreen::Choice { prompt, numbered, status, .. } = screen else { panic!("no choice: {screen:?}") };
    (prompt, numbered, status)
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
    assert_eq!((prompt.as_deref(), options.len()), (Some("Raus – rate mit"), 3));
    let still_in = ChoiceStatus { text: "Du bist drin · noch 2 von 3".into(), out: false };
    assert_eq!(status(phone(&g, &ps, 0)), (Some(g.round().category.clone()), true, Some(still_in)));
    let ghost = ChoiceStatus { text: "Zählt für deinen Platz · noch 2 von 3 dabei".into(), out: true };
    assert_eq!(status(phone(&g, &ps, 2)).2, Some(ghost), "an out player sees faded buttons");
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
    assert_eq!(outcome(phone(&g, &ps, 3)), ("Richtig".into(), Tone::Muted));
    assert_eq!(outcome(phone(&g, &ps, 4)), ("Falsch".into(), Tone::Muted));
    let t = next(&mut g, &mut ps, t.max(close));
    let t = answer(&mut g, &mut ps, &[0], &[5], 500.0).max(t);
    tick(&mut g, &mut ps, t);
    assert_eq!(outcome(phone(&g, &ps, 0)), ("Richtig!".into(), Tone::Good), "the last one in goes on to the final, not on");
}

#[test]
fn the_reveal_shows_the_right_field_and_a_wrong_pick_struck_through() {
    let mut ps = players(6);
    let mut g = game(12, &ps);
    let t = answer(&mut g, &mut ps, &[0, 1, 2, 5], &[3, 4], 500.0);
    let t = next(&mut g, &mut ps, t);
    // Ben picks wrong and Cem not at all, so both go out; Dana and Eli guess as ghosts.
    let t = answer(&mut g, &mut ps, &[0, 3, 5], &[1, 4], 500.0).max(t);
    let close = opens_at(&g) + 12_000.0;
    tick(&mut g, &mut ps, close);
    let c = g.round().correct;
    let right = key(&g, c, "Richtig", false);
    let missed = key(&g, (c + 1) % 3, "Dein Tipp", true);
    let left = Some("Noch 2 von 6 dabei".to_string());
    assert_eq!(reveal(phone(&g, &ps, 0)), ("Weiter!".into(), Tone::Good, left, vec![right.clone()]));
    let from_now_on = Some("Rate weiter – Treffer zählen für deinen Platz.".to_string());
    assert_eq!(reveal(phone(&g, &ps, 1)), ("Raus!".into(), Tone::Bad, from_now_on.clone(), vec![missed.clone(), right.clone()]));
    assert_eq!(reveal(phone(&g, &ps, 2)).3, vec![right.clone()], "no pick, nothing to strike");
    let unscored = Some("Zählt für deinen Platz · noch 2 von 6 dabei".to_string());
    assert_eq!(reveal(phone(&g, &ps, 4)), ("Falsch".into(), Tone::Muted, unscored, vec![missed, right]));
    // Fe goes out in the last round: the game is over, nothing left to guess.
    let t = next(&mut g, &mut ps, t.max(close));
    let t = answer(&mut g, &mut ps, &[0], &[5], 500.0).max(t);
    tick(&mut g, &mut ps, t);
    assert_eq!(reveal(phone(&g, &ps, 5)).2, None);
    assert_eq!(reveal(phone(&g, &ps, 0)).2, None);
}

#[test]
fn when_everyone_alive_is_wrong_nobody_is_told_to_leave() {
    let mut ps = players(2);
    let mut g = game(12, &ps);
    let t = answer(&mut g, &mut ps, &[], &[0, 1], 500.0);
    tick(&mut g, &mut ps, t);
    let c = g.round().correct;
    let keys = vec![key(&g, (c + 1) % 3, "Dein Tipp", true), key(&g, c, "Richtig", false)];
    let left = Some("Noch 2 von 2 dabei".to_string());
    assert_eq!(reveal(phone(&g, &ps, 0)), ("Alle daneben – alle bleiben drin".into(), Tone::Neutral, left, keys));
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

#[test]
fn a_leaver_out_in_an_all_wrong_round_is_not_told_everyone_stays() {
    let mut ps = players(3);
    let mut g = game(12, &ps);
    ps[2] = Player::for_test(2, "Cem", false);
    let t = answer(&mut g, &mut ps, &[], &[0, 1], 500.0);
    tick(&mut g, &mut ps, t);
    let (title, _, text, _) = reveal(phone(&g, &ps, 0));
    assert_eq!((title.as_str(), text.as_deref()), ("Alle daneben", Some("Noch 2 von 3 dabei")), "Cem left and is out");
}
