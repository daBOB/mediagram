//! Viewing stats: how long each profile watched what, and when — recorded
//! by this device's own position writes, synced as rows each device owns,
//! and summed for the stats page. Pinned to the web by the `stats-*.json`
//! fixtures under `web/test/fixtures/watch-state/`.
//!
//! This file is the recording half: the step rule, the in-memory last tick
//! it measures from, and the title and day rows a write adds to. Rows of
//! other devices arrive only through `exchange`, and a position merged in
//! from another device is never recorded — it was not watched here.

use std::collections::HashMap;

use rusqlite::{Connection, OptionalExtension, params};

use super::{StateDb, rows, sync};

mod calendar;
pub(crate) mod exchange;
pub mod summary;

/// The most one step counts: 1.5 × the player's 10 s save tick. A longer
/// gap between two writes was a pause, a seek or a sleep, not watching.
pub const STEP_CAP_SECONDS: f64 = 15.0;

/// A title's last own position write: where it was, and when.
#[derive(Debug, Clone, Copy, PartialEq, serde::Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct Tick {
    pub at: f64,
    pub wall_ms: i64,
}

/// The last tick of every title this process wrote a position for, by
/// (profile, set). Never stored or synced: a restarted process starts
/// empty, so its first write of a title counts nothing rather than the
/// hours the app was closed.
pub(crate) type Ticks = HashMap<(String, String), Tick>;

/// Seconds watched between `prev` and a write at position `at`, wall clock
/// `now_ms`: the smaller of the two advances, capped. A seek forward counts
/// the time it took, 2× speed counts wall time, and a first write, a seek
/// back or a clock that went back count nothing.
pub fn step_seconds(prev: Option<&Tick>, at: f64, now_ms: i64) -> f64 {
    let Some(prev) = prev else { return 0.0 };
    let d_pos = at - prev.at;
    let d_wall = (now_ms - prev.wall_ms) as f64 / 1000.0;
    // Both `>` are false for NaN, so a broken position counts nothing rather
    // than whatever `f64::min` would make of it.
    if d_pos > 0.0 && d_wall > 0.0 {
        d_pos.min(d_wall).min(STEP_CAP_SECONDS)
    } else {
        0.0
    }
}

/// Whether a write starts a finished title over: no position for it here,
/// yet it is marked watched. One underway since its restart has a position
/// again, so it is not started over a second time.
pub fn again_now(had_progress: bool, watched_live: bool) -> bool {
    !had_progress && watched_live
}

impl StateDb {
    /// This device's own position write, and the watch time it adds. The
    /// position, this device's title row and its day row commit together or
    /// not at all, so a total never runs ahead of a write that did not land.
    /// `local_day` is the viewer's date, `YYYY-MM-DD`: a step that spans
    /// midnight counts on the day of the write that ends it. `None` when
    /// nothing could be written, as for every `with`.
    pub(crate) fn set_progress_counted(
        &self,
        profile_id: &str,
        set_id: &str,
        at: f64,
        duration: Option<f64>,
        local_day: &str,
        now_ms: i64,
    ) -> Option<()> {
        let at = at.max(0.0);
        let key = (profile_id.to_string(), set_id.to_string());
        self.with(|conn| {
            let mut ticks = self.ticks.lock().unwrap_or_else(|error| error.into_inner());
            let step = step_seconds(ticks.get(&key), at, now_ms);
            let tx = conn.unchecked_transaction()?;
            // Read before the upsert, which makes the position exist.
            let again = again_now(
                found(&tx, HAS_PROGRESS, profile_id, set_id)?,
                found(&tx, IS_WATCHED, profile_id, set_id)?,
            );
            rows::set_progress(&tx, profile_id, set_id, at, duration)?;
            let device = sync::device_id(&tx)?;
            add_to_title(&tx, profile_id, set_id, &device, step, again, now_ms)?;
            if step > 0.0 {
                add_to_day(&tx, profile_id, local_day, &device, step, now_ms)?;
            }
            tx.commit()?;
            ticks.insert(
                key,
                Tick {
                    at,
                    wall_ms: now_ms,
                },
            );
            Ok(())
        })
    }

    /// Forgets a title's last tick: once it is finished, its next play is a
    /// new viewing, measured from nothing.
    pub(crate) fn forget_tick(&self, profile_id: &str, set_id: &str) {
        let mut ticks = self.ticks.lock().unwrap_or_else(|error| error.into_inner());
        ticks.remove(&(profile_id.to_string(), set_id.to_string()));
    }
}

const HAS_PROGRESS: &str = "SELECT 1 FROM progress WHERE profile_id = ?1 AND set_id = ?2";
const IS_WATCHED: &str =
    "SELECT 1 FROM watched WHERE profile_id = ?1 AND set_id = ?2 AND removed_at IS NULL";

fn found(conn: &Connection, sql: &str, profile_id: &str, set_id: &str) -> rusqlite::Result<bool> {
    conn.query_row(sql, params![profile_id, set_id], |_| Ok(()))
        .optional()
        .map(|row| row.is_some())
}

/// Starts the title on this device at its first write here, and adds the
/// step. `again_at` moves only on a start-over and is kept otherwise.
///
/// `updated_at` never moves backwards, here or for the day row: a clock that
/// steps back (or a reinstall's own rows imported with stamps from ahead)
/// must not give this device's newer seconds an older stamp than a copy
/// other devices already hold, or the next import would undo them.
fn add_to_title(
    conn: &Connection,
    profile_id: &str,
    set_id: &str,
    device: &str,
    step: f64,
    again: bool,
    now_ms: i64,
) -> rusqlite::Result<()> {
    conn.execute(
        "INSERT INTO stats_titles(profile_id, set_id, device, started_at, last_watched_at, seconds, again_at, updated_at)
           VALUES (?1, ?2, ?3, ?4, ?4, ?5, ?6, ?4)
           ON CONFLICT(profile_id, set_id, device) DO UPDATE SET
             seconds = seconds + excluded.seconds,
             last_watched_at = excluded.last_watched_at,
             again_at = COALESCE(excluded.again_at, again_at),
             updated_at = MAX(excluded.updated_at, updated_at + 1)",
        params![profile_id, set_id, device, now_ms, step, again.then_some(now_ms)],
    )?;
    Ok(())
}

fn add_to_day(
    conn: &Connection,
    profile_id: &str,
    day: &str,
    device: &str,
    step: f64,
    now_ms: i64,
) -> rusqlite::Result<()> {
    conn.execute(
        "INSERT INTO stats_days(profile_id, day, device, seconds, updated_at) VALUES (?1, ?2, ?3, ?4, ?5)
           ON CONFLICT(profile_id, day, device) DO UPDATE SET
             seconds = seconds + excluded.seconds,
             updated_at = MAX(excluded.updated_at, updated_at + 1)",
        params![profile_id, day, device, step, now_ms],
    )?;
    Ok(())
}

#[cfg(test)]
#[path = "stats_tests.rs"]
mod tests;
