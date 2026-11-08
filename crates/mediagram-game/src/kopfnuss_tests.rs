use std::collections::HashSet;
use std::sync::{Arc, Mutex};

use super::*;
use crate::general::general_pack;
use crate::protocol::KopfnussStage;

fn game(rounds: usize, players: &[Player]) -> KopfnussGame {
    seeded(rounds, players, 9)
}

fn seeded(rounds: usize, players: &[Player], seed: u64) -> KopfnussGame {
    let cfg = KopfnussConfig { rounds, seed: Some(seed), ..KopfnussConfig::default() };
    let supply = QuestionSupply::new(Arc::new(general_pack()), vec![], Arc::new(Mutex::new(HashSet::new())), 0);
    KopfnussGame::new(cfg, supply, players, 0.0)
}

fn trio() -> Vec<Player> {
    (0..3).map(|i| Player::for_test(i, ["Ana", "Ben", "Cem"][i as usize], true)).collect()
}

fn tap(g: &mut KopfnussGame, players: &mut [Player], now: f64, player: PlayerId, index: u8) -> Result<(), ActionError> {
    g.on_action(&mut GameCtx { now, players }, player, &Action::Choose { index, at: now })
}

fn tick(g: &mut KopfnussGame, players: &mut [Player], now: f64) {
    g.on_tick(&mut GameCtx { now, players });
}

/// Ticks in 100 ms steps from `now` until `done` holds; returns the time it did.
fn run_until(g: &mut KopfnussGame, players: &mut [Player], mut now: f64, done: impl Fn(&KopfnussGame) -> bool) -> f64 {
    for _ in 0..20_000 {
        if done(g) {
            return now;
        }
        now += 100.0;
        tick(g, players, now);
    }
    panic!("stage never reached, stuck in {:?}", g.stage);
}

fn phone(g: &KopfnussGame, players: &[Player], player: PlayerId) -> PhoneScreen {
    g.phone_screen(&ViewCtx { now: 0.0, players, leader: Some(0) }, player)
}

/// Player `p` votes 0, sends trick 0 to the first target offered, a step
/// every half second; returns the time of the last tap.
fn pick_all(g: &mut KopfnussGame, players: &mut [Player], now: f64, p: PlayerId) -> f64 {
    for i in 0..3 {
        tap(g, players, now + 500.0 * f64::from(i), p, 0).unwrap();
    }
    now + 1000.0
}

#[test]
fn a_round_moves_on_as_soon_as_everyone_picked() {
    let mut players = trio();
    let mut g = game(2, &players);
    let mut last = 0.0;
    for p in 0..3 {
        last = pick_all(&mut g, &mut players, 100.0, p);
    }
    tick(&mut g, &mut players, last + 100.0);
    assert!(matches!(g.stage, Stage::Resolve { .. }), "{:?}", g.stage);
    run_until(&mut g, &mut players, last + 100.0, |g| g.stage == Stage::Question);
}

#[test]
fn a_round_without_picks_still_proceeds() {
    let mut players = trio();
    let mut g = game(2, &players);
    run_until(&mut g, &mut players, 0.0, |g| g.stage == Stage::Question);
    assert!(g.tricks.is_empty());
}

#[test]
fn pick_screens_open_at_strictly_later_times() {
    let mut players = trio();
    let mut g = game(2, &players);
    let opens = |g: &KopfnussGame, players: &[Player]| match phone(g, players, 0) {
        PhoneScreen::Choice { opens_at, .. } => opens_at,
        other => panic!("{other:?}"),
    };
    let mut last = opens(&g, &players);
    for (i, now) in [50.0, 600.0].into_iter().enumerate() {
        tap(&mut g, &mut players, now, 0, 0).unwrap();
        let next = opens(&g, &players);
        assert!(next > last, "step {i}: {next} after {last}");
        last = next;
    }
}

#[test]
fn tricks_ride_on_the_target_phone_for_exactly_three_questions() {
    let mut players = trio();
    let mut g = game(2, &players);
    // Ana sends her first offered trick to her first target (Ben).
    let last = pick_all(&mut g, &mut players, 100.0, 0);
    let target = g.sheets[&0].target.unwrap();
    let trick = g.sheets[&0].trick.unwrap();
    let mut now = run_until(&mut g, &mut players, last, |g| g.stage == Stage::Question);
    let mut asked = HashSet::new();
    while g.stage == Stage::Question {
        if let PhoneScreen::Choice { tricks, opens_at, .. } = phone(&g, &players, target) {
            assert_eq!(tricks, vec![trick]);
            asked.insert(opens_at.to_bits());
            assert!(matches!(phone(&g, &players, 2), PhoneScreen::Choice { tricks, .. } if tricks.is_empty()));
        }
        now += 100.0;
        tick(&mut g, &mut players, now);
    }
    assert_eq!(asked.len(), 3, "three questions");
    assert!(matches!(g.stage, Stage::Pick { .. }));
    assert!(g.tricks.is_empty(), "gone in the next pick");
}

#[test]
fn points_come_from_the_contained_quiz() {
    let mut players = trio();
    let mut g = game(1, &players);
    let now = run_until(&mut g, &mut players, 0.0, |g| {
        g.quiz.as_ref().is_some_and(|q| matches!(q.stage, crate::quiz_config::Stage::Open { .. }))
    });
    let correct = g.quiz.as_ref().unwrap().round().correct;
    tap(&mut g, &mut players, now + 50.0, 1, correct).unwrap();
    run_until(&mut g, &mut players, now + 50.0, |g| g.stage != Stage::Question);
    assert!(players[1].score > 0);
    assert_eq!(players[0].score, 0);
}

#[test]
fn a_disconnected_player_neither_blocks_nor_gets_targeted() {
    let mut players = trio();
    players[2] = Player::for_test(2, "Cem", false);
    let mut g = game(1, &players);
    assert!(g.sheets.values().all(|s| !s.targets.contains(&2)));
    pick_all(&mut g, &mut players, 100.0, 0);
    let last = pick_all(&mut g, &mut players, 100.0, 1);
    tick(&mut g, &mut players, last + 100.0);
    assert!(matches!(g.stage, Stage::Resolve { .. }));
}

#[test]
fn after_the_last_round_comes_the_climb_then_the_fact_then_the_finish() {
    let mut players = trio();
    let mut g = game(1, &players);
    let mut now = run_until(&mut g, &mut players, 0.0, |g| g.stage == Stage::Climb);
    assert!(matches!(g.tv_scene(&ViewCtx { now, players: &players, leader: None }), TvScene::Kopfnuss { scene } if matches!(scene.stage, KopfnussStage::Climb { .. })));
    // Cem answers everything right and reaches the top.
    while g.stage == Stage::Climb {
        let climb = g.climb.as_ref().unwrap();
        let idx = climb.seats[&2].idx;
        let right = climb.deck[idx].correct;
        now += 500.0;
        tap(&mut g, &mut players, now, 2, right).unwrap();
        tick(&mut g, &mut players, now);
    }
    assert!(matches!(g.stage, Stage::Fact { .. }));
    assert_eq!(g.order[0], 2);
    assert!(!g.fact.is_empty());
    run_until(&mut g, &mut players, now, |g| g.stage == Stage::Final);
    assert!(g.is_finished());
    assert!(matches!(phone(&g, &players, 2), PhoneScreen::Final { rank: 1, .. }));
}

#[test]
fn a_player_who_left_does_not_decide_a_tie() {
    for seed in 0..12 {
        // Ana left with the lowest score; of those still here, Ben is last.
        let mut players = trio();
        players[0] = Player::for_test(0, "Ana", false);
        players[1].score = 100;
        players[2].score = 200;
        let mut g = seeded(1, &players, seed);
        tap(&mut g, &mut players, 100.0, 1, 0).unwrap();
        tap(&mut g, &mut players, 100.0, 2, 1).unwrap();
        run_until(&mut g, &mut players, 100.0, |g| matches!(g.stage, Stage::Resolve { .. }));
        assert_eq!(g.chosen, 0, "seed {seed}: Ben's vote breaks the tie");
    }
}

#[test]
fn the_climb_winner_ends_with_the_most_points() {
    let mut players = trio();
    players[0].score = 9000;
    let mut g = game(0, &players);
    let mut now = 0.0;
    while g.stage == Stage::Climb {
        let climb = g.climb.as_ref().unwrap();
        let right = climb.deck[climb.seats[&2].idx].correct;
        now += 500.0;
        tap(&mut g, &mut players, now, 2, right).unwrap();
        tick(&mut g, &mut players, now);
    }
    let top = players.iter().max_by_key(|p| p.score).unwrap().id;
    assert_eq!((g.order[0], top), (2, 2), "Cem climbed to the top and leads the points");
}
