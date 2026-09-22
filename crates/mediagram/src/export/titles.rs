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
        match kind.parse::<Kind>() {
            Ok(kind @ (Kind::Movie | Kind::Ep)) => out.push((kind, id)),
            // Poster keys exist only for films and series.
            Ok(Kind::Tut | Kind::Doc) => {}
            // A kind this build does not know cannot be given a poster key.
            Err(err) => tracing::warn!(%err, "no poster"),
        }
    }
    Ok(out)
}

/// The same, opening the live index read-only for a command that only reads.
///
/// Read-only at the SQLite level rather than by convention: a command that
/// describes a library has no business migrating its schema or checkpointing
/// a WAL the uploader owns.
pub fn distinct_titles_in(live: &std::path::Path) -> Result<Vec<(Kind, u64)>> {
    let conn = Connection::open_with_flags(
        live,
        rusqlite::OpenFlags::SQLITE_OPEN_READ_ONLY | rusqlite::OpenFlags::SQLITE_OPEN_URI,
    )
    .with_context(|| format!("opening {} read-only", live.display()))?;
    distinct_titles(&conn)
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
