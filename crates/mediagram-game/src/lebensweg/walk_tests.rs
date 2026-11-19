use super::board::{Board, Kind, MORE};
use super::walk::{walk, Halt};

fn first(b: &Board, name: &str) -> usize {
    b.fields.iter().position(|f| f.name == name).unwrap()
}

#[test]
fn start_is_a_fork_so_the_first_move_needs_a_choice() {
    let b = Board::new(MORE);
    let w = walk(&b, b.start(), 7, None, None);
    assert_eq!((w.to, w.halt, w.path.len()), (b.start(), Halt::Fork { left: 7 }, 0));
}

#[test]
fn the_short_path_runs_into_the_career_stop_whatever_was_spun() {
    let b = Board::new(MORE);
    let w = walk(&b, b.start(), 10, Some(1), None);
    assert_eq!(b.fields[w.to].name, "Berufswahl");
    assert_eq!((w.halt, w.path.len()), (Halt::Stop, 5), "four Ausbildung fields, then the STOP");
}

#[test]
fn a_payday_pays_when_passed_and_when_landed_on() {
    let b = Board::new(1.0);
    let from = first(&b, "Beruf") - 1;
    let to_payday = (from + 1..).find(|&f| b.fields[f].kind == Kind::Payday).unwrap() - from;
    assert_eq!(walk(&b, from, to_payday as u8, None, None).paydays, 1, "landed");
    assert_eq!(walk(&b, from, to_payday as u8 + 1, None, None).paydays, 1, "passed");
    assert_eq!(walk(&b, from, to_payday as u8 - 1, None, None).paydays, 0);
}

#[test]
fn a_fork_on_the_way_halts_with_the_steps_left_and_the_branch_finishes_the_move() {
    let b = Board::new(MORE);
    let fork = first(&b, "Familienweg") - 1;
    let w = walk(&b, fork - 2, 6, None, None);
    assert_eq!((w.to, w.halt), (fork, Halt::Fork { left: 4 }));
    let on = walk(&b, fork, 4, Some(1), None);
    assert_eq!((b.fields[on.to].name, on.halt), ("Abenteuerweg", Halt::Landed));
}

#[test]
fn the_last_fork_is_the_retirement_choice_and_a_home_ends_the_race() {
    let b = Board::new(MORE);
    let last = first(&b, "Villa am See") - 1;
    let w = walk(&b, last, 9, Some(1), None);
    assert_eq!((b.fields[w.to].name, w.halt, w.path.len()), ("Landhaus", Halt::Home, 1));
}
