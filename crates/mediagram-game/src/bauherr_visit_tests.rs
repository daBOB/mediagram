//! Chained questions, Rathaus visits with several errands, and what an absent
//! player does with free things.

use super::tests::{asking, game, options, own, place, rolling, tap, until};
use super::*;
use crate::bauherr_rules::START_CASH;

const ANA: PlayerId = 0;

/// Ana stands one field before the Rathaus, owns Kastanienhof with one stage
/// and rolls onto it; returns the time its menu is up.
fn at_rathaus(rolls: &[u8]) -> (BauherrGame, Vec<Player>, f64) {
    let (mut g, mut p) = game(rolls);
    own(&mut g, 0, 0, 1);
    place(&mut g, 0, 17);
    tap(&mut g, &mut p, ANA, 0, 10.0).unwrap();
    let now = until(&mut g, &mut p, 10.0, asking);
    (g, p, now)
}

fn entrances(g: &BauherrGame) -> usize {
    g.table.entrances.iter().flatten().count()
}

fn absent(p: &mut [Player]) {
    p[0].conn = None;
    p[0].left_at = Some(0.0);
}

#[test]
fn a_double_tap_cannot_answer_the_question_that_follows_the_first_tap() {
    let (mut g, mut p, now) = at_rathaus(&[1]);
    tap(&mut g, &mut p, ANA, 1, now + 300.0).unwrap(); // Eingang kaufen
    assert_eq!(tap(&mut g, &mut p, ANA, 0, now + 500.0), Err(ActionError::Ignored));
    assert_eq!(g.table.seats[0].cash, START_CASH, "no entrance bought by the second tap");
    tap(&mut g, &mut p, ANA, 0, now + 900.0).unwrap();
    assert_eq!(g.table.seats[0].cash, START_CASH - 300);
}

#[test]
fn the_rathaus_offers_another_errand_until_fertig() {
    let (mut g, mut p, now) = at_rathaus(&[1, 1]);
    let t = now + 300.0;
    tap(&mut g, &mut p, ANA, 0, t).unwrap(); // Hotel ausbauen
    tap(&mut g, &mut p, ANA, 0, t + 900.0).unwrap(); // Kastanienhof
    let t = until(&mut g, &mut p, t, asking);
    tap(&mut g, &mut p, ANA, 0, t + 300.0).unwrap(); // one stage
    assert_eq!(g.table.levels(0), 2);
    assert_eq!(g.turn, 0, "the visit goes on");
    let t = t + 300.0;
    assert_eq!(options(&g, &p, ANA, t), ["Hotel ausbauen", "Eingang kaufen", "Fertig"]);
    tap(&mut g, &mut p, ANA, 1, t + 900.0).unwrap(); // Eingang kaufen
    tap(&mut g, &mut p, ANA, 0, t + 1800.0).unwrap(); // first field
    assert_eq!(entrances(&g), 1);
    assert_eq!(g.table.seats[0].cash, START_CASH - 900 - 300);
    let t = t + 1800.0;
    assert_eq!(options(&g, &p, ANA, t).last().map(String::as_str), Some("Fertig"));
    tap(&mut g, &mut p, ANA, 2, t + 900.0).unwrap();
    assert_eq!(g.turn, 1);
}

#[test]
fn an_absent_player_does_one_errand_at_the_rathaus_and_is_done() {
    let (mut g, mut p) = game(&[1]);
    own(&mut g, 0, 0, 3);
    place(&mut g, 0, 17);
    absent(&mut p);
    until(&mut g, &mut p, 0.0, |g| g.turn == 1 && rolling(g));
    assert_eq!(entrances(&g), 1);
    assert_eq!(g.table.seats[0].cash, START_CASH - 300);
}

#[test]
fn an_absent_player_with_little_cash_still_takes_what_is_free() {
    // a free entrance at the Eingang field, below the reserve
    let (mut g, mut p) = game(&[3]);
    own(&mut g, 0, 0, 1);
    place(&mut g, 0, 6);
    g.table.seats[0].cash = 1500;
    absent(&mut p);
    until(&mut g, &mut p, 0.0, |g| g.turn == 1 && rolling(g));
    assert_eq!(entrances(&g), 1);
    assert_eq!(g.table.seats[0].cash, 1500);
}

#[test]
fn at_the_rathaus_an_absent_player_buys_the_entrance_when_building_would_eat_the_reserve() {
    let (mut g, mut p) = game(&[1]);
    own(&mut g, 0, 0, 1); // the next stage costs 900, an entrance 300
    place(&mut g, 0, 17);
    g.table.seats[0].cash = 2500;
    absent(&mut p);
    until(&mut g, &mut p, 0.0, |g| g.turn == 1 && rolling(g));
    assert_eq!(entrances(&g), 1);
    assert_eq!(g.table.seats[0].cash, 2200);
    assert_eq!(g.table.levels(0), 1);
}

#[test]
fn a_doubled_permit_nobody_can_afford_says_so() {
    let (mut g, mut p) = game(&[2, 6]);
    own(&mut g, 0, 0, 1);
    place(&mut g, 0, 33);
    g.table.seats[0].cash = 1000; // 900 builds, 1800 does not
    tap(&mut g, &mut p, ANA, 0, 10.0).unwrap();
    let now = until(&mut g, &mut p, 10.0, asking);
    tap(&mut g, &mut p, ANA, 0, now + 300.0).unwrap();
    until(&mut g, &mut p, now, |g| matches!(g.stage, Stage::Note { .. }));
    assert!(matches!(&g.stage, Stage::Note { text, .. } if text == "Ana kann sich das Doppelte nicht leisten."));
}
