use super::*;

fn row(day: &str, device: &str, seconds: f64) -> DayStatRow {
    DayStatRow {
        day: day.into(),
        device: device.into(),
        seconds,
        updated_at: 1.0,
    }
}

fn totals(rows: &[DayStatRow]) -> Vec<(i64, f64)> {
    day_totals(rows)
        .into_iter()
        .map(|total| (total.day, total.seconds))
        .collect()
}

/// A local day watched on two devices is one day, oldest day first however
/// the rows arrived.
#[test]
fn every_devices_seconds_add_up_per_day_oldest_first() {
    let rows = [
        row("2026-10-04", "phone", 60.0),
        row("2026-10-03", "laptop", 600.0),
        row("2026-10-03", "phone", 30.0),
    ];
    assert_eq!(totals(&rows), [(20_729, 630.0), (20_730, 60.0)]);
}

#[test]
fn a_row_whose_day_names_no_date_counts_toward_no_day() {
    let rows = [
        row("2026-13-01", "phone", 60.0),
        row("someday", "phone", 60.0),
        row("2026-10-03", "phone", 5.0),
    ];
    assert_eq!(totals(&rows), [(20_729, 5.0)]);
}

#[test]
fn no_rows_is_no_days() {
    assert!(day_totals(&[]).is_empty());
}
