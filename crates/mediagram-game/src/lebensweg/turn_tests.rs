use super::cards::{Perk, CAREERS, HOUSES, SALARIES};
use super::rules::{Deck, Home, Rules};
use super::turn::{Game, Opt, Question};

fn game(players: usize) -> Game {
    Game::new(players, Rules::default(), 7)
}

fn pick(g: &mut Game, opt: Opt) {
    let i = g.ask().unwrap().options.iter().position(|&o| o == opt).unwrap_or_else(|| panic!("{opt:?} not in {:?}", g.ask()));
    g.answer(i);
}

fn spin(g: &mut Game, n: u8) {
    g.table.queued.push_back(Some(n));
    pick(g, Opt::Spin);
}

fn field(g: &Game, name: &str) -> usize {
    g.table.board.fields.iter().position(|f| f.name == name).unwrap()
}

fn career_with(perk: Perk) -> usize {
    CAREERS.iter().position(|c| c.perk == perk).unwrap()
}

#[test]
fn ausbildung_draws_a_career_at_start_and_studium_borrows_the_tuition() {
    let mut g = game(2);
    spin(&mut g, 1);
    assert_eq!(g.ask().unwrap().question, Question::Fork);
    pick(&mut g, Opt::Branch(1));
    let s = &g.table.seats[0];
    assert!(s.career.is_some_and(|c| !CAREERS[c].degree) && s.salary.is_some());
    assert_eq!(s.cash, 13_000 + SALARIES[s.salary.unwrap()].pay, "start money × 1.3, first field is a payday");
    let mut g = game(1);
    spin(&mut g, 1);
    pick(&mut g, Opt::Branch(0));
    let s = &g.table.seats[0];
    assert!(s.degree && s.career.is_none());
    assert_eq!((s.cash, s.loans), (13_000 - 52_000 + 40_000 + 20_000, 2), "tuition 40,000 × 1.3 in loans of 20,000, then the scholarship field");
}

#[test]
fn berufswahl_stops_students_for_a_career_and_a_salary_then_they_spin_again() {
    let mut g = game(1);
    spin(&mut g, 10);
    pick(&mut g, Opt::Branch(0));
    spin(&mut g, 10);
    assert_eq!(g.table.seats[0].field, field(&g, "Berufswahl"));
    assert_eq!((g.ask().unwrap().question, g.ask().unwrap().options.len()), (Question::Career, 3));
    g.answer(0);
    assert_eq!(g.ask().unwrap().question, Question::Salary);
    g.answer(0);
    assert_eq!(g.ask().unwrap().options, [Opt::Spin], "spin again, no purchases");
}

#[test]
fn ausbildung_players_drive_through_berufswahl() {
    let mut g = game(1);
    spin(&mut g, 1);
    pick(&mut g, Opt::Branch(1));
    spin(&mut g, 6);
    assert!(g.table.seats[0].field > field(&g, "Berufswahl"));
}

fn standing_on(g: &mut Game, space: u8) {
    g.table.seats[0].field = g.table.board.fields.iter().position(|f| f.space == Some(space)).unwrap();
    g.land(0);
}

#[test]
fn career_fields_pay_the_careers_holder_and_nobody_pays_their_own() {
    let mut g = game(2);
    g.table.seats[1].career = Some(career_with(Perk::Medicine));
    standing_on(&mut g, 19);
    assert_eq!((g.table.seats[0].cash, g.table.seats[1].cash), (8_000, 18_000));
    g.table.seats[0].career = Some(career_with(Perk::Medicine));
    g.table.seats[1].career = None;
    standing_on(&mut g, 19);
    assert_eq!(g.table.seats[0].cash, 8_000);
}

#[test]
fn taxes_go_to_buchhaltung_and_insurance_waives_damage() {
    let mut g = game(2);
    g.table.seats[0].salary = Some(8);
    g.table.seats[1].career = Some(career_with(Perk::Taxes));
    g.table.seats[0].cash = 100_000;
    standing_on(&mut g, 33);
    assert_eq!((g.table.seats[0].cash, g.table.seats[1].cash), (55_000, 58_000));
    g.table.seats[0].car_insured = true;
    standing_on(&mut g, 24);
    assert_eq!(g.table.seats[0].cash, 55_000);
    g.table.seats[0].house = Some(0);
    standing_on(&mut g, 105);
    assert_eq!(g.table.seats[0].cash, 5_000);
}

#[test]
fn once_the_pile_is_empty_a_life_field_takes_a_token_from_someone() {
    let mut g = game(2);
    g.table.pile.clear();
    g.table.seats[1].tokens = vec![3];
    g.table.seats[0].field = g.table.board.fields.iter().position(|f| f.space == Some(3)).unwrap() - 1;
    spin(&mut g, 1);
    assert_eq!(g.ask().unwrap().question, Question::Steal);
    pick(&mut g, Opt::Player(1));
    assert_eq!((g.table.seats[0].tokens.clone(), g.table.seats[1].tokens.len()), (vec![3], 0));
}

#[test]
fn spins_pay_stocks_kunst_on_a_1_and_polizei_on_a_10() {
    let mut g = game(2);
    g.table.seats[1].stocks = vec![1];
    g.table.seats[1].career = Some(career_with(Perk::Art));
    g.spun(0, 1);
    assert_eq!((g.table.seats[0].cash, g.table.seats[1].cash), (3_000, 33_000));
    g.table.seats[1].career = Some(career_with(Perk::Police));
    g.spun(0, 10);
    assert_eq!(g.table.seats[1].cash, 38_000);
}

#[test]
fn hauskauf_charges_the_price_times_1_3_and_offers_insurance() {
    let mut g = game(1);
    g.table.seats[0].field = field(&g, "Hauskauf") - 1;
    g.table.seats[0].career = Some(0);
    g.table.seats[0].cash = 1_000_000;
    let before = g.table.seats[0].cash;
    g.table.queued.push_back(Some(1));
    pick(&mut g, Opt::Spin);
    let h = g.table.seats[0].house.unwrap();
    assert_eq!(before - g.table.seats[0].cash, Rules::default().once(HOUSES[h].price));
    pick(&mut g, Opt::Yes);
    assert!(g.table.seats[0].home_insured);
}

#[test]
fn retiring_repays_loans_with_interest_and_the_villas_richest_takes_the_set_aside_tokens() {
    let mut g = game(2);
    for (p, cash) in [(0, 500_000), (1, 400_000)] {
        g.table.seats[p].cash = cash;
        g.table.seats[p].field = field(&g, "Villa am See") - 1;
    }
    g.table.seats[0].loans = 2;
    spin(&mut g, 3);
    pick(&mut g, Opt::Branch(0));
    assert_eq!((g.table.seats[0].cash, g.table.seats[0].retired), (450_000, Some(Home::Villa)));
    spin(&mut g, 3);
    pick(&mut g, Opt::Branch(1));
    assert!(g.over);
    assert_eq!(g.table.seats[0].tokens.len(), 5, "4 × 1.3");
    assert_eq!(g.table.seats[1].tokens.len(), 1, "Landhaus: one token");
}

#[test]
fn a_whole_seeded_game_ends_with_everyone_retired_and_no_debts() {
    for seed in 0..20 {
        let mut g = Game::new(4, Rules::default(), seed);
        let mut answers = 0;
        while !g.over {
            g.answer(0);
            answers += 1;
            assert!(answers < 5_000, "seed {seed} does not end");
        }
        assert!(g.table.seats.iter().all(|s| s.retired.is_some() && s.loans == 0 && s.career.is_none()));
        assert!(g.table.estate.len() <= 1, "seed {seed}: estate tokens left {:?}", g.table.estate);
        let _ = Deck::Career;
    }
}

#[test]
fn night_school_costs_20_000_opens_degree_careers_and_lets_you_keep_your_own() {
    let mut g = game(1);
    g.table.seats[0].field = g.table.board.fields.iter().position(|f| f.space == Some(31)).unwrap() - 1;
    g.table.seats[0].career = Some(career_with(Perk::Art));
    g.table.seats[0].cash = 50_000;
    spin(&mut g, 1);
    pick(&mut g, Opt::Yes);
    assert!(g.table.seats[0].degree);
    assert_eq!(g.ask().unwrap().question, Question::Career);
    pick(&mut g, Opt::Keep);
    assert_eq!(g.table.seats[0].career, Some(career_with(Perk::Art)));
    assert_eq!(g.table.seats[0].cash, 30_000);
}

#[test]
fn trading_salaries_swaps_the_cards() {
    let mut g = game(2);
    g.table.seats[0].salary = Some(0);
    g.table.seats[1].salary = Some(8);
    g.table.seats[0].field = g.table.board.fields.iter().position(|f| f.space == Some(50)).unwrap() - 1;
    spin(&mut g, 1);
    pick(&mut g, Opt::Player(1));
    assert_eq!((g.table.seats[0].salary, g.table.seats[1].salary), (Some(8), Some(0)));
}

#[test]
fn spin_again_only_when_not_alone_in_the_lead() {
    for (mine, theirs, again) in [(10_000, 50_000, true), (90_000, 50_000, false)] {
        let mut g = game(2);
        g.table.seats[1].cash = theirs;
        g.table.seats[0].cash = mine;
        g.table.seats[0].field = g.table.board.fields.iter().position(|f| f.space == Some(74)).unwrap() - 1;
        spin(&mut g, 1);
        assert_eq!(g.ask().unwrap().player == 0, again);
    }
}

#[test]
fn the_wedding_brings_a_partner_and_a_token_and_another_spin() {
    let mut g = game(1);
    g.table.seats[0].field = field(&g, "Hochzeit") - 1;
    g.table.seats[0].career = Some(0);
    spin(&mut g, 5);
    let s = &g.table.seats[0];
    assert!(s.partner && s.tokens.len() == 1);
    assert_eq!(g.ask().unwrap().options, [Opt::Spin]);
}

#[test]
fn a_missed_turn_passes_to_the_next_player() {
    let mut g = game(2);
    g.table.seats[1].miss_turn = true;
    spin(&mut g, 1);
    pick(&mut g, Opt::Branch(1));
    assert_eq!(g.ask().unwrap().player, 0);
    assert!(!g.table.seats[1].miss_turn);
}

#[test]
fn it_beratung_earns_when_the_wheel_stops_between_numbers_and_vertrieb_on_purchases() {
    let mut g = game(3);
    g.table.seats[1].career = Some(career_with(Perk::Repair));
    g.table.seats[2].career = Some(career_with(Perk::Sales));
    g.table.queued.push_back(None);
    spin(&mut g, 1);
    assert_eq!(g.table.seats[1].cash, 13_000 + 50_000);
    g.buy(0, Opt::CarInsurance);
    assert_eq!(g.table.seats[2].cash, 13_000 + 5_000);
}
