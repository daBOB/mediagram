//! `categories`: a hand-set label on a course, a documentary collection or a
//! standalone documentary — the same unit its custom artwork already keys
//! (`mlib_spec::category_key`) — that files it into a row on its department
//! page. Kept apart from `sets` and `shows` because it is set once by hand
//! and must survive both a `metadata` run and a `rescan`, neither of which
//! touches this table.
//!
//! `category` is `NULL` for "cleared", not a deleted row: a merge needs a
//! timestamped row to carry that clear to the other machine (see
//! `index::merge_categories`), and a deleted row carries nothing.

use rusqlite::{Connection, OptionalExtension, params};

/// The category on `(department, item_key)`, or `None` when no row exists or
/// the row is a cleared `NULL` — the two read the same to a caller asking
/// whether a unit is categorised.
pub fn get(conn: &Connection, department: &str, item_key: &str) -> rusqlite::Result<Option<String>> {
    conn.query_row(
        "SELECT category FROM categories WHERE department = ?1 AND item_key = ?2",
        params![department, item_key],
        |row| row.get::<_, Option<String>>(0),
    )
    .optional()
    .map(Option::flatten)
}

/// Writes the category, replacing whatever was there. `category = None`
/// clears it but keeps the row, stamped with `at` — a Unix timestamp — so a
/// merge can tell a clear apart from nothing having happened yet.
pub fn set(
    conn: &Connection,
    department: &str,
    item_key: &str,
    category: Option<&str>,
    at: i64,
) -> rusqlite::Result<()> {
    conn.execute(
        "INSERT INTO categories(department, item_key, category, set_at)
         VALUES (?1, ?2, ?3, ?4)
         ON CONFLICT(department, item_key) DO UPDATE SET category = excluded.category, set_at = excluded.set_at",
        params![department, item_key, category, at],
    )?;
    Ok(())
}

/// Every distinct, non-`NULL` category already set in `department`, other
/// than `except_key`'s own row — so re-setting a unit's category to itself
/// is never mistaken for adopting a different unit's spelling.
pub fn in_use(conn: &Connection, department: &str, except_key: &str) -> rusqlite::Result<Vec<String>> {
    let mut stmt = conn.prepare(
        "SELECT DISTINCT category FROM categories
         WHERE department = ?1 AND item_key != ?2 AND category IS NOT NULL",
    )?;
    stmt.query_map(params![department, except_key], |row| row.get::<_, String>(0))?
        .collect()
}

#[cfg(test)]
#[path = "categories_tests.rs"]
mod tests;
