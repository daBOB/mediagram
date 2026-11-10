use std::collections::BTreeMap;

use super::*;

/// Picks of one round: `(player, choice, speed points)`.
fn picks(list: &[(PlayerId, u8, i32)]) -> BTreeMap<PlayerId, (u8, i32)> {
    list.iter().map(|&(p, c, s)| (p, (c, s))).collect()
}

/// Everyone connected, so nobody counts as having left.
const EVERYONE: &[PlayerId] = &[1, 2, 3, 4];
const RIGHT: u8 = 1;
const WRONG: u8 = 2;

#[test]
fn a_wrong_or_missing_pick_eliminates_a_right_one_survives() {
    let mut t = Tally::new([1, 2, 3]);
    let out = t.settle_round(0, RIGHT, &picks(&[(1, RIGHT, 900), (2, WRONG, 950)]), EVERYONE);
    assert_eq!(out, vec![2, 3]);
    assert!(t.is_alive(1) && !t.is_alive(2) && !t.is_alive(3));
}

#[test]
fn when_every_alive_player_is_wrong_nobody_is_out() {
    let mut t = Tally::new([1, 2, 3]);
    assert!(t.settle_round(0, RIGHT, &picks(&[(1, WRONG, 900), (2, 0, 900)]), EVERYONE).is_empty());
    assert!(t.is_alive(1) && t.is_alive(2) && t.is_alive(3));
}

#[test]
fn ghost_picks_never_change_who_is_alive() {
    let mut t = Tally::new([1, 2, 3]);
    t.settle_round(0, RIGHT, &picks(&[(1, RIGHT, 900), (2, RIGHT, 900)]), EVERYONE);
    // 3 is out; its right guess cannot save 1 and 2 from an all-wrong round, nor put 3 back.
    assert!(t.settle_round(1, RIGHT, &picks(&[(1, WRONG, 0), (2, WRONG, 0), (3, RIGHT, 900)]), EVERYONE).is_empty());
    assert!(t.is_alive(1) && t.is_alive(2) && !t.is_alive(3));
}

#[test]
fn the_last_one_alive_wins_and_two_wrong_both_stay() {
    let mut t = Tally::new([1, 2]);
    t.settle_round(0, RIGHT, &picks(&[(1, WRONG, 0), (2, WRONG, 0)]), EVERYONE);
    assert!(!t.is_over(1, 12, &[1, 2]), "both stayed in");
    t.settle_round(1, RIGHT, &picks(&[(1, RIGHT, 700), (2, WRONG, 0)]), EVERYONE);
    assert!(t.is_over(2, 12, &[1, 2]));
    assert_eq!(t.finishing_order(), vec![1, 2]);
}

#[test]
fn at_the_cap_the_alive_are_ranked_by_speed_then_id() {
    let mut t = Tally::new([1, 2, 3, 4]);
    t.settle_round(0, RIGHT, &picks(&[(1, RIGHT, 600), (2, RIGHT, 900), (3, RIGHT, 600), (4, WRONG, 0)]), EVERYONE);
    assert!(!t.is_over(1, 2, &[1, 2, 3, 4]));
    t.settle_round(1, RIGHT, &picks(&[(1, RIGHT, 500), (2, RIGHT, 500), (3, RIGHT, 500)]), EVERYONE);
    assert!(t.is_over(2, 2, &[1, 2, 3, 4]), "the cap ends a game with several alive");
    assert_eq!(t.finishing_order(), vec![2, 1, 3, 4]);
}

#[test]
fn a_later_drop_places_better() {
    let mut t = Tally::new([1, 2, 3]);
    t.settle_round(0, RIGHT, &picks(&[(1, RIGHT, 500), (2, RIGHT, 500)]), EVERYONE);
    t.settle_round(1, RIGHT, &picks(&[(1, RIGHT, 500)]), EVERYONE);
    assert_eq!(t.finishing_order(), vec![1, 2, 3]);
    assert_eq!(t.out(), vec![(3, 0), (2, 1)], "in the order they went out");
}

#[test]
fn same_round_drops_rank_by_later_ghost_guesses_then_speed_then_id() {
    // 3 and 4 drop together; 4 guesses right afterwards, 3 does not.
    let mut t = Tally::new([1, 2, 3, 4]);
    t.settle_round(0, RIGHT, &picks(&[(1, RIGHT, 900), (2, RIGHT, 800), (3, RIGHT, 999), (4, RIGHT, 500)]), EVERYONE);
    t.settle_round(1, RIGHT, &picks(&[(1, RIGHT, 900), (2, RIGHT, 800)]), EVERYONE);
    t.settle_round(2, RIGHT, &picks(&[(1, RIGHT, 900), (2, RIGHT, 800), (3, WRONG, 0), (4, RIGHT, 900)]), EVERYONE);
    assert_eq!(t.finishing_order()[2..], [4, 3], "the ghost's right guess beats 3's speed");

    // Equal ghost guesses: the faster player while alive places better.
    let mut t = Tally::new([1, 3, 4]);
    t.settle_round(0, RIGHT, &picks(&[(1, RIGHT, 900), (3, RIGHT, 600), (4, RIGHT, 800)]), EVERYONE);
    t.settle_round(1, RIGHT, &picks(&[(1, RIGHT, 900)]), EVERYONE);
    assert_eq!(t.finishing_order(), vec![1, 4, 3]);

    // Equal in everything: the lower id first.
    let mut t = Tally::new([1, 3, 4]);
    t.settle_round(0, RIGHT, &picks(&[(1, RIGHT, 900)]), EVERYONE);
    assert_eq!(t.finishing_order(), vec![1, 3, 4]);
}

#[test]
fn without_an_alive_player_connected_the_game_is_over() {
    let mut t = Tally::new([1, 2, 3]);
    t.settle_round(0, RIGHT, &picks(&[(1, RIGHT, 500), (2, RIGHT, 900)]), EVERYONE);
    assert!(!t.is_over(1, 12, &[1, 3]));
    assert!(t.is_over(1, 12, &[3]), "only the out player is still here");
    assert_eq!(t.finishing_order(), vec![2, 1, 3], "the alive ranked by speed");
}

#[test]
fn final_scores_pay_100_per_player_beaten() {
    assert_eq!(placement_scores(&[3, 1, 2]), vec![(3, 200), (1, 100), (2, 0)]);
    assert!(placement_scores(&[]).is_empty());
}

#[test]
fn a_player_who_left_without_a_pick_is_out_even_when_everyone_else_is_wrong() {
    let mut t = Tally::new([1, 2, 3]);
    assert_eq!(t.settle_round(0, RIGHT, &picks(&[(1, WRONG, 0)]), &[1, 2]), vec![3], "2 is here, so stays like 1");
    assert!(t.is_alive(1) && t.is_alive(2));
}

#[test]
fn when_everyone_still_in_has_left_nobody_is_put_out() {
    let mut t = Tally::new([1, 2]);
    assert!(t.settle_round(0, RIGHT, &picks(&[]), &[]).is_empty());
    assert!(t.is_over(1, 12, &[]));
}
