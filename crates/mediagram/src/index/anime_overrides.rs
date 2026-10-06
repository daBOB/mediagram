//! `anime_overrides`: a hand-set decision that a title is, or is not, anime
//! — kept apart from `shows` because `shows::upsert` replaces a row whole on
//! every `metadata` run and would clobber a person's choice. Keyed the way a
//! poster key is (`source`, `kind`, `id`), so one row covers every set of a
//! title, present and future.
//!
//! `anime` is `NULL` for "back to the automatic rule", not a deleted row: a
//! merge needs a timestamped row to carry that decision to the other machine
//! (see `index::merge::anime_overrides`), and a deleted row carries
//! nothing.

use mediagram_tmdb::posters::kind_key;
use mlib_spec::Kind;
use rusqlite::{Connection, OptionalExtension, params};

const SOURCE: &str = "tmdb";

/// The hand-set decision for a title, or `None` when no row exists or the
/// row says "automatic" (`anime IS NULL`) — the two read the same to a
/// caller deciding whether a title is anime.
pub fn get(conn: &Connection, kind: Kind, id: u64) -> rusqlite::Result<Option<bool>> {
    conn.query_row(
        "SELECT anime FROM anime_overrides WHERE source = ?1 AND kind = ?2 AND id = ?3",
        params![SOURCE, kind_key(kind), id],
        |row| row.get::<_, Option<bool>>(0),
    )
    .optional()
    .map(Option::flatten)
}

/// Writes the override, replacing whatever was there. `anime = None` clears
/// it back to automatic but keeps the row, stamped with `at` — a Unix
/// timestamp — so a merge can tell a clear apart from nothing having
/// happened yet.
pub fn set(conn: &Connection, kind: Kind, id: u64, anime: Option<bool>, at: i64) -> rusqlite::Result<()> {
    conn.execute(
        "INSERT INTO anime_overrides(source, kind, id, anime, set_at)
         VALUES (?1, ?2, ?3, ?4, ?5)
         ON CONFLICT(source, kind, id) DO UPDATE SET anime = excluded.anime, set_at = excluded.set_at",
        params![SOURCE, kind_key(kind), id, anime, at],
    )?;
    Ok(())
}

#[cfg(test)]
#[path = "anime_overrides_tests.rs"]
mod tests;
