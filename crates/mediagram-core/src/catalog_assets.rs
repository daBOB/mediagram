//! Text that belongs to a set: a summary — the `assets` row the uploader
//! writes from a file beside each video. A subtitle track lives here too
//! until it has a bundle, but a reader asks for one through
//! [`crate::catalog_subtitles`], which knows both generations; this module
//! keeps only the summary half, a port of `web/src/assets.ts`.

use std::collections::HashSet;

use mlib_spec::schema::ASSET_SUMMARY;
use rusqlite::{Connection, OptionalExtension};

/// Every set that carries a summary row, so `list_sets` can flag it without
/// a query per row.
pub fn summaries(conn: &Connection) -> rusqlite::Result<HashSet<String>> {
    let mut stmt = conn.prepare("SELECT set_id FROM assets WHERE kind = ?1 AND lang = ''")?;
    let rows = stmt.query_map([ASSET_SUMMARY], |row| row.get(0))?;
    rows.collect()
}

/// A summary's text. `None` for a `kind` this store does not know — now only
/// `"summary"`, since `Core::set_text` refuses `"subtitle"` before this is
/// ever called — a set with no such row, or a set that does not exist; none
/// of those is a distinction worth telling apart here.
pub fn text(conn: &Connection, set_id: &str, kind: &str, _lang: &str) -> rusqlite::Result<Option<String>> {
    match kind {
        "summary" => conn
            .query_row(
                "SELECT body FROM assets WHERE set_id = ?1 AND kind = ?2 AND lang = ''",
                [set_id, ASSET_SUMMARY],
                |row| row.get(0),
            )
            .optional(),
        _ => Ok(None),
    }
}

#[cfg(test)]
#[path = "catalog_assets_tests.rs"]
mod tests;
