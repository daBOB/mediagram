//! The synced subtitle choices on the sync record: this device's rows out,
//! the merged rows back in. Matches `web/src/state/preferences-record.ts`.
//!
//! No row is ever deleted here or by a writer: a synced name carries no
//! tombstone, so a delete would come back from any device that still holds
//! the row. Every writer stores a value (`off`, a track key, a size) instead.

use rusqlite::{Connection, OptionalExtension, params};

use super::record::{SYNCED_NAMES, SyncPreference};

pub fn export_preferences(
    conn: &Connection,
    profile_id: &str,
) -> rusqlite::Result<Vec<SyncPreference>> {
    let mut stmt = conn
        .prepare("SELECT scope, name, value, updated_at FROM preferences WHERE profile_id = ?1")?;
    let rows = stmt.query_map([profile_id], |row| {
        Ok(SyncPreference {
            scope: row.get(0)?,
            name: row.get(1)?,
            value: row.get(2)?,
            updated_at: row.get::<_, i64>(3)? as f64,
        })
    })?;
    let mut kept = Vec::new();
    for row in rows {
        let row = row?;
        if SYNCED_NAMES.contains(&row.name.as_str()) {
            kept.push(row);
        }
    }
    Ok(kept)
}

/// Takes in the merged rows that are newer than the local ones. The merge
/// already broke equal-time ties by device, so an equal time with a different
/// value is the winner and is applied; an identical row is not a change.
pub fn import_preferences(
    conn: &Connection,
    profile_id: &str,
    rows: &[SyncPreference],
) -> rusqlite::Result<u64> {
    let mut changed = 0u64;
    for row in rows
        .iter()
        .filter(|row| SYNCED_NAMES.contains(&row.name.as_str()))
    {
        let standing: Option<(String, i64)> = conn
            .query_row(
                "SELECT value, updated_at FROM preferences
                   WHERE profile_id = ?1 AND scope = ?2 AND name = ?3",
                params![profile_id, row.scope, row.name],
                |r| Ok((r.get(0)?, r.get(1)?)),
            )
            .optional()?;
        if let Some((value, at)) = standing {
            let at = at as f64;
            if at > row.updated_at || (at == row.updated_at && value == row.value) {
                continue;
            }
        }
        conn.execute(
            "INSERT INTO preferences(profile_id, scope, name, value, updated_at)
               VALUES (?1, ?2, ?3, ?4, ?5)
               ON CONFLICT(profile_id, scope, name)
                 DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at",
            params![
                profile_id,
                row.scope,
                row.name,
                row.value,
                row.updated_at as i64
            ],
        )?;
        changed += 1;
    }
    Ok(changed)
}

#[cfg(test)]
#[path = "preferences_exchange_tests.rs"]
mod tests;
