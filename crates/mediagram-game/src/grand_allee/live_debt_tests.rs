use super::live::{GrandAlleeGame, Stage};
use super::live_tests::{game, options, own, pick, place, until, waiting, ANA, BEN, CEM};
use crate::game::{Game, ViewCtx};
use crate::protocol::{GrandAlleeTurn, PhoneScreen, TvScene};
use crate::room_seats::Player;

/// Ben (seat 1) is on turn at field 2 with 100 Taler and the plots in
/// `plots`; Cem's Lagune (rate 225) has an entrance on field 5, where Ben's
/// roll of 3 ends, and the nights die says 4: 900 Taler.
fn ben_owes_cem(plots: &[u8]) -> (GrandAlleeGame, Vec<Player>) {
    let (mut g, p) = game(3, &[3, 4]);
    own(&mut g, 2, 0, 3);
    g.t.entrances[5] = Some(0);
    for &h in plots {
        own(&mut g, 1, h, 0);
    }
    g.t.seats[1].cash = 100;
    place(&mut g, 1, 2);
    (g, p)
}

fn auction_view(g: &GrandAlleeGame, p: &[Player]) -> crate::protocol::GrandAlleeAuction {
    let TvScene::GrandAllee { scene } = g.tv_scene(&ViewCtx { now: 0.0, players: p, leader: None }) else { panic!("not grand allee") };
    match scene.turn {
        GrandAlleeTurn::Auction { auction } => auction,
        other => panic!("no auction: {other:?}"),
    }
}

#[test]
fn a_debt_auction_takes_sealed_bids_from_every_other_phone_and_a_tie_goes_to_the_nearest_after_the_seller() {
    let (mut g, mut p) = ben_owes_cem(&[3]);
    let now = pick(&mut g, &mut p, BEN, "Würfeln", 10.0);
    assert_eq!(options(&g, &p, BEN, now), ["Hacienda Sol versteigern · Wert 1.000 Taler"], "the debt must be met: no way out");
    let now = pick(&mut g, &mut p, BEN, "Hacienda Sol versteigern · Wert 1.000 Taler", now);
    let bids = ["100 Taler", "200 Taler", "500 Taler", "700 Taler", "1.000 Taler", "Passen"];
    assert_eq!((options(&g, &p, ANA, now), options(&g, &p, CEM, now)), (bids.map(String::from).to_vec(), bids.map(String::from).to_vec()));
    assert!(options(&g, &p, BEN, now).is_empty(), "the seller does not bid");
    let now = pick(&mut g, &mut p, ANA, "500 Taler", now);
    let auction = auction_view(&g, &p);
    assert_eq!((auction.bidders, auction.bid, auction.passed), (vec![CEM, ANA], vec![ANA], vec![]), "who has bid, never how much");
    assert!(matches!(g.phone_screen(&ViewCtx { now, players: &p, leader: None }, ANA), PhoneScreen::Sheet { picked: Some(2), .. }));
    pick(&mut g, &mut p, CEM, "500 Taler", now);
    assert_eq!(g.t.estate(3).owner, Some(2), "Cem sits nearer after Ben than Ana");
    // 100 + 500 still short of 900, and nothing left to sell: Ben pays what he has and is out
    assert!(g.t.seats[1].out);
    assert_eq!((g.t.seats[0].cash, g.t.seats[2].cash), (12_000, 12_000 - 500 + 600));
    assert_eq!((g.turn, g.gone.clone()), (2, vec![1]));
}

#[test]
fn without_a_bid_the_bank_buys_and_the_debt_goes_on_until_it_is_covered() {
    let (mut g, mut p) = ben_owes_cem(&[3, 4]);
    let now = pick(&mut g, &mut p, BEN, "Würfeln", 10.0);
    let now = pick(&mut g, &mut p, BEN, "Hacienda Sol versteigern · Wert 1.000 Taler", now);
    let now = pick(&mut g, &mut p, ANA, "Passen", now);
    // Cem stays silent: the auction closes when its 15 s run out
    let Stage::Auction(a) = &g.stage else { panic!("auction closed early") };
    let closes = a.since + g.cfg.auction_ms;
    let now = until(&mut g, &mut p, now, |g| !matches!(g.stage, Stage::Auction(_)));
    assert!(now >= closes);
    assert_eq!(g.t.estate(3).owner, None, "back to the bank, free");
    let now = until(&mut g, &mut p, now, waiting);
    assert_eq!(options(&g, &p, BEN, now), ["Riad Atlas versteigern · Wert 1.300 Taler"], "500 from the bank is not enough");
    // Ben lets the clock run: the house auctions what is left
    let now = until(&mut g, &mut p, now, |g| matches!(g.stage, Stage::Auction(_)));
    p[0].conn = None;
    pick(&mut g, &mut p, CEM, "Passen", now);
    assert!(!matches!(g.stage, Stage::Auction(_)), "an absent bidder does not hold the auction up");
    until(&mut g, &mut p, now, |g| g.turn == 2);
    assert_eq!(g.t.seats[1].cash, 100 + 500 + 650 - 900);
    assert!(!g.t.seats[1].out);
}

#[test]
fn an_open_decision_is_taken_by_the_house_when_its_clock_runs_out() {
    let (mut g, mut p) = game(2, &[4]);
    let now = pick(&mut g, &mut p, ANA, "Würfeln", 10.0);
    let Stage::Ask { since, .. } = g.stage else { panic!("no ask") };
    let now = until(&mut g, &mut p, now, |g| g.t.estate(6).owner.is_some());
    assert!(now >= since + g.cfg.ask_ms, "the house waits for the clock");
    assert_eq!(g.t.estate(6).owner, Some(0), "the balanced bot takes the bigger plot");
}

#[test]
fn an_absent_player_plays_itself_after_a_moment() {
    let (mut g, mut p) = game(2, &[1]);
    p[0].conn = None;
    let now = until(&mut g, &mut p, 0.0, |g| matches!(g.stage, Stage::Rolled { .. }));
    assert_eq!(now, g.cfg.auto_ms);
}

#[test]
fn the_game_ends_after_the_round_limit_with_the_richest_first() {
    let (mut g, mut p) = game(2, &[1, 1]);
    g.rounds = 1;
    g.t.seats[1].cash = 13_000;
    let now = pick(&mut g, &mut p, ANA, "Würfeln", 10.0);
    pick(&mut g, &mut p, BEN, "Würfeln", now);
    assert!(g.is_finished());
    assert_eq!(g.standings(), [1, 0]);
    assert_eq!(p[1].score, 13_000, "the score is the net worth");
    let view = ViewCtx { now, players: &p, leader: Some(ANA) };
    assert!(matches!(g.phone_screen(&view, ANA), PhoneScreen::Final { rank: 2, of: 2, can_restart: true, .. }));
}

#[test]
fn the_game_ends_when_one_player_is_left() {
    let (mut g, mut p) = game(2, &[3, 6]);
    own(&mut g, 1, 0, 3);
    g.t.entrances[5] = Some(0);
    g.t.seats[0].cash = 100;
    place(&mut g, 0, 2);
    pick(&mut g, &mut p, ANA, "Würfeln", 10.0);
    assert!(g.is_finished());
    let TvScene::GrandAllee { scene } = g.tv_scene(&ViewCtx { now: 0.0, players: &p, leader: None }) else { panic!() };
    assert!(matches!(scene.turn, GrandAlleeTurn::Final { winner: BEN, ref ranking } if *ranking == [BEN, ANA]));
}
