use super::*;
use crate::protocol::{ButtonNeeds, CardDesk, CardsScene};
use Rank::*;
use Suit::*;

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

/// A game with the given hands, `top` face up and `deck` to draw from (last card first).
fn rig(hands: Vec<Vec<Card>>, top: Card, deck: Vec<Card>) -> (MauMauGame, Vec<Player>) {
    let p = players(hands.len());
    let mut g = MauMauGame::new(MauMauConfig { seed: Some(1), ..MauMauConfig::default() }, &p, 0.0);
    g.hands = hands;
    g.discard = vec![top];
    g.deck = deck;
    g.suit = Some(top.suit);
    g.owed = 0;
    g.turn = 0;
    g.stage = Stage::Turn { since: 0.0 };
    (g, p)
}

fn act(g: &mut MauMauGame, p: &mut [Player], who: PlayerId, button: &str, hand: &[u8], now: f64) -> Result<(), ActionError> {
    g.on_action(&mut GameCtx { now, players: p }, who, &Action::Cards { button: button.into(), hand: hand.to_vec(), table: None })
}

fn tick(g: &mut MauMauGame, p: &mut [Player], now: f64) {
    g.on_tick(&mut GameCtx { now, players: p });
}

fn desk(g: &MauMauGame, p: &[Player], who: PlayerId, now: f64) -> CardDesk {
    match g.phone_screen(&ViewCtx { now, players: p, leader: Some(ANA) }, who) {
        PhoneScreen::Cards { desk } => desk,
        other => panic!("expected a desk, got {other:?}"),
    }
}

fn scene(g: &MauMauGame, p: &[Player], now: f64) -> CardsScene {
    match g.tv_scene(&ViewCtx { now, players: p, leader: Some(ANA) }) {
        TvScene::Cards { scene } => scene,
        other => panic!("expected a card scene, got {other:?}"),
    }
}

#[test]
fn the_deal_gives_five_cards_and_sizes_the_deck_by_players() {
    for (n, total) in [(2, 32), (5, 32), (6, 52), (8, 52)] {
        let g = MauMauGame::new(MauMauConfig::default(), &players(n), 0.0);
        assert!(g.hands.iter().all(|h| h.len() == DEAL));
        assert_eq!(g.deck.len() + g.discard.len() + n * DEAL, total, "{n} players");
    }
}

#[test]
fn a_starting_seven_hits_the_first_player_an_eight_skips_them_and_a_jack_allows_any_suit() {
    let started = |rank: Rank| {
        (0..400)
            .map(|seed| MauMauGame::new(MauMauConfig { seed: Some(seed), ..MauMauConfig::default() }, &players(3), 0.0))
            .find(|g| g.top().card.rank == rank)
            .expect("some seed starts with it")
    };
    let seven = started(Seven);
    assert_eq!((seven.owed, seven.turn), (2, 0));
    let eight = started(Eight);
    assert_eq!((eight.owed, eight.turn), (0, 1));
    assert_eq!(started(Jack).suit, None);
}

#[test]
fn only_a_fitting_card_can_be_played_and_only_on_your_turn() {
    let (mut g, mut p) = rig(vec![vec![c(Clubs, Nine), c(Hearts, Ace), c(Hearts, King)], vec![c(Clubs, Ace)], vec![c(Clubs, Ten)]], c(Hearts, Nine), vec![]);
    assert!(matches!(act(&mut g, &mut p, ANA, "play", &[0], 1.0), Ok(())), "the nine matches by rank");
    assert_eq!(g.turn, 1);
    assert_eq!(act(&mut g, &mut p, ANA, "play", &[0], 2.0), Err(ActionError::Ignored));
    assert_eq!(act(&mut g, &mut p, BEN, "play", &[0], 2.0), Ok(()), "the ace matches the clubs nine");
    let (mut g, mut p) = rig(vec![vec![c(Spades, Ace), c(Hearts, King)], vec![c(Clubs, Ace)]], c(Hearts, Nine), vec![]);
    assert_eq!(act(&mut g, &mut p, ANA, "play", &[0], 1.0), Err(ActionError::NotAllowed("Diese Karte passt nicht.")));
}

#[test]
fn sevens_stack_and_the_one_who_cannot_answer_draws_them_all() {
    let (mut g, mut p) = rig(
        vec![vec![c(Hearts, Seven), c(Clubs, Ace), c(Clubs, King)], vec![c(Spades, Seven), c(Clubs, Ten)], vec![c(Clubs, Nine), c(Clubs, Eight)]],
        c(Hearts, Nine),
        vec![c(Diamonds, Two), c(Diamonds, Three), c(Diamonds, Four), c(Diamonds, Five), c(Diamonds, Six)],
    );
    act(&mut g, &mut p, ANA, "play", &[0], 1.0).unwrap();
    assert_eq!(g.owed, 2);
    let d = desk(&g, &p, BEN, 1.0);
    assert_eq!(d.hand.iter().map(|h| h.enabled).collect::<Vec<_>>(), vec![true, false], "only the seven can answer");
    assert!(d.buttons.iter().any(|b| b.id == "draw" && b.label == "2 Karten ziehen"));
    act(&mut g, &mut p, BEN, "play", &[0], 2.0).unwrap();
    assert_eq!(g.owed, 4);
    assert_eq!(scene(&g, &p, 2.0).note.as_deref(), Some("Zieh 4"));
    assert!(act(&mut g, &mut p, CEM, "play", &[0], 3.0).is_err(), "a nine is not a seven");
    act(&mut g, &mut p, CEM, "draw", &[], 3.0).unwrap();
    assert_eq!((g.hands[2].len(), g.owed, g.turn), (6, 0, 0));
}

#[test]
fn an_eight_skips_the_next_player_and_in_a_pair_plays_again() {
    let (mut g, mut p) = rig(vec![vec![c(Hearts, Eight), c(Clubs, Ace), c(Clubs, Two)], vec![c(Clubs, Ten)], vec![c(Clubs, Nine)]], c(Hearts, Nine), vec![]);
    act(&mut g, &mut p, ANA, "play", &[0], 1.0).unwrap();
    assert_eq!(g.turn, 2);
    assert_eq!(scene(&g, &p, 1.0).banner.unwrap().text, " setzt aus");
    let (mut g, mut p) = rig(vec![vec![c(Hearts, Eight), c(Clubs, Ace)], vec![c(Clubs, Ten)]], c(Hearts, Nine), vec![]);
    act(&mut g, &mut p, ANA, "mau", &[0], 1.0).unwrap();
    assert_eq!(g.turn, 0);
}

#[test]
fn a_jack_wishes_a_suit_that_the_next_card_must_follow() {
    let (mut g, mut p) = rig(
        vec![vec![c(Spades, Jack), c(Clubs, Ace), c(Clubs, King)], vec![c(Spades, Ace), c(Diamonds, Ace)], vec![c(Clubs, Ten)]],
        c(Hearts, Nine),
        vec![],
    );
    act(&mut g, &mut p, ANA, "play", &[0], 1.0).unwrap();
    assert_eq!(g.turn, 0, "the wish comes first");
    let d = desk(&g, &p, ANA, 1.0);
    assert_eq!(d.buttons.len(), 4);
    assert!(d.buttons.iter().all(|b| b.needs == ButtonNeeds::Nothing && b.suit.is_some()));
    act(&mut g, &mut p, BEN, "wish_diamonds", &[], 2.0).expect_err("only the jack's player wishes");
    act(&mut g, &mut p, ANA, "wish_diamonds", &[], 2.0).unwrap();
    assert_eq!((g.turn, g.wish), (1, Some(Diamonds)));
    assert_eq!(scene(&g, &p, 2.0).wish, Some(Diamonds));
    act(&mut g, &mut p, BEN, "play", &[0], 3.0).expect_err("spades does not fit the wish");
    act(&mut g, &mut p, BEN, "play", &[1], 3.0).unwrap();
    assert_eq!(scene(&g, &p, 3.0).wish, None);
}

#[test]
fn forgetting_mau_costs_a_card_and_saying_it_does_not() {
    let hands = || vec![vec![c(Hearts, Ace), c(Hearts, King)], vec![c(Clubs, Ace), c(Clubs, King)]];
    let (mut g, mut p) = rig(hands(), c(Hearts, Nine), vec![c(Diamonds, Two)]);
    act(&mut g, &mut p, ANA, "play", &[0], 1.0).unwrap();
    assert_eq!(g.hands[0].len(), 2, "one card back");
    assert_eq!(scene(&g, &p, 1.0).banner.unwrap().text, " vergisst Mau – Strafkarte!");
    assert!(matches!(g.events.last().unwrap().kind, CardsEventKind::Draw { count: 1 }));
    let (mut g, mut p) = rig(hands(), c(Hearts, Nine), vec![c(Diamonds, Two)]);
    act(&mut g, &mut p, ANA, "mau", &[0], 1.0).unwrap();
    assert_eq!(g.hands[0].len(), 1);
    let s = scene(&g, &p, 1.0);
    assert_eq!(s.banner.unwrap().text, " ruft Mau!");
    assert_eq!(s.seats[0].marker.as_deref(), Some("Mau!"));
}

#[test]
fn mau_is_offered_with_two_cards_only() {
    let (g, p) = rig(vec![vec![c(Hearts, Ace), c(Hearts, King)], vec![c(Clubs, Ace)]], c(Hearts, Nine), vec![]);
    let ids: Vec<_> = desk(&g, &p, ANA, 0.0).buttons.into_iter().map(|b| b.id).collect();
    assert_eq!(ids, ["mau", "play", "draw"]);
    let (g, p) = rig(vec![vec![c(Hearts, Ace), c(Hearts, King), c(Clubs, Two)], vec![c(Clubs, Ace)]], c(Hearts, Nine), vec![]);
    let ids: Vec<_> = desk(&g, &p, ANA, 0.0).buttons.into_iter().map(|b| b.id).collect();
    assert_eq!(ids, ["play", "draw"]);
}

#[test]
fn a_drawn_card_that_fits_may_be_played_or_kept_and_one_that_does_not_passes() {
    let (mut g, mut p) = rig(vec![vec![c(Clubs, Ace), c(Clubs, King), c(Clubs, Two)], vec![c(Clubs, Ten)]], c(Hearts, Nine), vec![c(Spades, Two), c(Hearts, Six)]);
    act(&mut g, &mut p, ANA, "draw", &[], 1.0).unwrap();
    assert!(matches!(g.stage, Stage::Drawn { card, .. } if card == c(Hearts, Six)));
    let d = desk(&g, &p, ANA, 1.0);
    assert_eq!(d.hand.iter().filter(|h| h.enabled).count(), 1);
    assert_eq!(act(&mut g, &mut p, ANA, "play", &[0], 1.5), Err(ActionError::NotAllowed("Spiele die gezogene Karte oder behalte sie.")));
    act(&mut g, &mut p, ANA, "keep", &[], 2.0).unwrap();
    assert_eq!((g.turn, g.hands[0].len()), (1, 4));
    act(&mut g, &mut p, BEN, "draw", &[], 3.0).unwrap();
    assert_eq!((g.turn, g.hands[1].len()), (0, 2), "a card that does not fit ends the turn");
}

#[test]
fn an_empty_draw_pile_is_refilled_from_the_discards_under_the_top_card() {
    let (mut g, mut p) = rig(vec![vec![c(Clubs, Ace), c(Clubs, King), c(Clubs, Two)], vec![c(Clubs, Ten)]], c(Hearts, Nine), vec![]);
    g.discard = vec![c(Spades, Three), c(Spades, Four), c(Spades, Five), c(Hearts, Nine)];
    act(&mut g, &mut p, ANA, "draw", &[], 1.0).unwrap();
    assert_eq!(g.hands[0].len(), 4);
    assert_eq!(g.discard, vec![c(Hearts, Nine)]);
    assert_eq!(g.deck.len(), 2);
    assert!(g.events.iter().any(|e| e.kind == CardsEventKind::Reshuffle));
}

#[test]
fn drawing_from_nothing_just_passes_the_turn() {
    let (mut g, mut p) = rig(vec![vec![c(Clubs, Ace), c(Clubs, King)], vec![c(Clubs, Ten)]], c(Hearts, Nine), vec![]);
    act(&mut g, &mut p, ANA, "draw", &[], 1.0).unwrap();
    assert_eq!((g.turn, g.hands[0].len()), (1, 2));
}

#[test]
fn the_first_empty_hand_wins_and_the_rest_rank_by_cards_left() {
    let (mut g, mut p) = rig(
        vec![vec![c(Hearts, Ace)], vec![c(Clubs, Ace), c(Clubs, King), c(Clubs, Ten)], vec![c(Spades, Two), c(Spades, Three)]],
        c(Hearts, Nine),
        vec![],
    );
    act(&mut g, &mut p, ANA, "play", &[0], 1.0).unwrap();
    assert!(g.is_finished());
    let s = scene(&g, &p, 1.0);
    assert_eq!(s.ranking, Some(vec![ANA, CEM, BEN]));
    assert_eq!(s.turn, None);
    assert_eq!(p.iter().map(|x| x.score).collect::<Vec<_>>(), [2, 0, 1]);
    let view = ViewCtx { now: 1.0, players: &p, leader: Some(ANA) };
    assert!(matches!(g.phone_screen(&view, CEM), PhoneScreen::Final { rank: 2, of: 3, score: 1, can_restart: false }));
    assert_eq!(act(&mut g, &mut p, BEN, "draw", &[], 2.0), Err(ActionError::Ignored));
}

#[test]
fn an_absent_player_plays_a_plain_card_says_mau_and_wishes_their_longest_suit() {
    let (mut g, mut p) = rig(
        vec![vec![c(Hearts, Eight), c(Hearts, King)], vec![c(Clubs, Jack), c(Spades, Two), c(Spades, Ten), c(Clubs, Ace)], vec![c(Clubs, Ten)]],
        c(Hearts, Nine),
        vec![],
    );
    p[0].conn = None;
    p[0].left_at = Some(100.0);
    tick(&mut g, &mut p, 1000.0);
    assert_eq!(g.turn, 0, "waits auto_ms after leaving");
    tick(&mut g, &mut p, 1700.0);
    assert_eq!((g.turn, g.hands[0].len()), (1, 1), "the king, not the eight, with Mau");
    p[1].conn = None;
    p[1].left_at = Some(1700.0);
    tick(&mut g, &mut p, 3300.0);
    assert!(matches!(g.stage, Stage::Wish { .. }), "the jack is the only card that fits");
    tick(&mut g, &mut p, 4900.0);
    assert_eq!((g.wish, g.turn), (Some(Spades), 2));
}
