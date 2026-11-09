use super::*;
use crate::general::general_pack;
use crate::quiz_config::QuizConfig;

pub(crate) fn players(n: u8) -> Vec<Player> {
    (0..n).map(|i| Player::for_test(i, ["Ana", "Ben", "Cem", "Dana", "Eli", "Fe"][usize::from(i)], true)).collect()
}

pub(crate) fn game(rounds: usize, players: &[Player]) -> DreifelderGame {
    let base = DreifelderConfig::default();
    let cfg = DreifelderConfig { quiz: QuizConfig { rounds, seed: Some(5), ..base.quiz.clone() }, ..base };
    DreifelderGame::new(cfg, general_pack(), players, 0.0)
}

pub(crate) fn tap(g: &mut DreifelderGame, players: &mut [Player], now: f64, player: PlayerId, index: u8) -> Result<(), ActionError> {
    g.on_action(&mut GameCtx { now, players }, player, &Action::Choose { index, at: now })
}

pub(crate) fn tick(g: &mut DreifelderGame, players: &mut [Player], now: f64) {
    g.on_tick(&mut GameCtx { now, players });
}

pub(crate) fn opens_at(g: &DreifelderGame) -> f64 {
    let Stage::Question { opens_at, .. } = g.stage else { panic!("no question open: {:?}", g.stage) };
    opens_at
}

/// `right` tap the right field, `wrong` a wrong one, `after` ms past the opening; returns that time.
pub(crate) fn answer(g: &mut DreifelderGame, players: &mut [Player], right: &[PlayerId], wrong: &[PlayerId], after: f64) -> f64 {
    let now = opens_at(g) + after;
    let c = g.round().correct;
    for &p in right {
        tap(g, players, now, p, c).unwrap();
    }
    for &p in wrong {
        tap(g, players, now, p, (c + 1) % 3).unwrap();
    }
    now
}

/// Ticks through the reveal into the next question or the final; returns the time.
pub(crate) fn next(g: &mut DreifelderGame, players: &mut [Player], now: f64) -> f64 {
    tick(g, players, now);
    let Stage::Reveal { ends_at } = g.stage else { panic!("no reveal: {:?}", g.stage) };
    tick(g, players, ends_at);
    ends_at
}

#[test]
fn the_fields_go_live_at_the_opening_with_a_little_early_slack() {
    let mut ps = players(3);
    let mut g = game(12, &ps);
    let open = opens_at(&g);
    assert_eq!(open, 3000.0, "three seconds of intro");
    assert_eq!(g.round().options.len(), 3);
    assert_eq!(tap(&mut g, &mut ps, open - 200.0, 0, 0), Err(ActionError::Ignored));
    tap(&mut g, &mut ps, open - 100.0, 0, 0).unwrap();
    assert_eq!(g.answers[&0].at, open, "an early tap counts as at the opening");
    assert_eq!(tap(&mut g, &mut ps, open + 50.0, 0, 1), Err(ActionError::Ignored), "one answer per question");
    // A tap claiming 1 s before its arrival is trusted only 400 ms back.
    g.on_action(&mut GameCtx { now: open + 2000.0, players: &mut ps }, 1, &Action::Choose { index: 1, at: open + 1000.0 }).unwrap();
    assert_eq!(g.answers[&1].at, open + 1600.0);
    assert!(matches!(tap(&mut g, &mut ps, open + 2000.0, 2, 3), Err(ActionError::NotAllowed(_))), "there is no fourth field");
}

#[test]
fn a_round_ends_once_every_alive_connected_player_answered() {
    let mut ps = players(3);
    let mut g = game(12, &ps);
    let t = answer(&mut g, &mut ps, &[0, 1], &[], 500.0);
    tick(&mut g, &mut ps, t);
    assert!(matches!(g.stage, Stage::Question { .. }), "Cem has not answered");
    let t = answer(&mut g, &mut ps, &[], &[2], 900.0);
    tick(&mut g, &mut ps, t);
    assert!(matches!(g.stage, Stage::Reveal { .. }));
    assert_eq!(g.eliminated, vec![2]);
    let t = next(&mut g, &mut ps, t);
    assert_eq!(g.index, 1);
    assert!(g.answers.is_empty() && g.eliminated.is_empty());
    let t = answer(&mut g, &mut ps, &[0, 1], &[], 500.0).max(t);
    tick(&mut g, &mut ps, t);
    assert!(matches!(g.stage, Stage::Reveal { .. }), "the out player does not hold the round up");
}

#[test]
fn an_unanswered_round_closes_on_time_and_everyone_stays() {
    let mut ps = players(2);
    let mut g = game(12, &ps);
    let Stage::Question { closes_at, .. } = g.stage else { panic!() };
    tick(&mut g, &mut ps, closes_at - 1.0);
    assert!(matches!(g.stage, Stage::Question { .. }));
    tick(&mut g, &mut ps, closes_at);
    assert!(matches!(g.stage, Stage::Reveal { .. }));
    assert!(g.eliminated.is_empty() && g.tally.is_alive(0) && g.tally.is_alive(1));
}

#[test]
fn ghost_guesses_score_nothing_but_order_the_ones_out_together() {
    let mut ps = players(4);
    let mut g = game(12, &ps);
    let t = answer(&mut g, &mut ps, &[0, 1], &[2, 3], 500.0);
    let t = next(&mut g, &mut ps, t);
    // Dana guesses right as a ghost, Cem wrong; Ben goes out, Ana is the last one in.
    let t = answer(&mut g, &mut ps, &[0, 3], &[1, 2], 500.0).max(t);
    tick(&mut g, &mut ps, t);
    assert_eq!(g.eliminated, vec![1]);
    assert!(!g.tally.is_alive(3), "a right ghost guess does not bring Dana back");
    assert!(ps.iter().all(|p| p.score == 0), "no points during the game");
    next(&mut g, &mut ps, t);
    assert!(g.is_finished());
    assert_eq!(g.order, vec![0, 1, 3, 2]);
    let scores: Vec<i32> = ps.iter().map(|p| p.score).collect();
    assert_eq!(scores, vec![300, 200, 0, 100], "100 per player beaten");
}

#[test]
fn the_cap_ends_a_game_with_several_alive_the_fastest_first() {
    let mut ps = players(2);
    let mut g = game(2, &ps);
    answer(&mut g, &mut ps, &[1], &[], 500.0);
    let t = answer(&mut g, &mut ps, &[0], &[], 3000.0);
    let t = next(&mut g, &mut ps, t);
    let t = answer(&mut g, &mut ps, &[0, 1], &[], 500.0).max(t);
    next(&mut g, &mut ps, t);
    assert!(g.is_finished());
    assert_eq!(g.order, vec![1, 0]);
}

#[test]
fn an_alive_player_who_leaves_is_out_at_the_reveal_and_stays_out() {
    let mut ps = players(3);
    let mut g = game(12, &ps);
    ps[2] = Player::for_test(2, "Cem", false);
    let t = answer(&mut g, &mut ps, &[0, 1], &[], 500.0);
    tick(&mut g, &mut ps, t);
    assert_eq!(g.eliminated, vec![2], "nobody waits for a player who left");
    let t = next(&mut g, &mut ps, t);
    ps[2] = Player::for_test(2, "Cem", true);
    let t = answer(&mut g, &mut ps, &[0, 1, 2], &[], 500.0).max(t);
    tick(&mut g, &mut ps, t);
    assert!(!g.tally.is_alive(2), "coming back does not bring Cem back in");
    let mut ps = players(2);
    let mut g = game(12, &ps);
    ps[0] = Player::for_test(0, "Ana", false);
    let t = answer(&mut g, &mut ps, &[], &[1], 500.0);
    next(&mut g, &mut ps, t);
    assert_eq!(g.order, vec![1, 0], "an all-wrong round does not keep a player who left in");
}

#[test]
fn the_game_ends_when_no_alive_player_is_connected() {
    let mut ps = players(3);
    let mut g = game(12, &ps);
    answer(&mut g, &mut ps, &[1], &[], 300.0);
    let t = answer(&mut g, &mut ps, &[0], &[2], 900.0);
    let t = next(&mut g, &mut ps, t);
    ps[0] = Player::for_test(0, "Ana", false);
    ps[1] = Player::for_test(1, "Ben", false);
    let open = opens_at(&g).max(t);
    tick(&mut g, &mut ps, open);
    next(&mut g, &mut ps, open + 1.0);
    assert!(g.is_finished());
    assert_eq!(g.order, vec![1, 0, 2], "the alive ranked by speed");
}

#[test]
fn a_double_tap_never_answers_the_next_question() {
    let mut ps = players(2);
    let mut g = game(12, &ps);
    let t = answer(&mut g, &mut ps, &[0, 1], &[], 500.0);
    tick(&mut g, &mut ps, t);
    assert_eq!(tap(&mut g, &mut ps, t + 60.0, 0, 0), Err(ActionError::Ignored), "the round is in its reveal");
    next(&mut g, &mut ps, t + 60.0);
    assert!(g.answers.is_empty());
}

#[test]
fn a_player_who_joins_mid_game_waits_and_holds_nothing_up() {
    let mut ps = players(2);
    let mut g = game(12, &ps);
    ps.push(Player::for_test(2, "Cem", true));
    let open = opens_at(&g);
    assert_eq!(tap(&mut g, &mut ps, open, 2, 0), Err(ActionError::Ignored));
    let t = answer(&mut g, &mut ps, &[0, 1], &[], 500.0);
    tick(&mut g, &mut ps, t);
    assert!(matches!(g.stage, Stage::Reveal { .. }), "Cem does not hold the round up");
    let view = ViewCtx { now: t, players: &ps, leader: Some(0) };
    assert!(matches!(g.phone_screen(&view, 2), PhoneScreen::Wait { .. }));
}

#[test]
fn without_questions_the_game_ends_at_once() {
    let ps = players(2);
    let g = DreifelderGame::new(DreifelderConfig::default(), vec![], &ps, 0.0);
    assert!(g.is_finished());
    let view = ViewCtx { now: 0.0, players: &ps, leader: Some(0) };
    assert!(matches!(g.tv_scene(&view), TvScene::Dreifelder { .. }));
    assert!(matches!(g.phone_screen(&view, 0), PhoneScreen::Final { rank: 1, .. }));
}
