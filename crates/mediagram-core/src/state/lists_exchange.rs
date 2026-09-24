//! Watchlist, Kids and collections on the sync record.
//!
//! Converts removal tombstones between SQLite and the wire format, matching
//! `web/src/state/lists-exchange.ts`.

use rusqlite::{Connection, OptionalExtension, params};

use super::record::{CollectionRow, ListRow};

/// A row's LWW timestamp is whichever of adding/marking or removing it this
/// store last recorded — the later of the two, since only one is ever set.
fn to_list_row(set_id: String, added_or_marked_at: i64, removed_at: Option<i64>) -> ListRow {
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

pub fn export_collections(
    conn: &Connection,
    profile_id: &str,
) -> rusqlite::Result<Vec<CollectionRow>> {
    let mut heads = conn.prepare(
        "SELECT id, name, updated_at, removed_at FROM collections WHERE profile_id = ?1",
    )?;
    let heads: Vec<(String, String, i64, Option<i64>)> = heads
        .query_map([profile_id], |row| {
            Ok((row.get(0)?, row.get(1)?, row.get(2)?, row.get(3)?))
        })?
        .collect::<rusqlite::Result<_>>()?;

    let mut items_stmt = conn.prepare(
        "SELECT set_id FROM collection_items WHERE collection_id = ?1 ORDER BY position",
    )?;
    let mut rows = Vec::with_capacity(heads.len());
    for (id, name, updated_at, removed_at) in heads {
        let items: Vec<String> = items_stmt
            .query_map([&id], |row| row.get(0))?
            .collect::<rusqlite::Result<_>>()?;
        rows.push(CollectionRow {
            id,
            name,
            items,
            updated_at: updated_at as f64,
            removed: removed_at.is_some(),
        });
    }
    Ok(rows)
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

/// Whole-list LWW: the newest row wins name and membership together.
/// Keep the incoming id so older writes cannot recreate the same collection.
pub fn import_collections(
    conn: &Connection,
    profile_id: &str,
    rows: &[CollectionRow],
) -> rusqlite::Result<u64> {
    let mut changed = 0u64;
    for row in rows {
        let standing: Option<i64> = conn
            .query_row(
                "SELECT updated_at FROM collections WHERE id = ?1 AND profile_id = ?2",
                params![row.id, profile_id],
                |r| r.get(0),
            )
            .optional()?;
        if standing.is_some_and(|at| at as f64 >= row.updated_at) {
            continue;
        }

        let removed_at = row.removed.then_some(row.updated_at as i64);
        if standing.is_none() {
            conn.execute(
                "INSERT INTO collections(id, profile_id, name, created_at, updated_at, removed_at)
                   VALUES (?1, ?2, ?3, ?4, ?4, ?5)",
                params![
                    row.id,
                    profile_id,
                    row.name,
                    row.updated_at as i64,
                    removed_at
                ],
            )?;
        } else {
            conn.execute(
                "UPDATE collections SET name = ?3, updated_at = ?4, removed_at = ?5 WHERE id = ?1 AND profile_id = ?2",
                params![row.id, profile_id, row.name, row.updated_at as i64, removed_at],
            )?;
        }

        conn.execute(
            "DELETE FROM collection_items WHERE collection_id = ?1",
            [&row.id],
        )?;
        for (position, set_id) in row.items.iter().enumerate() {
            conn.execute(
                "INSERT INTO collection_items(collection_id, set_id, position) VALUES (?1, ?2, ?3)
                   ON CONFLICT(collection_id, set_id) DO NOTHING",
                params![row.id, set_id, position as i64],
            )?;
        }
        changed += 1;
    }
    Ok(changed)
}

#[cfg(test)]
#[path = "lists_exchange_tests.rs"]
mod tests;
