//! What a search reads of the catalog: every playable set's text, and the
//! original title of whatever it is filed under.

use anyhow::{Context, Result};
use rusqlite::Connection;

/// One set as the search index reads it: its text, and nothing else. A port
/// of `web/src/search/index.ts`'s `Searchable`, restricted to the columns
/// ranking touches — [`crate::catalog::PlayableSet`] also carries codec and size fields no
/// search field reads.
#[derive(Debug, Clone, PartialEq)]
pub struct SearchableSet {
    pub set_id: String,
    pub title: Option<String>,
    pub show: Option<String>,
    pub chap: Option<String>,
    pub path: Option<String>,
    pub summary: Option<String>,
    /// The provider's original title of what the set is filed under (`Point
    /// Break`), v14 — `None` on an older index.
    pub original_title: Option<String>,
}

/// Every playable set with the text a search reads, summary included — a
/// port of `web/src/catalog.ts`'s `listSearchable`. One query rather than
/// [`crate::catalog::list_playable`] plus a summary lookup per row: the subquery costs one
/// pass over `assets`, not one round trip per set.
pub fn list_searchable(conn: &Connection) -> Result<Vec<SearchableSet>> {
    let has_original: bool = conn
        .query_row(
            "SELECT EXISTS(SELECT 1 FROM pragma_table_info('shows') WHERE name = 'original_title')",
            [],
            |row| row.get(0),
        )
        .context("checking for original titles")?;
    let original = if has_original {
        "(SELECT h.original_title FROM shows h WHERE h.source = 'tmdb' AND h.id = s.tmdb
           AND h.kind = CASE s.kind WHEN 'movie' THEN 'movie' ELSE 'tv' END)"
    } else {
        "NULL"
    };
    let sql = format!(
        "SELECT set_id, title, show, chap, path,
                (SELECT body FROM assets a
                  WHERE a.set_id = s.set_id AND a.kind = ?1 AND a.lang = '') AS summary,
                {original} AS original_title
           FROM sets s WHERE {}",
        mlib_spec::schema::PLAYABLE_SQL
    );
    let mut stmt = conn.prepare(&sql).context("preparing the search query")?;
    let rows = stmt
        .query_map([mlib_spec::schema::ASSET_SUMMARY], |row| {
            Ok(SearchableSet {
                set_id: row.get("set_id")?,
                title: row.get("title")?,
                show: row.get("show")?,
                chap: row.get("chap")?,
                path: row.get("path")?,
                summary: row.get("summary")?,
                original_title: row.get("original_title")?,
            })
        })
        .context("listing searchable sets")?;
    rows.collect::<rusqlite::Result<Vec<_>>>().context("reading a searchable row")
}
