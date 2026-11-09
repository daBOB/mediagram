use super::live::{GrandAlleeGame, Stage};
use super::live_debt_tests::bid;
use super::live_tests::{game, options, own, pick, place, until, ANA, BEN};
use super::rules::Estate;
use crate::protocol::ServerTime;
use crate::room_seats::Player;

const SELL_IT: &str = "Lagune versteigern · Wert 1.200 Taler";
const RAISE: &str = "Anderes versteigern und bauen";

/// Ana owns Lagune's plot (and Hacienda Sol's when `more`) with 1 500 Taler,
/// names Lagune's first stage (1 200) on the Baustelle at field 6, and the
/// permit die says Doppelt: 2 400 Taler she does not have.
fn doubled(more: bool) -> (GrandAlleeGame, Vec<Player>, ServerTime) {
    let (mut g, mut p) = game(2, &[4, 6]);
    own(&mut g, 0, 0, 0);
    if more {
        own(&mut g, 0, 3, 0);
    }
    g.t.seats[0].cash = 1_500;
    place(&mut g, 0, 2);
    let now = pick(&mut g, &mut p, ANA, "Würfeln", 10.0);
    let now = pick(&mut g, &mut p, ANA, "Lagune: 1 Stufe · 1.200 Taler", now);
    (g, p, now)
}

fn auctioned(g: &GrandAlleeGame) -> Option<u8> {
    match &g.stage {
        Stage::Auction(a) => Some(a.hotel),
        _ => None,
    }
}

#[test]
fn a_double_permit_that_cannot_be_paid_auctions_the_hotel_when_there_is_nothing_else() {
    let (mut g, mut p, now) = doubled(false);
    assert_eq!(auctioned(&g), Some(0), "2.400 Taler for one stage are more than Ana has");
    bid(&mut g, &mut p, BEN, 600, now);
    assert_eq!(g.t.estate(0), Estate { owner: Some(1), levels: 0 });
    assert_eq!((g.t.seats[0].cash, g.t.seats[1].cash), (2_100, 11_400), "the permit costs nothing");
}

#[test]
fn with_more_to_sell_the_mover_chooses_and_may_auction_the_hotel_in_question() {
    let (mut g, mut p, now) = doubled(true);
    assert_eq!(options(&g, &p, ANA, now), [SELL_IT, RAISE]);
    let now = pick(&mut g, &mut p, ANA, SELL_IT, now);
    assert_eq!(auctioned(&g), Some(0));
    bid(&mut g, &mut p, BEN, 600, now);
    assert_eq!((g.t.estate(0).owner, g.t.estate(3).owner, g.t.seats[0].cash), (Some(1), Some(0), 2_100));
}

#[test]
fn raising_the_money_auctions_other_properties_then_builds_at_the_doubled_price() {
    let (mut g, mut p, now) = doubled(true);
    let now = pick(&mut g, &mut p, ANA, RAISE, now);
    assert_eq!(options(&g, &p, ANA, now), ["Hacienda Sol versteigern · Wert 1.000 Taler"], "Lagune is the one being built");
    let now = pick(&mut g, &mut p, ANA, "Hacienda Sol versteigern · Wert 1.000 Taler", now);
    let now = bid(&mut g, &mut p, BEN, 1_000, now);
    assert_eq!((g.t.estate(0), g.t.seats[0].cash), (Estate { owner: Some(0), levels: 1 }, 1_500 + 1_000 - 2_400));
    assert!(options(&g, &p, ANA, now).iter().all(|o| o.starts_with("Feld ")), "the main building brings its free entrance");
}

#[test]
fn raising_too_little_auctions_the_hotel_in_question_after_all_and_the_permit_costs_nothing() {
    let (mut g, mut p, now) = doubled(true);
    let now = pick(&mut g, &mut p, ANA, RAISE, now);
    let now = pick(&mut g, &mut p, ANA, "Hacienda Sol versteigern · Wert 1.000 Taler", now);
    // no bid: the bank pays half, 2 000 in all, still short of 2 400
    let now = bid(&mut g, &mut p, BEN, 0, now);
    assert_eq!((auctioned(&g), g.t.seats[0].cash), (Some(0), 2_000));
    bid(&mut g, &mut p, BEN, 300, now);
    assert_eq!((g.t.estate(0), g.t.seats[0].cash), (Estate { owner: Some(1), levels: 0 }, 2_300));
}

#[test]
fn when_the_clock_runs_out_the_house_auctions_the_hotel_in_question() {
    let (mut g, mut p, now) = doubled(true);
    until(&mut g, &mut p, now, |g| auctioned(g).is_some());
    assert_eq!(auctioned(&g), Some(0));
}

#[test]
fn the_sheet_carries_the_clock_to_the_mover_and_the_hotels_as_cards() {
    use crate::game::{Game, ViewCtx};
    use crate::protocol::{HotelCard, PhoneScreen};
    let (mut g, p) = game(2, &[4]);
    own(&mut g, 0, 0, 2);
    g.t.entrances[4] = Some(0);
    let Stage::Ask { since, .. } = g.stage else { panic!("no ask") };
    let view = ViewCtx { now: since, players: &p, leader: None };
    let PhoneScreen::Sheet { lines, hotels, closes_at, .. } = g.phone_screen(&view, ANA) else { panic!("not a sheet") };
    assert_eq!(closes_at, Some(since + g.cfg.ask_ms));
    let lines: Vec<(String, String)> = lines.into_iter().map(|l| (l.label, l.value)).collect();
    assert_eq!(lines[0], ("Taler".into(), "12.000".into()), "cash first");
    let lagune = HotelCard { name: "Lagune".into(), color: "#2aa3a0".into(), built: 2, stages: 3, rate: g.t.rate(0), entrances: 1, value: 3_300 };
    assert_eq!(hotels, [lagune]);
    let PhoneScreen::Sheet { closes_at, hotels, .. } = g.phone_screen(&view, BEN) else { panic!("not a sheet") };
    assert_eq!((closes_at, hotels), (None, vec![]), "nothing to decide, nothing owned");
}
