//! Dates as day numbers, without a date library: the core gets its "today"
//! from Kotlin as `YYYY-MM-DD` and only ever needs to count days back from
//! it and name them again. Proleptic Gregorian, after Howard Hinnant's
//! `days_from_civil` and `civil_from_days`.

use crate::state::record::is_day;

/// Days since 1970-01-01, or `None` for anything that is not `YYYY-MM-DD`
/// with a month 1–12 and a day 1–31.
pub(super) fn day_number(day: &str) -> Option<i64> {
    if !is_day(day) {
        return None;
    }
    let year: i64 = day[0..4].parse().ok()?;
    let month: i64 = day[5..7].parse().ok()?;
    let date: i64 = day[8..10].parse().ok()?;
    if !(1..=12).contains(&month) || !(1..=31).contains(&date) {
        return None;
    }
    // Years start in March here, so a leap day ends one rather than sits in it.
    let year = if month <= 2 { year - 1 } else { year };
    let era = year.div_euclid(400);
    let year_of_era = year - era * 400;
    let day_of_year = (153 * ((month + 9) % 12) + 2) / 5 + date - 1;
    let day_of_era = year_of_era * 365 + year_of_era / 4 - year_of_era / 100 + day_of_year;
    Some(era * 146_097 + day_of_era - 719_468)
}

/// `YYYY-MM-DD` for a day number — `day_number`'s inverse.
pub(super) fn day_name(number: i64) -> String {
    let shifted = number + 719_468;
    let era = shifted.div_euclid(146_097);
    let day_of_era = shifted - era * 146_097;
    let year_of_era =
        (day_of_era - day_of_era / 1_460 + day_of_era / 36_524 - day_of_era / 146_096) / 365;
    let day_of_year = day_of_era - (365 * year_of_era + year_of_era / 4 - year_of_era / 100);
    let march_based = (5 * day_of_year + 2) / 153;
    let date = day_of_year - (153 * march_based + 2) / 5 + 1;
    let month = if march_based < 10 {
        march_based + 3
    } else {
        march_based - 9
    };
    let year = year_of_era + era * 400 + i64::from(month <= 2);
    format!("{year:04}-{month:02}-{date:02}")
}

/// Monday of the ISO week `number` falls in. 1970-01-01 was a Thursday.
pub(super) fn monday_of(number: i64) -> i64 {
    number - (number + 3).rem_euclid(7)
}
