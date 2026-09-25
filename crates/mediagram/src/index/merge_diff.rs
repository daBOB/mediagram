//! Detecting shared sets whose metadata differs between the local index and
//! the attached channel snapshot. Detection only: which side is right is not
//! decided here — see `crate::index::merge_conflicts`.

use anyhow::{Context, Result};
use rusqlite::Connection;

use crate::index::merge_columns::shared_columns;

/// Metadata columns `edit` (and so a conflict resolution) can rewrite; the
/// same set `sets::update_metadata` touches. A difference anywhere else
/// (`total`, `set_hash`, `status`, codecs, ...) describes the bytes in the
/// channel, which the two indexes cannot disagree about without one of them
/// being wrong in a way a caption re-read cannot fix.
const CONFLICT_COLUMNS: [&str; 12] = [
    "kind", "show", "chap", "path", "title", "year", "season", "episode", "abs", "tmdb", "tvdb",
    "imdb",
];

/// Set ids present in both `main.sets` and `channel.sets` whose metadata
/// columns disagree, in a stable order.
pub(super) fn conflicting_sets(conn: &Connection) -> Result<Vec<String>> {
    let cols = shared_columns(conn, "sets")?;
    let compared: Vec<&str> = CONFLICT_COLUMNS
        .iter()
        .copied()
        .filter(|wanted| cols.iter().any(|c| c == wanted))
        .collect();
    if compared.is_empty() {
        return Ok(Vec::new());
    }
    let where_clause = compared
        .iter()
        .map(|c| format!("local.{c} IS NOT remote.{c}"))
        .collect::<Vec<_>>()
        .join(" OR ");
    let sql = format!(
        "SELECT local.set_id FROM main.sets local
         JOIN channel.sets remote ON local.set_id = remote.set_id
         WHERE {where_clause} ORDER BY local.set_id"
    );
    let mut stmt = conn
        .prepare(&sql)
        .context("preparing the conflict comparison")?;
    stmt.query_map([], |row| row.get::<_, String>(0))
        .context("comparing shared sets")?
        .collect::<rusqlite::Result<Vec<_>>>()
        .context("reading a conflicting set id")
}
