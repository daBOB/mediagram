use super::*;

const BONUS: u32 = 500;

#[test]
fn points_follow_how_close_a_guess_is_either_way() {
    assert_eq!(points(1800, 2000, BONUS), 900, "10 % under");
    assert_eq!(points(2200, 2000, BONUS), 900, "10 % over scores the same");
    assert_eq!(points(1800, 1999, BONUS), 900, "rounded to the nearest point");
    assert_eq!(points(2999, 2999, BONUS), 1500, "to the cent: 1000 and the bonus");
    assert_eq!(points(5998, 2999, BONUS), 0, "twice the price is nothing");
    assert_eq!(points(9_000_000, 2999, BONUS), 0, "never below nothing");
    assert_eq!(points(1, 50_000, BONUS), 0, "a cent for 500 € is as good as no guess");
}

#[test]
fn the_closest_guess_either_side_is_marked_and_equal_distances_share_it() {
    let rows = round_rows(3990, &[(1, Some(3590)), (2, Some(4390)), (3, Some(6000)), (4, None)], BONUS);
    let closest: Vec<PlayerId> = rows.iter().filter(|r| r.closest).map(|r| r.player).collect();
    assert_eq!(closest, [1, 2]);
    assert_eq!(rows.last().unwrap(), &PriceRow { player: 4, cents: None, points: 0, closest: false, exact: false }, "no guess: last, nothing");
    assert_eq!(rows.iter().map(|r| r.player).collect::<Vec<_>>(), [1, 2, 3, 4], "closest first");
}

#[test]
fn a_guess_to_the_cent_is_exact() {
    let rows = round_rows(999, &[(1, Some(999)), (2, Some(1000))], BONUS);
    assert_eq!((rows[0].player, rows[0].exact, rows[0].points), (1, true, 1500));
    assert!(!rows[1].exact && !rows[1].closest);
}

#[test]
fn nobody_guessing_has_no_closest() {
    let rows = round_rows(999, &[(1, None), (2, None)], BONUS);
    assert!(rows.iter().all(|r| !r.closest && r.points == 0));
}

#[test]
fn steady_beats_lucky_over_five_articles() {
    // The rules prototype's walkthrough: Ana always about 10 % under, Ben and Cem now spot on, now far off.
    let prices = [1999, 8900, 14900, 999, 39900];
    let guesses: [(PlayerId, [u32; 5]); 3] = [
        (1, [1800, 8000, 13500, 900, 36000]),
        (2, [2050, 8800, 30000, 950, 120000]),
        (3, [3000, 13500, 15100, 2500, 42000]),
    ];
    let totals: Vec<(PlayerId, u32)> =
        guesses.iter().map(|(p, g)| (*p, prices.iter().zip(g).map(|(&price, &g)| points(g, price, BONUS)).sum())).collect();
    assert_eq!(totals, [(1, 4508), (2, 2914), (3, 2916)]);
    let order: Vec<(PlayerId, u8)> = standings(&totals).iter().map(|s| (s.player, s.place)).collect();
    assert_eq!(order, [(1, 1), (3, 2), (2, 3)]);
}

#[test]
fn equal_totals_share_a_place() {
    let places: Vec<u8> = standings(&[(1, 100), (2, 300), (3, 100)]).iter().map(|s| s.place).collect();
    assert_eq!(places, [1, 2, 2]);
}

#[test]
fn the_price_check_reads_as_a_short_date() {
    assert_eq!(checked_label("2026-11-12T19:14:54.484Z"), "12.11.");
    assert_eq!(checked_label("2026-01-05"), "5.1.");
    assert_eq!(checked_label("gestern"), "");
}
