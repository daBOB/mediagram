//! Whether a title belongs in the Anime department: Japanese animation, or
//! a hand-set override.
//!
//! A line-for-line port of the web player's own rule, `isAnime` in
//! `web/src/catalog/anime.ts` — one small pure function is easy to keep two
//! languages honest about; a whole projection module is not. Both are held
//! to the same cases, `web/test/fixtures/anime/cases.json`
//! (`shared_anime_fixtures.rs`), so a change here that only this crate's own
//! tests would catch never lands unnoticed.
//!
//! The genre check is by name, not TMDB id — see the TS twin's own doc for
//! why (this library is `de-DE`, and TMDB names genre 16 "Animation" the
//! same in English, German and French; a library in another language does
//! not match, the one ceiling this rule has).

use std::collections::HashMap;

use rusqlite::Connection;

use super::SOURCE;

/// Whether `kind` and `genres` describe an anime title, once `forced` (a
/// hand-set override, or `None` for automatic) and `original_language` have
/// had their say.
///
/// `kind` is the catalog set's own spelling (`movie`/`ep`/…), the same
/// string [`crate::dto::SetSummary::kind`] carries — only those two kinds
/// can be anime at all, so a documentary or a course is never anime, hand-set
/// override or not.
pub fn is_anime(kind: &str, genres: &[String], original_language: Option<&str>, forced: Option<bool>) -> bool {
    if kind != "movie" && kind != "ep" {
        return false;
    }
    if let Some(forced) = forced {
        return forced;
    }
    original_language == Some("ja") && genres.iter().any(|genre| genre == "Animation")
}

/// Every hand-set anime decision, by the poster key that names it
/// (`tmdb-movie-603`, `tmdb-tv-1396`) — the same key [`super::genres`] and
/// [`super::facts`] use.
///
/// Empty for an index written before v11 (no `anime_overrides` table yet)
/// or with no overrides at all. A `NULL` row ("back to automatic") is
/// skipped, the same as the TS twin drops it: an absent entry already means
/// "no override", so keeping the row would only cost every lookup a wasted
/// check for a value that means the same as absent.
pub fn anime_overrides(conn: &Connection) -> rusqlite::Result<HashMap<String, bool>> {
    let mut overrides = HashMap::new();
    let present: bool = conn.query_row(
        "SELECT EXISTS(SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = 'anime_overrides')",
        [],
        |row| row.get(0),
    )?;
    if !present {
        return Ok(overrides);
    }
    let mut stmt =
        conn.prepare("SELECT kind, id, anime FROM anime_overrides WHERE source = ?1 AND anime IS NOT NULL")?;
    let rows = stmt.query_map([SOURCE], |row| {
        let kind: String = row.get(0)?;
        let id: i64 = row.get(1)?;
        let anime: i64 = row.get(2)?;
        Ok((mlib_spec::package::tmdb_key(&kind, id), anime != 0))
    })?;
    for row in rows {
        let (key, anime) = row?;
        overrides.insert(key, anime);
    }
    Ok(overrides)
}

#[cfg(test)]
#[path = "anime_tests.rs"]
mod tests;
