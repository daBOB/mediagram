use super::live::{GrandAlleeConfig, GrandAlleeGame, Stage};
use super::rules::{Estate, Rules};
use crate::game::{Game, GameCtx, ViewCtx};
use crate::protocol::{Action, PhoneScreen, PlayerId, ServerTime};
use crate::room_seats::Player;

pub(super) const ANA: PlayerId = 0;
pub(super) const BEN: PlayerId = 1;
pub(super) const CEM: PlayerId = 2;

/// `n` connected players; the dice fall as `rolls` say, then as the seed says.
pub(super) fn game(n: usize, rolls: &[u8]) -> (GrandAlleeGame, Vec<Player>) {
    game_with(n, rolls, Rules::default())
}

/// Like `game`, under `rules`.
pub(super) fn game_with(n: usize, rolls: &[u8], rules: Rules) -> (GrandAlleeGame, Vec<Player>) {
    let names = ["Ana", "Ben", "Cem", "Dana", "Eli", "Fay", "Gus", "Hal"];
    let players: Vec<Player> = (0..n).map(|i| Player::for_test(i as u8, names[i], true)).collect();
    let cfg = GrandAlleeConfig { rules, rolls: rolls.to_vec(), seed: Some(1), ..GrandAlleeConfig::default() };
    (GrandAlleeGame::new(cfg, &players, 0.0), players)
}

/// Ticks every 50 ms until `stop` holds (or 60 s pass) and returns the time.
pub(super) fn until(g: &mut GrandAlleeGame, p: &mut [Player], mut now: ServerTime, stop: impl Fn(&GrandAlleeGame) -> bool) -> ServerTime {
    let end = now + 60_000.0;
    while !stop(g) && now < end {
        now += 50.0;
        g.on_tick(&mut GameCtx { now, players: p });
    }
    assert!(stop(g), "stage never reached, stuck at {:?}", g.stage);
    now
}

/// The mover or a bidder has something to decide, or the game is over.
pub(super) fn waiting(g: &GrandAlleeGame) -> bool {
    matches!(g.stage, Stage::Ask { .. } | Stage::Auction(_) | Stage::Final)
}

pub(super) fn options(g: &GrandAlleeGame, p: &[Player], who: PlayerId, now: ServerTime) -> Vec<String> {
    match g.phone_screen(&ViewCtx { now, players: p, leader: Some(ANA) }, who) {
        PhoneScreen::Sheet { options, .. } => options.into_iter().map(|o| o.label).collect(),
        other => panic!("not a sheet: {other:?}"),
    }
}

/// `who` taps the button labelled `label` once it is open, and play goes on
/// until someone has to decide again; returns the time.
pub(super) fn pick(g: &mut GrandAlleeGame, p: &mut [Player], who: PlayerId, label: &str, now: ServerTime) -> ServerTime {
    let labels = options(g, p, who, now);
    let index = labels.iter().position(|l| l == label).unwrap_or_else(|| panic!("no {label:?} in {labels:?}"));
    let at = match &g.stage {
        Stage::Ask { since, .. } => since.max(now),
        Stage::Auction(a) => a.since.max(now),
        _ => now,
    };
    g.on_action(&mut GameCtx { now: at, players: p }, who, &Action::Choose { index: index as u8, at }).unwrap();
    // one tick first, so a bid that completes an auction closes it
    let now = at + 50.0;
    g.on_tick(&mut GameCtx { now, players: p });
    until(g, p, now, waiting)
}

/// Seat `s` stands on `field` and its turn begins.
pub(super) fn place(g: &mut GrandAlleeGame, s: usize, field: u8) {
    g.t.seats[s].field = field;
    g.begin_move(s, 0.0);
}

pub(super) fn own(g: &mut GrandAlleeGame, s: usize, hotel: u8, levels: u8) {
    g.t.estates[usize::from(hotel)] = Estate { owner: Some(s), levels };
}

#[test]
fn turns_go_round_in_seat_order_and_skip_players_who_are_out() {
    let (mut g, mut p) = game(3, &[3, 1, 5]);
    assert_eq!(options(&g, &p, ANA, 0.0), ["Würfeln"]);
    assert!(options(&g, &p, BEN, 0.0).is_empty(), "only the mover decides");
    let now = pick(&mut g, &mut p, ANA, "Würfeln", 10.0);
    assert_eq!((g.turn, g.t.seats[0].field), (1, 3));
    let now = pick(&mut g, &mut p, BEN, "Würfeln", now);
    assert_eq!(g.turn, 2);
    g.t.seats[0].out = true;
    pick(&mut g, &mut p, CEM, "Würfeln", now);
    assert_eq!((g.turn, g.round), (1, 2), "Ana is out: round two starts with Ben");
}

#[test]
fn a_six_rolls_again_and_the_extra_turn_may_be_declined() {
    let (mut g, mut p) = game(2, &[6, 2, 6]);
    let now = pick(&mut g, &mut p, ANA, "Würfeln", 10.0);
    assert_eq!(g.turn, 0);
    assert_eq!(options(&g, &p, ANA, now), ["Nochmal würfeln", "Lieber nicht"]);
    let now = pick(&mut g, &mut p, ANA, "Nochmal würfeln", now);
    assert_eq!((g.turn, g.t.seats[0].field), (1, 8));
    let now = pick(&mut g, &mut p, BEN, "Würfeln", now);
    assert_eq!(g.turn, 1, "Ben's 6 offers another turn too");
    pick(&mut g, &mut p, BEN, "Lieber nicht", now);
    assert_eq!((g.turn, g.round), (0, 2));
}

#[test]
fn after_a_six_a_playing_bot_rolls_again_and_the_house_lets_it_go() {
    use super::bots::{Bot, Style};
    use super::live::HOUSE;
    let (mut g, mut p) = game(2, &[6]);
    let now = pick(&mut g, &mut p, ANA, "Würfeln", 10.0);
    let Stage::Ask { ask, .. } = g.stage.clone() else { panic!("no ask: {:?}", g.stage) };
    assert_eq!(options(&g, &p, ANA, now), ["Nochmal würfeln", "Lieber nicht"]);
    assert_eq!(Bot { style: Style::Cautious, house: false }.pick(&g.t, 0, &ask), 0);
    assert_eq!(HOUSE.pick(&g.t, 0, &ask), 1);
}

#[test]
fn passing_the_rathaus_offers_one_entrance_per_built_hotel() {
    let (mut g, mut p) = game(2, &[3]);
    own(&mut g, 0, 0, 1);
    place(&mut g, 0, 18);
    let now = pick(&mut g, &mut p, ANA, "Würfeln", 10.0);
    assert_eq!(options(&g, &p, ANA, now), ["Lagune · 300 Taler", "Lieber nicht"]);
    let now = pick(&mut g, &mut p, ANA, "Lagune · 300 Taler", now);
    assert_eq!(options(&g, &p, ANA, now), ["Feld 5", "Feld 6", "Feld 7", "Feld 8", "Feld 9", "Zurück"]);
    pick(&mut g, &mut p, ANA, "Feld 6", now);
    assert_eq!(g.t.entrances[5], Some(0));
    assert_eq!(g.t.seats[0].cash, 11_700);
    assert_eq!(g.turn, 1, "one entrance per hotel: the pass is done");
}

#[test]
fn an_unbuilt_plot_of_another_player_can_be_bought_by_force() {
    let (mut g, mut p) = game(2, &[4]);
    own(&mut g, 1, 0, 0);
    let now = pick(&mut g, &mut p, ANA, "Würfeln", 10.0);
    assert_eq!(options(&g, &p, ANA, now), ["Lagune von Ben übernehmen · 600 Taler", "Maharadscha kaufen · 3.000 Taler", "Lieber nicht"]);
    pick(&mut g, &mut p, ANA, "Lagune von Ben übernehmen · 600 Taler", now);
    assert_eq!(g.t.estate(0).owner, Some(0));
    assert_eq!((g.t.seats[0].cash, g.t.seats[1].cash), (11_400, 12_600));
    // a built plot is never forced
    let (mut g, mut p) = game(2, &[4]);
    own(&mut g, 1, 0, 1);
    let now = pick(&mut g, &mut p, ANA, "Würfeln", 10.0);
    assert_eq!(options(&g, &p, ANA, now), ["Maharadscha kaufen · 3.000 Taler", "Lieber nicht"]);
}

#[test]
fn a_granted_permit_is_used_and_the_main_building_brings_a_free_entrance() {
    let (mut g, mut p) = game(2, &[4, 1]);
    own(&mut g, 0, 0, 0);
    place(&mut g, 0, 2);
    let now = pick(&mut g, &mut p, ANA, "Würfeln", 10.0);
    assert_eq!(options(&g, &p, ANA, now), ["Lagune: 1 Stufe · 1.200 Taler", "Lagune: 2 Stufen · 2.100 Taler", "Lieber nicht"]);
    let now = pick(&mut g, &mut p, ANA, "Lagune: 2 Stufen · 2.100 Taler", now);
    assert_eq!((g.t.estate(0).levels, g.t.seats[0].cash), (2, 9_900));
    let fields = options(&g, &p, ANA, now);
    assert_eq!(fields.len(), 5, "a field for the free entrance, no way to turn it down: {fields:?}");
    pick(&mut g, &mut p, ANA, "Feld 5", now);
    assert_eq!(g.t.entrances[4], Some(0));
    assert_eq!(g.t.seats[0].cash, 9_900);
}

#[test]
fn free_build_can_raise_the_main_building_with_its_entrance() {
    let (mut g, mut p) = game(2, &[2]);
    own(&mut g, 0, 0, 0);
    let now = pick(&mut g, &mut p, ANA, "Würfeln", 10.0);
    assert_eq!(options(&g, &p, ANA, now), ["Lagune: Stufe 1 gratis", "Lieber nicht"]);
    let now = pick(&mut g, &mut p, ANA, "Lagune: Stufe 1 gratis", now);
    pick(&mut g, &mut p, ANA, "Feld 9", now);
    assert_eq!((g.t.estate(0).levels, g.t.entrances[8], g.t.seats[0].cash), (1, Some(0), 12_000));
}

#[test]
fn passing_the_bank_pays_nothing_once_only_two_are_left() {
    let (mut g, mut p) = game(3, &[4, 4]);
    place(&mut g, 0, 38);
    let now = pick(&mut g, &mut p, ANA, "Würfeln", 10.0);
    assert_eq!(g.t.seats[0].cash, 14_000);
    g.t.seats[2].out = true;
    place(&mut g, 1, 38);
    pick(&mut g, &mut p, BEN, "Würfeln", now);
    assert_eq!(g.t.seats[1].cash, 12_000);
}
