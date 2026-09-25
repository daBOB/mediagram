//! Watchlist, Kids and collections on the sync record.
//!
//! Converts removal tombstones between SQLite and the wire format, matching
//! `web/src/state/lists-exchange.ts`. The editor's choice and collections
//! are the same shape as Kids and the watchlist respectively, but live in
//! their own files here, split out to keep this one under the line limit.

use rusqlite::{Connection, OptionalExtension, params};

use super::record::ListRow;

mod collections;
mod editors_choice;
pub use collections::{export_collections, import_collections};
pub use editors_choice::{export_editors_choice, import_editors_choice};

/// A row's LWW timestamp is whichever of adding/marking or removing it this
/// store last recorded — the later of the two, since only one is ever set.
pub(super) fn to_list_row(set_id: String, added_or_marked_at: i64, removed_at: Option<i64>) -> ListRow {
    ListRow {
        set_id,
        updated_at: removed_at.unwrap_or(added_or_marked_at) as f64,
        removed: removed_at.is_some(),
    }
}

/// The titles marked as a child's, tombstones included — everything the
/// wire needs to say. `rows::kids` is the live-only half of this.
pub fn export_kids(conn: &Connection) -> rusqlite::Result<Vec<ListRow>> {
    let mut stmt = conn.prepare("SELECT set_id, marked_at, removed_at FROM kids")?;
    let rows = stmt.query_map([], |row| {
        Ok(to_list_row(row.get(0)?, row.get(1)?, row.get(2)?))
    })?;
    rows.collect()
}

pub fn export_watchlist(conn: &Connection, profile_id: &str) -> rusqlite::Result<Vec<ListRow>> {
    let mut stmt =
        conn.prepare("SELECT set_id, added_at, removed_at FROM watchlist WHERE profile_id = ?1")?;
    let rows = stmt.query_map([profile_id], |row| {
        Ok(to_list_row(row.get(0)?, row.get(1)?, row.get(2)?))
    })?;
    rows.collect()
}

/// Takes in the kept titles. Corrective, like everything `import_merged`
/// calls: a row this device already holds newer news about is left alone.
pub fn import_kids(conn: &Connection, rows: &[ListRow]) -> rusqlite::Result<u64> {
    let mut changed = 0u64;
    for row in rows {
        let standing: Option<i64> = conn
            .query_row(
                "SELECT CASE WHEN removed_at IS NOT NULL THEN removed_at ELSE marked_at END FROM kids WHERE set_id = ?1",
                [&row.set_id],
                |r| r.get(0),
            )
            .optional()?;
        if standing.is_some_and(|at| at as f64 >= row.updated_at) {
            continue;
        }

        if row.removed {
            conn.execute(
                "INSERT INTO kids(set_id, marked_at, removed_at) VALUES (?1, ?2, ?2)
                   ON CONFLICT(set_id) DO UPDATE SET removed_at = excluded.removed_at",
                params![row.set_id, row.updated_at as i64],
            )?;
        } else {
            conn.execute(
                "INSERT INTO kids(set_id, marked_at, removed_at) VALUES (?1, ?2, NULL)
                   ON CONFLICT(set_id) DO UPDATE SET marked_at = excluded.marked_at, removed_at = NULL",
                params![row.set_id, row.updated_at as i64],
            )?;
        }
        changed += 1;
    }
    Ok(changed)
}

pub fn import_watchlist(
    conn: &Connection,
    profile_id: &str,
    rows: &[ListRow],
) -> rusqlite::Result<u64> {
    let mut changed = 0u64;
    for row in rows {
        let standing: Option<i64> = conn
            .query_row(
                "SELECT CASE WHEN removed_at IS NOT NULL THEN removed_at ELSE added_at END
                   FROM watchlist WHERE profile_id = ?1 AND set_id = ?2",
                params![profile_id, row.set_id],
                |r| r.get(0),
            )
            .optional()?;
        if standing.is_some_and(|at| at as f64 >= row.updated_at) {
            continue;
        }

        if row.removed {
            conn.execute(
                "INSERT INTO watchlist(profile_id, set_id, added_at, removed_at) VALUES (?1, ?2, ?3, ?3)
                   ON CONFLICT(profile_id, set_id) DO UPDATE SET removed_at = excluded.removed_at",
                params![profile_id, row.set_id, row.updated_at as i64],
            )?;
        } else {
            conn.execute(
                "INSERT INTO watchlist(profile_id, set_id, added_at, removed_at) VALUES (?1, ?2, ?3, NULL)
                   ON CONFLICT(profile_id, set_id) DO UPDATE SET added_at = excluded.added_at, removed_at = NULL",
                params![profile_id, row.set_id, row.updated_at as i64],
            )?;
        }
        changed += 1;
    }
    Ok(changed)
}

#[cfg(test)]
#[path = "lists_exchange_tests.rs"]
mod tests;
