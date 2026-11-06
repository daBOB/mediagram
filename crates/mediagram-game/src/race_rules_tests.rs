use super::*;

fn seats(pieces: &[[Progress; PIECES]]) -> Vec<Seat> {
    let at = corners(pieces.len());
    pieces.iter().enumerate().map(|(i, p)| Seat { player: i as u8, corner: at[i], pieces: *p }).collect()
}

const HOME: [Progress; PIECES] = [None; PIECES];

#[test]
fn all_at_home_only_a_six_comes_out() {
    let s = seats(&[HOME, HOME]);
    for roll in 1..=5 {
        assert!(legal_moves(&s, 0, roll).is_empty(), "roll {roll}");
    }
    assert_eq!(legal_moves(&s, 0, 6), vec![Move { piece: 0, from: None, to: 0, captures: None }]);
    assert!(s[0].three_tries());
}

#[test]
fn a_six_must_bring_a_piece_out_when_the_start_is_free() {
    let s = seats(&[[Some(12), None, None, None], HOME]);
    assert_eq!(legal_moves(&s, 0, 6), vec![Move { piece: 1, from: None, to: 0, captures: None }]);
}

#[test]
fn the_start_field_must_be_cleared_while_pieces_wait() {
    let s = seats(&[[Some(0), Some(12), None, None], HOME]);
    // A 6 cannot bring the next piece out onto the own piece; the start piece moves on.
    assert_eq!(legal_moves(&s, 0, 6), vec![Move { piece: 0, from: Some(0), to: 6, captures: None }]);
    assert_eq!(legal_moves(&s, 0, 3), vec![Move { piece: 0, from: Some(0), to: 3, captures: None }]);
    // Blocked by an own piece: then the others may move.
    let blocked = seats(&[[Some(0), Some(4), None, None], HOME]);
    assert_eq!(legal_moves(&blocked, 0, 4), vec![Move { piece: 1, from: Some(4), to: 8, captures: None }]);
}

#[test]
fn no_landing_on_an_own_piece_but_jumping_over_is_fine() {
    let s = seats(&[[Some(3), Some(5), Some(30), Some(31)], HOME]);
    let moves = legal_moves(&s, 0, 2);
    assert!(moves.iter().all(|m| m.from != Some(3)), "3 + 2 lands on the own piece at 5");
    assert!(moves.iter().any(|m| m.from == Some(5) && m.to == 7));
    assert!(legal_moves(&s, 0, 4).iter().any(|m| m.from == Some(3) && m.to == 7), "jumps over 5");
}

#[test]
fn landing_on_an_opponent_captures_it() {
    // Seat 1 sits in corner 2: its progress 5 is track field 25, which is seat 0's progress 25.
    let mut s = seats(&[[Some(22), None, None, None], [Some(5), None, None, None]]);
    let moves = legal_moves(&s, 0, 3);
    assert_eq!(moves, vec![Move { piece: 0, from: Some(22), to: 25, captures: Some((1, 0)) }]);
    apply(&mut s, 0, &moves[0]);
    assert_eq!(s[0].pieces[0], Some(25));
    assert_eq!(s[1].pieces[0], None);
}

#[test]
fn coming_out_captures_an_opponent_on_the_start_field() {
    // Seat 1's progress 20 is field 0, seat 0's start.
    let s = seats(&[HOME, [Some(20), None, None, None]]);
    assert_eq!(legal_moves(&s, 0, 6)[0].captures, Some((1, 0)));
}

#[test]
fn the_goal_needs_an_exact_count_and_no_jumping_over_own_pieces() {
    let s = seats(&[[Some(38), Some(42), Some(GOAL_END), Some(20)], HOME]);
    let from_38 = |roll| legal_moves(&s, 0, roll).into_iter().find(|m| m.from == Some(38));
    assert_eq!(from_38(2).map(|m| m.to), Some(40));
    assert_eq!(from_38(3).map(|m| m.to), Some(41));
    assert_eq!(from_38(4), None, "42 is taken");
    assert_eq!(from_38(5), None, "would jump over 42");
    assert_eq!(from_38(6), None, "past the end");
    assert!(legal_moves(&s, 0, 1).iter().all(|m| m.from != Some(42)), "43 is taken");
}

#[test]
fn three_tries_only_with_nothing_left_to_move() {
    assert!(seats(&[[Some(GOAL_END), Some(42), None, None], HOME])[0].three_tries());
    assert!(!seats(&[[Some(GOAL_END), Some(41), None, None], HOME])[0].three_tries(), "41 can still move to 42");
    assert!(!seats(&[[Some(7), None, None, None], HOME])[0].three_tries());
}

#[test]
fn corners_seat_two_players_opposite() {
    assert_eq!(corners(2), &[0, 2]);
    assert_eq!(corners(3), &[0, 1, 2]);
    assert_eq!(corners(4), &[0, 1, 2, 3]);
}

#[test]
fn standings_put_the_winner_first_then_goal_pieces_then_distance() {
    let s = seats(&[
        [Some(10), None, None, None],
        [Some(40), Some(41), Some(42), Some(43)],
        [Some(40), Some(3), None, None],
        [Some(30), None, None, None],
    ]);
    assert_eq!(standings(&s, 1), vec![1, 2, 3, 0]);
    assert!(s[1].finished());
}
