use super::*;
use crate::room_seats::Player;
use crate::general::general_pack;

fn cfg(rounds: usize) -> QuizConfig {
    QuizConfig { rounds, seed: Some(7), ..QuizConfig::default() }
}

fn choose(quiz: &mut Quiz, players: &mut [Player], now: f64, player: PlayerId, index: u8, at: f64) {
    let mut ctx = GameCtx { now, players };
    let _ = quiz.on_action(&mut ctx, player, &Action::Choose { index, at });
}

fn tick(quiz: &mut Quiz, players: &mut [Player], now: f64) {
    quiz.on_tick(&mut GameCtx { now, players });
}

#[test]
fn question_order_is_kept() {
    let qs = general_pack();
    let quiz = Quiz::new(QuizConfig { rounds: 3, seed: Some(1), ..Default::default() }, qs.clone(), 0.0);
    let texts: Vec<_> = quiz.round_texts();
    assert_eq!(texts, qs.iter().take(3).map(|q| q.text.clone()).collect::<Vec<_>>());
}

#[test]
fn fast_correct_answer_scores_near_max() {
    let mut players = vec![Player::for_test(0, "Ana", true), Player::for_test(1, "Ben", true)];
    let mut quiz = Quiz::new(cfg(1), general_pack(), 0.0);
    let correct = quiz.round().correct;
    let wrong = (correct + 1) % 4;
    tick(&mut quiz, &mut players, 3500.0);
    choose(&mut quiz, &mut players, 3600.0, 0, correct, 3550.0);
    choose(&mut quiz, &mut players, 9500.0, 1, wrong, 9400.0);
    tick(&mut quiz, &mut players, 9550.0);
    assert!(matches!(quiz.stage, Stage::Reveal { .. }), "everyone answered, closes early");
    assert_eq!(players[0].score, 998);
    assert_eq!(players[1].score, 0);
}

#[test]
fn claimed_time_is_bounded_by_lag() {
    let mut players = vec![Player::for_test(0, "Ana", true)];
    let mut quiz = Quiz::new(cfg(1), general_pack(), 0.0);
    let correct = quiz.round().correct;
    tick(&mut quiz, &mut players, 3500.0);
    // Claims to have tapped at the very start, arrives 6 s later.
    choose(&mut quiz, &mut players, 9500.0, 0, correct, 0.0);
    assert_eq!(quiz.answers[&0].at, 9100.0);
}

#[test]
fn taps_before_open_are_ignored_beyond_tolerance() {
    let mut players = vec![Player::for_test(0, "Ana", true)];
    let mut quiz = Quiz::new(cfg(1), general_pack(), 0.0);
    choose(&mut quiz, &mut players, 3000.0, 0, 0, 3000.0);
    assert!(quiz.answers.is_empty());
    choose(&mut quiz, &mut players, 3400.0, 0, 0, 3400.0);
    assert_eq!(quiz.answers[&0].at, 3500.0, "within tolerance, counted as the opening instant");
}

#[test]
fn second_tap_does_not_change_answer() {
    let mut players = vec![Player::for_test(0, "Ana", true), Player::for_test(1, "Ben", true)];
    let mut quiz = Quiz::new(cfg(1), general_pack(), 0.0);
    tick(&mut quiz, &mut players, 3500.0);
    choose(&mut quiz, &mut players, 4000.0, 0, 1, 4000.0);
    choose(&mut quiz, &mut players, 4100.0, 0, 2, 4100.0);
    assert_eq!(quiz.answers[&0].choice, 1);
}

#[test]
fn disconnected_players_do_not_block_early_close() {
    let mut players = vec![Player::for_test(0, "Ana", true), Player::for_test(1, "Ben", false)];
    let mut quiz = Quiz::new(cfg(1), general_pack(), 0.0);
    tick(&mut quiz, &mut players, 3500.0);
    choose(&mut quiz, &mut players, 4000.0, 0, 0, 4000.0);
    tick(&mut quiz, &mut players, 4050.0);
    assert!(matches!(quiz.stage, Stage::Reveal { .. }));
}

#[test]
fn runs_through_all_rounds_to_final() {
    let mut players = vec![Player::for_test(0, "Ana", true)];
    let mut quiz = Quiz::new(cfg(3), general_pack(), 0.0);
    let mut now = 0.0;
    while !quiz.is_finished() && now < 120_000.0 {
        now += 50.0;
        tick(&mut quiz, &mut players, now);
    }
    assert!(quiz.is_finished());
    assert_eq!(quiz.index, 3);
    let view = ViewCtx { now, players: &players, leader: Some(0) };
    assert!(matches!(
        quiz.phone_screen(&view, 0),
        PhoneScreen::Final { rank: 1, of: 1, can_restart: true, .. }
    ));
}

#[test]
fn options_contain_the_correct_answer_once() {
    let pack = general_pack();
    let quiz = Quiz::new(cfg(pack.len()), pack.clone(), 0.0);
    assert_eq!(quiz.rounds.len(), pack.len());
    for r in &quiz.rounds {
        let q = pack.iter().find(|q| q.text == r.text).unwrap();
        assert_eq!(r.options[usize::from(r.correct)], q.correct);
        assert_eq!(r.options.iter().filter(|o| **o == q.correct).count(), 1);
    }
}
