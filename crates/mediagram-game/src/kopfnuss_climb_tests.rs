use super::*;
use crate::general::general_pack;
use rand::SeedableRng;

fn seated(scores: &[i32]) -> Vec<Player> {
    scores
        .iter()
        .enumerate()
        .map(|(i, &s)| Player { score: s, ..Player::for_test(i as PlayerId, &format!("P{i}"), true) })
        .collect()
}

fn climb(players: &[Player], deck: usize) -> Climb {
    let cfg = KopfnussConfig { deck, ..KopfnussConfig::default() };
    let questions = general_pack().into_iter().take(deck).collect();
    Climb::new(&cfg, questions, &mut StdRng::seed_from_u64(5), players, 0.0)
}

fn right(c: &Climb, p: PlayerId) -> u8 {
    c.deck[c.seats[&p].idx].correct
}

fn wrong(c: &Climb, p: PlayerId) -> u8 {
    (right(c, p) + 1) % 4
}

fn step(c: &Climb, p: PlayerId) -> u8 {
    c.seats[&p].step
}

#[test]
fn head_start_by_rank_shared_on_ties() {
    let c = climb(&seated(&[900, 900, 500, 0]), 30);
    assert_eq!([step(&c, 0), step(&c, 1), step(&c, 2), step(&c, 3)], [3, 3, 1, 0]);
}

#[test]
fn a_right_answer_climbs_and_serves_the_next_question_at_once() {
    let mut c = climb(&seated(&[0, 0]), 30);
    let a = right(&c, 0);
    assert_eq!(c.on_choose(0, a, 100.0), Ok(()));
    assert_eq!((step(&c, 0), c.seats[&0].idx), (4, 1));
    assert_eq!(c.seats[&0].opens_at, 100.0 + KopfnussConfig::default().next_gap_ms, "the next one opens a beat later");
}

#[test]
fn a_double_tap_cannot_answer_the_next_question_unseen() {
    let mut c = climb(&seated(&[0, 0]), 30);
    let a = right(&c, 0);
    c.on_choose(0, a, 100.0).unwrap();
    let b = right(&c, 0);
    assert_eq!(c.on_choose(0, b, 180.0), Err(ActionError::Ignored), "second tap of a double tap");
    assert_eq!((step(&c, 0), c.seats[&0].idx), (4, 1));
}

#[test]
fn a_tap_just_before_the_lock_ends_counts() {
    // A phone whose clock runs a little ahead lights up early; its tap must not vanish.
    let mut c = climb(&seated(&[0, 0]), 30);
    let w = wrong(&c, 0);
    c.on_choose(0, w, 100.0).unwrap();
    let r = right(&c, 0);
    let opens = c.seats[&0].opens_at;
    assert_eq!(c.on_choose(0, r, opens - 100.0), Ok(()));
}

#[test]
fn the_climb_screen_has_no_clock_so_a_lost_tap_never_freezes_it() {
    let c = climb(&seated(&[0, 0]), 30);
    assert!(matches!(c.phone(0), PhoneScreen::Choice { closes_at: None, .. }));
}

#[test]
fn a_wrong_answer_locks_the_next_question() {
    let mut c = climb(&seated(&[0, 0]), 30);
    let w = wrong(&c, 0);
    c.on_choose(0, w, 100.0).unwrap();
    assert_eq!(step(&c, 0), 3, "no step lost");
    let r = right(&c, 0);
    assert_eq!(c.on_choose(0, r, 1500.0), Err(ActionError::Ignored), "still locked");
    assert_eq!(c.on_choose(0, r, 2100.0), Ok(()));
    assert_eq!(step(&c, 0), 4);
}

#[test]
fn everyone_sees_the_same_stream() {
    let c = climb(&seated(&[0, 0]), 30);
    let prompt = |p| match c.phone(p) {
        PhoneScreen::Choice { prompt, options, .. } => (prompt, options),
        other => panic!("{other:?}"),
    };
    assert_eq!(prompt(0), prompt(1));
}

#[test]
fn first_to_the_top_wins() {
    let mut c = climb(&seated(&[900, 0]), 30);
    let players = seated(&[900, 0]);
    for i in 0..7 {
        let a = right(&c, 0);
        c.on_choose(0, a, 500.0 * f64::from(i + 1)).unwrap();
    }
    assert_eq!(step(&c, 0), 10);
    c.on_tick(&players, 3600.0);
    assert_eq!(c.winner, Some(0));
    let a = right(&c, 1);
    assert_eq!(c.on_choose(1, a, 3700.0), Err(ActionError::Ignored), "decided");
}

#[test]
fn at_the_cap_the_highest_step_wins_and_the_earlier_breaks_a_tie() {
    let players = seated(&[0, 0, 0]);
    let mut c = climb(&players, 30);
    let (a1, a0) = (right(&c, 1), right(&c, 0));
    c.on_choose(1, a1, 500.0).unwrap();
    c.on_choose(0, a0, 900.0).unwrap();
    c.on_tick(&players, 1000.0);
    assert_eq!(c.winner, None, "clock still running");
    c.on_tick(&players, c.ends_at);
    assert_eq!(c.winner, Some(1), "same step, reached first");
}

#[test]
fn a_drained_deck_ends_the_climb() {
    let players = seated(&[0, 0]);
    let mut c = climb(&players, 2);
    for p in [0, 1] {
        for t in [10.0, 20.0] {
            let w = wrong(&c, p);
            c.on_choose(p, w, t + 3000.0 * f64::from(p) + if t > 10.0 { 2500.0 } else { 0.0 }).unwrap();
        }
    }
    assert!(matches!(c.phone(0), PhoneScreen::Wait { .. }));
    c.on_tick(&players, 10_000.0);
    assert!(c.winner.is_some());
}

#[test]
fn the_summit_bonus_makes_the_points_follow_the_climb() {
    // The quiz leader (Ana, 5000) climbs worst; Ben (100) reaches the top.
    let mut players = seated(&[5000, 100, 3000]);
    let mut c = climb(&players, 30);
    for (p, step) in [(0, 5), (1, 10), (2, 6)] {
        c.seats.get_mut(&p).unwrap().step = step;
    }
    c.winner = Some(1);
    let order = c.standings();
    assert_eq!(order, vec![1, 2, 0]);
    c.award(&mut players, &order);
    let score = |id: PlayerId| players.iter().find(|p| p.id == id).unwrap().score;
    assert!(score(1) > score(2) && score(2) > score(0), "{} {} {}", score(1), score(2), score(0));
    assert_eq!(score(0), 5000 + 5 * 100, "the bottom keeps quiz points plus its steps");
}
