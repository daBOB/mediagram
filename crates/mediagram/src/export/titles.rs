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
            let tmdb: Option<i64> = row.get(1)?;
            Ok((kind, tmdb))
        })
        .context("listing titles")?;

    let mut out = Vec::new();
    for row in rows {
        let (kind, tmdb) = row.context("reading a title row")?;
        let Some(id) = mlib_spec::ids::id_from_column(tmdb) else {
            continue;
        };
        match kind.parse::<Kind>() {
            Ok(kind @ (Kind::Movie | Kind::Ep)) => out.push((kind, id)),
            // Poster keys exist only for films and series; a course or a
            // documentary is keyed by its title, not a provider id.
            Ok(Kind::Tut | Kind::Doc | Kind::Docu) => {}
            // A kind this build does not know cannot be given a poster key.
            Err(err) => tracing::warn!(%err, "no poster"),
        }
    }
    Ok(out)
}
