use super::board::{Board, Kind, MORE};

fn spaces(board: &Board) -> Vec<u8> {
    board.fields.iter().filter_map(|f| f.space).collect()
}

#[test]
fn at_factor_one_the_board_is_the_originals_spaces_in_order() {
    let b = Board::new(1.0);
    let original: Vec<u8> = (1..=146).filter(|s| ![11, 25, 36, 81].contains(s)).collect();
    assert_eq!(spaces(&b), original);
    assert_eq!(b.fields.len(), 148, "142 spaces, 4 STOPs (Start, Berufswahl, Hochzeit, Hauskauf), 2 homes");
}

#[test]
fn stretched_every_space_comes_once_or_twice_in_order() {
    let b = Board::new(MORE);
    assert_eq!(b.fields.len(), 192);
    let s = spaces(&b);
    assert!(s.windows(2).all(|w| w[0] <= w[1]), "order kept");
    for id in (1..=146).filter(|s| ![11, 25, 36, 81].contains(s)) {
        let n = s.iter().filter(|&&x| x == id).count();
        assert!((1..=2).contains(&n), "space {id} comes {n} times");
    }
}

#[test]
fn the_field_colours_follow_the_spaces() {
    let b = Board::new(1.0);
    let count = |k: Kind| b.fields.iter().filter(|f| f.kind == k).count();
    assert_eq!((count(Kind::Payday), count(Kind::Baby), count(Kind::Life)), (21, 8, 29));
}

#[test]
fn the_path_forks_four_times_and_ends_in_two_homes() {
    let b = Board::new(MORE);
    let forks: Vec<&str> = b.fields.iter().filter(|f| f.next.len() == 2).map(|f| f.name).collect();
    assert_eq!(forks, ["Start", "Kinder", "Kreuzung", "Ruhestand naht"]);
    let ends: Vec<&str> = b.fields.iter().enumerate().filter(|(i, _)| b.is_end(*i)).map(|(_, f)| f.name).collect();
    assert_eq!(ends, ["Villa am See", "Landhaus"]);
}

#[test]
fn every_route_from_start_reaches_a_home_moving_forward() {
    let b = Board::new(MORE);
    for choices in 0..16u32 {
        let (mut at, mut fork, mut steps) = (b.start(), 0, 0);
        while !b.is_end(at) {
            let next = &b.fields[at].next;
            let pick = if next.len() == 2 { fork += 1; (choices >> (fork - 1)) as usize & 1 } else { 0 };
            assert!(next[pick] > at, "field {at} leads back");
            at = next[pick];
            steps += 1;
        }
        assert!(steps > 100, "route {choices:04b} is only {steps} steps");
    }
}

#[test]
fn stops_carry_their_age_and_the_homes_are_at_67() {
    let b = Board::new(MORE);
    let stops: Vec<(&str, Option<u8>)> = b.fields.iter().filter(|f| f.kind == Kind::Stop).map(|f| (f.name, f.age)).collect();
    assert_eq!(stops, [("Start", Some(18)), ("Berufswahl", Some(22)), ("Hochzeit", Some(27)), ("Hauskauf", Some(31)), ("Villa am See", Some(67)), ("Landhaus", Some(67))]);
}

#[test]
fn the_deck_is_the_originals_36_cards_and_25_tokens_worth_650_000() {
    use super::cards::{CAREERS, HOUSES, SALARIES, STOCKS, TOKENS};
    assert_eq!(CAREERS.len() + HOUSES.len() + SALARIES.len() + STOCKS.len(), 36);
    assert_eq!(TOKENS.iter().map(|t| t.value).sum::<i64>(), 650_000);
    assert_eq!(CAREERS.iter().filter(|c| c.degree).count(), 2);
}

#[test]
fn stretched_each_careers_fields_end_within_one_field_of_1_3_times_and_paydays_grow_1_3() {
    use super::cards::Perk;
    use super::spaces::{space, Effect};
    let amounts = |b: &Board, perk: Perk| -> Vec<i64> {
        b.fields.iter().filter_map(|f| f.space).filter_map(|id| match space(id).0 {
            Effect::Career(p, a) if p == perk => Some(a),
            _ => None,
        }).collect()
    };
    let (orig, ours) = (Board::new(1.0), Board::new(MORE));
    for perk in [Perk::Art, Perk::Sport, Perk::Repair, Perk::Medicine, Perk::Stage, Perk::Sales, Perk::Teaching] {
        let (o, s) = (amounts(&orig, perk), amounts(&ours, perk));
        let off = (s.iter().sum::<i64>() as f64 - MORE * o.iter().sum::<i64>() as f64).abs();
        // Fields are whole: Medizin's 5,000/5,000/25,000/100,000 cannot make × 1.3 exactly.
        assert!(off <= *o.iter().max().unwrap() as f64, "{perk:?}: {off} off");
    }
    let paydays = |b: &Board| b.fields.iter().filter(|f| f.kind == Kind::Payday).count() as f64;
    assert!((paydays(&ours) / paydays(&orig) - MORE).abs() < 0.1);
}

#[test]
fn no_fork_branch_gets_a_second_payday() {
    let b = Board::new(MORE);
    for name in ["Ausbildung", "Familienweg", "Abenteuerweg", "Riskanter Weg", "Sicherer Weg"] {
        assert_eq!(b.fields.iter().filter(|f| f.name == name && f.kind == Kind::Payday).count(), 1, "{name}");
    }
}
