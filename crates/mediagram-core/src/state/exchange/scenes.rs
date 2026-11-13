//! Marked scenes on the sync record: the rows a profile's `scenes` table
//! holds, as the wire says them and back. Matches
//! `web/src/state/scenes-exchange.ts`.

use rusqlite::{Connection, OptionalExtension, params};

use crate::state::record::SceneRow;

pub fn export_scenes(conn: &Connection, profile_id: &str) -> rusqlite::Result<Vec<SceneRow>> {
    let mut stmt = conn.prepare(
        "SELECT id, set_id, at_seconds, marked_at, removed_at FROM scenes WHERE profile_id = ?1",
    )?;
    let rows = stmt.query_map([profile_id], |row| {
        let removed_at: Option<i64> = row.get(4)?;
        Ok(SceneRow {
            id: row.get(0)?,
            set_id: row.get(1)?,
            at: row.get(2)?,
            updated_at: removed_at.unwrap_or(row.get(3)?) as f64,
            removed: removed_at.is_some(),
        })
    })?;
    rows.collect()
}

/// Corrective, like every import: newer local news is left alone. An id
/// another profile already holds is skipped rather than moved — ids are
/// random, so that is a document lying, not a collision.
pub fn import_scenes(
    conn: &Connection,
    profile_id: &str,
    rows: &[SceneRow],
) -> rusqlite::Result<u64> {
    let mut changed = 0u64;
    for row in rows {
        let standing: Option<(String, f64, bool)> = conn
            .query_row(
                "SELECT profile_id, COALESCE(removed_at, marked_at), removed_at IS NOT NULL
                   FROM scenes WHERE id = ?1",
                [&row.id],
                |r| Ok((r.get(0)?, r.get::<_, i64>(1)? as f64, r.get(2)?)),
            )
            .optional()?;
        if let Some((owner, at, removed)) = standing
            && (owner != profile_id
                || at > row.updated_at
                || (at == row.updated_at && removed == row.removed))
        {
            continue;
        }
        let stamp = row.updated_at as i64;
        conn.execute(
            "INSERT INTO scenes(id, profile_id, set_id, at_seconds, marked_at, removed_at)
               VALUES (?1, ?2, ?3, ?4, ?5, ?6)
               ON CONFLICT(id) DO UPDATE SET
                 set_id = excluded.set_id,
                 at_seconds = excluded.at_seconds,
                 marked_at = CASE WHEN excluded.removed_at IS NULL
                                  THEN excluded.marked_at ELSE marked_at END,
                 removed_at = excluded.removed_at",
            params![row.id, profile_id, row.set_id, row.at, stamp, row.removed.then_some(stamp)],
        )?;
        changed += 1;
    }
    Ok(changed)
}

#[cfg(test)]
#[path = "scenes_tests.rs"]
mod tests;
