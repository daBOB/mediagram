//! Collections on the sync record — split out of `lists_exchange.rs` only
//! to keep that file under the line limit; this is the same module, not a
//! separate concern.

use rusqlite::{Connection, OptionalExtension, params};

use crate::state::record::CollectionRow;

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
