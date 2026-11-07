use super::*;
use Rank::*;
use Suit::*;

fn c(suit: Suit, rank: Rank) -> Card {
    Card::new(suit, rank)
}

#[test]
fn a_card_matches_by_suit_or_rank() {
    let top = Top::plain(c(Hearts, Nine));
    assert!(playable(top, 0, c(Hearts, Ace)));
    assert!(playable(top, 0, c(Spades, Nine)));
    assert!(!playable(top, 0, c(Spades, Ace)));
}

#[test]
fn a_jack_goes_on_anything_and_a_wish_replaces_the_suit() {
    assert!(playable(Top::plain(c(Hearts, Nine)), 0, c(Clubs, Jack)));
    let wished = Top { card: c(Spades, Jack), suit: Some(Diamonds) };
    assert!(playable(wished, 0, c(Diamonds, Ace)));
    assert!(!playable(wished, 0, c(Spades, Ace)), "the jack's own suit no longer counts");
    assert!(playable(wished, 0, c(Hearts, Jack)));
}

#[test]
fn a_jack_turned_up_to_start_allows_any_suit() {
    let start = Top { card: c(Clubs, Jack), suit: None };
    assert!(playable(start, 0, c(Hearts, Eight)));
}

#[test]
fn owed_sevens_allow_only_a_seven() {
    let top = Top::plain(c(Hearts, Seven));
    assert!(playable(top, 2, c(Spades, Seven)));
    assert!(!playable(top, 2, c(Hearts, Ace)));
    assert!(!playable(top, 2, c(Hearts, Jack)), "a jack does not save from a seven");
}

#[test]
fn most_held_suit_picks_the_longest_and_falls_back_to_hearts() {
    assert_eq!(most_held_suit(&[c(Clubs, Ace), c(Spades, Ace), c(Spades, King)]), Spades);
    assert_eq!(most_held_suit(&[c(Clubs, Ace), c(Spades, King)]), Clubs, "a tie goes to the first suit");
    assert_eq!(most_held_suit(&[]), Hearts);
}

#[test]
fn an_absent_player_plays_a_plain_card_before_a_special_one() {
    let top = Top::plain(c(Hearts, Nine));
    let hand = [c(Hearts, Eight), c(Hearts, King), c(Clubs, Jack)];
    assert_eq!(auto_pick(top, 0, &hand), Some(1));
    assert_eq!(auto_pick(top, 0, &[c(Clubs, Ace)]), None);
}

#[test]
fn ranking_orders_by_cards_left_then_points() {
    let hands = vec![vec![], vec![c(Clubs, Ace), c(Clubs, Ten)], vec![c(Spades, Two), c(Spades, Three)], vec![c(Hearts, Ace)]];
    assert_eq!(ranking(0, &hands), vec![0, 3, 2, 1]);
}

#[test]
fn lowest_rank_follows_the_player_count() {
    assert_eq!(lowest_rank(5), Seven);
    assert_eq!(lowest_rank(6), Two);
}
