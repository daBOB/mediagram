use super::board::{field, hotels_at, Field, FIELDS, HOTELS, RATHAUS};
use super::play::play;
use super::rules::{permit_for, Permit, Rules, Table};

fn table(players: usize) -> Table {
    Table::new(players, Rules::default())
}

#[test]
fn board_has_the_designed_field_counts() {
    let count = |k: Field| (0..FIELDS).filter(|&f| field(f) == k).count();
    assert_eq!((count(Field::Bank), count(Field::Rathaus), count(Field::Plot), count(Field::Site)), (1, 1, 8, 8));
    assert_eq!((count(Field::FreeEntrance), count(Field::FreeBuild), count(Field::Allee)), (2, 2, 18));
    assert_eq!(field(RATHAUS), Field::Rathaus);
}

#[test]
fn every_hotel_touches_a_plot_and_a_building_site_on_a_straight() {
    for h in &HOTELS {
        let kinds: Vec<Field> = h.frontage_fields().map(field).collect();
        assert!(kinds.contains(&Field::Plot) && kinds.contains(&Field::Site), "{}", h.name);
        assert!(h.frontage_fields().all(|f| (4..=19).contains(&f) || (24..=39).contains(&f)), "{}", h.name);
    }
    // a straight field borders one hotel per side, never more
    assert!((0..FIELDS).all(|f| hotels_at(f).count() <= 2));
}

#[test]
fn passing_the_bank_pays_until_only_two_players_are_left() {
    let mut t = table(3);
    t.seats[0].field = 38;
    assert!(t.walk(0, 4).bank_paid);
    assert_eq!(t.seats[0].cash, 14_000);
    t.seats[2].out = true;
    t.seats[1].field = 38;
    assert!(!t.walk(1, 4).bank_paid);
    assert_eq!(t.seats[1].cash, 12_000);
    // a game that starts with two players is not "two left": the Bank pays
    let mut two = table(2);
    two.seats[0].field = 38;
    assert!(two.walk(0, 4).bank_paid);
}

#[test]
fn a_pawn_moves_on_past_an_occupied_field_and_notes_the_rathaus() {
    let mut t = table(2);
    t.seats[1].field = 21;
    t.seats[0].field = 18;
    let walk = t.walk(0, 3);
    assert_eq!(walk.to, 22);
    assert!(walk.passed_rathaus);
}

#[test]
fn a_forced_purchase_pays_the_owner_half_the_land_price() {
    let mut t = table(2);
    t.buy(1, 1); // Kirschblüte, land 2 000
    t.forced_buy(0, 1);
    assert_eq!(t.estate(1).owner, Some(0));
    assert_eq!((t.seats[0].cash, t.seats[1].cash), (11_000, 11_000));
}

#[test]
fn the_permit_die_prices_the_named_stages() {
    let t = table(2);
    assert_eq!([1, 2, 3, 4, 5, 6].map(permit_for), [Permit::Approved, Permit::Approved, Permit::Approved, Permit::Rejected, Permit::Free, Permit::Double]);
    // Kirschblüte: 2 000 + 1 500
    assert_eq!(t.stage_price(1, 2, Permit::Approved), 3_500);
    assert_eq!(t.stage_price(1, 2, Permit::Free), 1_500);
    assert_eq!(t.stage_price(1, 2, Permit::Double), 7_000);
}

#[test]
fn the_leisure_wing_is_outside_the_permit_and_comes_last() {
    let mut t = table(2);
    t.buy(0, 0); // Lagune, three stages
    assert_eq!(t.permit_stages_left(0), 2);
    assert!(t.build(0, 0, 2, 0));
    assert_eq!(t.permit_stages_left(0), 0);
    assert_eq!(t.leisure_cost(0), Some(900));
    assert!(!t.build(0, 0, 1, 900));
    assert_eq!(t.leisure_cost(0), None);
}

#[test]
fn an_entrance_blocks_the_field_for_the_hotel_opposite() {
    let mut t = table(2);
    t.buy(0, 0); // Lagune, sea side 4–8
    t.buy(1, 6); // Maharadscha, park 4–11
    assert!(t.free_frontage(0).is_empty(), "no entrance before the main building");
    t.build(0, 0, 1, 0);
    t.build(1, 6, 1, 0);
    t.place_entrance(0, 0, 5, 300);
    assert!(!t.free_frontage(6).contains(&5));
    assert_eq!(t.host_at(1, 5), Some((0, 0)));
    assert_eq!(t.host_at(0, 5), None, "no one pays at their own entrance");
    assert_eq!(t.rate(0), 75);
}

#[test]
fn a_released_hotel_loses_its_entrances_and_net_worth_counts_stages() {
    let mut t = table(2);
    t.buy(0, 0);
    t.build(0, 0, 1, 1_200);
    t.place_entrance(0, 0, 4, 0);
    assert_eq!(t.net_worth(0), 12_000 - 1_200 - 1_200 + 1_200 + 1_200);
    t.eliminate(0);
    assert_eq!(t.estate(0).owner, None);
    assert!(t.entrances.iter().all(Option::is_none));
}

#[test]
fn rounds_scale_with_players_within_the_limits() {
    let r = Rules::default();
    assert_eq!((r.rounds(2), r.rounds(4), r.rounds(8)), (120, 70, 35));
}

#[test]
fn a_simulated_game_is_deterministic_and_ends() {
    let (a, b) = (play(7, 4, Rules::default()), play(7, 4, Rules::default()));
    assert_eq!((a.minutes, a.winner, a.rounds), (b.minutes, b.winner, b.rounds));
    assert!(a.rounds >= 1 && a.minutes > 0.0);
}
