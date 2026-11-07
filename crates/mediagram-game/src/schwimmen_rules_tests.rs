use super::*;
use crate::cards::{Rank::*, Suit::*};

fn c(suit: Suit, rank: Rank) -> Card {
    Card::new(suit, rank)
}

#[test]
fn a_hand_is_worth_its_best_suit() {
    assert_eq!(value(&[c(Hearts, Ace), c(Hearts, Seven), c(Spades, King)]), Value(36), "A + 7 hearts = 18");
    assert_eq!(value(&[c(Hearts, Ace), c(Spades, Seven), c(Clubs, Eight)]), Value(22), "a lone ace = 11");
    assert_eq!(value(&[c(Clubs, Nine), c(Clubs, Eight), c(Clubs, Seven)]), Value(48));
}

#[test]
fn picture_cards_and_ten_count_ten_and_an_ace_eleven_so_31_is_ace_plus_two_tens() {
    assert_eq!(value(&[c(Spades, Ace), c(Spades, King), c(Spades, Jack)]), FEUER);
    assert_eq!(value(&[c(Spades, Ace), c(Spades, Ten), c(Spades, Queen)]), FEUER);
    assert_eq!(FEUER.show(), "31");
}

#[test]
fn three_of_a_kind_is_thirty_and_a_half() {
    let v = value(&[c(Hearts, Seven), c(Spades, Seven), c(Clubs, Seven)]);
    assert_eq!(v, TRIPS);
    assert!(v < FEUER && v > value(&[c(Hearts, Ace), c(Hearts, King), c(Hearts, Eight)]));
    assert_eq!(v.show(), "30½");
    assert_eq!(value(&[c(Hearts, Ace), c(Spades, Ace), c(Clubs, Ace)]), TRIPS);
}

#[test]
fn values_are_shown_with_halves_only_when_needed() {
    assert_eq!(KNOCK_AT.show(), "27");
    assert_eq!(Value(0).show(), "0");
}

#[test]
fn the_best_swap_is_found_among_one_card_swaps_and_the_swap_of_all_three() {
    let hand = [c(Spades, Two), c(Clubs, Three), c(Diamonds, Four)];
    let middle = [c(Hearts, Ace), c(Hearts, King), c(Hearts, Ten)];
    let swap = best_swap(&hand, &middle).unwrap();
    assert_eq!(swap, Swap::All, "A K 10 of hearts beats any single swap");
    let (mut h, mut m) = (hand, middle);
    apply(&mut h, &mut m, swap);
    assert_eq!((h, m), (middle, hand));
    let one = best_swap(&[c(Hearts, Ace), c(Hearts, King), c(Clubs, Three)], &[c(Hearts, Ten), c(Clubs, Two), c(Clubs, Four)]).unwrap();
    assert_eq!(one, Swap::One { hand: 2, middle: 0 });
}

#[test]
fn no_swap_is_offered_when_none_improves_the_hand() {
    assert_eq!(best_swap(&[c(Hearts, Ace), c(Hearts, King), c(Hearts, Queen)], &[c(Clubs, Two), c(Clubs, Three), c(Spades, Four)]), None);
}
