//! Viewing stats on the sync record: every row of a profile out — every
//! device's, not only this one's, so a device that goes away keeps its
//! minutes — and the merged rows back in.
//!
//! Corrective, never wholesale, like the rest of `import_merged`: a row is
//! taken only when it is newer than the local one or missing here, and
//! nothing is ever deleted. Nothing here records watch time.

use rusqlite::{Connection, params};

use crate::state::record::{DayStatRow, TitleStatRow};

/// Both tables' rows for `profile_id`, every device's, in a fixed order so
/// an unchanged store exports an unchanged document.
///
/// The stamps are read as `f64`, which takes an integer and a real alike: a
/// stamp at the top of the integer range that a write's `+ 1` pushed past it
/// is a real in SQLite, and reading it as an integer would fail every export.
pub(crate) fn export(
    conn: &Connection,
    profile_id: &str,
) -> rusqlite::Result<(Vec<TitleStatRow>, Vec<DayStatRow>)> {
    let mut title_rows = conn.prepare(
        "SELECT set_id, device, started_at, last_watched_at, seconds, again_at, updated_at
           FROM stats_titles WHERE profile_id = ?1 ORDER BY set_id, device",
    )?;
    let titles = title_rows
        .query_map([profile_id], |row| {
            Ok(TitleStatRow {
                set_id: row.get(0)?,
                device: row.get(1)?,
                started_at: row.get(2)?,
                last_watched_at: row.get(3)?,
                seconds: row.get(4)?,
                again_at: row.get(5)?,
                updated_at: row.get(6)?,
            })
        })?
        .collect::<rusqlite::Result<Vec<_>>>()?;
    let mut day_rows = conn.prepare(
        "SELECT day, device, seconds, updated_at
           FROM stats_days WHERE profile_id = ?1 ORDER BY day, device",
    )?;
    let days = day_rows
        .query_map([profile_id], |row| {
            Ok(DayStatRow {
                day: row.get(0)?,
                device: row.get(1)?,
                seconds: row.get(2)?,
                updated_at: row.get(3)?,
            })
        })?
        .collect::<rusqlite::Result<Vec<_>>>()?;
    Ok((titles, days))
}

/// Takes in every merged row newer than the local one for its key, or
/// missing here — this device's own rows too: a reinstall that kept its
/// device id gets its minutes back. Returns how many rows changed.
pub(crate) fn import(
    conn: &Connection,
    profile_id: &str,
    titles: &[TitleStatRow],
    days: &[DayStatRow],
) -> rusqlite::Result<u64> {
    let mut changed = 0u64;
    for row in titles {
        changed += conn.execute(
            "INSERT INTO stats_titles(profile_id, set_id, device, started_at, last_watched_at, seconds, again_at, updated_at)
               VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)
               ON CONFLICT(profile_id, set_id, device) DO UPDATE SET
                 started_at = excluded.started_at,
                 last_watched_at = excluded.last_watched_at,
                 seconds = excluded.seconds,
                 again_at = excluded.again_at,
                 updated_at = excluded.updated_at
               WHERE excluded.updated_at > stats_titles.updated_at",
            params![
                profile_id,
                row.set_id,
                row.device,
                row.started_at as i64,
                row.last_watched_at as i64,
                row.seconds,
                row.again_at.map(|at| at as i64),
                row.updated_at as i64
            ],
        )? as u64;
    }
    for row in days {
        changed += conn.execute(
            "INSERT INTO stats_days(profile_id, day, device, seconds, updated_at) VALUES (?1, ?2, ?3, ?4, ?5)
               ON CONFLICT(profile_id, day, device) DO UPDATE SET
                 seconds = excluded.seconds,
                 updated_at = excluded.updated_at
               WHERE excluded.updated_at > stats_days.updated_at",
            params![profile_id, row.day, row.device, row.seconds, row.updated_at as i64],
        )? as u64;
    }
    Ok(changed)
}

#[cfg(test)]
#[path = "exchange_tests.rs"]
mod tests;
