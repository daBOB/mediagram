//! The rules cards before round 1, who may skip them, and that nobody moves
//! while they run.

use crate::fluechtig::tests::{phone, scene_with, table, tap, NAMES};
use crate::fluechtig::{FluechtigConfig, FluechtigGame, Step};
use crate::fluechtig_intro::Intro;
use crate::fluechtig_rules::Turn;
use crate::game::{ActionError, Game, GameCtx, HOUSE_GRACE_MS};
use crate::protocol::FluechtigIntro;
use crate::room_seats::Player;

/// Four players, the live game's cards, Ana (seat 0) leads.
fn cards() -> (FluechtigGame, Vec<Player>) {
    let p = table(4, true);
    (FluechtigGame::new(FluechtigConfig { seed: Some(1), ..FluechtigConfig::default() }, &p, 0.0), p)
}

fn tick_to(g: &mut FluechtigGame, p: &mut [Player], from: f64, to: f64) {
    let mut now = from;
    while now < to {
        now += 50.0;
        g.on_tick(&mut GameCtx { now, players: p });
    }
}

#[test]
fn three_cards_of_ten_seconds_then_the_fugitive_is_asked() {
    let (mut g, mut p) = cards();
    let mut opened = vec![(0, 0.0)];
    let mut now = 0.0;
    while let Some(Intro { card, at }) = g.intro {
        if opened.last() != Some(&(card, at)) {
            opened.push((card, at));
        }
        now += 50.0;
        g.on_tick(&mut GameCtx { now, players: &mut p });
    }
    assert_eq!(opened, [(0, 0.0), (1, 10_000.0), (2, 20_000.0)]);
    assert_eq!(now, 30_000.0, "the chase begins when the last card closes");
    assert_eq!((g.ask.opens_at, g.ask.step, g.chase.turn), (30_000.0, Step::Field, Turn::Fugitive));
}

#[test]
fn the_leaders_tap_skips_and_a_double_tap_does_not_move() {
    let (mut g, mut p) = cards();
    assert_eq!(tap(&mut g, &mut p, 0, 0, 3_000.0), Ok(()));
    assert_eq!(g.intro, None);
    assert_eq!(g.ask.opens_at, 3_400.0, "the first question opens a moment after the skip");
    let runner = g.fugitive;
    assert_eq!(tap(&mut g, &mut p, runner, 0, 3_080.0), Err(ActionError::Ignored));
    assert_eq!(g.ask.step, Step::Field);
    assert!(g.chase.log.is_empty());
}

#[test]
fn nobody_but_the_leader_skips_and_the_next_connected_player_leads_when_the_first_is_gone() {
    let (mut g, mut p) = cards();
    assert_eq!(tap(&mut g, &mut p, 1, 0, 2_000.0), Err(ActionError::Ignored));
    assert_eq!(g.intro, Some(Intro { card: 0, at: 0.0 }));
    p[0] = Player::for_test(0, NAMES[0], false);
    assert_eq!(tap(&mut g, &mut p, 1, 0, 2_500.0), Ok(()));
    assert_eq!(g.intro, None);
}

#[test]
fn no_house_move_while_the_cards_run() {
    let (mut g, mut p) = cards();
    let runner = usize::from(g.fugitive);
    p[runner] = Player::for_test(g.fugitive, NAMES[runner], false);
    tick_to(&mut g, &mut p, 0.0, 29_950.0);
    assert!(g.chase.log.is_empty());
    assert_eq!((g.chase.round, g.chase.turn), (1, Turn::Fugitive));
    tick_to(&mut g, &mut p, 29_950.0, 30_000.0 + HOUSE_GRACE_MS);
    assert_eq!(g.chase.log.len(), 1, "the house moves the absent fugitive after the cards and the grace");
}

#[test]
fn no_intro_when_intro_ms_is_0() {
    let p = table(4, true);
    let g = FluechtigGame::new(FluechtigConfig { seed: Some(1), intro_ms: 0.0, ..FluechtigConfig::default() }, &p, 0.0);
    assert_eq!(g.intro, None);
}

#[test]
fn the_tv_gets_the_card_its_times_and_the_leaders_name() {
    let (mut g, mut p) = cards();
    let first = FluechtigIntro { card: 1, cards: 3, opened_at: 0.0, closes_at: 10_000.0, leader: Some("Ana".into()) };
    assert_eq!(scene_with(&g, &p).intro, Some(first));
    tick_to(&mut g, &mut p, 0.0, 10_000.0);
    assert_eq!(scene_with(&g, &p).intro.map(|i| (i.card, i.opened_at, i.closes_at)), Some((2, 10_000.0, 20_000.0)));
    tick_to(&mut g, &mut p, 10_000.0, 30_000.0);
    assert_eq!(scene_with(&g, &p).intro, None);
}

#[test]
fn phones_get_their_role_and_only_the_leaders_may_skip() {
    let (g, p) = cards();
    let runner = phone(&g, &p, g.fugitive);
    assert!(runner.intro.is_some());
    assert_eq!((runner.hiding, runner.ask.clone()), (Some(g.chase.fugitive.at), None));
    assert_eq!(runner.reveal_rounds, g.chase.rules.reveal_rounds);
    assert!(runner.partners.is_empty());
    for &h in &g.owners {
        let ph = phone(&g, &p, h);
        assert!(ph.intro.is_some());
        assert_eq!((ph.hiding, ph.pieces.len(), ph.ask.is_none()), (None, 1, true));
        let others: Vec<String> = g.owners.iter().filter(|&&o| o != h).map(|&o| NAMES[usize::from(o)].to_string()).collect();
        assert_eq!(ph.partners, others);
    }
    for id in 0..4 {
        assert_eq!(phone(&g, &p, id).may_skip, id == 0, "player {id}");
    }
}
