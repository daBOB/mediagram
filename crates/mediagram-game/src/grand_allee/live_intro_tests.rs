//! The rules cards before the first roll, and who may skip them.

use super::live::{GrandAlleeConfig, GrandAlleeGame, Stage};
use super::live_tests::{options, ANA, BEN};
use crate::game::{ActionError, Game, GameCtx, ViewCtx};
use crate::protocol::{Action, GrandAlleeTurn, PhoneScreen, PlayerId, ServerTime, TvScene};
use crate::room_seats::Player;

/// Ana, Ben and Cem at the table, with the live game's intro.
fn intro() -> (GrandAlleeGame, Vec<Player>) {
    let players: Vec<Player> = ["Ana", "Ben", "Cem"].iter().enumerate().map(|(i, n)| Player::for_test(i as u8, n, true)).collect();
    (GrandAlleeGame::new(GrandAlleeConfig { seed: Some(1), ..GrandAlleeConfig::default() }, &players, 0.0), players)
}

fn tap(g: &mut GrandAlleeGame, p: &mut [Player], who: PlayerId, at: ServerTime) -> Result<(), ActionError> {
    g.on_action(&mut GameCtx { now: at, players: p }, who, &Action::Choose { index: 0, at })
}

fn tv_turn(g: &GrandAlleeGame, p: &[Player]) -> GrandAlleeTurn {
    let TvScene::GrandAllee { scene } = g.tv_scene(&ViewCtx { now: 0.0, players: p, leader: None }) else { panic!("not grand allee") };
    scene.turn
}

#[test]
fn three_cards_of_ten_seconds_then_seat_0_is_asked_to_roll() {
    let (mut g, mut p) = intro();
    let mut opened = vec![(0, 0.0)];
    let mut now = 0.0;
    while let Stage::Intro { card, at } = g.stage {
        let due = g.due(&GameCtx { now, players: &mut p });
        assert_eq!(due, Some(at + 10_000.0), "card {card} closes after its ten seconds");
        if opened.last() != Some(&(card, at)) {
            opened.push((card, at));
        }
        now += 50.0;
        g.on_tick(&mut GameCtx { now, players: &mut p });
    }
    assert_eq!(opened, [(0, 0.0), (1, 10_000.0), (2, 20_000.0)]);
    assert_eq!(now, 30_000.0, "the first move begins when the last card closes");
    assert!(matches!(g.stage, Stage::Ask { since, .. } if since == 30_000.0) && g.turn == 0, "seat 0 rolls: {:?}", g.stage);
    assert_eq!(options(&g, &p, ANA, now), ["Würfeln"]);
}

#[test]
fn the_leaders_tap_skips_the_intro_and_a_double_tap_does_not_roll() {
    let (mut g, mut p) = intro();
    assert_eq!(tap(&mut g, &mut p, ANA, 3_000.0), Ok(()));
    assert!(matches!(g.stage, Stage::Ask { .. }) && g.turn == 0, "the first move began: {:?}", g.stage);
    assert_eq!(tap(&mut g, &mut p, ANA, 3_080.0), Err(ActionError::Ignored), "the second tap is too early for the roll");
    assert!(matches!(g.stage, Stage::Ask { .. }), "nothing rolled");
}

#[test]
fn anybody_but_the_leader_is_ignored_and_the_next_connected_player_leads_when_the_first_is_gone() {
    let (mut g, mut p) = intro();
    assert_eq!(tap(&mut g, &mut p, BEN, 2_000.0), Err(ActionError::Ignored));
    assert_eq!(g.stage, Stage::Intro { card: 0, at: 0.0 });
    p[0] = Player::for_test(ANA, "Ana", false);
    assert_eq!(tap(&mut g, &mut p, BEN, 2_100.0), Ok(()), "with Ana away Ben leads");
    assert!(matches!(g.stage, Stage::Ask { .. }) && g.turn == 0, "seat 0 still moves first");
}

#[test]
fn the_leaders_phone_offers_the_skip_and_the_others_point_at_the_tv() {
    let (g, p) = intro();
    let view = ViewCtx { now: 0.0, players: &p, leader: Some(ANA) };
    for (who, buttons) in [(ANA, vec!["Überspringen"]), (BEN, vec![])] {
        let PhoneScreen::Sheet { prompt, options, closes_at, .. } = g.phone_screen(&view, who) else { panic!("not a sheet") };
        assert_eq!(prompt.as_deref(), Some("Schau auf den Fernseher: Das Ziel"));
        assert_eq!(options.iter().map(|o| o.label.as_str()).collect::<Vec<_>>(), buttons);
        assert_eq!(closes_at, None, "no clock: the TV keeps the time");
    }
}

#[test]
fn the_tv_gets_the_card_with_its_times_the_games_numbers_and_the_leaders_name() {
    let (mut g, p) = intro();
    let GrandAlleeTurn::Intro { card, title, lines, opened_at, closes_at, leader } = tv_turn(&g, &p) else { panic!("no intro") };
    assert_eq!((card, title.as_str(), opened_at, closes_at, leader.as_deref()), (1, "Das Ziel", 0.0, 10_000.0, Some("Ana")));
    assert_eq!(lines[0], format!("Nach **{} Runden** gewinnt, wer am meisten besitzt:", g.rounds));
    g.stage = Stage::Intro { card: 2, at: 20_000.0 };
    let GrandAlleeTurn::Intro { card, lines, .. } = tv_turn(&g, &p) else { panic!("no intro") };
    assert_eq!((card, lines[1].as_str()), (3, "Über die **Bank**: 2.000 Taler."));
}
