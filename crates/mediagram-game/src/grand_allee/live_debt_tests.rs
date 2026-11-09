use super::live::{GrandAlleeGame, Stage};
use super::rules::Rules;
use super::live_tests::{game, game_with, options, own, pick, place, until, waiting, ANA, BEN, CEM};
use crate::game::{ActionError, Game, GameCtx, ViewCtx, HOUSE_GRACE_MS};
use crate::protocol::{Action, BidRange, GrandAlleeTurn, PhoneScreen, PlayerId, ServerTime, TvScene};
use crate::room_seats::Player;

/// The bid `who`'s phone offers, if any.
pub(super) fn bid_range(g: &GrandAlleeGame, p: &[Player], who: PlayerId, now: ServerTime) -> Option<BidRange> {
    match g.phone_screen(&ViewCtx { now, players: p, leader: None }, who) {
        PhoneScreen::Sheet { bid, .. } => bid,
        other => panic!("not a sheet: {other:?}"),
    }
}

fn send_bid(g: &mut GrandAlleeGame, p: &mut [Player], who: PlayerId, amount: i32, at: ServerTime) -> Result<(), ActionError> {
    g.on_action(&mut GameCtx { now: at, players: p }, who, &Action::Bid { amount, at })
}

/// `who` bids `amount` (0 passes), and play goes on until someone has to decide again; returns the time.
pub(super) fn bid(g: &mut GrandAlleeGame, p: &mut [Player], who: PlayerId, amount: i32, now: ServerTime) -> ServerTime {
    let Stage::Auction(a) = &g.stage else { panic!("no auction: {:?}", g.stage) };
    let at = a.since.max(now);
    send_bid(g, p, who, amount, at).unwrap();
    // one tick first, so a bid that completes an auction closes it
    let now = at + 50.0;
    g.on_tick(&mut GameCtx { now, players: p });
    until(g, p, now, waiting)
}

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

fn tv_turn(g: &GrandAlleeGame, p: &[Player]) -> GrandAlleeTurn {
    let TvScene::GrandAllee { scene } = g.tv_scene(&ViewCtx { now: 0.0, players: p, leader: None }) else { panic!("not grand allee") };
    scene.turn
}

#[test]
fn a_debt_auction_takes_sealed_bids_from_every_other_phone_and_a_tie_goes_to_the_nearest_after_the_seller() {
    let (mut g, mut p) = ben_owes_cem(&[3]);
    let now = pick(&mut g, &mut p, BEN, "Würfeln", 10.0);
    assert_eq!(options(&g, &p, BEN, now), ["Hacienda Sol versteigern · Wert 1.000 Taler"], "the debt must be met: no way out");
    let now = pick(&mut g, &mut p, BEN, "Hacienda Sol versteigern · Wert 1.000 Taler", now);
    let open = BidRange { value: 1_000, bank: 500, max: 12_000, step: 100, placed: None };
    assert_eq!((bid_range(&g, &p, ANA, now), bid_range(&g, &p, CEM, now)), (Some(open.clone()), Some(open)), "no minimum, up to all one has");
    assert_eq!(bid_range(&g, &p, BEN, now), None, "the seller does not bid");
    let now = bid(&mut g, &mut p, ANA, 500, now);
    let GrandAlleeTurn::Auction { auction } = tv_turn(&g, &p) else { panic!("no auction") };
    assert_eq!((auction.bidders, auction.bid, auction.passed), (vec![CEM, ANA], vec![ANA], vec![]), "who has bid, never how much");
    assert_eq!(bid_range(&g, &p, ANA, now).and_then(|b| b.placed), Some(500));
    assert_eq!(send_bid(&mut g, &mut p, ANA, 900, now), Err(ActionError::Ignored), "sealed and final");
    send_bid(&mut g, &mut p, CEM, 500, now).unwrap();
    let now = until(&mut g, &mut p, now, |g| matches!(g.stage, Stage::Note { .. }));
    assert_eq!(g.t.estate(3).owner, Some(2), "Cem sits nearer after Ben than Ana");
    // 100 + 500 still short of 900, and nothing left to sell: Ben pays what he has and is out
    assert!(g.t.seats[1].out);
    assert!(matches!(tv_turn(&g, &p), GrandAlleeTurn::Note { player: BEN, broke: true, .. }), "the TV is told Ben went broke");
    until(&mut g, &mut p, now, waiting);
    assert_eq!((g.t.seats[0].cash, g.t.seats[2].cash), (12_000, 12_000 - 500 + 600));
    assert_eq!((g.turn, g.gone.clone()), (2, vec![1]));
}

#[test]
fn without_a_bid_the_bank_buys_and_the_debt_goes_on_until_it_is_covered() {
    let (mut g, mut p) = ben_owes_cem(&[3, 4]);
    let now = pick(&mut g, &mut p, BEN, "Würfeln", 10.0);
    let now = pick(&mut g, &mut p, BEN, "Hacienda Sol versteigern · Wert 1.000 Taler", now);
    let now = bid(&mut g, &mut p, ANA, 0, now);
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
    bid(&mut g, &mut p, CEM, 0, now);
    assert!(!matches!(g.stage, Stage::Auction(_)), "an absent bidder does not hold the auction up");
    until(&mut g, &mut p, now, |g| g.turn == 2);
    assert_eq!(g.t.seats[1].cash, 100 + 500 + 650 - 900);
    assert!(!g.t.seats[1].out);
}

#[test]
fn a_bid_must_be_whole_hundreds_within_the_bidders_cash() {
    let (mut g, mut p) = ben_owes_cem(&[3]);
    let now = pick(&mut g, &mut p, BEN, "Würfeln", 10.0);
    let now = pick(&mut g, &mut p, BEN, "Hacienda Sol versteigern · Wert 1.000 Taler", now);
    g.t.seats[0].cash = 1_250;
    assert_eq!(bid_range(&g, &p, ANA, now).map(|b| b.max), Some(1_200));
    for wrong in [1_300, 450, -100] {
        assert!(matches!(send_bid(&mut g, &mut p, ANA, wrong, now), Err(ActionError::NotAllowed(_))), "{wrong}");
    }
    assert_eq!(send_bid(&mut g, &mut p, BEN, 100, now), Err(ActionError::Ignored), "the seller cannot bid");
    send_bid(&mut g, &mut p, ANA, 1_200, now).unwrap();
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
    assert_eq!(now, HOUSE_GRACE_MS);
}

#[test]
fn the_game_ends_after_the_round_limit_with_the_richest_first() {
    let (mut g, mut p) = game_with(2, &[1, 1], Rules { min_rounds: 1, max_rounds: 1, ..Rules::default() });
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
