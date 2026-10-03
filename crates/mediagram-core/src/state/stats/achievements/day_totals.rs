//! Watching summed per local day — the days the day-based achievements
//! (hours, streaks) are counted over.

use std::collections::BTreeMap;

use super::super::calendar::day_number;
use crate::state::record::DayStatRow;

/// One local day's seconds across every device, as days since 1970-01-01.
pub(super) struct DayTotal {
    pub day: i64,
    pub seconds: f64,
}

/// Every device's rows summed per local day, oldest day first. A row whose
/// day names no date counts toward no day-based achievement.
pub(super) fn day_totals(rows: &[DayStatRow]) -> Vec<DayTotal> {
    let mut by_day: BTreeMap<i64, f64> = BTreeMap::new();
    for row in rows {
        if let Some(day) = day_number(&row.day) {
            *by_day.entry(day).or_default() += row.seconds;
        }
    }
    by_day
        .into_iter()
        .map(|(day, seconds)| DayTotal { day, seconds })
        .collect()
}
