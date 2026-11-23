use super::*;
use crate::card_game_driver::c;
use crate::cards::Rank::{self, *};
use crate::cards::Suit::{self, *};

/// One card of each rank given, all in `suit`.
fn of(suit: Suit, ranks: &[Rank]) -> Vec<Card> {
    ranks.iter().map(|&r| c(suit, r)).collect()
}

/// A pair (spades and hearts) of each rank given.
fn pairs(ranks: &[Rank]) -> Vec<Card> {
    ranks.iter().flat_map(|&r| [c(Spades, r), c(Hearts, r)]).collect()
}

fn made(cards: &[Card]) -> Combo {
    combo(cards).expect("a combination")
}

#[test]
fn the_three_of_spades_is_lowest_and_the_two_of_hearts_highest() {
    let mut all = crate::cards::deck_from(Two);
    sort_hand(&mut all);
    assert_eq!((all[0], all[51]), (c(Spades, Three), c(Hearts, Two)));
    assert!(strength(c(Spades, Eight)) > strength(c(Hearts, Seven)), "rank counts before suit");
    let suits: Vec<Suit> = all[..4].iter().map(|c| c.suit).collect();
    assert_eq!(suits, [Spades, Clubs, Diamonds, Hearts], "suits, lowest first");
}

#[test]
fn the_lowest_card_dealt_names_who_leads() {
    let hands = vec![of(Hearts, &[Three, Ace]), of(Clubs, &[Three, Four]), of(Diamonds, &[Two])];
    assert_eq!(opener(&hands), Some((1, c(Clubs, Three))), "no three of spades at a short table");
}

#[test]
fn sets_of_one_rank_are_single_pair_triple_and_four_of_a_kind() {
    let nines = [c(Spades, Nine), c(Clubs, Nine), c(Diamonds, Nine), c(Hearts, Nine)];
    for (n, kind) in [(1, Kind::Single), (2, Kind::Pair), (3, Kind::Triple), (4, Kind::Quad)] {
        assert_eq!(made(&nines[..n]).kind, kind);
    }
    assert_eq!(combo(&[c(Spades, Nine), c(Clubs, Ten)]), None, "two ranks are no pair");
    assert_eq!(combo(&[]), None);
}

#[test]
fn a_run_needs_three_ranks_in_a_row_and_may_end_on_the_two() {
    assert_eq!(made(&of(Clubs, &[Four, Five, Six])).kind, Kind::Run);
    let top = made(&[c(Spades, Jack), c(Hearts, Queen), c(Clubs, King), c(Diamonds, Ace), c(Spades, Two)]);
    assert_eq!((top.kind, top.len, top.high), (Kind::Run, 5, strength(c(Spades, Two))));
    assert_eq!(made(&of(Hearts, &[Six, Four, Five])).kind, Kind::Run, "picked in any order");
    assert_eq!(combo(&of(Clubs, &[Four, Five])), None, "two cards are too few");
    assert_eq!(combo(&of(Clubs, &[Four, Five, Seven])), None, "a gap");
    assert_eq!(combo(&of(Clubs, &[Ace, Two, Three])), None, "not round the corner");
}

#[test]
fn a_run_of_pairs_needs_three_pairs_in_a_row() {
    let three = made(&pairs(&[Three, Four, Five]));
    assert_eq!((three.kind, three.len), (Kind::PairRun, 6));
    assert_eq!(made(&pairs(&[Six, Seven, Eight, Nine])).len, 8);
    assert_eq!(combo(&pairs(&[Three, Four])), None, "two pairs are too few");
    assert_eq!(combo(&pairs(&[Three, Four, Six])), None, "a gap");
    let mut odd = pairs(&[Three, Four]);
    odd.extend([c(Spades, Five), c(Hearts, Six)]);
    assert_eq!(combo(&odd), None, "the last two are no pair");
}

#[test]
fn only_a_higher_play_of_the_same_kind_and_length_beats() {
    let beat = |top: &[Card], play: &[Card]| beats(made(top), made(play));
    assert!(beat(&[c(Hearts, Seven)], &[c(Spades, Eight)]));
    assert!(!beat(&[c(Spades, Eight)], &[c(Hearts, Seven)]));
    assert!(beat(&[c(Diamonds, Seven), c(Spades, Seven)], &[c(Hearts, Seven), c(Clubs, Seven)]), "the heart beats the diamond");
    assert!(beat(&of(Hearts, &[Eight, Nine, Ten]), &[c(Spades, Nine), c(Spades, Ten), c(Spades, Jack)]));
    assert!(
        beat(&[c(Hearts, Eight), c(Hearts, Nine), c(Diamonds, Ten)], &[c(Spades, Eight), c(Spades, Nine), c(Hearts, Ten)]),
        "only the highest cards are compared"
    );
    assert!(!beat(&[c(Spades, Four), c(Clubs, Four)], &of(Hearts, &[Five])), "a pair is not beaten by a single");
    assert!(!beat(&pairs(&[Five]), &[c(Spades, Six), c(Clubs, Six), c(Hearts, Six)]), "nor by a triple");
    assert!(!beat(&of(Clubs, &[Three, Four, Five, Six]), &of(Hearts, &[Four, Five, Six, Seven, Eight])), "nor a run by a longer one");
}

#[test]
fn bombs_beat_twos_and_nothing_else() {
    let quad = made(&[c(Spades, Four), c(Clubs, Four), c(Diamonds, Four), c(Hearts, Four)]);
    let (three, four, five) = (made(&pairs(&[Three, Four, Five])), made(&pairs(&[Three, Four, Five, Six])), made(&pairs(&[Three, Four, Five, Six, Seven])));
    let (two, two_pair) = (made(&[c(Hearts, Two)]), made(&pairs(&[Two])));
    let two_triple = made(&[c(Spades, Two), c(Clubs, Two), c(Hearts, Two)]);

    assert!(beats(two, quad) && beats(two, three), "one two falls to four of a kind and to three pairs");
    assert!(!beats(two, four) && !beats(two, five));
    assert!(beats(two_pair, four) && !beats(two_pair, quad) && !beats(two_pair, three));
    assert!(beats(two_triple, five) && !beats(two_triple, four));
    assert!(!beats(made(&[c(Hearts, Ace)]), quad), "an ace is no two");
    assert!(!beats(made(&pairs(&[Ace])), four));
    assert!(beats(quad, made(&[c(Spades, Five), c(Clubs, Five), c(Diamonds, Five), c(Hearts, Five)])), "a bomb falls to a higher one of its kind");
    assert!(!beats(quad, three) && !beats(three, quad), "bombs of two kinds do not beat each other");
}

#[test]
fn twos_and_bombs_are_told_apart() {
    assert!(made(&pairs(&[Two])).twos() && !made(&pairs(&[Ace])).twos());
    assert!(!made(&of(Hearts, &[King, Ace, Two])).twos(), "a run ending on the two is a run");
    assert!(made(&pairs(&[Three, Four, Five])).bomb() && !made(&pairs(&[Two])).bomb());
}
