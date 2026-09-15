//! What the export reads out of the snapshot: which titles need a poster, and
//! how much the package describes. Every query runs against the copy, never
//! the live index.

use anyhow::{Context, Result};
use mlib_spec::Kind;
use rusqlite::Connection;

/// Distinct `(kind, tmdb id)` pairs. Movie and television ids are independent
/// at TMDB, so the kind travels with the id and the two never collapse.
pub fn distinct_titles(conn: &Connection) -> Result<Vec<(Kind, u64)>> {
    let mut stmt = conn
        .prepare("SELECT DISTINCT kind, tmdb FROM sets WHERE tmdb IS NOT NULL ORDER BY kind, tmdb")
        .context("preparing the title query")?;
    let rows = stmt
        .query_map([], |row| {
            let kind: String = row.get(0)?;
            let tmdb: i64 = row.get(1)?;
            Ok((kind, tmdb))
        })
        .context("listing titles")?;

    let mut out = Vec::new();
    for row in rows {
        let (kind, tmdb) = row.context("reading a title row")?;
        let Ok(id) = u64::try_from(tmdb) else {
            continue;
        };
        match kind.as_str() {
            "movie" => out.push((Kind::Movie, id)),
            "ep" => out.push((Kind::Ep, id)),
            // A kind this build does not know cannot be given a poster key.
            other => tracing::warn!(kind = other, "unknown set kind, no poster"),
        }
    }
    Ok(out)
}

/// Set and part counts, for the manifest.
pub fn counts(conn: &Connection) -> Result<(u64, u64)> {
    let sets: i64 = conn
        .query_row("SELECT COUNT(*) FROM sets", [], |r| r.get(0))
        .context("counting sets")?;
    let parts: i64 = conn
        .query_row("SELECT COUNT(*) FROM parts", [], |r| r.get(0))
        .context("counting parts")?;
    Ok((sets.max(0) as u64, parts.max(0) as u64))
}
