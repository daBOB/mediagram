//! Viewing stats on the sync record: one row per (title, device) and per
//! (day, device), each written by the device it names and passed on by
//! every other. New keys on a profile, not a format bump: an older reader
//! drops them and keeps merging. Pinned to the web by
//! `stats-record-parse.json`.

use serde::{Deserialize, Serialize};
use serde_json::Value;

use super::hostile_json::text_;

/// The most one device can watch in one day; a row claiming more is not one
/// any player wrote.
const DAY_SECONDS: f64 = 86_400.0;

/// One device's viewing of one title. Times are epoch milliseconds.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct TitleStatRow {
    pub set_id: String,
    pub device: String,
    /// Its first play on that device.
    pub started_at: f64,
    pub last_watched_at: f64,
    pub seconds: f64,
    /// Its latest start-over after being finished, if it had one.
    #[serde(default, skip_serializing_if = "Option::is_none")]
    pub again_at: Option<f64>,
    pub updated_at: f64,
}

/// One device's watch time on one of its own local days.
#[derive(Debug, Clone, PartialEq, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct DayStatRow {
    /// `YYYY-MM-DD`, the watching device's date.
    pub day: String,
    pub device: String,
    pub seconds: f64,
    pub updated_at: f64,
}

/// `YYYY-MM-DD` in ASCII digits: the shape, not whether the date exists.
pub(crate) fn is_day(day: &str) -> bool {
    let bytes = day.as_bytes();
    bytes.len() == 10
        && bytes.iter().enumerate().all(|(i, byte)| {
            if i == 4 || i == 7 {
                *byte == b'-'
            } else {
                byte.is_ascii_digit()
            }
        })
}

/// A count or a time a row carries: a JSON number, finite, never negative.
/// No coercion, unlike the older rows: no writer ever put a string, `null`
/// or a boolean in these keys.
fn amount(value: Option<&Value>) -> Option<f64> {
    value?.as_f64().filter(|n| n.is_finite() && *n >= 0.0)
}

pub(super) fn title_stat_row(raw: &Value) -> Option<TitleStatRow> {
    let row = raw.as_object()?;
    // Absent or `null` means never started over; anything else has to be a
    // time like the rest, or the row goes.
    let again_at = match row.get("againAt") {
        None | Some(Value::Null) => None,
        given => Some(amount(given)?),
    };
    Some(TitleStatRow {
        set_id: text_(row.get("setId"))?,
        device: text_(row.get("device"))?,
        started_at: amount(row.get("startedAt"))?,
        last_watched_at: amount(row.get("lastWatchedAt"))?,
        seconds: amount(row.get("seconds"))?,
        again_at,
        updated_at: amount(row.get("updatedAt"))?,
    })
}

pub(super) fn day_stat_row(raw: &Value) -> Option<DayStatRow> {
    let row = raw.as_object()?;
    // Shape only, and untrimmed: `2026-13-45` is kept, ` 2026-10-03` is not.
    let day = row.get("day").and_then(Value::as_str).filter(|day| is_day(day))?;
    Some(DayStatRow {
        day: day.to_string(),
        device: text_(row.get("device"))?,
        seconds: amount(row.get("seconds")).filter(|seconds| *seconds <= DAY_SECONDS)?,
        updated_at: amount(row.get("updatedAt"))?,
    })
}

#[cfg(test)]
#[path = "stats_record_tests.rs"]
mod tests;
