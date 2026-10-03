use super::super::calendar::{day_name, day_number, monday_of};
use super::*;

fn day(day: &str, device: &str, seconds: f64) -> DayStatRow {
    DayStatRow {
        day: day.into(),
        device: device.into(),
        seconds,
        updated_at: 1.0,
    }
}

fn title(
    set_id: &str,
    device: &str,
    started_at: f64,
    again_at: Option<f64>,
    seconds: f64,
) -> TitleStatRow {
    TitleStatRow {
        set_id: set_id.into(),
        device: device.into(),
        started_at,
        last_watched_at: started_at,
        seconds,
        again_at,
        updated_at: 1.0,
    }
}

fn finished(set_id: &str, finished_at: i64) -> WatchedRow {
    WatchedRow {
        set_id: set_id.into(),
        finished_at,
    }
}

fn input(
    today: &str,
    titles: Vec<TitleStatRow>,
    days: Vec<DayStatRow>,
    watched: Vec<WatchedRow>,
) -> SummaryInput {
    SummaryInput {
        today: today.into(),
        titles,
        days,
        watched,
    }
}

#[test]
fn day_numbers_count_from_1970_and_name_back() {
    assert_eq!(day_number("1970-01-01"), Some(0));
    assert_eq!(day_number("2000-03-01"), Some(11_017));
    assert_eq!(day_number("2024-02-29"), Some(19_782));
    assert_eq!(day_number("2026-10-03"), Some(20_729));
    for number in -1_000..30_000 {
        assert_eq!(day_number(&day_name(number)), Some(number));
    }
    for bad in [
        "",
        "2026-13-01",
        "2026-00-10",
        "2026-10-32",
        "2026-1-03",
        "2026/10/03",
    ] {
        assert_eq!(day_number(bad), None, "{bad:?}");
    }
}

#[test]
fn an_iso_week_runs_monday_to_sunday() {
    for day in ["2026-09-28", "2026-10-03", "2026-10-04"] {
        assert_eq!(
            day_name(monday_of(day_number(day).unwrap())),
            "2026-09-28",
            "{day}"
        );
    }
}

/// Today is Saturday 3 October: the week began on Monday 28 September, the
/// month on the 1st, and a row dated tomorrow counts only in all.
#[test]
fn week_and_month_split_at_their_boundaries_and_the_future_counts_only_in_all() {
    let days = vec![
        day("2026-09-27", "laptop", 1.0),
        day("2026-09-28", "laptop", 2.0),
        day("2026-10-01", "laptop", 4.0),
        day("2026-10-03", "phone", 8.0),
        day("2026-10-03", "laptop", 16.0),
        day("2026-10-04", "laptop", 32.0),
    ];
    let summary = summarize(&input("2026-10-03", vec![], days, vec![]));
    assert_eq!(
        (
            summary.week_seconds,
            summary.month_seconds,
            summary.all_seconds
        ),
        (30.0, 28.0, 63.0)
    );
    assert_eq!(summary.last30.len(), 30);
    assert_eq!(summary.last30[0].day, "2026-09-04");
    assert_eq!(
        summary.last30[29],
        DayBar {
            day: "2026-10-03".into(),
            seconds: 24.0
        }
    );
    assert_eq!(
        summary.last30[25],
        DayBar {
            day: "2026-09-29".into(),
            seconds: 0.0
        }
    );
}

#[test]
fn the_last_30_days_cross_a_year_boundary() {
    let summary = summarize(&input(
        "2026-01-10",
        vec![],
        vec![day("2025-12-31", "laptop", 5.0)],
        vec![],
    ));
    assert_eq!(summary.last30[0].day, "2025-12-12");
    assert_eq!(
        summary.last30[19],
        DayBar {
            day: "2025-12-31".into(),
            seconds: 5.0
        }
    );
    assert_eq!((summary.week_seconds, summary.month_seconds), (0.0, 0.0));
}

#[test]
fn history_is_newest_first_with_each_titles_total() {
    let titles = vec![
        title("01A", "laptop", 100.0, None, 600.0),
        title("01A", "phone", 50.0, Some(300.0), 120.0),
        title("01B", "laptop", 300.0, None, 60.0),
        title("01C", "laptop", 400.0, None, 0.0),
    ];
    let watched = vec![
        finished("01A", 200),
        finished("01OLD", 10),
        finished("01C", 400),
    ];
    let summary = summarize(&input("2026-10-03", titles, vec![], watched));
    let lines: Vec<(HistoryKind, &str, i64, f64)> = summary
        .history
        .iter()
        .map(|line| (line.kind, line.set_id.as_str(), line.at, line.seconds))
        .collect();
    assert_eq!(
        lines,
        [
            (HistoryKind::Finished, "01C", 400, 0.0),
            (HistoryKind::Started, "01C", 400, 0.0),
            (HistoryKind::Again, "01A", 300, 720.0),
            (HistoryKind::Started, "01B", 300, 60.0),
            (HistoryKind::Finished, "01A", 200, 720.0),
            (HistoryKind::Started, "01A", 50, 720.0),
            (HistoryKind::Finished, "01OLD", 10, 0.0),
        ]
    );
}

#[test]
fn nothing_watched_is_thirty_zero_days_and_no_history() {
    let summary = summarize(&input("2026-10-03", vec![], vec![], vec![]));
    assert_eq!(
        (
            summary.week_seconds,
            summary.month_seconds,
            summary.all_seconds
        ),
        (0.0, 0.0, 0.0)
    );
    assert!(
        !summary.all_seconds.is_sign_negative(),
        "a plain zero, not -0.0"
    );
    assert!(summary.last30.iter().all(|day| day.seconds == 0.0) && summary.last30.len() == 30);
    assert!(summary.history.is_empty());
}

#[test]
fn a_today_that_is_not_a_date_still_sums_all_and_lists_history() {
    let summary = summarize(&input(
        "someday",
        vec![title("01A", "laptop", 1.0, None, 5.0)],
        vec![day("2026-10-03", "laptop", 5.0)],
        vec![],
    ));
    assert_eq!(
        (
            summary.all_seconds,
            summary.week_seconds,
            summary.last30.len()
        ),
        (5.0, 0.0, 0)
    );
    assert_eq!(summary.history.len(), 1);
}
