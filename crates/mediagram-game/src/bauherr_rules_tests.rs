use super::*;

fn table() -> Table {
    Table::new(&[0, 1])
}

/// Seat `s` owns `plot` with `levels` stages built.
fn own(t: &mut Table, s: usize, plot: u8, levels: u8) {
    t.estates[usize::from(plot)] = Estate { owner: Some(s), levels };
}

#[test]
fn walking_over_or_onto_the_bank_pays_and_elsewhere_does_not() {
    let mut t = table();
    t.seats[0].field = 34;
    assert_eq!(t.walk(0, 4), (34, 2, true));
    assert_eq!(t.seats[0].cash, START_CASH + BANK_BONUS);
    t.seats[1].field = 30;
    assert_eq!(t.walk(1, 6), (30, 0, true), "landing on the bank pays");
    t.seats[1].cash = 0;
    assert_eq!(t.walk(1, 6), (0, 6, false));
    assert_eq!(t.seats[1].cash, 0);
}

#[test]
fn buying_a_plot_takes_its_land_price() {
    let mut t = table();
    t.buy(1, 3);
    assert_eq!(t.owner(3), Some(1));
    assert_eq!(t.seats[1].cash, START_CASH - 3500);
}

#[test]
fn the_permit_die_approves_three_times_in_six() {
    use BauherrPermit::*;
    let outcomes: Vec<_> = (1..=6).map(permit_for).collect();
    assert_eq!(outcomes, [Approved, Approved, Approved, Rejected, Free, Double]);
}

#[test]
fn stages_are_built_in_order_and_priced_by_permit() {
    let mut t = table();
    own(&mut t, 0, 0, 0); // Kastanienhof: 1200, 900, 900
    assert_eq!(t.level_prices(0, 0, BauherrPermit::Approved), [(1, 1200), (2, 2100), (3, 3000)]);
    assert_eq!(t.level_prices(0, 0, BauherrPermit::Free), [(1, 0), (2, 900), (3, 1800)]);
    assert_eq!(t.level_prices(0, 0, BauherrPermit::Double), [(1, 2400), (2, 4200), (3, 6000)]);
    t.build(0, 0, 2, 2100);
    assert_eq!(t.levels(0), 2);
    assert_eq!(t.next_cost(0), Some(900));
    assert_eq!(t.level_prices(0, 0, BauherrPermit::Approved), [(1, 900)], "only the leisure stage is left");
    t.build(0, 0, 1, 900);
    assert_eq!(t.next_cost(0), None);
    assert!(t.level_prices(0, 0, BauherrPermit::Approved).is_empty());
}

#[test]
fn only_stages_the_player_can_pay_are_offered() {
    let mut t = table();
    own(&mut t, 0, 0, 0);
    t.seats[0].cash = 2500;
    assert_eq!(t.level_prices(0, 0, BauherrPermit::Approved), [(1, 1200), (2, 2100)]);
    assert_eq!(t.level_prices(0, 0, BauherrPermit::Double), [(1, 2400)]);
}

#[test]
fn an_entrance_needs_a_built_hotel_and_a_free_field_each() {
    let mut t = table();
    own(&mut t, 0, 0, 0);
    assert!(t.free_fields(0).is_empty(), "no stage, no entrance");
    t.estates[0].levels = 1;
    assert_eq!(t.free_fields(0), [1, 2, 35, 34]);
    t.place_entrance(0, 0, 2, 300);
    assert_eq!(t.free_fields(0), [1, 35, 34]);
    assert_eq!(t.seats[0].cash, START_CASH - 300);
}

#[test]
fn only_other_players_entrances_make_a_guest() {
    let mut t = table();
    own(&mut t, 0, 0, 1);
    t.place_entrance(0, 0, 2, 0);
    assert_eq!(t.host_at(1, 2), Some((0, 0)));
    assert_eq!(t.host_at(0, 2), None, "the owner is no guest");
    assert_eq!(t.host_at(1, 1), None);
    assert_eq!(t.rate(0), 150);
}

#[test]
fn a_short_guest_sells_top_stages_back_at_half_price() {
    let mut t = table();
    own(&mut t, 0, 0, 3); // Kastanienhof: top stages 900 each
    own(&mut t, 0, 3, 0);
    t.seats[0].cash = 100;
    assert_eq!(t.pay(0, Some(1), 600), 600);
    assert_eq!(t.levels(0), 1, "two top stages sold for 450 each");
    assert_eq!(t.owner(3), Some(0), "plots are only sold once no stage is left");
    assert_eq!(t.seats[0].cash, 100 + 900 - 600);
    assert_eq!(t.seats[1].cash, START_CASH + 600);
    assert!(!t.seats[0].out);
}

#[test]
fn plots_follow_the_stages_and_a_sold_out_hotel_loses_its_entrances() {
    let mut t = table();
    own(&mut t, 0, 0, 1);
    own(&mut t, 0, 3, 0);
    t.place_entrance(0, 0, 2, 0);
    t.seats[0].cash = 0;
    assert_eq!(t.pay(0, None, 2000), 2000);
    assert_eq!(t.levels(0), 0);
    assert_eq!(t.entrances[2], None);
    assert_eq!(t.owner(3), None, "the dearest plot goes first");
    assert_eq!(t.owner(0), Some(0));
    assert_eq!(t.seats[0].cash, 600 + 1750 - 2000);
}

#[test]
fn a_player_who_cannot_pay_even_after_selling_everything_is_out() {
    let mut t = table();
    own(&mut t, 0, 3, 0);
    t.seats[0].cash = 0;
    assert_eq!(t.pay(0, Some(1), 5000), 1750);
    assert!(t.seats[0].out);
    assert_eq!(t.owned(0).count(), 0);
    assert_eq!(t.seats[1].cash, START_CASH + 1750);
    assert_eq!(t.active(), 1);
}

#[test]
fn net_worth_is_cash_plus_land_plus_built_stages() {
    let mut t = table();
    own(&mut t, 0, 0, 2);
    assert_eq!(t.worth(0), START_CASH + 1000 + 1200 + 900);
    assert_eq!(t.worth(1), START_CASH);
}

#[test]
fn the_round_limit_comes_from_the_turn_budget() {
    assert_eq!([2, 3, 4].map(rounds_for), [48, 32, 24]);
}

#[test]
fn standings_rank_by_worth_and_put_the_broke_last() {
    let mut t = Table::new(&[0, 1, 2]);
    t.seats[2].cash = 20_000;
    t.seats[0].cash = 0;
    t.seats[0].out = true;
    assert_eq!(t.standings(), [2, 1, 0]);
}
