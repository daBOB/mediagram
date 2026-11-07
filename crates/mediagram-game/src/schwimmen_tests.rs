use super::*;
use crate::protocol::{ButtonNeeds, CardDesk, CardsScene};
use Rank::*;
use crate::cards::Suit::{self, *};

const ANA: PlayerId = 0;
const BEN: PlayerId = 1;
const CEM: PlayerId = 2;

fn c(suit: Suit, rank: Rank) -> Card {
    Card::new(suit, rank)
}

fn players(n: usize) -> Vec<Player> {
    ["Ana", "Ben", "Cem", "Dana", "Eli", "Fay", "Gus", "Hal"][..n]
        .iter()
        .enumerate()
        .map(|(i, name)| Player::for_test(i as PlayerId, name, true))
        .collect()
}

/// A game with the given hands and middle, all three lives, seat 0 to move.
fn rig(hands: Vec<Vec<Card>>, middle: Vec<Card>) -> (SchwimmenGame, Vec<Player>) {
    let p = players(hands.len());
    let mut g = SchwimmenGame::new(SchwimmenConfig { seed: Some(1), ..SchwimmenConfig::default() }, &p, 0.0);
    g.hands = hands;
    g.hands.iter_mut().for_each(|h| h.sort());
    g.middle = middle;
    g.lives.fill(LIVES);
    g.out.fill(false);
    g.out_order.clear();
    g.lost.fill(false);
    g.revealed = false;
    g.knocker = None;
    g.passes = 0;
    g.starter = 0;
    g.turn = 0;
    g.stage = Stage::Turn { since: 0.0 };
    (g, p)
}

fn act(g: &mut SchwimmenGame, p: &mut [Player], who: PlayerId, button: &str, hand: &[u8], table: Option<u8>, now: f64) -> Result<(), ActionError> {
    g.on_action(&mut GameCtx { now, players: p }, who, &Action::Cards { button: button.into(), hand: hand.to_vec(), table })
}

fn pass(g: &mut SchwimmenGame, p: &mut [Player], who: PlayerId, now: f64) {
    act(g, p, who, "pass", &[], None, now).unwrap();
}

fn tick(g: &mut SchwimmenGame, p: &mut [Player], now: f64) {
    g.on_tick(&mut GameCtx { now, players: p });
}

fn desk(g: &SchwimmenGame, p: &[Player], who: PlayerId) -> CardDesk {
    match g.phone_screen(&ViewCtx { now: 0.0, players: p, leader: Some(ANA) }, who) {
        PhoneScreen::Cards { desk } => desk,
        other => panic!("expected a desk, got {other:?}"),
    }
}

fn scene(g: &SchwimmenGame, p: &[Player], now: f64) -> CardsScene {
    match g.tv_scene(&ViewCtx { now, players: p, leader: Some(ANA) }) {
        TvScene::Cards { scene } => scene,
        other => panic!("expected a card scene, got {other:?}"),
    }
}

/// Worth 9, 10 and 11: three different hands, none close to 31.
fn three_dull() -> Vec<Vec<Card>> {
    vec![
        vec![c(Hearts, Two), c(Spades, Three), c(Clubs, Nine)],
        vec![c(Hearts, Two), c(Spades, Three), c(Clubs, Ten)],
        vec![c(Hearts, Two), c(Spades, Three), c(Clubs, Ace)],
    ]
}

fn middle_dull() -> Vec<Card> {
    vec![c(Diamonds, Seven), c(Diamonds, Eight), c(Spades, Seven)]
}

#[test]
fn the_deal_gives_three_cards_each_three_in_the_middle_and_three_lives() {
    for n in [2, 5, 8] {
        let g = SchwimmenGame::new(SchwimmenConfig { seed: Some(7), ..SchwimmenConfig::default() }, &players(n), 0.0);
        let dealt: usize = g.hands.iter().map(Vec::len).sum();
        assert!(g.hands.iter().all(|h| h.len() == 3));
        assert_eq!(g.middle.len() + g.deck.len() + dealt, 32, "{n} players");
        assert_eq!(g.lives, vec![3; n]);
    }
}

#[test]
fn swapping_one_card_trades_a_hand_card_for_a_middle_card() {
    let (mut g, mut p) = rig(three_dull(), middle_dull());
    let before = g.hands[0].clone();
    act(&mut g, &mut p, ANA, "swap", &[2], Some(1), 1.0).unwrap();
    assert!(g.middle.contains(&before[2]) && g.middle[1] == before[2], "the hand card took the slot");
    assert!(g.hands[0].contains(&c(Diamonds, Eight)));
    assert_eq!((g.turn, g.passes), (1, 0));
    assert_eq!(g.events.last().unwrap().kind, CardsEventKind::Play { card: before[2], pile: 1 });
}

#[test]
fn a_swap_needs_one_hand_card_and_one_middle_card_and_only_on_your_turn() {
    let (mut g, mut p) = rig(three_dull(), middle_dull());
    assert!(matches!(act(&mut g, &mut p, ANA, "swap", &[], Some(0), 1.0), Err(ActionError::NotAllowed(_))));
    assert!(matches!(act(&mut g, &mut p, ANA, "swap", &[0], None, 1.0), Err(ActionError::NotAllowed(_))));
    assert_eq!(act(&mut g, &mut p, ANA, "swap", &[3], Some(0), 1.0), Err(ActionError::Ignored));
    assert_eq!(act(&mut g, &mut p, BEN, "swap", &[0], Some(0), 1.0), Err(ActionError::Ignored));
    assert_eq!(act(&mut g, &mut p, BEN, "pass", &[], None, 1.0), Err(ActionError::Ignored));
    assert_eq!(g.turn, 0);
}

#[test]
fn swapping_all_three_trades_the_whole_hand() {
    let (mut g, mut p) = rig(three_dull(), middle_dull());
    let mine = g.hands[0].clone();
    act(&mut g, &mut p, ANA, "swap_all", &[], None, 1.0).unwrap();
    assert_eq!(g.middle, mine);
    assert_eq!(g.hands[0].len(), 3);
    assert!(g.hands[0].contains(&c(Diamonds, Seven)) && g.hands[0].contains(&c(Spades, Seven)));
    assert_eq!(g.turn, 1);
}

#[test]
fn passing_keeps_the_cards_and_a_swap_in_between_restarts_the_count() {
    let (mut g, mut p) = rig(three_dull(), middle_dull());
    pass(&mut g, &mut p, ANA, 1.0);
    pass(&mut g, &mut p, BEN, 2.0);
    assert_eq!((g.passes, g.middle.clone()), (2, middle_dull()));
    act(&mut g, &mut p, CEM, "swap", &[0], Some(0), 3.0).unwrap();
    assert_eq!(g.passes, 0);
}

#[test]
fn when_everyone_passes_in_a_row_the_middle_is_replaced_from_the_deck() {
    let (mut g, mut p) = rig(three_dull(), middle_dull());
    g.deck = vec![c(Clubs, Two), c(Clubs, Five), c(Clubs, Six), c(Hearts, Five), c(Hearts, Six)];
    for (i, who) in [ANA, BEN, CEM].into_iter().enumerate() {
        pass(&mut g, &mut p, who, i as f64);
    }
    assert_eq!(g.middle, vec![c(Clubs, Six), c(Hearts, Five), c(Hearts, Six)], "the top three of the deck");
    assert_eq!(g.deck.len(), 5, "the old middle went under the deck");
    assert_eq!(g.deck[..3], middle_dull()[..]);
    assert_eq!(g.passes, 0);
    assert_eq!(scene(&g, &p, 3.0).banner.unwrap().text, " schiebt – neue Karten in der Mitte");
}

#[test]
fn knocking_gives_everyone_else_one_more_turn_then_the_reveal() {
    let (mut g, mut p) = rig(three_dull(), middle_dull());
    pass(&mut g, &mut p, ANA, 1.0);
    act(&mut g, &mut p, BEN, "knock", &[], None, 2.0).unwrap();
    assert_eq!(g.knocker, Some(1));
    assert_eq!(scene(&g, &p, 2.0).note.as_deref(), Some("Geklopft – letzte Runde"));
    assert_eq!(scene(&g, &p, 2.0).seats[1].marker.as_deref(), Some("●●● · klopft"));
    let d = desk(&g, &p, CEM);
    assert!(d.buttons.iter().all(|b| b.id != "knock"), "no second knock");
    assert!(d.prompt.contains("Ben hat geklopft"));
    assert_eq!(act(&mut g, &mut p, CEM, "knock", &[], None, 3.0), Err(ActionError::NotAllowed("Es wurde schon geklopft.")));
    pass(&mut g, &mut p, CEM, 3.0);
    assert!(matches!(g.stage, Stage::Turn { .. }), "Ana has her last turn");
    assert_eq!(g.turn, 0);
    pass(&mut g, &mut p, ANA, 4.0);
    assert!(matches!(g.stage, Stage::Reveal { .. }), "the turn came back round to the knocker");
}

#[test]
fn the_knocker_skips_the_refresh_when_the_last_players_pass() {
    let (mut g, mut p) = rig(three_dull(), middle_dull());
    act(&mut g, &mut p, ANA, "knock", &[], None, 1.0).unwrap();
    pass(&mut g, &mut p, BEN, 2.0);
    assert_eq!(g.middle, middle_dull());
}

#[test]
fn a_hand_worth_31_ends_the_round_at_once() {
    let mut hands = three_dull();
    hands[1] = vec![c(Hearts, Ace), c(Hearts, Queen), c(Clubs, Three)];
    let (mut g, mut p) = rig(hands, vec![c(Hearts, King), c(Diamonds, Eight), c(Spades, Seven)]);
    pass(&mut g, &mut p, ANA, 1.0);
    act(&mut g, &mut p, BEN, "swap", &[0], Some(0), 2.0).unwrap();
    assert!(matches!(g.stage, Stage::Reveal { .. }), "Ben has 31, Cem never moves");
    assert_eq!(g.lives, vec![2, 3, 3], "Ana's 9 is the lowest");
    let s = scene(&g, &p, 2.0);
    assert_eq!(s.note.as_deref(), Some("Feuer – 31!"));
    assert_eq!(s.seats[1].marker.as_deref(), Some("●●●"));
}

#[test]
fn a_dealt_31_is_revealed_without_a_turn() {
    let (g, _) = (0..2000u64)
        .map(|seed| {
            let p = players(3);
            (SchwimmenGame::new(SchwimmenConfig { seed: Some(seed), ..SchwimmenConfig::default() }, &p, 0.0), p)
        })
        .find(|(g, _)| g.hands.iter().any(|h| value(h) == FEUER))
        .expect("some deal holds 31");
    assert!(matches!(g.stage, Stage::Reveal { .. }));
    assert!(g.revealed);
}

#[test]
fn three_of_a_kind_is_30_and_a_half_and_beats_every_ordinary_hand() {
    let mut hands = three_dull();
    hands[0] = vec![c(Hearts, Nine), c(Spades, Nine), c(Clubs, Nine)];
    hands[1] = vec![c(Hearts, Ace), c(Hearts, King), c(Hearts, Nine)];
    let (mut g, mut p) = rig(hands, middle_dull());
    act(&mut g, &mut p, ANA, "knock", &[], None, 1.0).unwrap();
    pass(&mut g, &mut p, BEN, 2.0);
    pass(&mut g, &mut p, CEM, 3.0);
    assert_eq!(g.lives, vec![3, 3, 2], "Cem's 11 is lowest");
    let marker = scene(&g, &p, 3.0).seats[0].marker.clone().unwrap();
    assert_eq!(marker, "●●●");
    assert_eq!(scene(&g, &p, 3.0).seats[0].worth, Some(61), "30½ in half points");
}

#[test]
fn the_lowest_hand_loses_a_life_and_ties_cost_all_of_them() {
    let mut hands = three_dull();
    hands[1] = hands[2].clone();
    hands[0] = vec![c(Hearts, Ace), c(Hearts, King), c(Hearts, Nine)];
    let (mut g, mut p) = rig(hands, middle_dull());
    act(&mut g, &mut p, ANA, "knock", &[], None, 1.0).unwrap();
    pass(&mut g, &mut p, BEN, 2.0);
    pass(&mut g, &mut p, CEM, 3.0);
    assert_eq!(g.lives, vec![3, 2, 2]);
    let s = scene(&g, &p, 3.0);
    assert_eq!(s.banner.unwrap().text, " und alle mit gleichem Blatt verlieren");
    assert_eq!(s.seats[1].marker.as_deref(), Some("●●○ · −1"));
    assert_eq!(s.seats.iter().map(|x| x.worth).collect::<Vec<_>>(), [Some(2 * 30), Some(22), Some(22)], "values ride in the badge, in half points");
    assert_eq!(s.seats[0].open.len(), 3);
    assert_eq!(s.seats[0].cards, 0);
}

#[test]
fn a_round_where_everyone_ties_costs_nobody_a_life() {
    let same = vec![c(Hearts, Two), c(Spades, Three), c(Clubs, Nine)];
    let (mut g, mut p) = rig(vec![same.clone(), same.clone(), same], middle_dull());
    act(&mut g, &mut p, ANA, "knock", &[], None, 1.0).unwrap();
    pass(&mut g, &mut p, BEN, 2.0);
    pass(&mut g, &mut p, CEM, 3.0);
    assert_eq!(g.lives, vec![3, 3, 3]);
    assert_eq!((g.lost.clone(), g.out.clone()), (vec![false; 3], vec![false; 3]), "nobody loses, nobody is out");
    let s = scene(&g, &p, 3.0);
    assert_eq!(s.banner.unwrap().text, " und alle anderen sind gleichauf");
    assert!(s.seats.iter().all(|x| x.marker.as_deref() == Some("●●●")), "no loss on any seat");
    assert!(matches!(g.stage, Stage::Reveal { .. }), "the game goes on");
    // the same with swimmers: everyone at no life left and tied is still not out
    let (mut g, mut p) = rig(vec![vec![c(Hearts, Two), c(Spades, Three), c(Clubs, Nine)]; 2], middle_dull());
    g.lives = vec![0, 0];
    act(&mut g, &mut p, ANA, "knock", &[], None, 1.0).unwrap();
    pass(&mut g, &mut p, BEN, 2.0);
    assert_eq!((g.lives.clone(), g.out.clone()), (vec![0, 0], vec![false, false]));
}

#[test]
fn the_next_round_is_dealt_after_the_reveal_with_the_next_player_starting() {
    let (mut g, mut p) = rig(three_dull(), middle_dull());
    act(&mut g, &mut p, ANA, "knock", &[], None, 1.0).unwrap();
    pass(&mut g, &mut p, BEN, 2.0);
    pass(&mut g, &mut p, CEM, 3.0);
    tick(&mut g, &mut p, 5000.0);
    assert!(g.revealed, "the hands stay open for a while");
    let at = 3.0 + g.cfg.reveal_ms;
    tick(&mut g, &mut p, at);
    assert_eq!((g.starter, g.turn, g.knocker), (1, 1, None));
    assert!(g.hands.iter().all(|h| h.len() == 3));
    assert_eq!(g.lives, vec![2, 3, 3], "lives carry over");
}

#[test]
fn losing_the_last_life_means_swimming_and_the_next_loss_is_out() {
    let (mut g, mut p) = rig(three_dull(), middle_dull());
    g.lives = vec![1, 3, 3];
    g.hands[0] = vec![c(Hearts, Two), c(Spades, Three), c(Clubs, Four)];
    g.deck = vec![];
    act(&mut g, &mut p, ANA, "knock", &[], None, 1.0).unwrap();
    pass(&mut g, &mut p, BEN, 2.0);
    pass(&mut g, &mut p, CEM, 3.0);
    assert_eq!((g.lives[0], g.out[0]), (0, false), "swimming");
    assert_eq!(scene(&g, &p, 3.0).banner.unwrap().text, " schwimmt jetzt");
    assert_eq!(scene(&g, &p, 3.0).seats[0].marker.as_deref(), Some("schwimmt · −1"));
    // one more round with Ana still lowest
    g.hands = three_dull();
    g.hands[0] = vec![c(Hearts, Two), c(Spades, Three), c(Clubs, Four)];
    g.stage = Stage::Turn { since: 10.0 };
    g.revealed = false;
    g.knocker = None;
    g.turn = 0;
    g.lost.fill(false);
    act(&mut g, &mut p, ANA, "knock", &[], None, 11.0).unwrap();
    pass(&mut g, &mut p, BEN, 12.0);
    pass(&mut g, &mut p, CEM, 13.0);
    assert!(g.out[0]);
    assert_eq!(scene(&g, &p, 13.0).seats[0].marker.as_deref(), Some("raus"));
    assert!(matches!(g.stage, Stage::Reveal { .. }));
    let view = ViewCtx { now: 13.0, players: &p, leader: Some(ANA) };
    assert!(matches!(g.phone_screen(&view, ANA), PhoneScreen::Wait { .. }));
}

#[test]
fn the_last_player_left_wins_and_the_rest_rank_by_when_they_went_out() {
    let (mut g, mut p) = rig(three_dull(), middle_dull());
    g.lives = vec![3, 0, 0];
    g.hands[1] = vec![c(Hearts, Two), c(Spades, Three), c(Clubs, Four)];
    g.hands[2] = vec![c(Hearts, Five), c(Spades, Three), c(Clubs, Four)];
    g.out_order = vec![];
    act(&mut g, &mut p, ANA, "knock", &[], None, 1.0).unwrap();
    pass(&mut g, &mut p, BEN, 2.0);
    pass(&mut g, &mut p, CEM, 3.0);
    assert!(!g.is_finished(), "Ben out, Cem and Ana remain");
    assert_eq!(g.out_order, vec![1]);
    g.hands[0] = vec![c(Hearts, Ace), c(Hearts, King), c(Hearts, Nine)];
    g.hands[2] = vec![c(Hearts, Two), c(Spades, Three), c(Clubs, Four)];
    g.lives[2] = 0;
    g.stage = Stage::Turn { since: 10.0 };
    g.revealed = false;
    g.knocker = None;
    g.turn = 0;
    g.lost.fill(false);
    act(&mut g, &mut p, ANA, "knock", &[], None, 11.0).unwrap();
    pass(&mut g, &mut p, CEM, 12.0);
    assert!(g.is_finished());
    let s = scene(&g, &p, 12.0);
    assert_eq!(s.ranking, Some(vec![ANA, CEM, BEN]));
    assert_eq!(p.iter().map(|x| x.score).collect::<Vec<_>>(), [2, 0, 1]);
    assert_eq!(s.banner.unwrap().text, " gewinnt!");
    let view = ViewCtx { now: 12.0, players: &p, leader: Some(ANA) };
    assert!(matches!(g.phone_screen(&view, CEM), PhoneScreen::Final { rank: 2, of: 3, score: 1, can_restart: false }));
    assert_eq!(act(&mut g, &mut p, ANA, "pass", &[], None, 13.0), Err(ActionError::Ignored));
}

#[test]
fn the_desk_offers_the_four_moves_with_every_card_live_only_on_your_turn() {
    let (g, p) = rig(three_dull(), middle_dull());
    let d = desk(&g, &p, ANA);
    let ids: Vec<_> = d.buttons.iter().map(|b| (b.id.as_str(), b.needs)).collect();
    assert_eq!(
        ids,
        [("swap", ButtonNeeds::HandAndTable), ("swap_all", ButtonNeeds::Nothing), ("pass", ButtonNeeds::Nothing), ("knock", ButtonNeeds::Nothing)]
    );
    assert!(d.hand.iter().chain(&d.table).all(|x| x.enabled));
    assert_eq!(d.table.len(), 3);
    assert_eq!(d.info.as_deref(), Some("Dein Blatt: 9 · ●●●"));
    let other = desk(&g, &p, BEN);
    assert!(other.buttons.is_empty() && other.hand.iter().chain(&other.table).all(|x| !x.enabled));
    assert_eq!(other.prompt, "Ana ist dran");
}

#[test]
fn the_tv_shows_lives_and_the_middle_but_no_hands_during_play() {
    let (g, p) = rig(three_dull(), middle_dull());
    let s = scene(&g, &p, 0.0);
    assert_eq!(s.game, "Schwimmen");
    assert_eq!(s.piles.len(), 3);
    assert!(s.piles.iter().all(|x| x.cards.len() == 1));
    assert!(s.seats.iter().all(|x| x.cards == 3 && x.open.is_empty() && x.marker.as_deref() == Some("●●●")));
    assert_eq!(s.turn, Some(ANA));
}

#[test]
fn an_absent_player_knocks_at_27_swaps_for_the_best_hand_or_passes() {
    let mut hands = three_dull();
    hands[0] = vec![c(Hearts, Ace), c(Hearts, Seven), c(Hearts, Nine)];
    hands[1] = vec![c(Spades, Two), c(Clubs, Three), c(Diamonds, Four)];
    let (mut g, mut p) = rig(hands, vec![c(Hearts, King), c(Spades, Ace), c(Spades, King)]);
    p[0].conn = None;
    p[0].left_at = Some(100.0);
    tick(&mut g, &mut p, 1000.0);
    assert_eq!(g.turn, 0, "waits auto_ms after leaving");
    tick(&mut g, &mut p, 1700.0);
    assert_eq!(g.knocker, Some(0), "27 is enough to knock");
    p[1].conn = None;
    p[1].left_at = Some(1700.0);
    tick(&mut g, &mut p, 3300.0);
    assert!(g.hands[1].contains(&c(Spades, Ace)) || g.hands[1].contains(&c(Spades, King)), "the best swap takes a high card");
    let (mut g, mut p) = rig(
        vec![vec![c(Hearts, Ace), c(Hearts, King), c(Spades, Two)], three_dull().remove(1)],
        vec![c(Clubs, Two), c(Diamonds, Three), c(Clubs, Four)],
    );
    p[0].conn = None;
    p[0].left_at = Some(0.0);
    tick(&mut g, &mut p, 2000.0);
    assert_eq!((g.turn, g.passes, g.knocker), (1, 1, None), "21 is under 27 and nothing in the middle helps: pass");
}
