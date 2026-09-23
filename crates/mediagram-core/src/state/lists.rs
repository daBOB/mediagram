//! Hand-built collections: named lists a profile keeps, in the order they
//! were built. A port of the collection half of `web/src/state/store.ts`.

use rusqlite::{Connection, params};

use super::profiles::now_ms;

/// How long a name may be — the same cap `profiles::create` holds names to.
const MAX_NAME: usize = 120;

#[derive(Debug, Clone, PartialEq, uniffi::Record)]
pub struct ListRow {
    pub id: String,
    pub name: String,
    pub items: Vec<String>,
}

pub fn collections_for(conn: &Connection, profile_id: &str) -> rusqlite::Result<Vec<ListRow>> {
    let mut stmt = conn.prepare(
        "SELECT id, name FROM collections WHERE profile_id = ?1 AND removed_at IS NULL ORDER BY created_at",
    )?;
    let heads: Vec<(String, String)> =
        stmt.query_map([profile_id], |row| Ok((row.get(0)?, row.get(1)?)))?.collect::<rusqlite::Result<_>>()?;

    let mut items_stmt =
        conn.prepare("SELECT set_id FROM collection_items WHERE collection_id = ?1 ORDER BY position")?;
    let mut lists = Vec::with_capacity(heads.len());
    for (id, name) in heads {
        let items: Vec<String> = items_stmt.query_map([&id], |row| row.get(0))?.collect::<rusqlite::Result<_>>()?;
        lists.push(ListRow { id, name, items });
    }
    Ok(lists)
}

/// A new, empty list. `None` for a name with nothing left after trimming.
pub fn create(conn: &Connection, profile_id: &str, name: &str) -> rusqlite::Result<Option<ListRow>> {
    let Some(clean) = clean_name(name) else { return Ok(None) };
    let id = ulid::Ulid::new().to_string();
    conn.execute(
        "INSERT INTO collections(id, profile_id, name, created_at, updated_at) VALUES (?1, ?2, ?3, ?4, ?4)",
        params![id, profile_id, clean, now_ms()],
    )?;
    Ok(Some(ListRow { id, name: clean, items: Vec::new() }))
}

/// Whether the list was there to rename, so a caller can answer "not found".
pub fn rename(conn: &Connection, profile_id: &str, id: &str, name: &str) -> rusqlite::Result<bool> {
    let Some(clean) = clean_name(name) else { return Ok(false) };
    let changed = conn.execute(
        "UPDATE collections SET name = ?3, updated_at = ?4 WHERE id = ?2 AND profile_id = ?1 AND removed_at IS NULL",
        params![profile_id, id, clean, now_ms()],
    )?;
    Ok(changed > 0)
}

/// Kept as a tombstone, like `rows::set_watchlisted` — items are left where
/// they are rather than cascaded away, since a newer live copy arriving
/// from another device (`lists_exchange::import_collections`) has to find
/// the same row to update rather than a gap it would re-create under a new
/// id.
pub fn delete(conn: &Connection, profile_id: &str, id: &str) -> rusqlite::Result<bool> {
    let at = now_ms();
    let changed = conn.execute(
        "UPDATE collections SET removed_at = ?3, updated_at = ?3 WHERE id = ?2 AND profile_id = ?1 AND removed_at IS NULL",
        params![profile_id, id, at],
    )?;
    Ok(changed > 0)
}

/// Adds or removes `set_id`, whichever `included` asks for. `false` when
/// the list is not this profile's, or is a deleted (tombstoned) one — a
/// list belonging to someone else, or gone, is simply not there.
pub fn set_in_collection(
    conn: &Connection,
    profile_id: &str,
    id: &str,
    set_id: &str,
    included: bool,
) -> rusqlite::Result<bool> {
    let exists: bool = conn
        .query_row(
            "SELECT 1 FROM collections WHERE id = ?2 AND profile_id = ?1 AND removed_at IS NULL",
            params![profile_id, id],
            |_| Ok(()),
        )
        .is_ok();
    if !exists {
        return Ok(false);
    }

    // Only a real change moves the list's own clock — a no-op add or remove
    // must not out-race another device's edit to the same list.
    let touched = if included {
        // Idempotent: adding a title already in the list must not make it
        // hold that title twice.
        let last: i64 = conn.query_row(
            "SELECT COALESCE(MAX(position), -1) FROM collection_items WHERE collection_id = ?1",
            [id],
            |row| row.get(0),
        )?;
        conn.execute(
            "INSERT INTO collection_items(collection_id, set_id, position) VALUES (?1, ?2, ?3)
               ON CONFLICT(collection_id, set_id) DO NOTHING",
            params![id, set_id, last + 1],
        )?
    } else {
        conn.execute(
            "DELETE FROM collection_items WHERE collection_id = ?1 AND set_id = ?2",
            params![id, set_id],
        )?
    };
    if touched > 0 {
        conn.execute("UPDATE collections SET updated_at = ?2 WHERE id = ?1", params![id, now_ms()])?;
    }
    Ok(true)
}

/// A name with its edges trimmed and internal whitespace collapsed, or
/// `None` when there is nothing left — the same rule `profiles::clean_name`
/// applies to a viewer's name.
fn clean_name(name: &str) -> Option<String> {
    let collapsed = name.split_whitespace().collect::<Vec<_>>().join(" ");
    let clean: String = collapsed.chars().take(MAX_NAME).collect();
    (!clean.is_empty()).then_some(clean)
}

#[cfg(test)]
#[path = "lists_tests.rs"]
mod tests;
