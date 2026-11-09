//! Taps that come too fast and phones left on the table.

use super::live::{GrandAlleeGame, Stage};
use super::live_tests::{game, options, own, pick, place, until, ANA};
use crate::game::{Game, GameCtx};
use crate::protocol::{Action, ServerTime};
use crate::room_seats::Player;

fn tap(g: &mut GrandAlleeGame, p: &mut [Player], at: ServerTime) -> bool {
    g.on_action(&mut GameCtx { now: at, players: p }, ANA, &Action::Choose { index: 0, at }).is_ok()
}

#[test]
fn a_double_tap_on_the_last_entrance_does_not_answer_the_field_question() {
    let (mut g, mut p) = game(2, &[5]);
    own(&mut g, 0, 0, 1);
    place(&mut g, 0, 19);
    let now = pick(&mut g, &mut p, ANA, "Würfeln", 10.0);
    let now = pick(&mut g, &mut p, ANA, "Lagune · 300 Taler", now);
    let Stage::Ask { since, .. } = g.stage else { panic!("no place question: {:?}", g.stage) };
    let at = since.max(now);
    assert!(tap(&mut g, &mut p, at), "the first tap places the entrance");
    assert!(matches!(g.stage, Stage::Ask { .. }), "the field question follows");
    assert!(!tap(&mut g, &mut p, at + 80.0), "the second tap of a double tap is too early for it");
    assert_eq!((g.t.estate(3).owner, g.t.estate(8).owner), (None, None), "no plot bought by accident");
}

#[test]
fn a_player_who_lets_the_clock_run_out_has_the_rest_of_the_turn_decided_at_once() {
    let (mut g, mut p) = game(2, &[5]);
    own(&mut g, 0, 0, 1);
    own(&mut g, 0, 1, 1);
    place(&mut g, 0, 19);
    // the roll's clock runs out; the Rathaus, two entrances and the plot follow without new clocks
    let done = until(&mut g, &mut p, 0.0, |g| g.turn == 1);
    assert!(done < 30_000.0, "one 20 s clock, then the house: took {done} ms");
}

#[test]
fn after_a_clock_ran_out_the_house_answers_a_sixs_extra_roll_at_once() {
    // the 6 ends on a Baustelle where Ana has nothing to build, so the extra roll follows straight away
    let (mut g, mut p) = game(2, &[6]);
    let rolled = until(&mut g, &mut p, 0.0, |g| matches!(g.stage, Stage::Rolled { .. }));
    assert!(rolled >= g.cfg.ask_ms, "the roll waited for its clock");
    let extra = until(&mut g, &mut p, rolled, |g| matches!(g.stage, Stage::Ask { .. }));
    assert_eq!(options(&g, &p, ANA, extra), ["Nochmal würfeln", "Lieber nicht"]);
    let done = until(&mut g, &mut p, extra, |g| g.turn == 1);
    assert_eq!(done, extra + 50.0, "no second clock: the house lets the extra turn go on the next tick");
}
