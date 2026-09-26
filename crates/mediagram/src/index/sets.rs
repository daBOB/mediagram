//! `sets` table queries. The row type lives in `index::set_row`.

use anyhow::{Context, Result};

use rusqlite::{Connection, OptionalExtension, params};

use crate::index::set_row::SetRow;
use crate::index::status::SetStatus;

const COLUMNS: &str =
    "set_id, kind, tmdb, tvdb, imdb, show, chap, path, title, year, season, episode,
    abs, quality, hdr, container, vcodec, acodec, alang, slang, duration, variant, group_key,
    total, part_count, set_hash, status, created_at, spec_version";

/// Inserts a new set row. Fails if `set_id` already exists.
pub fn insert_set(conn: &Connection, row: &SetRow) -> Result<()> {
    let alang = serde_json::to_string(&row.alang)?;
    let slang = serde_json::to_string(&row.slang)?;
    let tmdb = row
        .tmdb
        .map(i64::try_from)
        .transpose()
        .context("storing TMDB id")?;
    let tvdb = row
        .tvdb
        .map(i64::try_from)
        .transpose()
        .context("storing TVDB id")?;
    let total = row.total as i64;
    conn.execute(
        &format!(
            "INSERT INTO sets({COLUMNS}) VALUES (
                ?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11, ?12,
                ?13, ?14, ?15, ?16, ?17, ?18, ?19, ?20, ?21, ?22,
                ?23, ?24, ?25, ?26, ?27, ?28, ?29)"
        ),
        params![
            row.set_id,
            row.kind.as_str(),
            tmdb,
            tvdb,
            row.imdb,
            row.show,
            row.chap,
            row.path,
            row.title,
            row.year,
            row.season,
            row.episode_json()?,
            row.abs,
            row.quality,
            row.hdr,
            row.container,
            row.vcodec,
            row.acodec,
            alang,
            slang,
            row.duration,
            row.variant,
            row.group_key,
            total,
            row.part_count,
            row.set_hash,
            row.status,
            row.created_at,
            row.spec_version,
        ],
    )?;
    Ok(())
}

/// Updates `status` for one set.
pub fn set_status(conn: &Connection, set_id: &str, status: SetStatus) -> Result<()> {
    conn.execute(
        "UPDATE sets SET status = ?1 WHERE set_id = ?2",
        params![status, set_id],
    )?;
    Ok(())
}

/// Moves a set's `created_at` back to `at` if `at` is earlier, never forward.
/// A rescan meets a set's parts in any order and dates it by the earliest.
pub fn date_no_later_than(conn: &Connection, set_id: &str, at: i64) -> Result<()> {
    conn.execute(
        "UPDATE sets SET created_at = ?1 WHERE set_id = ?2 AND created_at > ?1",
        params![at, set_id],
    )?;
    Ok(())
}

/// Writes a corrected row's metadata, and only its metadata.
///
/// `kind` is included: which shelf a set belongs on is metadata, and a film
/// uploaded through the course path is filed as a lesson until this fixes it.
///
/// The columns left out are deliberate: `total`, `part_count`, `set_hash`,
/// `status`, `container` and the codec fields describe the bytes sitting in
/// the channel. `verify` checks them and a player seeks with them, so a
/// correction that could touch them would turn a fixed title into a broken
/// set. The guard is here rather than at the call site because a row is an
/// easy thing to hand over with the wrong numbers in it.
pub fn update_metadata(conn: &Connection, row: &SetRow) -> Result<()> {
    let tmdb = row
        .tmdb
        .map(i64::try_from)
        .transpose()
        .context("storing TMDB id")?;
    let tvdb = row
        .tvdb
        .map(i64::try_from)
        .transpose()
        .context("storing TVDB id")?;
    conn.execute(
        "UPDATE sets SET kind = ?1, show = ?2, chap = ?3, path = ?4, title = ?5, year = ?6,
                         season = ?7, episode = ?8, abs = ?9, tmdb = ?10, tvdb = ?11, imdb = ?12
         WHERE set_id = ?13",
        params![
            row.kind.as_str(),
            row.show,
            row.chap,
            row.path,
            row.title,
            row.year,
            row.season,
            row.episode_json()?,
            row.abs,
            tmdb,
            tvdb,
            row.imdb,
            row.set_id,
        ],
    )
    .with_context(|| format!("updating metadata for {}", row.set_id))?;
    Ok(())
}

/// Records the final `set_hash` and marks the set `complete`.
pub fn set_hash_and_complete(conn: &Connection, set_id: &str, hash: &str) -> Result<()> {
    conn.execute(
        "UPDATE sets SET set_hash = ?1, status = ?3 WHERE set_id = ?2",
        params![hash, set_id, SetStatus::Complete],
    )?;
    Ok(())
}

/// One set by id, if it exists.
pub fn get_set(conn: &Connection, set_id: &str) -> Result<Option<SetRow>> {
    conn.query_row(
        &format!("SELECT {COLUMNS} FROM sets WHERE set_id = ?1"),
        [set_id],
        SetRow::from_row,
    )
    .optional()
    .map_err(Into::into)
}

/// One set whose `show` or `title` is exactly `name`, for resolving the
/// `<set-id|title>` argument `mediagram artwork` takes. Arbitrary among
/// several matches: a course, a documentary collection or a film's own title
/// only collides when two very different uploads share the same words, and
/// any of them names the same art key by that name.
pub fn find_by_name(conn: &Connection, name: &str) -> Result<Option<SetRow>> {
    conn.query_row(
        &format!("SELECT {COLUMNS} FROM sets WHERE show = ?1 OR title = ?1 LIMIT 1"),
        [name],
        SetRow::from_row,
    )
    .optional()
    .map_err(Into::into)
}

/// Every set still `pending`, oldest first (so `resume` finishes older sets before newer ones).
pub fn list_pending(conn: &Connection) -> Result<Vec<SetRow>> {
    let mut stmt = conn.prepare(&format!(
        "SELECT {COLUMNS} FROM sets WHERE status = ?1 ORDER BY created_at"
    ))?;
    let rows = stmt
        .query_map([SetStatus::Pending], SetRow::from_row)?
        .collect::<rusqlite::Result<Vec<_>>>()?;
    Ok(rows)
}

/// How many sets the index holds, whatever their status.
pub fn count(conn: &Connection) -> Result<u64> {
    conn.query_row("SELECT COUNT(*) FROM sets", [], |row| row.get(0))
        .context("counting sets")
}

/// How many sets are wholly in the channel.
pub fn count_complete(conn: &Connection) -> Result<u64> {
    conn.query_row(
        "SELECT COUNT(*) FROM sets WHERE status = ?1",
        [SetStatus::Complete],
        |row| row.get(0),
    )
    .context("counting complete sets")
}

// Covered by `tests/index_state.rs`: insert/get/list_pending/complete round
// trip through a real sqlite file, plus the not-found case.
