//! Preisfrage's scoring, as chosen from the rules prototype: the closest guess
//! either way, scored by accuracy. Up to 1000 points by how close a guess is,
//! nothing from twice the price on, and a bonus for a guess to the cent.

use crate::protocol::{PlayerId, PriceRow, PriceStanding};

/// `round(1000 × max(0, 1 − |guess − price| ÷ price))`, plus `exact_bonus` to the cent.
/// Whole numbers throughout, rounding half up as the prototype did.
pub(crate) fn points(guess: u32, price: u32, exact_bonus: u32) -> u32 {
    let (guess, price) = (u64::from(guess), u64::from(price));
    let off = guess.abs_diff(price);
    if price == 0 || off >= price {
        return 0;
    }
    let accuracy = (2_000 * (price - off) + price) / (2 * price);
    u32::try_from(accuracy).unwrap_or(0) + if off == 0 { exact_bonus } else { 0 }
}

/// Every player's row for one article, closest first and no guess last, the
/// given order breaking ties. Equal distances either side are both closest.
pub(crate) fn round_rows(price: u32, guesses: &[(PlayerId, Option<u32>)], exact_bonus: u32) -> Vec<PriceRow> {
    let best = guesses.iter().filter_map(|&(_, g)| g.map(|g| g.abs_diff(price))).min();
    let mut rows: Vec<PriceRow> = guesses
        .iter()
        .map(|&(player, cents)| {
            let off = cents.map(|g| g.abs_diff(price));
            PriceRow {
                player,
                cents,
                points: cents.map_or(0, |g| points(g, price, exact_bonus)),
                closest: off.is_some() && off == best,
                exact: off == Some(0),
            }
        })
        .collect();
    rows.sort_by_key(|r| (r.cents.is_none(), r.cents.map(|g| g.abs_diff(price))));
    rows
}

/// Highest total first; equal totals share a place.
pub(crate) fn standings(totals: &[(PlayerId, u32)]) -> Vec<PriceStanding> {
    let mut sorted = totals.to_vec();
    sorted.sort_by_key(|&(_, points)| std::cmp::Reverse(points));
    let above = |points: u32| sorted.iter().filter(|o| o.1 > points).count();
    sorted.iter().map(|&(player, points)| PriceStanding { player, points, place: (1 + above(points)) as u8 }).collect()
}

/// The fetch date as the reveal shows it, `12.11.`; empty when it is not a date.
pub(crate) fn checked_label(fetched: &str) -> String {
    let parts: Option<Vec<u32>> = fetched.get(..10).and_then(|d| d.split('-').map(|n| n.parse().ok()).collect());
    match parts.as_deref() {
        Some([_, month, day]) => format!("{day}.{month}."),
        _ => String::new(),
    }
}

#[cfg(test)]
#[path = "preisfrage_rules_tests.rs"]
mod tests;
