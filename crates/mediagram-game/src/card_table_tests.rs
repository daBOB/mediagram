use super::*;
use crate::card_game_driver::{ANA, BEN, CEM, players};

const BANNER_MS: ServerTime = 2600.0;

fn table() -> CardTable {
    CardTable::new(&players(3), BANNER_MS)
}

fn text(b: Option<CardsBanner>) -> Option<String> {
    b.map(|b| b.text)
}

#[test]
fn seats_follow_joining_order() {
    let t = table();
    assert_eq!(t.players(), [ANA, BEN, CEM]);
    assert_eq!((t.player_at(1), t.seat_of(CEM), t.seat_of(99)), (BEN, Some(2), None));
    assert_eq!(t.ask(2, 5.0).who(), CEM);
}

#[test]
fn the_ring_keeps_the_newest_thirteen_events_with_rising_ids() {
    let mut t = table();
    for k in 0..15 {
        t.event(f64::from(k), ANA, CardsEventKind::Draw { count: 1 });
    }
    let ids: Vec<u32> = t.events().iter().map(|e| e.id).collect();
    assert_eq!(ids, (2..15).collect::<Vec<u32>>());
    assert_eq!(t.events()[0].at, 2.0);
}

#[test]
fn a_banner_stays_up_its_time_unless_held() {
    let mut t = table();
    assert_eq!(t.banner(0.0, true), None, "nothing said yet");
    t.say(10.0, BEN, " zieht");
    assert_eq!(text(t.banner(10.0 + BANNER_MS - 1.0, false)).as_deref(), Some(" zieht"));
    assert_eq!(t.banner(10.0 + BANNER_MS, false), None, "read by now");
    assert_eq!(text(t.banner(10.0 + 10.0 * BANNER_MS, true)).as_deref(), Some(" zieht"), "held, as at the end of a game");
    assert_eq!(t.said(10.0 + BANNER_MS), None);
}

#[test]
fn saying_replaces_the_banner_at_once() {
    let mut t = table();
    t.say(10.0, ANA, " ruft Mau!");
    t.say(11.0, BEN, " zieht");
    let b = t.banner(11.0, false).unwrap();
    assert_eq!((b.player, b.text.as_str()), (BEN, " zieht"));
}

#[test]
fn a_queued_banner_waits_until_the_one_on_show_has_had_its_time() {
    let mut t = table();
    t.say(10.0, ANA, " ruft Mau!");
    t.say_next(11.0, ANA, " wünscht Pik");
    assert_eq!(text(t.banner(11.0, false)).as_deref(), Some(" ruft Mau!"));
    assert_eq!(t.said(11.0).map(|b| b.text.as_str()), Some(" ruft Mau!"), "the queued one is not said yet");
    let shown_from = 10.0 + BANNER_MS;
    assert_eq!(text(t.banner(shown_from, false)).as_deref(), Some(" wünscht Pik"));
    assert_eq!(text(t.banner(shown_from + BANNER_MS - 1.0, false)).as_deref(), Some(" wünscht Pik"), "it then has its own full time");
    assert_eq!(t.banner(shown_from + BANNER_MS, false), None);
}

#[test]
fn queueing_with_nothing_on_show_says_it_at_once_and_saying_drops_the_queue() {
    let mut t = table();
    t.say_next(1.0, ANA, " wünscht Pik");
    assert_eq!(text(t.banner(1.0, false)).as_deref(), Some(" wünscht Pik"));
    t.say(10.0, ANA, " ruft Mau!");
    t.say_next(11.0, ANA, " wünscht Herz");
    t.say(12.0, BEN, " zieht");
    assert_eq!(text(t.banner(10.0 + BANNER_MS, false)).as_deref(), Some(" zieht"), "the wish was dropped");
}

