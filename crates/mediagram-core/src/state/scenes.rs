//! Marked scenes: a moment in a title, many per title. A port of the scene
//! half of `WatchState` in `web/src/state/scenes-exchange.ts`. A removal is a
//! tombstone so a merge can see it, like the watchlist's.

use rusqlite::{Connection, params};

use super::profiles::now_ms;

/// One live mark.
#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct SceneMark {
    pub id: String,
    pub set_id: String,
    /// Film seconds.
    pub at: f64,
    pub marked_at: i64,
}

/// A profile's live marks, newest first; the rowid breaks a tie within one
/// millisecond.
pub fn live_for(conn: &Connection, profile_id: &str) -> rusqlite::Result<Vec<SceneMark>> {
    let mut stmt = conn.prepare(
        "SELECT id, set_id, at_seconds, marked_at FROM scenes
           WHERE profile_id = ?1 AND removed_at IS NULL ORDER BY marked_at DESC, rowid DESC",
    )?;
    let rows = stmt.query_map([profile_id], |row| {
        Ok(SceneMark {
            id: row.get(0)?,
            set_id: row.get(1)?,
            at: row.get(2)?,
            marked_at: row.get(3)?,
        })
    })?;
    rows.collect()
}

/// The new mark's id, or `None` for an `at` that is no film second or a
/// profile that is gone (deleted on another device).
pub fn mark(
    conn: &Connection,
    profile_id: &str,
    set_id: &str,
    at: f64,
) -> rusqlite::Result<Option<String>> {
    if !at.is_finite() || at < 0.0 {
        return Ok(None);
    }
    let mut bytes = [0u8; 8];
    getrandom::fill(&mut bytes).expect("the OS random source is available");
    // 16 alphanumerics: what the removal route accepts.
    let id: String = bytes.iter().map(|b| format!("{b:02x}")).collect();
    match conn.execute(
        "INSERT INTO scenes(id, profile_id, set_id, at_seconds, marked_at) VALUES (?1, ?2, ?3, ?4, ?5)",
        params![id, profile_id, set_id, at, now_ms()],
    ) {
        Ok(_) => Ok(Some(id)),
        Err(rusqlite::Error::SqliteFailure(error, _))
            if error.code == rusqlite::ErrorCode::ConstraintViolation =>
        {
            Ok(None)
        }
        Err(error) => Err(error),
    }
}

/// Keeps the mark as a tombstone; `false` when no live mark has that id.
pub fn remove(conn: &Connection, profile_id: &str, id: &str) -> rusqlite::Result<bool> {
    let changed = conn.execute(
        "UPDATE scenes SET removed_at = ?3 WHERE id = ?2 AND profile_id = ?1 AND removed_at IS NULL",
        params![profile_id, id, now_ms()],
    )?;
    Ok(changed > 0)
}

#[cfg(test)]
#[path = "scenes_tests.rs"]
mod tests;
