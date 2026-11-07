use super::*;
use Rank::*;
use Suit::*;

fn c(suit: Suit, rank: Rank) -> Card {
    Card::new(suit, rank)
}

#[test]
fn a_higher_card_of_the_suit_beats() {
    assert!(beats(Hearts, c(Spades, Seven), c(Spades, Eight)));
    assert!(beats(Hearts, c(Spades, Seven), c(Spades, Ace)));
}

#[test]
fn a_lower_or_equal_card_of_the_suit_does_not_beat() {
    assert!(!beats(Hearts, c(Spades, Nine), c(Spades, Eight)));
    assert!(!beats(Hearts, c(Spades, Nine), c(Spades, Nine)));
}

#[test]
fn a_card_of_another_plain_suit_never_beats() {
    assert!(!beats(Hearts, c(Spades, Six), c(Clubs, Ace)));
}

#[test]
fn any_trump_beats_a_plain_card() {
    assert!(beats(Hearts, c(Spades, Ace), c(Hearts, Six)));
}

#[test]
fn a_trump_is_beaten_only_by_a_higher_trump() {
    assert!(beats(Hearts, c(Hearts, Ten), c(Hearts, Jack)));
    assert!(!beats(Hearts, c(Hearts, Ten), c(Hearts, Nine)));
    assert!(!beats(Hearts, c(Hearts, Six), c(Spades, Ace)));
}

#[test]
fn throw_in_needs_a_rank_already_on_the_table() {
    let table = [c(Spades, Seven), c(Hearts, Nine)];
    assert!(can_throw(&table, c(Clubs, Seven)));
    assert!(can_throw(&table, c(Diamonds, Nine)));
    assert!(!can_throw(&table, c(Clubs, Eight)));
    assert!(!can_throw(&[], c(Clubs, Eight)));
}

#[test]
fn the_attack_limit_is_six_or_the_defenders_hand() {
    assert_eq!(attack_limit(9), 6);
    assert_eq!(attack_limit(6), 6);
    assert_eq!(attack_limit(2), 2);
}

#[test]
fn an_absent_attacker_leads_the_lowest_plain_card_before_any_trump() {
    let hand = [c(Hearts, Six), c(Spades, Nine), c(Clubs, Eight)];
    assert_eq!(auto_attack(Hearts, &hand), Some(2));
    assert_eq!(auto_attack(Hearts, &[c(Hearts, Seven), c(Hearts, Six)]), Some(1));
    assert_eq!(auto_attack(Hearts, &[]), None);
}

#[test]
fn an_absent_defender_beats_with_the_cheapest_card_or_none() {
    let hand = [c(Hearts, Six), c(Spades, Ace), c(Spades, Ten)];
    assert_eq!(auto_defence(Hearts, c(Spades, Nine), &hand), Some(2));
    assert_eq!(auto_defence(Hearts, c(Clubs, Nine), &hand), Some(0));
    assert_eq!(auto_defence(Hearts, c(Hearts, Ace), &hand), None);
}

#[test]
fn the_lowest_trump_leads_first() {
    let hands = vec![vec![c(Hearts, Ten)], vec![c(Hearts, Seven), c(Spades, Six)], vec![c(Clubs, Six)]];
    assert_eq!(first_attacker(Hearts, &hands), 1);
    assert_eq!(first_attacker(Diamonds, &hands), 0);
}
