use crate::protocol::Ranking;
use crate::room_seats::Player;

fn places(r: &Ranking) -> Vec<(u8, u8)> {
    r.places.iter().map(|p| (p.player, p.place)).collect()
}

#[test]
fn equal_scores_share_a_place_and_keep_their_seat_order() {
    let mut ps: Vec<Player> = ["Ana", "Ben", "Cem", "Dana"].iter().zip(0..).map(|(n, i)| Player::for_test(i, n, true)).collect();
    for (p, score) in ps.iter_mut().zip([5, 9, 5, 0]) {
        p.score = score;
    }
    assert_eq!(places(&Ranking::by_score(&ps)), [(1, 1), (0, 2), (2, 2), (3, 4)]);
    assert_eq!(places(&Ranking::in_order([2, 0, 1])), [(2, 1), (0, 2), (1, 3)], "an order shares no place");
}
