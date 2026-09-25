//! Wall-clock time as the index records it: whole seconds since the epoch.

use std::time::{SystemTime, UNIX_EPOCH};

/// Now, in whole seconds. A clock set before 1970 reads as 0 rather than
/// failing: every caller stores or compares the value, and none of them has
/// anything better to do with the error.
pub fn now_unix() -> i64 {
    SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map(|d| d.as_secs() as i64)
        .unwrap_or(0)
}

/// `YYMMDD-HHMM` in UTC, for a backup file name that sorts and reads by
/// itself. No timezone or calendar crate sits in this workspace, so this
/// converts by hand from days-since-epoch; a backup name has no reason to
/// reach further than that.
pub fn backup_timestamp(unix: i64) -> String {
    let days = unix.div_euclid(86_400);
    let secs_of_day = unix.rem_euclid(86_400);
    let (year, month, day) = civil_from_days(days);
    let hour = secs_of_day / 3_600;
    let minute = (secs_of_day % 3_600) / 60;
    format!(
        "{:02}{month:02}{day:02}-{hour:02}{minute:02}",
        year.rem_euclid(100)
    )
}

/// Gregorian year/month/day for the number of days since 1970-01-01.
/// Howard Hinnant's `civil_from_days` (public domain): the standard
/// division-based algorithm for turning a day count into a calendar date
/// without a lookup table.
fn civil_from_days(days: i64) -> (i64, u32, u32) {
    let z = days + 719_468;
    let era = z.div_euclid(146_097);
    let doe = z.rem_euclid(146_097); // [0, 146096]
    let yoe = (doe - doe / 1460 + doe / 36_524 - doe / 146_096) / 365; // [0, 399]
    let y = yoe + era * 400;
    let doy = doe - (365 * yoe + yoe / 4 - yoe / 100); // [0, 365]
    let mp = (5 * doy + 2) / 153; // [0, 11]
    let d = (doy - (153 * mp + 2) / 5 + 1) as u32; // [1, 31]
    let m = (if mp < 10 { mp + 3 } else { mp - 9 }) as u32; // [1, 12]
    (if m <= 2 { y + 1 } else { y }, m, d)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn epoch_reads_as_1970_01_01() {
        assert_eq!(backup_timestamp(0), "700101-0000");
    }

    #[test]
    fn a_known_date_round_trips() {
        // 2024-01-01T00:00:00Z
        assert_eq!(backup_timestamp(1_704_067_200), "240101-0000");
    }

    #[test]
    fn minutes_and_hours_carry_through() {
        // 2024-01-01T13:37:00Z
        assert_eq!(backup_timestamp(1_704_116_220), "240101-1337");
    }
}
