//! Progress, watched marks, the watchlist and Kids — the per-title facts a
//! profile (or, for Kids, the whole player) holds. A port of the matching
//! methods on `WatchState` in `web/src/state/store.ts`. The editor's choice
//! is the same shape as Kids but lives in `editors_choice.rs`, split out to
//! keep this file under the line limit.

use rusqlite::{Connection, params};

use super::profiles::now_ms;

/// Where a profile is in one title. `at`/`duration` are seconds, never a
/// percentage — a set's runtime can be unknown, and a percentage recorded
/// against an unknown length cannot be turned back into a position to seek
/// to.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct ProgressRow {
    pub set_id: String,
    pub at: f64,
    pub duration: Option<f64>,
    pub updated_at: i64,
}

/// One title a profile watched to the end, and when.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct WatchedRow {
    pub set_id: String,
    pub finished_at: i64,
}

pub fn progress_for(conn: &Connection, profile_id: &str) -> rusqlite::Result<Vec<ProgressRow>> {
    let mut stmt = conn.prepare(
        "SELECT set_id, at_seconds, duration, updated_at
           FROM progress WHERE profile_id = ?1 ORDER BY updated_at DESC",
    )?;
    let rows = stmt.query_map([profile_id], |row| {
        Ok(ProgressRow {
            set_id: row.get(0)?,
            at: row.get(1)?,
            duration: row.get(2)?,
            updated_at: row.get(3)?,
        })
    })?;
    rows.collect()
}

/// Sets where a profile is in `set_id`. Clamped to non-negative, like the
/// web: a negative position has no title to seek to.
pub fn set_progress(
    conn: &Connection,
    profile_id: &str,
    set_id: &str,
    at: f64,
    duration: Option<f64>,
) -> rusqlite::Result<()> {
    conn.execute(
        "INSERT INTO progress(profile_id, set_id, at_seconds, duration, updated_at)
           VALUES (?1, ?2, ?3, ?4, ?5)
           ON CONFLICT(profile_id, set_id) DO UPDATE SET
             at_seconds = excluded.at_seconds,
             duration = excluded.duration,
             updated_at = excluded.updated_at",
        params![profile_id, set_id, at.max(0.0), duration, now_ms()],
    )?;
    Ok(())
}

/// Forgets a position: started again, or watched to the end.
pub fn clear_progress(conn: &Connection, profile_id: &str, set_id: &str) -> rusqlite::Result<()> {
    conn.execute(
        "DELETE FROM progress WHERE profile_id = ?1 AND set_id = ?2",
        params![profile_id, set_id],
    )?;
    Ok(())
}

pub fn watched_for(conn: &Connection, profile_id: &str) -> rusqlite::Result<Vec<WatchedRow>> {
    let mut stmt = conn.prepare(
        "SELECT set_id, finished_at FROM watched
           WHERE profile_id = ?1 AND removed_at IS NULL ORDER BY finished_at DESC",
    )?;
    let rows = stmt.query_map([profile_id], |row| {
        Ok(WatchedRow {
            set_id: row.get(0)?,
            finished_at: row.get(1)?,
        })
    })?;
    rows.collect()
}

/// Records that a title was watched to the end, or takes it back.
///
/// Finishing clears the position at the same moment — a finished title has
/// no resume point. Taking it back does not: the mark is kept as a
/// tombstone (`removed_at`), the same reason and shape as
/// `set_watchlisted`, so a sync round can tell another device the un-mark
/// happened instead of that device re-importing the older mark it still
/// holds — and it must not touch a position it has nothing to say about
/// (`merge.rs`). Both directions are clamped to at least one millisecond
/// past whichever clock last touched this row: a value this device
/// imported can carry another device's clock, and this device's own clock
/// running behind would otherwise let a stale import win the next merge
/// back.
pub fn set_watched(
    conn: &Connection,
    profile_id: &str,
    set_id: &str,
    finished: bool,
) -> rusqlite::Result<()> {
    if finished {
        conn.execute(
            "INSERT INTO watched(profile_id, set_id, finished_at, removed_at) VALUES (?1, ?2, ?3, NULL)
               ON CONFLICT(profile_id, set_id) DO UPDATE SET
                 finished_at = MAX(excluded.finished_at, COALESCE(removed_at, 0) + 1), removed_at = NULL",
            params![profile_id, set_id, now_ms()],
        )?;
        clear_progress(conn, profile_id, set_id)?;
    } else {
        conn.execute(
            "UPDATE watched SET removed_at = MAX(?3, finished_at + 1) WHERE profile_id = ?1 AND set_id = ?2 AND removed_at IS NULL",
            params![profile_id, set_id, now_ms()],
        )?;
    }
    Ok(())
}

pub fn watchlist_for(conn: &Connection, profile_id: &str) -> rusqlite::Result<Vec<String>> {
    let mut stmt = conn
        .prepare("SELECT set_id FROM watchlist WHERE profile_id = ?1 AND removed_at IS NULL ORDER BY added_at DESC")?;
    let rows = stmt.query_map([profile_id], |row| row.get(0))?;
    rows.collect()
}

/// Adds, or removes, this profile's watchlist mark for `set_id`.
///
/// A removal is kept as a tombstone (`removed_at`) rather than a deleted
/// row — see `record.rs` on why — so re-adding clears the tombstone instead
/// of inserting a duplicate; the `WHERE removed_at IS NOT NULL` keeps a
/// second `true` in a row from bumping `added_at` for no reason.
pub fn set_watchlisted(
    conn: &Connection,
    profile_id: &str,
    set_id: &str,
    listed: bool,
) -> rusqlite::Result<()> {
    if listed {
        conn.execute(
            "INSERT INTO watchlist(profile_id, set_id, added_at, removed_at) VALUES (?1, ?2, ?3, NULL)
               ON CONFLICT(profile_id, set_id) DO UPDATE SET added_at = excluded.added_at, removed_at = NULL
                 WHERE removed_at IS NOT NULL",
            params![profile_id, set_id, now_ms()],
        )?;
    } else {
        conn.execute(
            "UPDATE watchlist SET removed_at = ?3 WHERE profile_id = ?1 AND set_id = ?2 AND removed_at IS NULL",
            params![profile_id, set_id, now_ms()],
        )?;
    }
    Ok(())
}

/// The titles marked as a child's, for everyone on this player. Not scoped
/// to a profile: see `schema.rs` on why.
pub fn kids(conn: &Connection) -> rusqlite::Result<Vec<String>> {
    let mut stmt =
        conn.prepare("SELECT set_id FROM kids WHERE removed_at IS NULL ORDER BY marked_at DESC")?;
    let rows = stmt.query_map([], |row| row.get(0))?;
    rows.collect()
}

/// A removal is a tombstone, not a delete — the same reason and the same
/// shape as `set_watchlisted`.
pub fn set_kids(conn: &Connection, set_id: &str, marked: bool) -> rusqlite::Result<()> {
    if marked {
        conn.execute(
            "INSERT INTO kids(set_id, marked_at, removed_at) VALUES (?1, ?2, NULL)
               ON CONFLICT(set_id) DO UPDATE SET marked_at = excluded.marked_at, removed_at = NULL
                 WHERE removed_at IS NOT NULL",
            params![set_id, now_ms()],
        )?;
    } else {
        conn.execute(
            "UPDATE kids SET removed_at = ?2 WHERE set_id = ?1 AND removed_at IS NULL",
            params![set_id, now_ms()],
        )?;
    }
    Ok(())
}

#[cfg(test)]
#[path = "rows_tests.rs"]
mod tests;
