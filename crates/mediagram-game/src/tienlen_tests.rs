use super::*;
use crate::card_game_driver::{ANA, BEN, CEM, act, c, desk, leave, players, scene, tick};
use crate::cards::Suit;
use crate::protocol::{ButtonNeeds, CardsEventKind};
use crate::tienlen_rules::{Kind, strength};
use Rank::*;
use Suit::*;

/// A game with the given hands; the holder of the lowest card leads.
fn rig(hands: Vec<Vec<Card>>) -> (TienLenGame, Vec<Player>) {
    let p = players(hands.len());
    (TienLenGame::dealt(TienLenConfig::default(), &p, hands, 0.0), p)
}

/// `who` lays `cards` out of their hand.
fn lay(g: &mut TienLenGame, p: &mut [Player], who: PlayerId, cards: &[Card], now: f64) -> Result<(), ActionError> {
    let hand = &g.hands[who as usize];
    let places: Vec<u8> = cards.iter().map(|card| hand.iter().position(|h| h == card).expect("the card is in hand") as u8).collect();
    act(g, p, who, "play", &places, None, now)
}

fn pass(g: &mut TienLenGame, p: &mut [Player], who: PlayerId, now: f64) -> Result<(), ActionError> {
    act(g, p, who, "pass", &[], None, now)
}

const FOURS: [Card; 4] = [Card { suit: Spades, rank: Four }, Card { suit: Clubs, rank: Four }, Card { suit: Diamonds, rank: Four }, Card { suit: Hearts, rank: Four }];

#[test]
fn everyone_is_dealt_thirteen_cards_lowest_first_and_the_lowest_card_leads() {
    for n in 2..=4 {
        let g = TienLenGame::new(TienLenConfig { seed: Some(7), ..TienLenConfig::default() }, &players(n), 0.0);
        assert!(g.hands.iter().all(|h| h.len() == 13 && h.is_sorted_by_key(|&card| strength(card))), "{n} players");
        let lowest = g.hands.iter().flatten().copied().min_by_key(|&card| strength(card)).unwrap();
        assert_eq!((g.hands[g.turn][0], g.opening), (lowest, Some(lowest)));
    }
    let full = TienLenGame::new(TienLenConfig::default(), &players(4), 0.0);
    assert_eq!(full.opening, Some(c(Spades, Three)), "four players hold the whole pack");
}

#[test]
fn the_first_lead_must_hold_the_lowest_card_and_be_a_combination() {
    let (mut g, mut p) = rig(vec![vec![c(Hearts, Nine), c(Clubs, Ace)], vec![c(Spades, Three), c(Hearts, Three), c(Clubs, Eight), c(Clubs, King)]]);
    assert_eq!(g.turn, 1, "Ben holds the three of spades");
    assert_eq!(lay(&mut g, &mut p, ANA, &[c(Hearts, Nine)], 1.0), Err(ActionError::Ignored), "not Ana's turn");
    assert_eq!(pass(&mut g, &mut p, BEN, 1.0), Err(ActionError::Ignored), "whoever leads cannot pass");
    assert_eq!(lay(&mut g, &mut p, BEN, &[c(Clubs, Eight)], 1.0), Err(ActionError::NotAllowed("Deine niedrigste Karte muss beim ersten Ausspiel dabei sein.")));
    assert_eq!(lay(&mut g, &mut p, BEN, &[c(Spades, Three), c(Clubs, Eight)], 1.0), Err(ActionError::NotAllowed("Das ist keine gültige Kombination.")));
    assert_eq!(act(&mut g, &mut p, BEN, "play", &[0, 0], None, 1.0), Err(ActionError::Ignored), "one card picked twice");
    assert_eq!(act(&mut g, &mut p, BEN, "play", &[9], None, 1.0), Err(ActionError::Ignored), "no such card");
    assert_eq!(lay(&mut g, &mut p, BEN, &[c(Hearts, Three), c(Spades, Three)], 1.0), Ok(()), "the lowest card as part of a pair");
    assert_eq!((g.opening, g.turn, g.hands[1].len()), (None, 0, 2));
    assert_eq!(lay(&mut g, &mut p, ANA, &[c(Hearts, Nine)], 2.0), Err(ActionError::NotAllowed("Das schlägt die Karten auf dem Tisch nicht.")), "a single on a pair");
}

#[test]
fn a_pass_locks_a_player_out_until_the_table_is_cleared() {
    let (mut g, mut p) = rig(vec![
        vec![c(Spades, Three), c(Spades, King), c(Spades, Queen), c(Spades, Four)],
        vec![c(Hearts, Ace), c(Hearts, Five)],
        vec![c(Clubs, Nine), c(Clubs, Six), c(Clubs, Seven)],
    ]);
    lay(&mut g, &mut p, ANA, &[c(Spades, Three)], 1.0).unwrap();
    pass(&mut g, &mut p, BEN, 2.0).unwrap();
    lay(&mut g, &mut p, CEM, &[c(Clubs, Nine)], 3.0).unwrap();
    lay(&mut g, &mut p, ANA, &[c(Spades, King)], 4.0).unwrap();
    assert_eq!(g.turn, 2, "Ben passed, so the king goes straight on to Cem");
    assert_eq!(lay(&mut g, &mut p, BEN, &[c(Hearts, Ace)], 5.0), Err(ActionError::Ignored), "his ace has to wait");
    assert_eq!(scene(&g, &p, 5.0).seats[1].marker.as_deref(), Some("Passt"));
    pass(&mut g, &mut p, CEM, 6.0).unwrap();
    assert_eq!((g.turn, &g.top, g.passed.contains(&true)), (0, &None, false), "nobody beat the king: Ana leads onto a cleared table");
    lay(&mut g, &mut p, ANA, &[c(Spades, Queen)], 7.0).unwrap();
    assert_eq!(lay(&mut g, &mut p, BEN, &[c(Hearts, Ace)], 8.0), Ok(()), "a new lead lets Ben back in");
}

#[test]
fn a_two_calls_back_whoever_passed_but_only_to_bomb_it() {
    let mut ben = FOURS.to_vec();
    ben.push(c(Diamonds, Two));
    let (mut g, mut p) = rig(vec![vec![c(Spades, Three), c(Spades, Two), c(Diamonds, Seven)], ben, vec![c(Clubs, Five), c(Clubs, Eight), c(Diamonds, Eight)]]);
    lay(&mut g, &mut p, ANA, &[c(Spades, Three)], 1.0).unwrap();
    pass(&mut g, &mut p, BEN, 2.0).unwrap();
    lay(&mut g, &mut p, CEM, &[c(Clubs, Five)], 3.0).unwrap();
    lay(&mut g, &mut p, ANA, &[c(Spades, Two)], 4.0).unwrap();
    assert_eq!(g.turn, 1, "the two asks Ben again");
    let d = desk(&g, &p, BEN, 4.0);
    assert_eq!((d.prompt.as_str(), d.info.as_deref()), ("Nur eine Bombe schlägt die 2", Some("Vierling oder Doppelstraße aus 3 Paaren")));
    assert_eq!(d.buttons.iter().map(|b| b.label.as_str()).collect::<Vec<_>>(), ["Bombe legen", "Passen"]);
    assert_eq!(lay(&mut g, &mut p, BEN, &[c(Diamonds, Two)], 5.0), Err(ActionError::NotAllowed("Du hast gepasst – jetzt schlägt nur noch eine Bombe.")));
    lay(&mut g, &mut p, BEN, &FOURS, 5.0).unwrap();
    let s = scene(&g, &p, 5.0);
    assert_eq!((s.banner.unwrap().text.as_str(), s.note.as_deref()), (" legt eine Bombe!", Some("Vierling")));
    assert_eq!((g.top.as_ref().unwrap().combo.kind, g.passed[1], g.turn), (Kind::Quad, false, 2), "the bomb is the play to beat, and Ben is back in");
    pass(&mut g, &mut p, CEM, 6.0).unwrap();
    pass(&mut g, &mut p, ANA, 7.0).unwrap();
    assert_eq!((g.turn, &g.top), (1, &None), "Ben leads");
}

#[test]
fn players_go_out_one_by_one_and_the_last_holding_cards_loses() {
    let (mut g, mut p) = rig(vec![
        vec![c(Spades, Three), c(Spades, Nine)],
        vec![c(Hearts, Four), c(Hearts, Six), c(Hearts, Seven)],
        vec![c(Diamonds, Five), c(Diamonds, Six), c(Diamonds, Jack), c(Diamonds, Queen)],
    ]);
    lay(&mut g, &mut p, ANA, &[c(Spades, Three)], 1.0).unwrap();
    lay(&mut g, &mut p, BEN, &[c(Hearts, Four)], 2.0).unwrap();
    lay(&mut g, &mut p, CEM, &[c(Diamonds, Five)], 3.0).unwrap();
    lay(&mut g, &mut p, ANA, &[c(Spades, Nine)], 4.0).unwrap();
    let s = scene(&g, &p, 4.0);
    assert_eq!((s.banner.unwrap().text.as_str(), s.seats[0].marker.as_deref(), s.seats[0].cards), (" ist fertig – Platz 1", Some("Platz 1"), 0));
    assert_eq!(desk(&g, &p, ANA, 4.0).info.as_deref(), Some("Du bist fertig – Platz 1"));
    assert_eq!(g.turn, 1, "the others may still beat her last card");
    pass(&mut g, &mut p, BEN, 5.0).unwrap();
    pass(&mut g, &mut p, CEM, 6.0).unwrap();
    assert_eq!((g.turn, &g.top), (1, &None), "Ana is out, so the lead passes to the next player with cards");
    lay(&mut g, &mut p, BEN, &[c(Hearts, Six)], 7.0).unwrap();
    lay(&mut g, &mut p, CEM, &[c(Diamonds, Jack)], 8.0).unwrap();
    pass(&mut g, &mut p, BEN, 9.0).unwrap();
    lay(&mut g, &mut p, CEM, &[c(Diamonds, Six)], 10.0).unwrap();
    assert!(g.ranking(&p).is_none());
    lay(&mut g, &mut p, BEN, &[c(Hearts, Seven)], 11.0).unwrap();

    assert_eq!(g.stage, Stage::Final { ranking: vec![0, 1, 2] });
    assert_eq!(p.iter().map(|x| x.score).collect::<Vec<_>>(), [2, 1, 0]);
    let s = scene(&g, &p, 99_999.0);
    assert_eq!(s.seats.iter().map(|seat| seat.marker.as_deref()).collect::<Vec<_>>(), [Some("Platz 1"), Some("Platz 2"), None]);
    assert_eq!((s.ranking, s.turn, s.banner.map(|b| (b.player, b.text))), (Some(vec![ANA, BEN, CEM]), None, Some((ANA, " gewinnt!".into()))));
    assert_eq!(pass(&mut g, &mut p, CEM, 12.0), Err(ActionError::Ignored), "the game is over");
}

#[test]
fn the_house_leads_the_lowest_card_and_passes_for_a_player_who_is_gone() {
    let (mut g, mut p) = rig(vec![vec![c(Spades, Three), c(Hearts, Ten)], vec![c(Clubs, Four), c(Hearts, King)]]);
    leave(&mut p[0], 0.0);
    tick(&mut g, &mut p, 1000.0);
    assert!(g.top.is_none(), "the grace is not over");
    tick(&mut g, &mut p, 1500.0);
    assert_eq!((g.top.as_ref().map(|t| t.cards.clone()), g.turn), (Some(vec![c(Spades, Three)]), 1));
    lay(&mut g, &mut p, BEN, &[c(Clubs, Four)], 2000.0).unwrap();
    tick(&mut g, &mut p, 3500.0);
    assert_eq!((g.turn, &g.top, g.hands[0].len()), (1, &None, 1), "the house passed, though the ten would have beaten the four");
}

#[test]
fn the_phones_show_only_their_own_hand_and_the_tv_none() {
    let (mut g, mut p) = rig(vec![vec![c(Spades, Three), c(Hearts, Three), c(Clubs, Ace)], vec![c(Clubs, Two), c(Spades, Six), c(Hearts, Six)]]);
    let lead = desk(&g, &p, ANA, 0.0);
    assert_eq!((lead.prompt.as_str(), lead.info.as_deref()), ("Du spielst aus", Some("Deine niedrigste Karte muss dabei sein")));
    assert_eq!(lead.buttons.iter().map(|b| (b.id.as_str(), b.needs)).collect::<Vec<_>>(), [("play", ButtonNeeds::SomeHand)]);
    assert!(lead.hand.iter().all(|h| h.enabled) && lead.table.is_empty());
    let waits = desk(&g, &p, BEN, 0.0);
    assert_eq!((waits.prompt.as_str(), waits.buttons.len()), ("Ana ist dran", 0));
    assert_eq!(waits.hand.iter().map(|h| (h.card, h.enabled)).collect::<Vec<_>>(), [(c(Spades, Six), false), (c(Hearts, Six), false), (c(Clubs, Two), false)], "lowest first");

    lay(&mut g, &mut p, ANA, &[c(Hearts, Three), c(Spades, Three)], 1.0).unwrap();
    let answer = desk(&g, &p, BEN, 1.0);
    assert_eq!(answer.prompt, "Schlage das Paar oder passe");
    assert_eq!(answer.table.iter().map(|t| t.card).collect::<Vec<_>>(), [c(Spades, Three), c(Hearts, Three)]);
    assert_eq!(answer.buttons.iter().map(|b| b.id.as_str()).collect::<Vec<_>>(), ["play", "pass"]);

    let s = scene(&g, &p, 1.0);
    assert_eq!((s.game.as_str(), s.note.as_deref(), s.deck, s.turn), ("Tiến Lên", Some("Paar"), 0, Some(BEN)));
    assert_eq!(s.piles.iter().map(|pile| pile.cards.clone()).collect::<Vec<_>>(), [vec![c(Spades, Three), c(Hearts, Three)]], "the play, lowest first");
    assert_eq!(s.seats.iter().map(|seat| (seat.cards, seat.open.len())).collect::<Vec<_>>(), [(1, 0), (3, 0)], "hands stay face down");
    let laid: Vec<(Card, f64)> = s.events.iter().map(|e| match e.kind { CardsEventKind::Play { card, .. } => (card, e.at), _ => panic!("only plays") }).collect();
    assert_eq!(laid, [(c(Spades, Three), 1.0), (c(Hearts, Three), 71.0)], "one card after the other");
}

#[test]
fn a_two_on_the_table_reminds_of_the_bomb() {
    let (mut g, mut p) = rig(vec![vec![c(Spades, Three), c(Spades, Two), c(Clubs, Nine)], vec![c(Clubs, Four), c(Hearts, King)]]);
    lay(&mut g, &mut p, ANA, &[c(Spades, Three)], 1.0).unwrap();
    lay(&mut g, &mut p, BEN, &[c(Clubs, Four)], 2.0).unwrap();
    lay(&mut g, &mut p, ANA, &[c(Spades, Two)], 3.0).unwrap();
    let d = desk(&g, &p, BEN, 3.0);
    assert_eq!((d.prompt.as_str(), d.info.as_deref()), ("Schlage die Karte oder passe", Some("Bombe: Vierling oder Doppelstraße aus 3 Paaren")));
    assert_eq!(scene(&g, &p, 3.0 + 10_000.0).banner.unwrap().text, " ist dran");
    pass(&mut g, &mut p, BEN, 4.0).unwrap();
    assert_eq!(scene(&g, &p, 4.0 + 10_000.0).banner.map(|b| (b.player, b.text)), Some((ANA, " spielt aus".into())));
}

#[test]
fn a_two_calls_back_only_those_holding_cards_enough_for_a_bomb() {
    let (mut g, mut p) = rig(vec![
        vec![c(Spades, Three), c(Spades, Two), c(Diamonds, Seven), c(Diamonds, Nine)],
        vec![c(Clubs, Four), c(Clubs, Six), c(Clubs, Nine)],
        vec![c(Clubs, Five), c(Clubs, Eight), c(Hearts, Eight), c(Clubs, King)],
    ]);
    lay(&mut g, &mut p, ANA, &[c(Spades, Three)], 1.0).unwrap();
    pass(&mut g, &mut p, BEN, 2.0).unwrap();
    lay(&mut g, &mut p, CEM, &[c(Clubs, Five)], 3.0).unwrap();
    lay(&mut g, &mut p, ANA, &[c(Spades, Two)], 4.0).unwrap();
    assert_eq!(g.turn, 2, "Ben passed and three cards make no bomb, as the whole table can count");
}

#[test]
fn a_pair_of_twos_falls_to_four_pairs_in_a_row_from_a_player_who_passed() {
    let pairs = |ranks: &[Rank]| ranks.iter().flat_map(|&r| [c(Spades, r), c(Hearts, r)]).collect::<Vec<_>>();
    let (mut g, mut p) = rig(vec![
        vec![c(Spades, Three), c(Hearts, Three), c(Clubs, Two), c(Diamonds, Two), c(Diamonds, King)],
        pairs(&[Five, Six, Seven, Eight]),
        vec![c(Clubs, Four), c(Diamonds, Four), c(Clubs, Ten), c(Diamonds, Ten), c(Clubs, Jack)],
    ]);
    lay(&mut g, &mut p, ANA, &[c(Spades, Three), c(Hearts, Three)], 1.0).unwrap();
    pass(&mut g, &mut p, BEN, 2.0).unwrap();
    lay(&mut g, &mut p, CEM, &[c(Clubs, Four), c(Diamonds, Four)], 3.0).unwrap();
    lay(&mut g, &mut p, ANA, &[c(Clubs, Two), c(Diamonds, Two)], 4.0).unwrap();
    assert_eq!(g.turn, 1, "Ben passed, but eight cards can be four pairs");
    assert_eq!(desk(&g, &p, BEN, 4.0).info.as_deref(), Some("Doppelstraße aus 4 Paaren"));
    assert_eq!(lay(&mut g, &mut p, BEN, &pairs(&[Five, Six, Seven]), 5.0), Err(ActionError::NotAllowed("Das schlägt die Karten auf dem Tisch nicht.")), "three pairs beat one two only");
    lay(&mut g, &mut p, BEN, &pairs(&[Five, Six, Seven, Eight]), 5.0).unwrap();
    assert_eq!((g.out.as_slice(), g.top.as_ref().unwrap().combo.kind, g.turn), (&[1][..], Kind::PairRun, 2), "Ben goes out on his bomb and Cem is asked");
}

#[test]
fn a_play_nobody_may_answer_lies_on_show_before_the_table_is_cleared() {
    let (mut g, mut p) = rig(vec![
        vec![c(Spades, Three), c(Spades, Nine)],
        vec![c(Hearts, Four), c(Hearts, Six), c(Hearts, Seven)],
        vec![c(Diamonds, Five), c(Diamonds, Jack), c(Diamonds, Queen), c(Diamonds, King)],
    ]);
    lay(&mut g, &mut p, ANA, &[c(Spades, Three)], 1.0).unwrap();
    lay(&mut g, &mut p, BEN, &[c(Hearts, Four)], 2.0).unwrap();
    lay(&mut g, &mut p, CEM, &[c(Diamonds, Five)], 3.0).unwrap();
    lay(&mut g, &mut p, ANA, &[c(Spades, Nine)], 4.0).unwrap();
    pass(&mut g, &mut p, BEN, 5.0).unwrap();
    // Ana is out and Ben has passed: nobody is left to answer Cem's jack
    lay(&mut g, &mut p, CEM, &[c(Diamonds, Jack)], 6.0).unwrap();

    assert_eq!(g.stage, Stage::Settle { since: 6.0 });
    let s = scene(&g, &p, 6.0);
    assert_eq!((s.piles[0].cards.as_slice(), s.turn, s.note.as_deref()), (&[c(Diamonds, Jack)][..], Some(CEM), Some("Einzelkarte")), "the jack is on the table for all to see");
    let cem = desk(&g, &p, CEM, 6.0);
    assert_eq!((cem.prompt.as_str(), cem.buttons.len(), cem.hand.iter().any(|h| h.enabled)), ("Gleich spielst du aus", 0, false));
    let ben = desk(&g, &p, BEN, 6.0);
    assert_eq!((ben.prompt.as_str(), ben.info.as_deref()), ("Cem ist dran", Some("Du hast gepasst")));
    assert_eq!(lay(&mut g, &mut p, CEM, &[c(Diamonds, Queen)], 7.0), Err(ActionError::Ignored), "not before the table is cleared");
    tick(&mut g, &mut p, 6.0 + 1999.0);
    assert!(g.top.is_some());
    tick(&mut g, &mut p, 6.0 + 2000.0);
    assert_eq!((&g.top, g.turn, g.passed.contains(&true)), (&None, 2, false));
    assert_eq!((desk(&g, &p, CEM, 2006.0).prompt.as_str(), scene(&g, &p, 2006.0).piles.len()), ("Du spielst aus", 0));
}

#[test]
fn the_house_plays_a_whole_game_at_every_table_size() {
    for n in 2..=4 {
        let mut p = players(n);
        p.iter_mut().for_each(|player| leave(player, 0.0));
        let mut g = TienLenGame::new(TienLenConfig { seed: Some(n as u64), ..TienLenConfig::default() }, &p, 0.0);
        let mut now = 0.0;
        while g.ranking(&p).is_none() {
            now += 2500.0;
            assert!(now < 2_000_000.0, "{n} players: the game never ends");
            tick(&mut g, &mut p, now);
            assert!(matches!(g.stage, Stage::Final { .. }) || !g.hands[g.turn].is_empty(), "{n} players: a seat without cards is in turn");
        }
        let Stage::Final { ranking } = &g.stage else { unreachable!("the game is ranked") };
        let mut seats = ranking.clone();
        seats.sort_unstable();
        assert_eq!(seats, (0..n).collect::<Vec<_>>(), "{n} players: everyone is ranked once");
        assert_eq!(ranking.iter().map(|&seat| p[seat].score).collect::<Vec<_>>(), (0..n as i32).rev().collect::<Vec<_>>());
    }
}
