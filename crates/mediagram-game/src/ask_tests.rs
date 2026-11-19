use super::{Ask, HOUSE_GRACE_MS};
use crate::game::GameCtx;
use crate::protocol::ServerTime;
use crate::room_seats::Player;

/// Ada at the table, Bo away since 500, Cy away with no time recorded.
fn table() -> Vec<Player> {
    let mut players = vec![Player::for_test(1, "Ada", true), Player::for_test(2, "Bo", false), Player::for_test(3, "Cy", false)];
    players[1].left_at = Some(500.0);
    players
}

fn due(ask: Ask, players: &mut [Player]) -> Option<ServerTime> {
    ask.house_due(&GameCtx { now: 0.0, players })
}

#[test]
fn an_ask_opens_for_one_player_with_or_without_a_clock() {
    let ask = Ask::open(1, 100.0, Some(20_000.0));
    assert_eq!((ask.who(), ask.opened_at(), ask.closes_at()), (1, 100.0, Some(20_100.0)));
    assert_eq!(Ask::open(1, 100.0, None).closes_at(), None, "without a clock it waits as long as it takes");
}

#[test]
fn the_house_waits_the_grace_after_the_later_of_leaving_and_the_ask_opening() {
    let mut p = table();
    assert_eq!(due(Ask::open(1, 100.0, None), &mut p), None, "a player at the table decides for themselves");
    assert_eq!(due(Ask::open(9, 100.0, None), &mut p), None, "nobody decides for a stranger");
    assert_eq!(due(Ask::open(2, 100.0, None), &mut p), Some(500.0 + HOUSE_GRACE_MS), "left after the ask opened");
    assert_eq!(due(Ask::open(2, 800.0, None), &mut p), Some(800.0 + HOUSE_GRACE_MS), "left before the ask opened");
    assert_eq!(due(Ask::open(3, 100.0, None), &mut p), Some(100.0 + HOUSE_GRACE_MS), "away with no time counts from the ask");
}

#[test]
fn on_a_clock_the_house_decides_at_the_earlier_of_the_clock_and_the_grace() {
    let mut p = table();
    assert_eq!(due(Ask::open(1, 100.0, Some(20_000.0)), &mut p), Some(20_100.0), "at the table: the clock");
    assert_eq!(due(Ask::open(2, 100.0, Some(20_000.0)), &mut p), Some(500.0 + HOUSE_GRACE_MS), "away: the grace comes first");
    assert_eq!(due(Ask::open(2, 100.0, Some(1_000.0)), &mut p), Some(1_100.0), "a short clock runs out first");
}

#[test]
fn the_house_decides_once_its_time_has_come() {
    let mut p = table();
    let ask = Ask::open(2, 800.0, None);
    assert!(!ask.house_decides(&GameCtx { now: 800.0 + HOUSE_GRACE_MS - 1.0, players: &mut p }));
    assert!(ask.house_decides(&GameCtx { now: 800.0 + HOUSE_GRACE_MS, players: &mut p }));
}

#[test]
fn a_player_who_comes_back_decides_again() {
    let mut p = table();
    let ask = Ask::open(2, 100.0, Some(20_000.0));
    p[1].conn = Some(7);
    assert_eq!(due(ask, &mut p), Some(20_100.0), "back at the table, only the clock is left");
    assert!(!ask.house_decides(&GameCtx { now: 5_000.0, players: &mut p }));
    (p[1].conn, p[1].left_at) = (None, Some(6_000.0));
    assert_eq!(due(ask, &mut p), Some(6_000.0 + HOUSE_GRACE_MS), "leaving again starts a fresh grace");
}
