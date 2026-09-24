//! Finding a set by what it is rather than by id: an episode by show and
//! numbers, a lesson or document by course and numbers. What lets a bulk
//! add be re-run after an interruption and skip what already finished,
//! without caring where a file sits.

use anyhow::{Context, Result};
use mlib_spec::Kind;
use rusqlite::{Connection, OptionalExtension, params};

use crate::index::status::SetStatus;

/// Whether a lesson with this identity is already uploaded and complete.
///
/// Identity is the collection id plus the chapter and lesson numbers, which
/// is what lets `add-course` be re-run after an interruption: it survives
/// renaming or moving the course folder, because none of the three comes
/// from a path.
pub fn complete_lesson_exists(
    conn: &Connection,
    cid: &str,
    chapter: u32,
    lesson: u32,
) -> Result<bool> {
    // `episode` holds the JSON encoding of the caption's `e` field, so a
    // single lesson number is stored as a bare integer.
    let episode = serde_json::to_string(&mlib_spec::caption::Episode::Single(lesson))?;
    let found: Option<i64> = conn
        .query_row(
            "SELECT 1 FROM sets
             WHERE group_key = ?1 AND season = ?2 AND episode = ?3
               AND kind = ?4 AND status = ?5",
            params![
                cid,
                chapter,
                episode,
                Kind::Tut.as_str(),
                SetStatus::Complete
            ],
            |row| row.get(0),
        )
        .optional()?;
    Ok(found.is_some())
}

/// The status of an episode already recorded for this show, if any.
///
/// Identity is the show plus the two numbers, so a bulk add re-run after an
/// interruption skips what finished without caring where the file sits.
pub fn episode_status(
    conn: &Connection,
    tmdb: u64,
    season: u32,
    episode: u32,
) -> Result<Option<SetStatus>> {
    let tmdb = i64::try_from(tmdb).context("looking up episode by TMDB id")?;
    let episode = serde_json::to_string(&mlib_spec::caption::Episode::Single(episode))?;
    let status: Option<SetStatus> = conn
        .query_row(
            "SELECT status FROM sets
             WHERE tmdb = ?1 AND season = ?2 AND episode = ?3 AND kind = 'ep'",
            params![tmdb, season, episode],
            |row| row.get(0),
        )
        .optional()?;
    Ok(status)
}

/// The status of a document already in the index, if any.
///
/// Identity is the collection id plus the chapter and the number within it,
/// the same three things that identify a lesson. The `kind` is what keeps the
/// two apart: a handout deliberately carries its lesson's number, so without
/// it a document would be mistaken for the lesson it sits beside and neither
/// would ever be uploaded twice — or at all.
pub fn document_status(
    conn: &Connection,
    cid: &str,
    chapter: u32,
    number: u32,
) -> Result<Option<SetStatus>> {
    let number = serde_json::to_string(&mlib_spec::caption::Episode::Single(number))?;
    let status: Option<SetStatus> = conn
        .query_row(
            "SELECT status FROM sets
             WHERE group_key = ?1 AND season = ?2 AND episode = ?3 AND kind = 'doc'",
            params![cid, chapter, number],
            |row| row.get(0),
        )
        .optional()?;
    Ok(status)
}

/// The status of a lesson already in the index, if any, so a caller can tell
/// "finished" from "interrupted" and route the second to `resume`.
pub fn lesson_status(
    conn: &Connection,
    cid: &str,
    chapter: u32,
    lesson: u32,
) -> Result<Option<SetStatus>> {
    let episode = serde_json::to_string(&mlib_spec::caption::Episode::Single(lesson))?;
    let status: Option<SetStatus> = conn
        .query_row(
            "SELECT status FROM sets
             WHERE group_key = ?1 AND season = ?2 AND episode = ?3 AND kind = 'tut'",
            params![cid, chapter, episode],
            |row| row.get(0),
        )
        .optional()?;
    Ok(status)
}
