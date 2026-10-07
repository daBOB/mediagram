//! The `categories` table: a hand-set label on a course, a documentary
//! collection or a standalone documentary — the same unit
//! [`mlib_spec::category_key::category_key`] keys, and the same unit whose
//! custom artwork already lives under that key. Only the uploader writes
//! this table (`mediagram::index::categories`); a player only ever reads it.

use std::collections::HashMap;

use rusqlite::Connection;

use crate::sqlite_schema::table_exists;

/// Every hand-set category already filed, by `(department, item_key)` — the
/// same pair [`mlib_spec::category_key::category_key`] returns.
///
/// Empty for an index written before v12 (no `categories` table yet), or
/// with nothing filed. A `NULL` row ("cleared") is skipped, the same as
/// `shows::anime_overrides` drops its own "back to automatic" rows: an
/// absent entry already reads as "uncategorised", so keeping a cleared row
/// would only cost every lookup a wasted check for a value that means the
/// same as absent.
pub fn categories(conn: &Connection) -> rusqlite::Result<HashMap<(String, String), String>> {
    let mut map = HashMap::new();
    if !table_exists(conn, "categories")? {
        return Ok(map);
    }
    let mut stmt =
        conn.prepare("SELECT department, item_key, category FROM categories WHERE category IS NOT NULL")?;
    let rows = stmt.query_map([], |row| {
        Ok((row.get::<_, String>(0)?, row.get::<_, String>(1)?, row.get::<_, String>(2)?))
    })?;
    for row in rows {
        let (department, item_key, category) = row?;
        map.insert((department, item_key), category);
    }
    Ok(map)
}

/// The category on the unit `kind`/`show`/`title` names, or `None` for a
/// film, an episode, or a unit nothing has filed —
/// [`mlib_spec::category_key::category_key`] decides which of those this is.
pub fn category_of(
    map: &HashMap<(String, String), String>,
    kind: &str,
    show: Option<&str>,
    title: Option<&str>,
) -> Option<String> {
    let (department, item_key) = mlib_spec::category_key::category_key(kind, show, title)?;
    map.get(&(department.to_string(), item_key)).cloned()
}

#[cfg(test)]
#[path = "catalog_categories_tests.rs"]
mod tests;
