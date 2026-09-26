//! The `credits` table: who is credited on a title — cast, director(s), and
//! (a series') creators — as opposed to what a title is about. Keyed like
//! `shows`, with one more part: a title credits more than one person, so
//! `ord`, the position this title lists them in, completes the key.
//!
//! Replaced whole per title, the way `shows::upsert` replaces a description
//! whole: a later fetch reflects TMDB's current credits, not an old cast
//! list with new rows wedged beside it.

use mediagram_tmdb::credits::CreditRow;
use mediagram_tmdb::posters::{PORTRAIT_WIDTH, PosterRef, is_image_path, kind_key};
use mlib_spec::Kind;
use rusqlite::{Connection, OptionalExtension, params};

use crate::shows::SOURCE;

#[path = "credits_read.rs"]
mod read;
pub use read::{Credited, PeopleHit, PersonCredits, TitleCredits, for_person, for_title, people_matching};

/// Replaces a title's credited people, wholly: every row for `(kind, id)` is
/// deleted first, so a shrunk cast list, or a director no longer credited,
/// leaves no orphaned row.
///
/// All or nothing, inside a savepoint: a run killed halfway through a cast
/// would otherwise leave a partial one that [`has`] then reports as done, so
/// no later run would fill it in. A savepoint rather than a transaction,
/// because a caller may already hold one; it also makes the dozen inserts
/// one disk sync instead of a dozen.
pub fn upsert(conn: &Connection, kind: Kind, id: u64, rows: &[CreditRow]) -> rusqlite::Result<()> {
    conn.execute_batch("SAVEPOINT credits_upsert")?;
    let written = replace(conn, kind, id, rows);
    match written {
        Ok(()) => conn.execute_batch("RELEASE credits_upsert"),
        Err(err) => {
            // Undo this title's half-write, then report what went wrong.
            let _ = conn.execute_batch("ROLLBACK TO credits_upsert; RELEASE credits_upsert");
            Err(err)
        }
    }
}

fn replace(conn: &Connection, kind: Kind, id: u64, rows: &[CreditRow]) -> rusqlite::Result<()> {
    conn.execute(
        "DELETE FROM credits WHERE source = ?1 AND kind = ?2 AND id = ?3",
        params![SOURCE, kind_key(kind), id],
    )?;
    for row in rows {
        conn.execute(
            "INSERT INTO credits(source, kind, id, ord, person_id, name, role, dept, profile)
             VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9)",
            params![
                SOURCE,
                kind_key(kind),
                id,
                row.ord,
                row.person_id,
                row.name,
                row.role,
                row.dept,
                row.profile_path,
            ],
        )?;
    }
    Ok(())
}

/// Whether a title already has credits recorded — the check the backfill
/// uses to avoid asking TMDB again for a title it already has the answer for.
pub fn has(conn: &Connection, kind: Kind, id: u64) -> rusqlite::Result<bool> {
    conn.query_row(
        "SELECT EXISTS(SELECT 1 FROM credits WHERE source = ?1 AND kind = ?2 AND id = ?3)",
        params![SOURCE, kind_key(kind), id],
        |row| row.get(0),
    )
}

/// Every distinct person this index credits with a known portrait, keyed
/// `tmdb-person-<id>` — the same key shape a poster or a backdrop uses —
/// deduplicated across every title the way `posters::resolve_posters` dedupes
/// a poster shared by several titles.
///
/// Reads `credits.profile` rather than asking TMDB again: a device that only
/// ever received a channel snapshot has no TMDB cache of its own, but the
/// snapshot already carries every profile path `mediagram metadata` recorded.
/// Empty for an index that predates `credits` (v8 and older) — the same
/// accommodation `shows::optional_column` makes for a column, extended here
/// to a whole missing table.
pub fn portraits(conn: &Connection) -> rusqlite::Result<Vec<PosterRef>> {
    if !read::has_table(conn)? {
        return Ok(Vec::new());
    }
    let mut stmt = conn.prepare(
        "SELECT person_id, MIN(profile) FROM credits
          WHERE source = ?1 AND profile IS NOT NULL
          GROUP BY person_id
          ORDER BY person_id",
    )?;
    let rows = stmt.query_map(params![SOURCE], |row| {
        let person_id: u64 = row.get(0)?;
        let path: String = row.get(1)?;
        Ok((person_id, path))
    })?;
    let mut found = Vec::new();
    for row in rows {
        let (person_id, path) = row?;
        if !is_image_path(&path) {
            continue;
        }
        found.push(PosterRef {
            key: format!("tmdb-person-{person_id}"),
            path,
            backdrop_width: Some(PORTRAIT_WIDTH),
        });
    }
    Ok(found)
}

/// The profile path TMDB gave for one person's credit, if this index ever
/// recorded one and the path is well-formed — the single-person counterpart
/// of [`portraits`], for a caller (`Core::fetch_portrait`) that only needs
/// one face rather than every one this index holds.
pub fn profile_of(conn: &Connection, person_id: u64) -> rusqlite::Result<Option<String>> {
    if !read::has_table(conn)? {
        return Ok(None);
    }
    let path: Option<String> = conn
        .query_row(
            "SELECT profile FROM credits
              WHERE source = ?1 AND person_id = ?2 AND profile IS NOT NULL LIMIT 1",
            params![SOURCE, person_id],
            |row| row.get(0),
        )
        .optional()?;
    Ok(path.filter(|p| is_image_path(p)))
}

#[cfg(test)]
#[path = "credits_tests.rs"]
mod tests;
