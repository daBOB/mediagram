use super::*;
use crate::protocol::{ButtonNeeds, CardDesk, CardsScene, DeskCard};
use Rank::*;
use Suit::*;

fn c(suit: Suit, rank: Rank) -> Card {
    Card::new(suit, rank)
}

fn players(n: usize) -> Vec<Player> {
    ["Ana", "Ben", "Cem", "Dana", "Eli", "Fay"][..n].iter().enumerate().map(|(i, name)| Player::for_test(i as PlayerId, name, true)).collect()
}

/// Hearts are trump. `deck` is drawn from its end, its first card is the trump card.
fn rig(hands: Vec<Vec<Card>>, deck: Vec<Card>) -> (DurakGame, Vec<Player>) {
    let p = players(hands.len());
    let mut g = DurakGame::new(DurakConfig { seed: Some(1), ..DurakConfig::default() }, &p, 0.0);
    g.hands = hands;
    g.hands.iter_mut().for_each(|h| h.sort());
    g.deck = deck;
    g.trump = c(Hearts, Six);
    g.attacker = 0;
    g.stage = Stage::Attack { since: 0.0 };
    (g, p)
}

fn index(g: &DurakGame, who: usize, card: Card) -> u8 {
    g.hands[who].iter().position(|&h| h == card).expect("the card is in hand") as u8
}

fn press(g: &mut DurakGame, p: &mut [Player], who: usize, button: &str, hand: &[Card], table: Option<Card>) -> Result<(), ActionError> {
    let hand = hand.iter().map(|&card| index(g, who, card)).collect();
    let table = table.map(|t| g.table().iter().position(|&x| x == t).expect("the card is on the table") as u8);
    g.on_action(&mut GameCtx { now: 1.0, players: p }, who as PlayerId, &Action::Cards { button: button.into(), hand, table })
}

fn leave(player: &mut Player, at: f64) {
    player.conn = None;
    player.left_at = Some(at);
}

fn tick(g: &mut DurakGame, p: &mut [Player], now: f64) {
    g.on_tick(&mut GameCtx { now, players: p });
}

/// Lets a finished bout's table time pass so the cards move on.
fn settle(g: &mut DurakGame, p: &mut [Player]) {
    assert!(matches!(g.stage, Stage::Settle { .. }), "{:?}", g.stage);
    tick(g, p, 10_000.0);
}

/// Everyone who is asked to throw in says they are done.
fn done_all(g: &mut DurakGame, p: &mut [Player]) {
    while matches!(g.stage, Stage::Throw { .. }) {
        let who = g.actor().expect("a thrower is on");
        press(g, p, who, "done", &[], None).unwrap();
    }
}

fn desk(g: &DurakGame, p: &[Player], who: PlayerId) -> CardDesk {
    match g.phone_screen(&ViewCtx { now: 1.0, players: p, leader: Some(0) }, who) {
        PhoneScreen::Cards { desk } => desk,
        other => panic!("expected a desk, got {other:?}"),
    }
}

fn scene(g: &DurakGame, p: &[Player], now: f64) -> CardsScene {
    match g.tv_scene(&ViewCtx { now, players: p, leader: Some(0) }) {
        TvScene::Cards { scene } => scene,
        other => panic!("expected a card scene, got {other:?}"),
    }
}

/// Ana leads the spade seven against Ben, who holds `ben`; Cem holds `cem`.
fn open(ana: Vec<Card>, ben: Vec<Card>, cem: Vec<Card>, deck: Vec<Card>) -> (DurakGame, Vec<Player>) {
    let (mut g, mut p) = rig(vec![ana, ben, cem], deck);
    press(&mut g, &mut p, 0, "play", &[c(Spades, Seven)], None).unwrap();
    (g, p)
}

#[test]
fn the_deal_gives_six_cards_and_the_lowest_trump_leads() {
    for n in 2..=6 {
        let g = DurakGame::new(DurakConfig { seed: Some(n as u64), ..DurakConfig::default() }, &players(n), 0.0);
        assert!(g.hands.iter().all(|h| h.len() == 6));
        assert_eq!(g.deck.len(), 36 - 6 * n);
        let lowest = g.hands.iter().flatten().filter(|x| x.suit == g.trump_suit()).map(|x| x.rank).min();
        assert!(lowest.is_none_or(|r| g.hands[g.attacker].iter().any(|x| x.suit == g.trump_suit() && x.rank == r)));
    }
}

#[test]
fn the_trump_is_the_bottom_card_and_comes_last() {
    let g = DurakGame::new(DurakConfig { seed: Some(7), ..DurakConfig::default() }, &players(2), 0.0);
    assert_eq!(g.trump, g.deck[0]);
    assert_eq!(scene(&g, &players(2), 0.0).trump, Some(g.trump));
}

#[test]
fn only_the_attacker_may_lead_and_the_defender_waits() {
    let (mut g, mut p) = rig(vec![vec![c(Spades, Seven)], vec![c(Spades, Eight)], vec![c(Clubs, Nine)]], vec![]);
    assert!(matches!(press(&mut g, &mut p, 1, "play", &[c(Spades, Eight)], None), Err(ActionError::Ignored)));
    press(&mut g, &mut p, 0, "play", &[c(Spades, Seven)], None).unwrap();
    assert!(matches!(g.stage, Stage::Defend { .. }));
    assert!(matches!(press(&mut g, &mut p, 2, "take", &[], None), Err(ActionError::Ignored)));
}

#[test]
fn a_higher_card_of_the_suit_beats() {
    let (mut g, mut p) = open(vec![c(Spades, Seven), c(Clubs, Ten)], vec![c(Spades, Eight), c(Clubs, Nine)], vec![c(Diamonds, Ace)], vec![]);
    press(&mut g, &mut p, 1, "beat", &[c(Spades, Eight)], Some(c(Spades, Seven))).unwrap();
    assert_eq!(g.bout, vec![(c(Spades, Seven), Some(c(Spades, Eight)))]);
    assert_eq!(g.hands[1], vec![c(Clubs, Nine)]);
}

#[test]
fn a_trump_beats_a_plain_card() {
    let (mut g, mut p) = open(vec![c(Spades, Seven), c(Clubs, Ten)], vec![c(Hearts, Seven), c(Clubs, Nine)], vec![c(Diamonds, Ace)], vec![]);
    press(&mut g, &mut p, 1, "beat", &[c(Hearts, Seven)], Some(c(Spades, Seven))).unwrap();
    assert_eq!(g.bout[0].1, Some(c(Hearts, Seven)));
}

#[test]
fn a_lower_card_or_another_plain_suit_does_not_beat() {
    let (mut g, mut p) = open(vec![c(Spades, Seven), c(Clubs, Ten)], vec![c(Spades, Six), c(Clubs, Ace)], vec![c(Diamonds, Ace)], vec![]);
    for card in [c(Spades, Six), c(Clubs, Ace)] {
        assert!(matches!(press(&mut g, &mut p, 1, "beat", &[card], Some(c(Spades, Seven))), Err(ActionError::NotAllowed(_))));
    }
    assert_eq!(g.hands[1].len(), 2);
    assert!(matches!(g.stage, Stage::Defend { .. }));
}

#[test]
fn a_beaten_attack_cannot_be_beaten_again() {
    let (mut g, mut p) = open(vec![c(Spades, Seven), c(Clubs, Ten)], vec![c(Spades, Eight), c(Spades, Nine), c(Clubs, Six)], vec![c(Diamonds, Ace)], vec![]);
    press(&mut g, &mut p, 1, "beat", &[c(Spades, Eight)], Some(c(Spades, Seven))).unwrap();
    assert!(g.attack_at(0).is_none());
    assert_eq!(g.attack_at(5), None);
}

#[test]
fn all_beaten_with_nothing_to_add_is_bito_and_the_defender_attacks() {
    let (mut g, mut p) = open(vec![c(Spades, Seven), c(Clubs, Ten)], vec![c(Spades, Eight), c(Clubs, Nine)], vec![c(Diamonds, Ace)], vec![]);
    press(&mut g, &mut p, 1, "beat", &[c(Spades, Eight)], Some(c(Spades, Seven))).unwrap();
    done_all(&mut g, &mut p);
    assert!(matches!(g.stage, Stage::Settle { taken: false, .. }));
    assert_eq!(scene(&g, &p, 1.5).banner.unwrap().text, " hat alles geschlagen – Bito!");
    tick(&mut g, &mut p, 1.5);
    assert!(matches!(g.stage, Stage::Settle { .. }), "the table stays in view first");
    settle(&mut g, &mut p);
    assert!(g.bout.is_empty());
    assert_eq!((g.attacker, g.actor()), (1, Some(1)));
    assert!(matches!(g.stage, Stage::Attack { .. }));
}

#[test]
fn taking_hands_over_everything_and_the_next_player_attacks() {
    let (mut g, mut p) = open(vec![c(Spades, Seven), c(Clubs, Ten)], vec![c(Clubs, Nine), c(Diamonds, Nine)], vec![c(Diamonds, Ace)], vec![]);
    press(&mut g, &mut p, 1, "take", &[], None).unwrap();
    done_all(&mut g, &mut p);
    assert!(matches!(g.stage, Stage::Settle { taken: true, .. }), "nobody can add a seven");
    settle(&mut g, &mut p);
    assert_eq!(g.hands[1], vec![c(Clubs, Nine), c(Spades, Seven), c(Diamonds, Nine)]);
    assert_eq!(g.attacker, 2);
}

#[test]
fn throwing_in_needs_a_rank_on_the_table_and_must_be_defended() {
    let (mut g, mut p) = open(
        vec![c(Spades, Seven), c(Clubs, Seven), c(Clubs, Ten)],
        vec![c(Spades, Eight), c(Clubs, Eight), c(Clubs, Nine)],
        vec![c(Diamonds, Ace)],
        vec![],
    );
    press(&mut g, &mut p, 1, "beat", &[c(Spades, Eight)], Some(c(Spades, Seven))).unwrap();
    assert_eq!(g.actor(), Some(0));
    assert!(matches!(press(&mut g, &mut p, 0, "play", &[c(Clubs, Ten)], None), Err(ActionError::NotAllowed(_))));
    press(&mut g, &mut p, 0, "play", &[c(Clubs, Seven)], None).unwrap();
    assert!(matches!(g.stage, Stage::Defend { .. }));
    assert_eq!(g.bout.len(), 2);
    press(&mut g, &mut p, 1, "beat", &[c(Clubs, Eight)], Some(c(Clubs, Seven))).unwrap();
    done_all(&mut g, &mut p);
    assert!(matches!(g.stage, Stage::Settle { taken: false, .. }), "the defence's own rank may be added, but nobody holds an eight");
}

#[test]
fn the_defenders_card_ranks_may_be_thrown_in_too() {
    let (mut g, mut p) = open(vec![c(Spades, Seven), c(Clubs, Eight)], vec![c(Spades, Eight), c(Clubs, Nine)], vec![c(Diamonds, Ace)], vec![]);
    press(&mut g, &mut p, 1, "beat", &[c(Spades, Eight)], Some(c(Spades, Seven))).unwrap();
    assert_eq!(g.actor(), Some(0));
    press(&mut g, &mut p, 0, "play", &[c(Clubs, Eight)], None).unwrap();
}

#[test]
fn everyone_with_cards_is_asked_in_seat_order_after_the_attacker_fit_or_not() {
    let hands = vec![
        vec![c(Spades, Seven), c(Clubs, Ten)],
        vec![c(Spades, Eight), c(Clubs, Nine), c(Diamonds, Nine)],
        vec![c(Clubs, Seven), c(Diamonds, Jack)],
        vec![c(Diamonds, Seven), c(Hearts, Seven), c(Clubs, Jack)],
    ];
    let (mut g, mut p) = rig(hands, vec![]);
    press(&mut g, &mut p, 0, "play", &[c(Spades, Seven)], None).unwrap();
    press(&mut g, &mut p, 1, "beat", &[c(Spades, Eight)], Some(c(Spades, Seven))).unwrap();
    assert_eq!(g.actor(), Some(0), "the attacker holds no seven or eight but is asked all the same");
    assert!(matches!(press(&mut g, &mut p, 0, "play", &[c(Clubs, Ten)], None), Err(ActionError::NotAllowed(_))));
    assert!(matches!(press(&mut g, &mut p, 3, "done", &[], None), Err(ActionError::Ignored)));
    press(&mut g, &mut p, 0, "done", &[], None).unwrap();
    assert_eq!(g.actor(), Some(2));
    press(&mut g, &mut p, 2, "done", &[], None).unwrap();
    assert_eq!(g.actor(), Some(3));
    press(&mut g, &mut p, 3, "play", &[c(Diamonds, Seven)], None).unwrap();
    assert!(matches!(g.stage, Stage::Defend { .. }));
    press(&mut g, &mut p, 1, "beat", &[c(Diamonds, Nine)], Some(c(Diamonds, Seven))).unwrap();
    assert_eq!(g.actor(), Some(3), "a thrower stays on until they are done");
    press(&mut g, &mut p, 3, "done", &[], None).unwrap();
    assert!(matches!(g.stage, Stage::Settle { taken: false, .. }), "a player who is done is not asked again");
}

#[test]
fn the_throw_in_looks_the_same_to_everyone_whether_a_card_fits_or_not() {
    let hands = vec![
        vec![c(Spades, Seven), c(Clubs, Ten)],
        vec![c(Spades, Eight), c(Clubs, Nine), c(Diamonds, Nine)],
        vec![c(Clubs, Seven), c(Diamonds, Jack)],
    ];
    let hands_nothing_fits = vec![hands[0].clone(), hands[1].clone(), vec![c(Clubs, Queen), c(Diamonds, Jack)]];
    let look = |hands: Vec<Vec<Card>>, asked: usize| {
        let (mut g, mut p) = rig(hands, vec![]);
        press(&mut g, &mut p, 0, "play", &[c(Spades, Seven)], None).unwrap();
        press(&mut g, &mut p, 1, "beat", &[c(Spades, Eight)], Some(c(Spades, Seven))).unwrap();
        press(&mut g, &mut p, 0, "done", &[], None).unwrap();
        assert_eq!(g.actor(), Some(asked));
        let tv = scene(&g, &p, 1.0);
        let others: Vec<_> = [0, 1].map(|w| desk(&g, &p, w).prompt).to_vec();
        let mine = desk(&g, &p, 2);
        (tv, others, mine)
    };
    let (tv_fit, others_fit, mine_fit) = look(hands, 2);
    let (tv_none, others_none, mine_none) = look(hands_nothing_fits, 2);
    assert_eq!(tv_fit, tv_none, "the TV cannot tell");
    assert_eq!(others_fit, others_none, "the other phones cannot tell");
    let shape = |d: &CardDesk| (d.prompt.clone(), d.buttons.iter().map(|b| b.id.clone()).collect::<Vec<_>>());
    assert_eq!(shape(&mine_fit), shape(&mine_none), "same prompt and buttons");
    assert!(mine_fit.hand.iter().any(|h| h.enabled), "Dazulegen has a card to use");
    assert!(mine_none.hand.iter().all(|h| !h.enabled), "Dazulegen has nothing to use");
    assert!(mine_none.buttons.iter().any(|b| b.id == "done"));
}

#[test]
fn while_taking_the_others_add_cards_that_are_not_defended() {
    let hands = vec![
        vec![c(Spades, Seven), c(Clubs, Seven), c(Clubs, Ten)],
        vec![c(Spades, Eight), c(Clubs, Nine), c(Diamonds, Nine)],
        vec![c(Diamonds, Seven), c(Clubs, Jack)],
    ];
    let (mut g, mut p) = rig(hands, vec![]);
    press(&mut g, &mut p, 0, "play", &[c(Spades, Seven)], None).unwrap();
    press(&mut g, &mut p, 1, "take", &[], None).unwrap();
    assert_eq!((g.actor(), matches!(g.stage, Stage::Throw { taking: true, .. })), (Some(0), true));
    press(&mut g, &mut p, 0, "play", &[c(Clubs, Seven)], None).unwrap();
    assert!(matches!(g.stage, Stage::Throw { taking: true, .. }), "no defence while taking");
    assert_eq!(g.actor(), Some(0), "the attacker is asked again while the limit allows");
    press(&mut g, &mut p, 0, "done", &[], None).unwrap();
    press(&mut g, &mut p, 2, "play", &[c(Diamonds, Seven)], None).unwrap();
    assert!(matches!(g.stage, Stage::Settle { taken: true, .. }), "three attacks, the defender holds three");
    settle(&mut g, &mut p);
    assert_eq!(g.hands[1].len(), 6);
}

#[test]
fn at_most_six_attacks_per_bout() {
    let defence = vec![c(Clubs, Eight), c(Clubs, Nine), c(Diamonds, Eight), c(Diamonds, Nine), c(Spades, Eight), c(Spades, Nine), c(Spades, Ten)];
    let (mut g, mut p) = rig(vec![vec![c(Clubs, Seven), c(Spades, Seven)], defence, vec![c(Diamonds, Ace)]], vec![]);
    // five attacks already beaten, so the sixth is the last one allowed
    g.bout = [Seven, Eight, Nine, Ten, Jack].map(|r| (c(Diamonds, r), Some(c(Hearts, r)))).to_vec();
    g.limit = 6;
    g.stage = Stage::Throw { since: 0.0, taking: false };
    press(&mut g, &mut p, 0, "play", &[c(Clubs, Seven)], None).unwrap();
    press(&mut g, &mut p, 1, "beat", &[c(Clubs, Eight)], Some(c(Clubs, Seven))).unwrap();
    assert_eq!(g.bout.len(), 6);
    assert!(matches!(g.stage, Stage::Settle { taken: false, .. }), "the attacker still holds a seven, but six is the limit");
}

#[test]
fn never_more_attacks_than_the_defender_holds() {
    let (mut g, mut p) = open(
        vec![c(Spades, Seven), c(Clubs, Seven), c(Diamonds, Seven)],
        vec![c(Spades, Eight), c(Clubs, Eight)],
        vec![c(Clubs, Jack)],
        vec![],
    );
    assert_eq!(g.limit, 2);
    press(&mut g, &mut p, 1, "beat", &[c(Spades, Eight)], Some(c(Spades, Seven))).unwrap();
    press(&mut g, &mut p, 0, "play", &[c(Clubs, Seven)], None).unwrap();
    press(&mut g, &mut p, 1, "beat", &[c(Clubs, Eight)], Some(c(Clubs, Seven))).unwrap();
    assert!(matches!(g.stage, Stage::Settle { taken: false, .. }), "the defender is out of cards; the third seven stays");
    assert_eq!(g.hands[0], vec![c(Diamonds, Seven)]);
}

#[test]
fn hands_refill_to_six_attacker_first_and_defender_last() {
    let deck = vec![c(Hearts, Six), c(Clubs, Six), c(Diamonds, Six), c(Spades, Six), c(Hearts, Seven), c(Diamonds, Seven)];
    let (mut g, mut p) = open(
        vec![c(Spades, Seven), c(Clubs, Ten), c(Clubs, Nine)],
        vec![c(Spades, Eight), c(Diamonds, Nine), c(Diamonds, Ten)],
        vec![c(Spades, Jack), c(Spades, Queen), c(Spades, King), c(Spades, Ace), c(Diamonds, Ace)],
        deck,
    );
    press(&mut g, &mut p, 1, "beat", &[c(Spades, Eight)], Some(c(Spades, Seven))).unwrap();
    done_all(&mut g, &mut p);
    settle(&mut g, &mut p);
    assert_eq!(g.hands[0].len(), 6);
    assert!(g.hands[0].contains(&c(Diamonds, Seven)) && g.hands[0].contains(&c(Spades, Six)), "the attacker takes the top four");
    assert_eq!(g.hands[2].len(), 6);
    assert!(g.hands[2].contains(&c(Clubs, Six)));
    assert_eq!(g.hands[1].len(), 3, "two left plus the trump card, last of all");
    assert!(g.hands[1].contains(&c(Hearts, Six)));
    assert!(g.deck.is_empty());
}

#[test]
fn a_player_out_of_cards_with_an_empty_deck_is_safe_and_the_last_holder_is_the_durak() {
    let (mut g, mut p) = open(
        vec![c(Spades, Seven)],
        vec![c(Spades, Eight), c(Hearts, Nine)],
        vec![c(Clubs, Ten), c(Clubs, Jack)],
        vec![],
    );
    press(&mut g, &mut p, 1, "beat", &[c(Spades, Eight)], Some(c(Spades, Seven))).unwrap();
    done_all(&mut g, &mut p);
    settle(&mut g, &mut p);
    assert_eq!(g.out, vec![0]);
    assert_eq!(scene(&g, &p, 10_000.0).seats[0].marker.as_deref(), Some("Raus"));
    assert_eq!(g.attacker, 1, "Bito: the defender attacks");
    press(&mut g, &mut p, 1, "play", &[c(Hearts, Nine)], None).unwrap();
    press(&mut g, &mut p, 2, "take", &[], None).unwrap();
    done_all(&mut g, &mut p);
    settle(&mut g, &mut p);
    assert_eq!(g.out, vec![0, 1]);
    let Stage::Final { ranking, durak } = g.stage.clone() else { panic!("expected the end, got {:?}", g.stage) };
    assert_eq!((ranking, durak), (vec![0, 1, 2], Some(2)));
    assert!(g.is_finished());
    assert_eq!(p.iter().map(|x| x.score).collect::<Vec<_>>(), vec![2, 1, 0]);
    let s = scene(&g, &p, 10_000.0);
    assert_eq!(s.ranking, Some(vec![0, 1, 2]));
    assert_eq!(s.seats[2].marker.as_deref(), Some("Durak"));
    assert_eq!(s.banner.unwrap().text, " ist der Durak!");
}

#[test]
fn nobody_is_durak_when_the_last_cards_go_together() {
    let (mut g, mut p) = open(vec![c(Spades, Seven)], vec![c(Spades, Eight)], vec![c(Clubs, Ten), c(Clubs, Jack)], vec![]);
    press(&mut g, &mut p, 1, "beat", &[c(Spades, Eight)], Some(c(Spades, Seven))).unwrap();
    settle(&mut g, &mut p);
    assert_eq!(g.out, vec![0, 1], "attacker first, defender last");
    assert!(matches!(g.stage, Stage::Final { durak: Some(2), .. }));
    let (mut g, mut p) = rig(vec![vec![c(Spades, Seven)], vec![c(Spades, Eight)]], vec![]);
    press(&mut g, &mut p, 0, "play", &[c(Spades, Seven)], None).unwrap();
    press(&mut g, &mut p, 1, "beat", &[c(Spades, Eight)], Some(c(Spades, Seven))).unwrap();
    settle(&mut g, &mut p);
    assert!(matches!(g.stage, Stage::Final { durak: None, .. }));
    assert_eq!(scene(&g, &p, 10_000.0).banner.unwrap().text, " – keiner ist Durak!");
}

#[test]
fn if_the_defender_is_out_after_a_bito_the_next_player_attacks() {
    let (mut g, mut p) = open(vec![c(Spades, Seven), c(Clubs, Ten)], vec![c(Spades, Eight)], vec![c(Clubs, Jack), c(Diamonds, Jack)], vec![]);
    press(&mut g, &mut p, 1, "beat", &[c(Spades, Eight)], Some(c(Spades, Seven))).unwrap();
    settle(&mut g, &mut p);
    assert_eq!(g.out, vec![1]);
    assert_eq!(g.attacker, 2);
}

#[test]
fn an_absent_attacker_leads_the_cheapest_card_after_the_delay() {
    let (mut g, mut p) = rig(vec![vec![c(Hearts, Seven), c(Clubs, Nine), c(Spades, Eight)], vec![c(Clubs, Ten)], vec![c(Clubs, Jack)]], vec![]);
    leave(&mut p[0], 10.0);
    tick(&mut g, &mut p, 500.0);
    assert!(g.bout.is_empty(), "the delay has not passed");
    tick(&mut g, &mut p, 1600.0);
    assert_eq!(g.bout, vec![(c(Spades, Eight), None)]);
}

#[test]
fn an_absent_defender_beats_cheaply_or_takes_and_throws_in_nothing() {
    let (mut g, mut p) = open(vec![c(Spades, Seven), c(Clubs, Seven)], vec![c(Diamonds, Six), c(Spades, Ace), c(Spades, Nine)], vec![c(Diamonds, Seven)], vec![]);
    leave(&mut p[1], 0.0);
    tick(&mut g, &mut p, 1600.0);
    assert_eq!(g.bout, vec![(c(Spades, Seven), Some(c(Spades, Nine)))]);
    // the attacker is present and adds a seven, which the absent defender cannot beat
    press(&mut g, &mut p, 0, "play", &[c(Clubs, Seven)], None).unwrap();
    tick(&mut g, &mut p, 3200.0);
    assert!(matches!(g.stage, Stage::Throw { taking: true, .. }), "taken, and Cem may still add a seven");
}

#[test]
fn an_absent_defender_with_no_beating_card_takes() {
    let (mut g, mut p) = open(vec![c(Spades, Seven)], vec![c(Clubs, Ace)], vec![c(Diamonds, Jack)], vec![]);
    leave(&mut p[1], 0.0);
    tick(&mut g, &mut p, 1600.0);
    assert!(matches!(g.stage, Stage::Settle { taken: true, .. }));
}

#[test]
fn an_absent_thrower_adds_nothing() {
    let (mut g, mut p) = open(vec![c(Spades, Seven), c(Clubs, Seven)], vec![c(Spades, Eight), c(Clubs, Nine)], vec![c(Diamonds, Jack)], vec![]);
    press(&mut g, &mut p, 1, "beat", &[c(Spades, Eight)], Some(c(Spades, Seven))).unwrap();
    leave(&mut p[0], 0.0);
    tick(&mut g, &mut p, 1600.0);
    assert_eq!(g.bout.len(), 1, "nothing was added");
    assert_eq!(g.actor(), Some(2), "the absent attacker said done by themselves");
    press(&mut g, &mut p, 2, "done", &[], None).unwrap();
    assert!(matches!(g.stage, Stage::Settle { taken: false, .. }));
}

#[test]
fn the_defenders_desk_offers_beating_and_taking() {
    let (g, p) = open(vec![c(Spades, Seven), c(Clubs, Ten)], vec![c(Spades, Eight), c(Clubs, Nine)], vec![c(Diamonds, Ace)], vec![]);
    let d = desk(&g, &p, 1);
    assert_eq!(d.buttons.iter().map(|b| (b.id.as_str(), b.needs)).collect::<Vec<_>>(), vec![("beat", ButtonNeeds::HandAndTable), ("take", ButtonNeeds::Nothing)]);
    assert_eq!(d.table, vec![DeskCard { card: c(Spades, Seven), enabled: true }]);
    assert_eq!(d.hand.iter().map(|h| (h.card, h.enabled)).collect::<Vec<_>>(), vec![(c(Clubs, Nine), false), (c(Spades, Eight), true)]);
    assert!(d.info.unwrap().starts_with("Trumpf ♥ Herz"));
    assert!(desk(&g, &p, 2).buttons.is_empty());
}

#[test]
fn the_scene_shows_pairs_trump_deck_and_who_attacks_and_defends() {
    let (mut g, mut p) = open(vec![c(Spades, Seven), c(Clubs, Ten)], vec![c(Spades, Eight), c(Clubs, Nine)], vec![c(Diamonds, Ace)], vec![c(Hearts, Six), c(Clubs, Six)]);
    press(&mut g, &mut p, 1, "beat", &[c(Spades, Eight)], Some(c(Spades, Seven))).unwrap();
    done_all(&mut g, &mut p);
    let s = scene(&g, &p, 1.5);
    assert_eq!(s.game, "Durak");
    assert_eq!(s.piles.iter().map(|x| x.cards.clone()).collect::<Vec<_>>(), vec![vec![c(Spades, Seven), c(Spades, Eight)]]);
    assert_eq!((s.deck, s.trump), (2, Some(c(Hearts, Six))));
    assert_eq!((s.seats[0].marker.as_deref(), s.seats[1].marker.as_deref(), s.seats[2].marker.as_deref()), (Some("Angreifer"), Some("Verteidiger"), None));
    assert_eq!(s.banner.unwrap().text, " hat alles geschlagen – Bito!");
}
