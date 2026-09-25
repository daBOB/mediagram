//! A show's genres, from the same row [`super::certifications`] reads its
//! rating from.

use std::collections::HashMap;

use rusqlite::Connection;

use super::SOURCE;

/// Every show's genres, by the poster key that names it (`tmdb-movie-603`).
///
/// One query for the whole catalog rather than one per row, the same shape
/// `certifications` uses: a listing builds every row in one pass. A show
/// with no genres recorded is simply absent from the map, so a caller
/// merging two sources of this (see `store::list_sets`) can tell "said
/// nothing" apart from "said it has none".
pub fn genres(conn: &Connection) -> rusqlite::Result<HashMap<String, Vec<String>>> {
    let mut stmt = conn.prepare("SELECT kind, id, genres FROM shows WHERE source = ?1")?;
    let rows = stmt.query_map([SOURCE], |row| {
        let kind: String = row.get(0)?;
        let id: i64 = row.get(1)?;
        let genres: Option<String> = row.get(2)?;
        Ok((format!("tmdb-{kind}-{id}"), genres))
    })?;

    let mut by_key = HashMap::new();
    for row in rows {
        let (key, genres) = row?;
        let split = split_genres(genres.as_deref());
        if !split.is_empty() {
            by_key.insert(key, split);
        }
    }
    Ok(by_key)
}

/// Comma-separated, trimmed, empties dropped — the same split
/// `web/src/shows.ts`'s `providerFactsByShow` applies to the same column.
fn split_genres(raw: Option<&str>) -> Vec<String> {
    raw.unwrap_or("")
        .split(',')
        .map(str::trim)
        .filter(|name| !name.is_empty())
        .map(str::to_string)
        .collect()
}

#[cfg(test)]
#[path = "genres_tests.rs"]
mod tests;
