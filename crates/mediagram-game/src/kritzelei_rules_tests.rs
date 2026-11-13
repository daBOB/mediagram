use super::*;

fn table(n: u8, offline: &[u8]) -> Vec<Player> {
    (0..n).map(|i| Player::for_test(i, "p", !offline.contains(&i))).collect()
}

fn line(color: u8, width: u8, points: Vec<u16>) -> DrawOp {
    DrawOp::Line { color, width, points }
}

fn refused(r: Result<usize, ActionError>) -> bool {
    matches!(r, Err(ActionError::NotAllowed(_)))
}

#[test]
fn turns_go_round_the_table_twice_below_five() {
    assert_eq!(turn_order(&table(3, &[]), 5), [0, 1, 2, 0, 1, 2]);
}

#[test]
fn five_players_draw_once_each() {
    assert_eq!(turn_order(&table(5, &[]), 5), [0, 1, 2, 3, 4]);
}

#[test]
fn a_disconnected_player_at_the_start_is_left_out() {
    assert_eq!(turn_order(&table(4, &[1]), 5), [0, 2, 3, 0, 2, 3]);
}

#[test]
fn a_line_needs_a_known_colour_width_and_whole_points() {
    let cfg = KritzeleiConfig::default();
    let ok = vec![1, 2, 3, 4];
    assert_eq!(check_op(&line(0, 0, ok.clone()), &[], 0, &cfg).unwrap(), 2);
    assert_eq!(check_op(&line(5, 2, ok.clone()), &[], 0, &cfg).unwrap(), 2, "the eraser is a colour");
    assert!(refused(check_op(&line(6, 0, ok.clone()), &[], 0, &cfg)), "colour 6");
    assert!(refused(check_op(&line(0, 3, ok.clone()), &[], 0, &cfg)), "width 3");
    assert!(refused(check_op(&line(0, 0, vec![1, 2, 3]), &[], 0, &cfg)), "odd length");
    assert!(refused(check_op(&line(0, 0, vec![]), &[], 0, &cfg)), "empty");
    assert!(refused(check_op(&line(0, 0, vec![10_001, 0]), &[], 0, &cfg)), "off the paper");
    assert_eq!(check_op(&line(0, 0, vec![10_000, 0]), &[], 0, &cfg).unwrap(), 1, "the edge is on the paper");
    assert!(refused(check_op(&line(0, 0, vec![0; 514]), &[], 0, &cfg)), "too many values");
    assert_eq!(check_op(&line(0, 0, vec![0; 512]), &[], 0, &cfg).unwrap(), 256);
    assert!(refused(check_op(&DrawOp::More { points: vec![1] }, &[line(0, 0, ok)], 0, &cfg)), "more is checked too");
}

#[test]
fn more_only_continues_a_line() {
    let cfg = KritzeleiConfig::default();
    let more = DrawOp::More { points: vec![5, 6] };
    let l = line(0, 0, vec![1, 2]);
    assert_eq!(check_op(&more, &[], 0, &cfg), Err(ActionError::Ignored));
    assert_eq!(check_op(&more, &[l.clone(), DrawOp::Undo], 0, &cfg), Err(ActionError::Ignored));
    assert_eq!(check_op(&more, &[l.clone(), DrawOp::Clear], 0, &cfg), Err(ActionError::Ignored));
    assert_eq!(check_op(&more, std::slice::from_ref(&l), 0, &cfg), Ok(1));
    assert_eq!(check_op(&more, &[l, more.clone()], 0, &cfg), Ok(1));
}

#[test]
fn a_turn_holds_at_most_max_points() {
    let cfg = KritzeleiConfig::default();
    let two = line(0, 0, vec![1, 2, 3, 4]);
    assert_eq!(check_op(&two, &[], 19_999, &cfg), Err(ActionError::Ignored));
    assert_eq!(check_op(&two, &[], 19_998, &cfg), Ok(2));
}

#[test]
fn undo_and_clear_always_pass() {
    let cfg = KritzeleiConfig::default();
    assert_eq!(check_op(&DrawOp::Undo, &[], 20_000, &cfg), Ok(0));
    assert_eq!(check_op(&DrawOp::Clear, &[], 20_000, &cfg), Ok(0));
}
