//! The stats page's numbers: watch time this week, this month and in all,
//! the last 30 days, and the history — summed over every device's rows.
//! Pure, and pinned to the web by `stats-summary.json`.

use std::collections::HashMap;

use serde::Deserialize;

use super::calendar::{day_name, day_number, monday_of};
use crate::state::record::{DayStatRow, TitleStatRow};
use crate::state::rows::WatchedRow;

/// One profile's stats rows from every device, its live `watched` marks,
/// and `today` — the reading device's local date, `YYYY-MM-DD`.
#[derive(Debug, Deserialize)]
pub struct SummaryInput {
    pub today: String,
    pub titles: Vec<TitleStatRow>,
    pub days: Vec<DayStatRow>,
    pub watched: Vec<WatchedRow>,
}

#[derive(Debug, Clone, Default, PartialEq, uniffi::Record)]
pub struct StatsSummary {
    /// Monday of today's ISO week through today.
    pub week_seconds: f64,
    /// The first of today's month through today.
    pub month_seconds: f64,
    /// Every day row, one dated after today included.
    pub all_seconds: f64,
    /// 30 days, oldest first, ending today; a day nothing was watched is 0.
    pub last30: Vec<DayBar>,
    /// Newest first.
    pub history: Vec<HistoryEntry>,
}

/// One bar of the last 30 days: every device's seconds on `day`.
#[derive(Debug, Clone, PartialEq, Deserialize, uniffi::Record)]
pub struct DayBar {
    pub day: String,
    pub seconds: f64,
}

/// One history line. `seconds` is the title's total across devices — the
/// same on each of its lines, and 0 for a title finished before stats.
#[derive(Debug, Clone, PartialEq, Deserialize, uniffi::Record)]
#[serde(rename_all = "camelCase")]
pub struct HistoryEntry {
    pub kind: HistoryKind,
    pub set_id: String,
    /// Epoch milliseconds.
    pub at: i64,
    pub seconds: f64,
}

/// First played, watched to the end, or started over after that.
#[derive(Debug, Clone, Copy, PartialEq, Eq, Deserialize, uniffi::Enum)]
#[serde(rename_all = "lowercase")]
pub enum HistoryKind {
    Started,
    Finished,
    Again,
}

pub fn summarize(input: &SummaryInput) -> StatsSummary {
    let all_seconds = total(input.days.iter());
    let history = history(input);
    let Some(today) = day_number(&input.today) else {
        // Nothing to count back from: only what needs no calendar.
        return StatsSummary {
            all_seconds,
            history,
            ..StatsSummary::default()
        };
    };
    let today_name = input.today.as_str();
    let monday = day_name(monday_of(today));
    let month = &today_name[..8];
    let so_far = || {
        input
            .days
            .iter()
            .filter(|row| row.day.as_str() <= today_name)
    };
    let week_seconds = total(so_far().filter(|row| row.day >= monday));
    let month_seconds = total(so_far().filter(|row| row.day.starts_with(month)));
    let mut by_day: HashMap<&str, f64> = HashMap::new();
    for row in &input.days {
        *by_day.entry(row.day.as_str()).or_insert(0.0) += row.seconds;
    }
    let last30 = (today - 29..=today)
        .map(day_name)
        .map(|day| DayBar {
            seconds: by_day.get(day.as_str()).copied().unwrap_or(0.0),
            day,
        })
        .collect();
    StatsSummary {
        week_seconds,
        month_seconds,
        all_seconds,
        last30,
        history,
    }
}

/// Summed from a plain 0: `Sum` for floats starts at -0.0, which would
/// reach Kotlin as a negative zero for a profile with nothing watched.
fn total<'a>(rows: impl Iterator<Item = &'a DayStatRow>) -> f64 {
    rows.fold(0.0, |sum, row| sum + row.seconds)
}

/// One title's rows from every device, folded.
struct Title {
    started: f64,
    again: Option<f64>,
    seconds: f64,
}

/// Per title: started at its earliest `startedAt`, started over at its
/// latest `againAt`, finished at its live `watched` mark — that one also
/// for a title with no stats rows, since a finish from before stats existed
/// is real history.
fn history(input: &SummaryInput) -> Vec<HistoryEntry> {
    let mut titles: HashMap<&str, Title> = HashMap::new();
    for row in &input.titles {
        let title = titles.entry(row.set_id.as_str()).or_insert(Title {
            started: row.started_at,
            again: None,
            seconds: 0.0,
        });
        title.started = title.started.min(row.started_at);
        if let Some(again) = row.again_at {
            title.again = Some(title.again.map_or(again, |held| held.max(again)));
        }
        title.seconds += row.seconds;
    }
    let entry = |kind, set_id: &str, at: i64| HistoryEntry {
        kind,
        set_id: set_id.to_string(),
        at,
        seconds: titles.get(set_id).map_or(0.0, |title| title.seconds),
    };
    let mut lines = Vec::new();
    for (set_id, title) in &titles {
        lines.push(entry(HistoryKind::Started, set_id, title.started as i64));
        if let Some(again) = title.again {
            lines.push(entry(HistoryKind::Again, set_id, again as i64));
        }
    }
    for row in &input.watched {
        lines.push(entry(HistoryKind::Finished, &row.set_id, row.finished_at));
    }
    lines.sort_by(|a, b| {
        b.at.cmp(&a.at)
            .then_with(|| a.set_id.cmp(&b.set_id))
            .then_with(|| rank(a.kind).cmp(&rank(b.kind)))
    });
    lines
}

/// At one moment on one title: finished, then started over, then started.
fn rank(kind: HistoryKind) -> u8 {
    match kind {
        HistoryKind::Finished => 0,
        HistoryKind::Again => 1,
        HistoryKind::Started => 2,
    }
}

#[cfg(test)]
#[path = "summary_tests.rs"]
mod tests;
