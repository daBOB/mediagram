use super::*;
use crate::articles::Article;
use crate::protocol::{PreisfrageStage, TvScene};

pub(crate) fn players(n: u8) -> Vec<Player> {
    (0..n).map(|i| Player::for_test(i, ["Ana", "Ben", "Cem", "Dana"][usize::from(i)], true)).collect()
}

fn articles(n: u32) -> Vec<Article> {
    (0..n).map(|i| Article { asin: format!("B{i:09}"), name: format!("Ding {i}"), title: format!("Ding {i}"), cents: 1000 * (i + 1), reviews: 0 }).collect()
}

fn game(n: u32, players: &[Player]) -> PreisfrageGame {
    PreisfrageGame::new(PreisfrageConfig::default(), articles(n), "12.11.".into(), players, 0.0)
}

fn guess(g: &mut PreisfrageGame, players: &mut [Player], now: f64, player: PlayerId, cents: u32) -> Result<(), ActionError> {
    g.on_action(&mut GameCtx { now, players }, player, &Action::GuessPrice { cents })
}

fn tick(g: &mut PreisfrageGame, players: &mut [Player], now: f64) {
    g.on_tick(&mut GameCtx { now, players });
}

fn window(g: &PreisfrageGame) -> (f64, f64) {
    let Stage::Guessing { opens_at, closes_at } = g.stage else { panic!("not guessing: {:?}", g.stage) };
    (opens_at, closes_at)
}

#[test]
fn intro_guessing_reveal_for_each_article_then_the_final() {
    let mut ps = players(2);
    let mut g = game(2, &ps);
    assert_eq!(window(&g), (3_000.0, 23_000.0), "3 s with the article alone, then 20 s to guess");
    tick(&mut g, &mut ps, 23_000.0);
    assert_eq!(g.stage, Stage::Reveal { ends_at: 30_000.0 }, "7 s of reveal");
    tick(&mut g, &mut ps, 30_000.0);
    assert_eq!((g.index, window(&g)), (1, (33_000.0, 53_000.0)));
    tick(&mut g, &mut ps, 53_000.0);
    tick(&mut g, &mut ps, 60_000.0);
    assert!(g.is_finished());
}

#[test]
fn a_guess_is_taken_once_while_the_keypads_are_open() {
    let mut ps = players(2);
    let mut g = game(1, &ps);
    assert_eq!(guess(&mut g, &mut ps, 1_000.0, 0, 500), Err(ActionError::Ignored), "the keypads are not open yet");
    assert_eq!(guess(&mut g, &mut ps, 4_000.0, 0, 0), Err(ActionError::NotAllowed("Das ist kein Preis.")));
    assert_eq!(guess(&mut g, &mut ps, 4_000.0, 0, MAX_CENTS + 1), Err(ActionError::NotAllowed("Das ist kein Preis.")));
    assert_eq!(guess(&mut g, &mut ps, 4_000.0, 0, 900), Ok(()));
    assert_eq!(guess(&mut g, &mut ps, 5_000.0, 0, 1000), Err(ActionError::Ignored), "one locked guess");
    tick(&mut g, &mut ps, 23_000.0);
    assert_eq!(guess(&mut g, &mut ps, 23_100.0, 1, 1000), Err(ActionError::Ignored), "after the clock");
}

#[test]
fn the_reveal_comes_once_everyone_here_has_guessed() {
    let mut ps = players(3);
    let mut g = game(1, &ps);
    ps[2].conn = None; // Cem left: he does not hold the article open
    guess(&mut g, &mut ps, 4_000.0, 0, 900).unwrap();
    tick(&mut g, &mut ps, 4_000.0);
    assert!(matches!(g.stage, Stage::Guessing { .. }), "Ben has not guessed");
    guess(&mut g, &mut ps, 5_000.0, 1, 1200).unwrap();
    tick(&mut g, &mut ps, 5_000.0);
    assert_eq!(g.stage, Stage::Reveal { ends_at: 12_000.0 });
}

#[test]
fn points_go_to_the_scores_at_each_reveal() {
    let mut ps = players(2);
    let mut g = game(2, &ps);
    guess(&mut g, &mut ps, 4_000.0, 0, 900).unwrap(); // 10 % under 10,00 €
    guess(&mut g, &mut ps, 4_000.0, 1, 1000).unwrap(); // to the cent
    tick(&mut g, &mut ps, 4_000.0);
    assert_eq!((ps[0].score, ps[1].score), (900, 1500));
    tick(&mut g, &mut ps, 11_000.0);
    guess(&mut g, &mut ps, 15_000.0, 0, 2000).unwrap();
    tick(&mut g, &mut ps, 34_000.0);
    assert_eq!((ps[0].score, ps[1].score), (2400, 1500), "no guess, no points");
}

#[test]
fn a_player_who_joins_midway_plays_from_the_next_article() {
    let mut ps = players(1);
    let mut g = game(2, &ps);
    ps.push(Player::for_test(1, "Ben", true));
    assert_eq!(guess(&mut g, &mut ps, 4_000.0, 1, 900), Err(ActionError::Ignored));
    guess(&mut g, &mut ps, 4_000.0, 0, 900).unwrap();
    tick(&mut g, &mut ps, 4_000.0);
    assert!(matches!(g.stage, Stage::Reveal { .. }), "Ben does not hold this article open");
    tick(&mut g, &mut ps, 11_000.0);
    assert_eq!(guess(&mut g, &mut ps, 15_000.0, 1, 900), Ok(()));
}

#[test]
fn the_tv_shows_who_guessed_never_what() {
    let mut ps = players(2);
    let mut g = game(1, &ps);
    guess(&mut g, &mut ps, 4_000.0, 1, 777).unwrap();
    let view = ViewCtx { now: 4_000.0, players: &ps, leader: Some(0) };
    let TvScene::Preisfrage { scene } = g.tv_scene(&view) else { panic!() };
    assert_eq!(scene.stage, PreisfrageStage::Guessing { opens_at: 3_000.0, closes_at: 23_000.0, guessed: vec![1] });
    assert!(!serde_json::to_string(&scene).unwrap().contains("777"));
    let PhoneScreen::Price { keypad } = g.phone_screen(&view, 1) else { panic!() };
    assert_eq!(keypad.sent, Some(777), "a phone shows its own guess");
    let PhoneScreen::Price { keypad } = g.phone_screen(&view, 0) else { panic!() };
    assert_eq!(keypad.sent, None);
}
