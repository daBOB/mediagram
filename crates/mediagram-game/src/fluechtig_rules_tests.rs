use super::*;
use crate::fluechtig_map::CityKind;
use Ticket::{Bus, Schatten, Taxi, Ubahn};

/// The small city: these tests know its fields.
fn small() -> &'static City {
    CityKind::Small.city()
}

fn chase(rules: Rules, fugitive: Field, hunters: &[Field]) -> Chase {
    Chase::new(rules, fugitive, hunters)
}

/// Plays a list of (to, ticket) moves for whoever is on the move.
fn play_all(c: &mut Chase, moves: &[(Field, Ticket)]) {
    for &(to, ticket) in moves {
        c.play(Move { to, ticket }).unwrap_or_else(|e| panic!("{to} {ticket:?}: {e} (turn {:?})", c.turn));
    }
}

fn log(c: &Chase) -> Vec<(Vec<Ticket>, Option<Field>)> {
    c.log.iter().map(|e| (e.tickets.clone(), e.seen_at)).collect()
}

fn hideouts(c: &Chase) -> Vec<Field> {
    c.hideouts.iter().copied().collect()
}

#[test]
fn the_city_is_one_connected_map_of_three_transports_and_a_ferry() {
    assert_eq!(small().spots.len(), 48);
    let count = |k| small().links.iter().filter(|l| l.kind == k).count();
    assert_eq!((count(Taxi), count(Bus), count(Ubahn), count(Schatten)), (77, 14, 4, 3));
    let mut seen = BTreeSet::from([1]);
    let mut todo = vec![1];
    while let Some(f) = todo.pop() {
        for &(to, _) in small().exits(f) {
            if seen.insert(to) {
                todo.push(to);
            }
        }
    }
    assert_eq!(seen.len(), 48);
}

#[test]
fn only_a_shadow_ride_boards_the_ferry() {
    // 27 is a pier: the ferry runs to 17 and 22
    let c = chase(Rules::small(), 27, &[3, 44]);
    let to_17: Vec<Ticket> = c.moves().iter().filter(|m| m.to == 17).map(|m| m.ticket).collect();
    assert_eq!(to_17, vec![Schatten], "the ferry is offered once, as a shadow ride");
    let no_shadow = Tickets { schatten: 0, ..Rules::small().fugitive_tickets };
    let c = chase(Rules { fugitive_tickets: no_shadow, ..Rules::small() }, 27, &[3, 44]);
    assert!(c.moves().iter().all(|m| m.to != 17), "without a shadow ride the ferry stays shut");
    // the room reckons with the ferry after a shadow ride, and only then
    let mut c = chase(Rules::small(), 27, &[3, 44]);
    play_all(&mut c, &[(26, Taxi)]);
    assert!(!hideouts(&c).contains(&17));
    let mut c = chase(Rules::small(), 27, &[3, 44]);
    play_all(&mut c, &[(17, Schatten)]);
    assert!(hideouts(&c).contains(&17));
    // a hunter on a pier stays ashore
    let mut c = chase(Rules::small(), 10, &[17, 44]);
    play_all(&mut c, &[(11, Taxi)]);
    assert!(c.moves().iter().all(|m| m.to != 27));
}

#[test]
fn a_reveal_shrinks_the_hideouts_to_one_field() {
    let mut c = chase(Rules::small(), 12, &[3, 44]);
    assert_eq!(hideouts(&c), vec![6, 10, 12, 24, 27, 33, 39, 46]);
    play_all(&mut c, &[(13, Taxi), (4, Taxi), (36, Taxi), (15, Bus), (21, Bus), (38, Bus), (16, Taxi)]);
    assert_eq!(hideouts(&c), vec![16]);
    play_all(&mut c, &[(7, Ubahn), (39, Taxi)]);
    assert_eq!((c.round, c.turn), (4, Turn::Fugitive));
    assert_eq!(log(&c), vec![(vec![Taxi], None), (vec![Bus], None), (vec![Taxi], Some(16))]);
    assert_eq!(hideouts(&c), vec![16]);
}

#[test]
fn a_shadow_ride_spreads_over_every_transport() {
    let rules = Rules { reveal_rounds: vec![2, 8, 13, 18], ..Rules::small() };
    let mut c = chase(rules, 12, &[3, 44]);
    play_all(&mut c, &[(13, Taxi), (4, Taxi), (45, Taxi), (21, Taxi), (12, Taxi), (37, Taxi), (42, Schatten)]);
    // all of 21's neighbours but 12 and 37, where hunters stand
    assert_eq!(hideouts(&c), vec![4, 7, 9, 13, 22, 29, 42, 47]);
    play_all(&mut c, &[(13, Taxi), (29, Taxi)]);
    assert_eq!(hideouts(&c), vec![4, 7, 9, 22, 42, 47]);
    assert_eq!(log(&c)[2], (vec![Schatten], None));
}

#[test]
fn a_double_move_on_a_reveal_round_shows_only_where_it_ends() {
    let mut c = chase(Rules::small(), 12, &[3, 44]);
    play_all(&mut c, &[(13, Taxi), (4, Taxi), (45, Taxi), (14, Taxi), (5, Taxi), (46, Taxi)]);
    c.announce_double().unwrap();
    assert!(c.announce_double().is_err(), "one double move at a time");
    play_all(&mut c, &[(23, Taxi)]);
    assert_eq!(c.turn, Turn::Fugitive, "the second step is still the fugitive's");
    play_all(&mut c, &[(31, Bus)]);
    assert_eq!(log(&c)[2], (vec![Taxi, Bus], Some(31)));
    assert_eq!(c.route(), vec![12, 13, 14, 23, 31]);
    assert_eq!(c.fugitive.tickets.doppel, 0);
}

#[test]
fn a_double_move_only_takes_first_steps_that_leave_a_second() {
    let one_taxi = Tickets { taxi: 1, doppel: 1, ..Tickets::default() };
    let c = chase(Rules { fugitive_tickets: one_taxi, ..Rules::small() }, 10, &[3, 44]);
    assert!(!c.can_double(), "one taxi ticket cannot make two steps");
    // 10's exits are all taxi; with two tickets every first step leaves a second
    let two_taxis = Tickets { taxi: 2, doppel: 1, ..Tickets::default() };
    let mut c = chase(Rules { fugitive_tickets: two_taxis, ..Rules::small() }, 10, &[3, 44]);
    c.announce_double().unwrap();
    assert_eq!(c.moves().len(), small().exits(10).len());
    // one taxi and one bus from 27: a taxi to 28 or 35 strands him (no bus there), to 26 or 36 does not
    let taxi_and_bus = Tickets { taxi: 1, bus: 1, doppel: 1, ..Tickets::default() };
    let mut c = chase(Rules { fugitive_tickets: taxi_and_bus, ..Rules::small() }, 27, &[3, 14]);
    c.announce_double().unwrap();
    let firsts: Vec<Field> = c.moves().iter().map(|m| m.to).collect();
    assert_eq!(firsts, vec![26, 36]);
}

#[test]
fn a_hunter_on_the_fugitive_field_catches_him() {
    let mut c = chase(Rules::small(), 10, &[3, 19]);
    play_all(&mut c, &[(9, Taxi), (2, Taxi), (18, Taxi), (1, Taxi), (1, Taxi)]);
    assert_eq!(c.winner(), Some(Side::Hunters));
    assert!(c.news.last().unwrap().contains("erwischt"));
}

#[test]
fn a_fugitive_with_nowhere_to_go_loses() {
    let mut c = chase(Rules::small(), 10, &[3, 17, 19]);
    play_all(&mut c, &[(9, Taxi), (2, Taxi), (18, Taxi), (11, Taxi), (1, Taxi), (10, Taxi), (2, Bus), (9, Bus)]);
    assert_eq!((c.winner(), c.round), (Some(Side::Hunters), 3));
    assert!(c.news.last().unwrap().contains("eingekesselt"));
}

#[test]
fn hunters_out_of_tickets_pass_and_then_lose() {
    let rules = Rules { hunter_tickets: Tickets { taxi: 1, bus: 1, ..Tickets::default() }, ..Rules::small() };
    let mut c = chase(rules, 12, &[3, 44]);
    play_all(&mut c, &[(13, Taxi), (4, Taxi), (45, Taxi), (14, Taxi), (21, Bus)]);
    assert!(c.news.iter().any(|n| n == "Jäger 2 kann nicht fahren und setzt aus."));
    assert_eq!((c.round, c.turn), (3, Turn::Fugitive));
    play_all(&mut c, &[(15, Taxi)]);
    assert_eq!(c.winner(), Some(Side::Fugitive));
}

#[test]
fn surviving_the_last_round_wins() {
    let rules = Rules { rounds: 2, reveal_rounds: vec![2], ..Rules::small() };
    let mut c = chase(rules, 12, &[3, 44]);
    play_all(&mut c, &[(13, Taxi), (4, Taxi), (45, Taxi), (14, Taxi), (5, Taxi)]);
    assert_eq!(c.winner(), None);
    play_all(&mut c, &[(46, Taxi)]);
    assert_eq!(c.winner(), Some(Side::Fugitive));
}

#[test]
fn illegal_moves_are_refused_and_change_nothing() {
    let mut c = chase(Rules::small(), 12, &[3, 13, 14, 44]);
    let before = format!("{c:?}");
    for (to, ticket) in [(13, Taxi), (4, Bus), (21, Ubahn)] {
        assert!(c.play(Move { to, ticket }).is_err(), "fugitive {to} {ticket:?}");
    }
    assert_eq!(format!("{c:?}"), before);
    play_all(&mut c, &[(20, Taxi)]);
    assert!(c.play(Move { to: 2, ticket: Schatten }).is_err(), "hunters have no shadow rides");
    assert!(c.announce_double().is_err(), "not the fugitive's turn");
    play_all(&mut c, &[(4, Taxi)]);
    assert!(c.play(Move { to: 14, ticket: Taxi }).is_err(), "another hunter stands there");
    play_all(&mut c, &[(12, Taxi)]);
}

#[test]
fn spent_hunter_tickets_go_to_the_fugitive() {
    let mut c = chase(Rules::small(), 12, &[3, 44]);
    play_all(&mut c, &[(13, Taxi), (4, Taxi)]);
    assert_eq!(c.fugitive.tickets.taxi, 4);
    assert_eq!(c.hunters[0].tickets.taxi, 9);
}

#[test]
fn hideouts_always_hold_the_real_field_through_a_whole_game() {
    let big = CityKind::Big.city();
    let games = [(Rules::small(), 27, vec![3, 14, 19, 44]), (Rules::big(), big.fugitive_starts[0], big.hunter_starts[..5].to_vec())];
    for (rules, start, hunters) in games {
        let mut c = chase(rules, start, &hunters);
        let mut step = 0;
        while c.winner().is_none() {
            assert!(c.hideouts.contains(&c.fugitive.at), "round {}", c.round);
            let moves = c.moves();
            c.play(moves[(step * 7) % moves.len()]).unwrap();
            step += 1;
            assert!(step < 400, "the chase must end");
        }
    }
}

#[test]
fn the_big_city_has_the_classic_totals_and_hangs_together() {
    let city = CityKind::Big.city();
    assert_eq!(city.spots.len(), 199);
    let count = |k| city.links.iter().filter(|l| l.kind == k).count();
    assert_eq!((count(Taxi), count(Bus), count(Ubahn), count(Schatten)), (346, 99, 20, 3));
    let stops = |k| city.links.iter().filter(|l| l.kind == k).flat_map(|l| [l.a, l.b]).collect::<BTreeSet<_>>().len();
    assert_eq!((stops(Bus), stops(Ubahn), stops(Schatten)), (62, 14, 4));
    // a taxi alone reaches every field
    let mut seen = BTreeSet::from([1]);
    let mut todo = vec![1];
    while let Some(f) = todo.pop() {
        for &(to, kind) in city.exits(f) {
            if kind == Taxi && seen.insert(to) {
                todo.push(to);
            }
        }
    }
    assert_eq!(seen.len(), 199);
    // no link twice, none from a field to itself
    let pairs: BTreeSet<_> = city.links.iter().map(|l| (l.a.min(l.b), l.a.max(l.b), l.kind as u8)).collect();
    assert_eq!(pairs.len(), city.links.len());
    assert!(city.links.iter().all(|l| l.a != l.b && l.a <= 199 && l.b <= 199));
    // starts for five hunters and the fugitive, no field in both lists
    let starts: BTreeSet<_> = city.hunter_starts.iter().chain(&city.fugitive_starts).collect();
    assert_eq!((city.hunter_starts.len(), city.fugitive_starts.len(), starts.len()), (9, 9, 18));
    // the river runs past both edges of the plan
    assert!(city.river.first().unwrap().0 < 0 && city.river.last().unwrap().0 > city.size.0);
}
